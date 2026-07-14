package com.example.pathfinder.detection;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.util.Log;
import android.util.Pair;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.gpu.CompatibilityList;
import org.tensorflow.lite.Delegate;
import org.tensorflow.lite.DelegateFactory;
import org.tensorflow.lite.gpu.GpuDelegate;
import org.tensorflow.lite.support.common.FileUtil;
import org.tensorflow.lite.support.common.ops.CastOp;
import org.tensorflow.lite.support.common.ops.NormalizeOp;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.MappedByteBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

public class YoloBase implements DetectorModel{
    private Interpreter interpreter = null;
    private List<String> modelLabels;

    private float CONFIDENCE_THRESHOLD = 0.3f;
    private float IOU_THRESHOLD = 0.4f;

    public YoloBase(Context context, String modelPath, String labelsPath) throws IOException {
        MappedByteBuffer modelFile = FileUtil.loadMappedFile(context, modelPath);
        Interpreter.Options options = new Interpreter.Options();

        CompatibilityList compatList = new CompatibilityList();
        if(compatList.isDelegateSupportedOnThisDevice()){
            // if the device has a supported GPU, add the GPU delegate
            var delegateOptions = compatList.getBestOptionsForThisDevice();
            options.addDelegate(new GpuDelegate(delegateOptions));
            Log.i("YoloBase", "GPU delegate is supported and added.");
        } else {
            // if the GPU is not supported, use CPU
            options.setNumThreads(Runtime.getRuntime().availableProcessors());
            Log.i("YoloBase", "GPU delegate is not supported. Using CPU.");
        }

        this.interpreter = new Interpreter(modelFile, options);

        List<String> labelList = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(context.getAssets().open(labelsPath)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                labelList.add(line);
            }
        }
        this.modelLabels = labelList;
    }

    @Override
    public TensorImage PreProcess(Bitmap ogImg) {
        var inputImageWidth = interpreter.getInputTensor(0).shape()[2];
        var inputImageHeight = interpreter.getInputTensor(0).shape()[1];
        Matrix matrix = new Matrix();
        matrix.postRotate(90);
        Bitmap rotated = Bitmap.createBitmap(ogImg, 0, 0,
                ogImg.getWidth(), ogImg.getHeight(),
                matrix, true);

        Bitmap resizedBitmap = Bitmap.createScaledBitmap(rotated, inputImageWidth, inputImageHeight, false);

        ImageProcessor imageProcessor = new ImageProcessor.Builder()
                .add(new NormalizeOp(0f, 255f))
                .add(new CastOp(DataType.FLOAT32))
                .build();

        TensorImage tensorImage = new TensorImage(DataType.FLOAT32);
        tensorImage.load(resizedBitmap);
        return imageProcessor.process(tensorImage);
    }

    @Override
    public List<BoundingBox> PostProcess(TensorBuffer outputBuffer) {

        var array = outputBuffer.getFloatArray();
        int numChannels = outputBuffer.getShape()[1];
        int numElements = outputBuffer.getShape()[2];

        List<BoundingBox> boundingBoxes = new ArrayList<BoundingBox>();

        for (int j = 0; j < 8400; j++) {
            var maxConf = CONFIDENCE_THRESHOLD; // Menor confiança admitida
            var maxIdx = -1; //Classe com maior confiança inicializada como 'nenhuma'
            int i = 4; //Inicializa i para percorrer as 80 classes

            var arrayIdx = numElements * i + j; // Indice do array. 8400 * i -> 'linha correta' + j -> 'coluna correta'

            while (i < numChannels) { //Numero de canais da saida [1, 84, 8400]
                if (array[arrayIdx] > maxConf) {
                    maxConf = array[arrayIdx];
                    maxIdx = i - 4; // Corrige o offset das colunas das caixas para selecionar o rotulo correto
                }
                i++;
                arrayIdx += numElements; // Muda para proxima linha/proxima classe de probs
            }


            if (maxConf > CONFIDENCE_THRESHOLD) {
                var clsName = this.modelLabels.get(maxIdx);
                var cx = array[j]; // 0
                var cy = array[j + numElements]; // 1
                var w = array[j + numElements * 2];
                var h = array[j + numElements * 3];
                var x1 = cx - (w / 2F);
                var y1 = cy - (h / 2F);
                var x2 = cx + (w / 2F);
                var y2 = cy + (h / 2F);

                //Descarta boxes que vazam das dimensoes da imagem
                if (x1 < 0F || x1 > 1F) continue;
                if (y1 < 0F || y1 > 1F) continue;
                if (x2 < 0F || x2 > 1F) continue;
                if (y2 < 0F || y2 > 1F) continue;


                boundingBoxes.add(new BoundingBox(x1, y1, x2, y2, cx, cy, h, w, maxConf, maxIdx, clsName));
            }
        }
        return applyNMS(boundingBoxes);
    }

    @Override
    public Pair<Bitmap, List<BoundingBox>> Detect(Bitmap img) {
        TensorImage tensorImage = PreProcess(img);
        TensorBuffer outputBuffer = TensorBuffer.createFixedSize(interpreter.getOutputTensor(0).shape(), DataType.FLOAT32);
        long startTime = System.nanoTime();

        interpreter.run(tensorImage.getBuffer(), outputBuffer.getBuffer());
        long endTime = System.nanoTime();
        Log.d("Performance", "Tempo de execução YOLO: " + (endTime - startTime) / 1_000_000.0 + " ms");


        return new Pair<>(img, PostProcess(outputBuffer));
    }

    //Private methods:
    private List<BoundingBox> applyNMS(List<BoundingBox> boxes) {
        // Step 1: Sort boxes by confidence score in descending order.
        boxes.sort(new Comparator<BoundingBox>() {
            @Override
            public int compare(BoundingBox o1, BoundingBox o2) {
                return Float.compare(o2.cnf, o1.cnf);
            }
        });

        List<BoundingBox> selectedBoxes = new ArrayList<>();

        // Step 2: Loop while there are boxes to process.
        while (!boxes.isEmpty()) {
            // Step 3: Select the box with the highest confidence.
            BoundingBox first = boxes.get(0);
            selectedBoxes.add(first);
            boxes.remove(0);

            // Step 4: Iterate through the remaining boxes and remove ones that overlap significantly.
            Iterator<BoundingBox> iterator = boxes.iterator();
            while (iterator.hasNext()) {
                BoundingBox nextBox = iterator.next();
                // Calculate the Intersection over Union (IoU).
                float iou = calculateIoU(first, nextBox);
                // If IoU is greater than the threshold, remove the box.
                if (iou >= IOU_THRESHOLD) {
                    iterator.remove();
                }
            }
        }

        return selectedBoxes;
    }

    private float calculateIoU(BoundingBox box1, BoundingBox box2) {
        float xA = Math.max(box1.x1, box2.x1);
        float yA = Math.max(box1.y1, box2.y1);
        float xB = Math.min(box1.x2, box2.x2);
        float yB = Math.min(box1.y2, box2.y2);

        // Calculate the area of the intersection rectangle.
        float interArea = Math.max(0, xB - xA) * Math.max(0, yB - yA);

        // Calculate the area of both bounding boxes.
        float box1Area = (box1.x2 - box1.x1) * (box1.y2 - box1.y1);
        float box2Area = (box2.x2 - box2.x1) * (box2.y2 - box2.y1);

        // Calculate the area of the union.
        float unionArea = box1Area + box2Area - interArea;

        // Compute the IoU. Return 0 if unionArea is 0 to avoid division by zero.
        return unionArea > 0 ? interArea / unionArea : 0;
    }

}

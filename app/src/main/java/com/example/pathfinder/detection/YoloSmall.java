package com.example.pathfinder.detection;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.util.Log;
import android.util.Pair;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.gpu.CompatibilityList;
import org.tensorflow.lite.gpu.GpuDelegate;
import org.tensorflow.lite.nnapi.NnApiDelegate;
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

public class YoloSmall extends YoloBase {
    static final String MODEL_PATH = "yolo11s_float32.tflite";
    static final String LABELS_PATH = "labels.txt";

    public YoloSmall(Context context) throws IOException {
        super(context, MODEL_PATH, LABELS_PATH);
    }

}

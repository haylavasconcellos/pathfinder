package com.example.pathfinder.detection;

import android.content.Context;
import java.io.IOException;

public class YoloNano extends YoloBase{
    static final String MODEL_PATH = "yolo11n_float32.tflite";
    static final String LABELS_PATH = "labels.txt";
    public YoloNano(Context context) throws IOException {
        super(context, MODEL_PATH, LABELS_PATH);
    }

}

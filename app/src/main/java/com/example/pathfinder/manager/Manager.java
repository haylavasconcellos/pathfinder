package com.example.pathfinder.manager;


import android.content.Context;
import android.graphics.Bitmap;
import android.media.Image;
import android.os.Handler;
import android.os.Looper;
import android.se.omapi.Session;
import android.util.Log;
import android.util.Pair;

import androidx.camera.core.impl.Config;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.pathfinder.detection.BoundingBox;
import com.example.pathfinder.detection.DetectorModel;
import com.example.pathfinder.risk.RiskAnalyzer;
import com.example.pathfinder.risk.RiskAssessment;
import com.example.pathfinder.slam.ARCoreDistanceCalculation;
import com.example.pathfinder.tts.TTS;
import com.example.pathfinder.tts.TTSMessage;
import com.example.pathfinder.ui.OverlayView;
import com.example.pathfinder.utils.ImageUtils;
import com.google.ar.core.Frame;
import com.google.ar.core.TrackingFailureReason;
import com.google.ar.sceneform.FrameTime;
import com.google.ar.sceneform.ux.ArFragment;

import com.example.pathfinder.risk.RiskLevel;
import java.util.List;

public class Manager {
    private final DetectorModel detector;
    private final OverlayView overlayView;
    private final RiskAnalyzer riskAnalyzer;
    private final TTS tts;

    private final ArFragment arFragment;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private boolean isProcessing = false;

    private boolean shouldProcess = false;
    private final MutableLiveData<Boolean> shouldAlert = new MutableLiveData<>(true);
    private final MutableLiveData<Boolean> showMetricsOnScreen = new MutableLiveData<>(false);
    private final MutableLiveData<Float> FPS = new MutableLiveData<>(0f);
    private final MutableLiveData<Float> latency = new MutableLiveData<>(0f);

    float EPSILON = 0.05f; // distância mínima para considerar válida

    private int arErrorMessageCooldown = 0;

    // Metrics
    private int framesProcessed = 0;
    private long lastFpsTimestamp = 0;

    public Manager(Context context, DetectorModel detector, OverlayView overlayView, ArFragment arFragment, int screenWidth, int screenHeight) {
        this.detector = detector;
        this.overlayView = overlayView;
        this.arFragment = arFragment;
        this.riskAnalyzer = new RiskAnalyzer(screenWidth, screenHeight);
        this.tts = new TTS(context);
    }

    private static TTSMessage.Priority convertRiskToTtsPriority(RiskLevel riskLevel) {
        if (riskLevel == null) {
            return TTSMessage.Priority.LOW;
        }

        switch (riskLevel) {
            case CRITICAL:
                return TTSMessage.Priority.CRITICAL;
            case HIGH:
                return TTSMessage.Priority.HIGH;
            case MEDIUM:
                return TTSMessage.Priority.MEDIUM;
            case SAFE:
            case LOW:
            default:
                // Grouping SAFE and LOW into the same TTS priority level
                return TTSMessage.Priority.LOW;
        }
    }

    public void startArCore() {
        arFragment.getArSceneView().getScene().addOnUpdateListener(this::handleFrameUpdate);
    }

    private void handleFrameUpdate(FrameTime frameTime) {
        if (!shouldProcess || isProcessing) return;

        isProcessing = true;
        long e2e_latency_start = System.nanoTime();

        Frame frame = arFragment.getArSceneView().getArFrame();
        if (frame == null) {
            isProcessing = false;
            return;
        }

        try {
            Image image = frame.acquireCameraImage();

            long startTime = System.nanoTime();
            Bitmap bitmap = ImageUtils.convertImageToBitmap(image); //convert frame to bitmap
            image.close();

            long afterConversion = System.nanoTime();
            Log.d("Performance", "Tempo de conversão: " + (afterConversion - startTime) / 1_000_000.0 + " ms");

            startTime = System.nanoTime();
            Pair<Bitmap, List<BoundingBox>> detectionResult = process(bitmap); //call model
            long endTime = System.nanoTime();
            Log.d("Performance", "Tempo de processamento YOLO: " + (endTime - startTime) / 1_000_000.0 + " ms");


            // Check ARCore state
            TrackingFailureReason arCoreState = ARCoreDistanceCalculation.getARCoreState(frame);
            if (arCoreState != null) {
                Log.e("ARCore", "ARCore state: " + arCoreState.toString());
                if (arErrorMessageCooldown == 0) {
                    String alert = "";
                    if (arCoreState == TrackingFailureReason.INSUFFICIENT_FEATURES) {
                        alert = "Não foi possível mapear esta área";
                    }
                    else if (arCoreState == TrackingFailureReason.EXCESSIVE_MOTION) {
                        alert = "Movimento excessivo. Por favor, mova o celular mais lentamente";
                    }
                    else if (arCoreState == TrackingFailureReason.INSUFFICIENT_LIGHT) {
                        alert = "Luz insuficiente. Por favor, ligue a luz do celular";
                    }

                    TTSMessage message = new TTSMessage(alert, TTSMessage.Priority.CRITICAL);
                    tts.speak(message);
                    arErrorMessageCooldown = 100; // o certo seria por segundos, mas aqui é MVP
                }
            }
            // Draw bounding boxes on the bitmap
            List<Pair<BoundingBox, Float>> objects = ARCoreDistanceCalculation.getObjectDistances(detectionResult, frame);
            mainHandler.post(() -> {
                overlayView.setResults(objects);
            });

            List<Pair<BoundingBox, Float>> nearObjects = ARCoreDistanceCalculation.getObjectsWithLessThanDistance(objects, 3f); //near objects, less than threshold

            for (Pair<BoundingBox, Float> obj : nearObjects) {
                Log.d("ARCoreDistance", "Objeto: " + obj.first.clsName + ", Distância: " + String.format("%.2f", obj.second) + " metros");
            }

            float distanceToNearestWall = ARCoreDistanceCalculation.distanceToNearestWall(frame);
            if (distanceToNearestWall > EPSILON && distanceToNearestWall < 3f) {
                Log.d("ARCoreDistance", "Parede, Distancia: " + distanceToNearestWall + " metros");
            }

            RiskAssessment riskAssessment = riskAnalyzer.analyzeRisk(nearObjects, distanceToNearestWall);
            Log.d("RiskAnalysis", riskAssessment.toString());

            if (riskAssessment.shouldAlert()) {
                Log.i("RiskAnalysis", "ALERTA: " + riskAssessment.getFullMessage());
                if (Boolean.TRUE.equals(shouldAlert.getValue())) {
                    TTSMessage message = new TTSMessage(riskAssessment.getMessage(),
                                                        convertRiskToTtsPriority(riskAssessment.getRiskLevel()));
                    tts.speak(message);
                }
            }

            // --- Metrics Calculation ---
            // E2E latency
            long e2e_latency_end = System.nanoTime();
            double latency = (e2e_latency_end - e2e_latency_start) / 1_000_000.0;
            this.latency.postValue((float) latency);
            Log.d("Performance", "E2E Latency: " + String.format("%.2f", latency) + " ms");

            // FPS Calculation
            framesProcessed++;
            long now = System.nanoTime();
            if (lastFpsTimestamp == 0) { // First frame
                lastFpsTimestamp = now;
            }
            long elapsedNanos = now - lastFpsTimestamp;

            if (elapsedNanos > 1_000_000_000) { // More than 1 second
                double fps = framesProcessed / (elapsedNanos / 1_000_000_000.0);
                this.FPS.postValue((float) fps);
                Log.d("Performance", "FPS: " + String.format("%.2f", fps));
                framesProcessed = 0;
                lastFpsTimestamp = now;
            }
            // --- End FPS Calculation ---

            isProcessing = false;
        } catch (Exception e) {
            Log.e("ARCore", "Erro ao capturar frame: " + e.getMessage());
            isProcessing = false;
        }
        finally {
            if (arErrorMessageCooldown > 0)
                arErrorMessageCooldown--;
        }
    }

    public Pair<Bitmap, List<BoundingBox>> process(Bitmap image) {
        return detector.Detect(image);
    }

    public LiveData<Boolean> getTtsInitialized() {
        return tts.isInitialized();
    }

    public LiveData<Boolean> getShouldAlert() {
        return shouldAlert;
    }

    public LiveData<Boolean> getShowMetricsOnScreen() {
        return showMetricsOnScreen;
    }

    public LiveData<Float> getFPS() {
        return FPS;
    }

    public LiveData<Float> getLatency() {
        return latency;
    }

    public void toggleProcessing() {
        shouldProcess = !shouldProcess;
        // Clear overlay when not processing frames
        if (!shouldProcess) {
            mainHandler.post(() -> overlayView.setResults(null));
        }
    }

    public void toggleTTS() {
        shouldAlert.setValue(!Boolean.TRUE.equals(shouldAlert.getValue()));
        // Clear TTS queue
        if (!Boolean.TRUE.equals(shouldAlert.getValue())) {
            tts.stop();
        }
    }

    public void toggleMetricsOnScreen() {
        showMetricsOnScreen.setValue(!Boolean.TRUE.equals(showMetricsOnScreen.getValue()));
    }

    public void repeatLastAlert() {
        if (Boolean.TRUE.equals(shouldAlert.getValue())) {
            tts.repeatLastAlert();
        }
    }

    public void shutdown() {
        tts.shutdown();
    }
}

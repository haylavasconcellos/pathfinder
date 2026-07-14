package com.example.pathfinder.slam;

import android.graphics.Bitmap;
import android.util.Pair;

import com.example.pathfinder.detection.BoundingBox;
import com.google.ar.core.Camera;
import com.google.ar.core.Frame;
import com.google.ar.core.Plane;
import com.google.ar.core.Pose;
import com.google.ar.core.TrackingFailureReason;
import com.google.ar.core.TrackingState;

import java.util.ArrayList;
import java.util.List;

public class ARCoreDistanceCalculation {


    public static TrackingFailureReason getARCoreState(Frame frame) {
        if (frame != null) {
            Camera camera = frame.getCamera();
            TrackingState trackingState = camera.getTrackingState();
            if (trackingState == TrackingState.TRACKING) {
                return null;
            }
            else {
                return camera.getTrackingFailureReason();
            }
        }
        return TrackingFailureReason.CAMERA_UNAVAILABLE;
    }
    public static List<Pair<BoundingBox, Float>> getObjectDistances(Pair<Bitmap, List<BoundingBox>> detectionResult, Frame frame) {
        List<BoundingBox> boundingBoxes = detectionResult.second;
        List<Pair<BoundingBox, Float>> distances = new ArrayList<>();

        for (BoundingBox box : boundingBoxes) {
            int centerX = (int) box.cx;
            int centerY = (int) box.cy;

            float calculatedDistance = calculateDistanceWithHitTest(frame, centerX, centerY);
            distances.add(new Pair<>(box, calculatedDistance));
        }

        return distances;
    }

    public static List<Pair<BoundingBox, Float>> getObjectsWithLessThanDistance(Pair<Bitmap, List<BoundingBox>> detectionResult, float threshold, Frame frame) {
        List<Pair<BoundingBox, Float>> objectDistances = getObjectDistances(detectionResult, frame);
        return getObjectsWithLessThanDistance(objectDistances, threshold);
    }

    public static List<Pair<BoundingBox, Float>> getObjectsWithLessThanDistance(List<Pair<BoundingBox, Float>> objectDistances, float threshold) {
        List<Pair<BoundingBox, Float>> nearObjects = new ArrayList<>();

        for (Pair<BoundingBox, Float> pair : objectDistances) {
            float calculatedDistance = pair.second;

            if (calculatedDistance <= threshold && calculatedDistance != Float.MIN_VALUE) {
                nearObjects.add(pair);
            }
        }

        return nearObjects;
    }

    public static float calculateDistanceWithHitTest(Frame frame, int screenX, int screenY) {
        List<com.google.ar.core.HitResult> hitResults = frame.hitTest(screenX, screenY);

        if (!hitResults.isEmpty()) {
            com.google.ar.core.HitResult hit = hitResults.get(0);

            Pose hitPose = hit.getHitPose();

            Pose cameraPose = frame.getCamera().getPose();

            float dx = hitPose.tx() - cameraPose.tx();
            float dy = hitPose.ty() - cameraPose.ty();
            float dz = hitPose.tz() - cameraPose.tz();

            return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);

        } else {
            //Distancia grande, sem risco de colisao
            return Float.MIN_VALUE;
        }
    }

    public static float distanceToNearestWall(Frame frame) {
        float minDistance = Float.MAX_VALUE;

        for (Plane plane : frame.getUpdatedTrackables(Plane.class)) {
            if (plane.getTrackingState() != TrackingState.TRACKING) continue;
            if (plane.getType() != Plane.Type.VERTICAL) continue;

            Pose planePose = plane.getCenterPose();
            Pose cameraPose = frame.getCamera().getPose();

            float dx = planePose.tx() - cameraPose.tx();
            float dy = planePose.ty() - cameraPose.ty();
            float dz = planePose.tz() - cameraPose.tz();

            float distance = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (distance < minDistance) minDistance = distance;
        }

        return minDistance < Float.MAX_VALUE ? minDistance : Float.MIN_VALUE; //Precisa tomar cuidado, resultados Float.MAX_VALUE e Float.MIN_VALUE quer dizer
    }                                                                         // que não foi detectado parede
}

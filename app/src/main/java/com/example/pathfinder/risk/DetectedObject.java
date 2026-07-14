package com.example.pathfinder.risk;

import com.example.pathfinder.detection.BoundingBox;

// Representa um objeto detectado com sua distância calculada
public class DetectedObject {
    private final BoundingBox boundingBox;
    private final float distance;
    private final String className;

    public DetectedObject(BoundingBox boundingBox, float distance) {
        this.boundingBox = boundingBox;
        this.distance = distance;
        this.className = boundingBox.clsName;
    }

    public BoundingBox getBoundingBox() {
        return boundingBox;
    }

    public float getDistance() {
        return distance;
    }

    public String getClassName() {
        return className;
    }

    public float getCenterX() {
        return boundingBox.cx;
    }

    public float getCenterY() {
        return boundingBox.cy;
    }

    public float getConfidence() {
        return boundingBox.cnf;
    }

    @Override
    public String toString() {
        return String.format("%s a %.2f metros", className, distance);
    }
}
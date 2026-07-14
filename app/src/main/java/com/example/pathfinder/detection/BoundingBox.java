package com.example.pathfinder.detection;

public class BoundingBox {
    public float x1, y1, x2, y2, cx, cy, w, h, cnf;
    public int cls;
    public String clsName;

    public BoundingBox(float x1, float y1, float x2, float y2, float cx, float cy, float w, float h, float cnf, int cls, String clsName) {
        this.x1 = x1;
        this.y1 = y1;
        this.x2 = x2;
        this.y2 = y2;
        this.cx = cx;
        this.cy = cy;
        this.w = w;
        this.h = h;
        this.cnf = cnf;
        this.cls = cls;
        this.clsName = clsName;
    }
}

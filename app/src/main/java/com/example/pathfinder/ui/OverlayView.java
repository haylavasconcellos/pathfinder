package com.example.pathfinder.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.Pair;
import android.view.View;

import com.example.pathfinder.detection.BoundingBox;

import java.util.List;

public class OverlayView extends View {
    private List<Pair<BoundingBox, Float>> detectedObjects;
    private final Paint boxPaint;
    private final Paint textPaint;

    public OverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);

        boxPaint = new Paint();
        boxPaint.setColor(Color.RED);
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(8f);

        textPaint = new Paint();
        textPaint.setColor(Color.RED);
        textPaint.setTextSize(50f);
    }

    public void setResults(List<Pair<BoundingBox, Float>> detectedObjects) {
        this.detectedObjects = detectedObjects;
        invalidate(); // Redraw the view
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();

        if (detectedObjects != null) {
            for (Pair<BoundingBox, Float> obj : detectedObjects) {
                var box = obj.first;
                var dist = obj.second;

                var left =  box.x1 * width;
                var top = box.y1 * height;
                var right = box.x2 * width;
                var bottom = box.y2 * height;

                // Draw the rectangle
                canvas.drawRect(left, top, right, bottom, boxPaint);

                // Draw the label, distance and confidence score
                String label = String.format("%s: %.2fm (%.2f)", box.clsName, dist, box.cnf);
                canvas.drawText(label, left, top - 10, textPaint);
            }
        }
    }
}

package com.example.fingerprintlocation;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class LineView extends View {

    private Paint linePaint;
    private Paint arrowPaint;
    private Paint startPaint;
    private Paint endPaint;

    // Path point list
    private List<PointF> points = new ArrayList<>();

    public LineView(Context context) {
        super(context);
        init();
    }

    public LineView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        // 1. Route paint (imitating Gaode blue)
        linePaint = new Paint();
        linePaint.setColor(0xFF4CAF50); // green
        linePaint.setStrokeWidth(12f); // Thicken the line
        linePaint.setAntiAlias(true);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeJoin(Paint.Join.ROUND); // Rounded corners
        linePaint.setStrokeCap(Paint.Cap.ROUND);   // Rounded line caps

        // 2. Arrow paint (white arrow)
        arrowPaint = new Paint();
        arrowPaint.setColor(Color.parseColor("#FD7E14"));
        arrowPaint.setStyle(Paint.Style.FILL);
        arrowPaint.setAntiAlias(true);

        // 3. Start point paint (green dot)
        startPaint = new Paint();
        startPaint.setColor(Color.parseColor("#2ECC71"));
        startPaint.setStyle(Paint.Style.FILL);
        startPaint.setAntiAlias(true);

        // 4. End point paint (red dot)
        endPaint = new Paint();
        endPaint.setColor(Color.parseColor("#E74C3C"));
        endPaint.setStyle(Paint.Style.FILL);
        endPaint.setAntiAlias(true);
    }

    public void setPathPoints(List<PointF> pts) {
        points.clear();
        if (pts != null) {
            points.addAll(pts);
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (points == null || points.isEmpty()) {
            return;
        }
        if (points.size() < 2) return;

        // --- First layer: draw the line ---
        Path path = new Path();
        PointF first = points.get(0);
        path.moveTo(first.x, first.y);
        for (int i = 1; i < points.size(); i++) {
            PointF p = points.get(i);
            path.lineTo(p.x, p.y);
        }
        canvas.drawPath(path, linePaint);

        // --- Second layer: draw arrows (in the middle of each line segment) ---
        for (int i = 0; i < points.size() - 1; i++) {
            PointF start = points.get(i);
            PointF end = points.get(i + 1);
            drawArrowOnSegment(canvas, start, end);
        }

        // --- Third layer: draw start and end points ---
        // Start point (solid green circle)
        canvas.drawCircle(points.get(0).x, points.get(0).y, 7, startPaint);
        // End point (solid red circle + outer ring)
        canvas.drawCircle(points.get(points.size() - 1).x, points.get(points.size() - 1).y, 7, endPaint);
    }

    // Calculate the direction and draw a triangular arrow in the middle of the line segment
    private void drawArrowOnSegment(Canvas canvas, PointF start, PointF end) {
        double dx = end.x - start.x;
        double dy = end.y - start.y;
        double distance = Math.sqrt(dx * dx + dy * dy);

        // If the line segment is too short (e.g., less than 40 pixels), don't draw an arrow to avoid clutter
        if (distance < 40) return;

        // Calculate the midpoint
        float midX = (float) (start.x + dx * 0.5);
        float midY = (float) (start.y + dy * 0.5);

        // Calculate the angle
        double angle = Math.atan2(dy, dx);

        // Arrow size
        float arrowSize = 10f;

        // Save the canvas state
        canvas.save();
        // Move to the midpoint and rotate the canvas
        canvas.translate(midX, midY);
        canvas.rotate((float) Math.toDegrees(angle));

        // Draw a triangle pointing to the right (since the canvas is already rotated, just draw to the right)
        Path arrowPath = new Path();
        arrowPath.moveTo(-arrowSize, -arrowSize); // Top left
        arrowPath.lineTo(arrowSize, 0);           // Nose (right)
        arrowPath.lineTo(-arrowSize, arrowSize);  // Bottom left
        arrowPath.close();

        canvas.drawPath(arrowPath, arrowPaint);

        // Restore the canvas
        canvas.restore();
    }
}
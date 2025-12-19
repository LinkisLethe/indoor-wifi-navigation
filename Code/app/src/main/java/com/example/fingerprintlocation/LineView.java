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

    // 路径点列表
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
        // 1. 路线画笔 (仿高德蓝)
        linePaint = new Paint();
        linePaint.setColor(Color.parseColor("#4A90E2")); // 高德蓝
        linePaint.setStrokeWidth(12f); // 线条加粗
        linePaint.setAntiAlias(true);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeJoin(Paint.Join.ROUND); // 拐角圆润
        linePaint.setStrokeCap(Paint.Cap.ROUND);   // 线头圆润

        // 2. 箭头画笔 (白色箭头)
        arrowPaint = new Paint();
        arrowPaint.setColor(Color.parseColor("#FD7E14"));
        arrowPaint.setStyle(Paint.Style.FILL);
        arrowPaint.setAntiAlias(true);

        // 3. 起点画笔 (绿色圆点)
        startPaint = new Paint();
        startPaint.setColor(Color.parseColor("#2ECC71"));
        startPaint.setStyle(Paint.Style.FILL);
        startPaint.setAntiAlias(true);

        // 4. 终点画笔 (红色圆点)
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

        if (points.size() < 2) return;

        // --- 第一层：画线 ---
        Path path = new Path();
        PointF first = points.get(0);
        path.moveTo(first.x, first.y);
        for (int i = 1; i < points.size(); i++) {
            PointF p = points.get(i);
            path.lineTo(p.x, p.y);
        }
        canvas.drawPath(path, linePaint);

        // --- 第二层：画箭头 (在每段线的中间) ---
        for (int i = 0; i < points.size() - 1; i++) {
            PointF start = points.get(i);
            PointF end = points.get(i + 1);
            drawArrowOnSegment(canvas, start, end);
        }

        // --- 第三层：画起终点 ---
        // 起点 (实心绿圆)
        canvas.drawCircle(points.get(0).x, points.get(0).y, 7, startPaint);
        // 终点 (实心红圆 + 外圈)
        canvas.drawCircle(points.get(points.size() - 1).x, points.get(points.size() - 1).y, 7, endPaint);
    }

    // 计算方向并在线段中间画一个三角形箭头
    private void drawArrowOnSegment(Canvas canvas, PointF start, PointF end) {
        double dx = end.x - start.x;
        double dy = end.y - start.y;
        double distance = Math.sqrt(dx * dx + dy * dy);

        // 如果线段太短（比如小于 40 像素），就不画箭头了，免得密密麻麻
        if (distance < 40) return;

        // 计算中点
        float midX = (float) (start.x + dx * 0.5);
        float midY = (float) (start.y + dy * 0.5);

        // 计算角度
        double angle = Math.atan2(dy, dx);

        // 箭头大小
        float arrowSize = 10f;

        // 保存画布状态
        canvas.save();
        // 移动到中点并旋转画布
        canvas.translate(midX, midY);
        canvas.rotate((float) Math.toDegrees(angle));

        // 画一个向右指的三角形 (因为画布已经旋转了，所以画向右的就行)
        Path arrowPath = new Path();
        arrowPath.moveTo(-arrowSize, -arrowSize); // 左上
        arrowPath.lineTo(arrowSize, 0);           // 鼻尖 (右)
        arrowPath.lineTo(-arrowSize, arrowSize);  // 左下
        arrowPath.close();

        canvas.drawPath(arrowPath, arrowPaint);

        // 恢复画布
        canvas.restore();
    }
}
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

    private Paint paint;
    // 要画的路径点（已经是该 View 内部的像素坐标）
    private List<PointF> points = new ArrayList<>();

    public LineView(Context context) {
        super(context);
        init();
    }

    public LineView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public LineView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        paint = new Paint();
        paint.setColor(Color.BLUE);   // 蓝色线
        paint.setStrokeWidth(8f);
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.STROKE); // 画线，不是填充
    }

    /**
     * 外部传入一条路径（已经转成坐标）
     */
    public void setPathPoints(List<PointF> pts) {
        points.clear();
        if (pts != null) {
            points.addAll(pts);
        }
        invalidate(); // 通知重绘
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (points.size() < 2) {
            return; // 点少于2个没法画线
        }

        Path path = new Path();
        PointF first = points.get(0);
        path.moveTo(first.x, first.y);

        for (int i = 1; i < points.size(); i++) {
            PointF p = points.get(i);
            path.lineTo(p.x, p.y);
        }

        canvas.drawPath(path, paint);
    }
}

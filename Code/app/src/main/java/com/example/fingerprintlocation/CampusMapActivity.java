package com.example.fingerprintlocation;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CampusMapActivity extends AppCompatActivity {

    private static final Pattern BUILDING_PATTERN = Pattern.compile("\\b(T\\d+)\\b");
    private ImageView map, marker;
    private int markerSizePx;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. 构建 UI 布局
        FrameLayout root = new FrameLayout(this);

        map = new ImageView(this);
        map.setImageResource(R.drawable.campus_map);
        map.setScaleType(ImageView.ScaleType.FIT_CENTER);
        root.addView(map, new FrameLayout.LayoutParams(-1, -1));

        marker = new ImageView(this);
        marker.setImageResource(R.drawable.ic_event_location);
        float density = getResources().getDisplayMetrics().density;
        markerSizePx = (int) (24 * density); // 图标大小 24dp
        root.addView(marker, new FrameLayout.LayoutParams(markerSizePx, markerSizePx));

        TextView title = new TextView(this);
        title.setPadding(30, 50, 30, 0);
        title.setTextColor(0xFF000000);
        title.setTextSize(18f);
        root.addView(title);

        setContentView(root);

        // 2. 获取传递的数据
        String evTitle = getIntent().getStringExtra("title");
        String venue = getIntent().getStringExtra("venue");
        title.setText(evTitle + "\n" + (venue == null ? "" : venue));

        // 3. 核心逻辑：在地图布局完成后计算位置
        map.post(() -> {
            Drawable drawable = map.getDrawable();
            if (drawable == null) return;

            // --- 【关键修改点：获取真正的原始像素尺寸】 ---
            android.graphics.BitmapFactory.Options options = new android.graphics.BitmapFactory.Options();
            options.inJustDecodeBounds = true; // 只读尺寸，不加载图片到内存，速度极快
            android.graphics.BitmapFactory.decodeResource(getResources(), R.drawable.campus_map, options);

            float imgW = options.outWidth;  // 这才是你在画图软件里看到的宽度（如 5000）
            float imgH = options.outHeight; // 这才是你在画图软件里看到的高度
            // ------------------------------------------

            float viewW = map.getWidth();
            float viewH = map.getHeight();

            // 重新计算比例
            float scale = Math.min(viewW / imgW, viewH / imgH);
            float offsetX = (viewW - imgW * scale) / 2f;
            float offsetY = (viewH - imgH * scale) / 2f;

            // 获取你填写的原图坐标
            float[] originXY = getRawPixelCoords(venue);
            float rawX = originXY[0];
            float rawY = originXY[1];

            // 计算并设置位置
            marker.setX((rawX * scale + offsetX) - markerSizePx / 2f);
            marker.setY((rawY * scale + offsetY) - markerSizePx / 2f);
        });
    }

    /**
     * 在这里填写你在画图软件里测得的【原图真实像素坐标】
     */
    private float[] getRawPixelCoords(String venue) {
        float x = 0f, y = 0f;
        if (venue == null) return new float[]{x, y};

        Matcher m = BUILDING_PATTERN.matcher(venue);
        if (m.find()) {
            String building = m.group(1);
            switch (building) {
                case "T1":  x = 4025f; y = 2130f; break;
                case "T2":  x = 3650f;  y = 2165f; break;
                case "T3":  x = 3339f;  y = 2079f;  break;
                case "T4":  x = 4167f; y = 1309f;  break;
                case "T6":  x = 3674f; y = 1501f;  break;
                case "T7":  x = 3363f;  y = 1467f;  break;
                case "T8":  x = 2988f;  y = 1616f;  break;
                case "T29": x = 2645f;  y = 1780f;  break;
                default:   x = 500f;  y = 500f;  break;
            }
        }
        return new float[]{x, y};
    }
}
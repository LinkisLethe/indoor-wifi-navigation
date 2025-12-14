package com.example.fingerprintlocation;

import android.os.Bundle;
import android.view.MotionEvent;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CampusMapActivity extends AppCompatActivity {

    private static final Pattern BUILDING_PATTERN = Pattern.compile("\\b(T\\d+)\\b");

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        // 你也可以用 XML 布局；这里用纯代码最省事
        FrameLayout root = new FrameLayout(this);

        ImageView map = new ImageView(this);
        map.setImageResource(R.drawable.campus_map);
        map.setScaleType(ImageView.ScaleType.FIT_CENTER);
        root.addView(map, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        ImageView marker = new ImageView(this);
        marker.setImageResource(R.drawable.ic_event_location);
        int sizeDp = 24;
        float density = getResources().getDisplayMetrics().density;
        int sizePx = (int) (sizeDp * density);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(sizePx, sizePx);
        marker.setLayoutParams(lp);
        root.addView(marker);

        TextView title = new TextView(this);
        title.setPadding(20, 40, 20, 20);
        root.addView(title);

        setContentView(root);

        String evTitle = getIntent().getStringExtra("title");
        String venue = getIntent().getStringExtra("venue");
        title.setText(evTitle + "\n" + (venue == null ? "" : venue));

        // 把 venue 映射到大地图上的一个点
        float[] xy = mapVenueToCampusXY(venue);

        // 注意：这里用 setX/setY 是相对 root 的坐标；如果你 map 适配缩放了，
        // 更严谨要在 map 的 onLayout 后按比例换算。作业版先用固定点通常也能接受。
        marker.setX(xy[0]);
        marker.setY(xy[1]);

        map.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                float x = event.getX();
                float y = event.getY();
                Toast.makeText(this,
                        String.format("x=%.0f , y=%.0f", x, y),
                        Toast.LENGTH_SHORT).show();
            }
            return true;
        });


    }

    private float[] mapVenueToCampusXY(String venue) {
        // 默认点
        float x = 300, y = 300;

        if (venue == null) return new float[]{x, y};

        Matcher m = BUILDING_PATTERN.matcher(venue);
        if (m.find()) {
            String b = m.group(1);
            switch (b) {
                case "T1": x = 1050; y = 1000; break;
                case "T2": x = 990; y = 1000; break;
                case "T3": x = 890; y = 990; break;
                case "T4": x = 1150; y = 815; break;
                case "T6": x = 1014; y = 846; break;
                case "T7": x = 900; y = 840; break;
                case "T8": x = 837; y = 873; break;
                case "T29": x = 762; y = 926; break;
                default: break;
            }
        }
        return new float[]{x, y};
    }
}

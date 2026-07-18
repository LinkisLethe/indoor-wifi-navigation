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

        // 1. Build UI layout
        FrameLayout root = new FrameLayout(this);

        map = new ImageView(this);
        map.setImageResource(R.drawable.campus_map);
        map.setScaleType(ImageView.ScaleType.FIT_CENTER);
        root.addView(map, new FrameLayout.LayoutParams(-1, -1));

        marker = new ImageView(this);
        marker.setImageResource(R.drawable.ic_event_location);
        float density = getResources().getDisplayMetrics().density;
        markerSizePx = (int) (24 * density); // Icon size 24dp
        root.addView(marker, new FrameLayout.LayoutParams(markerSizePx, markerSizePx));

        TextView title = new TextView(this);
        title.setPadding(30, 50, 30, 0);
        title.setTextColor(0xFF000000);
        title.setTextSize(18f);
        root.addView(title);

        setContentView(root);

        // 2. Get the passed data
        String evTitle = getIntent().getStringExtra("title");
        String venue = getIntent().getStringExtra("venue");
        title.setText(evTitle + "\n" + (venue == null ? "" : venue));

        // 3. Core logic: calculate the position after the map layout is complete
        map.post(() -> {
            Drawable drawable = map.getDrawable();
            if (drawable == null) return;

            // --- [Key modification: get the true original pixel size] ---
            android.graphics.BitmapFactory.Options options = new android.graphics.BitmapFactory.Options();
            options.inJustDecodeBounds = true; // Read dimensions only, do not load the image into memory, very fast
            android.graphics.BitmapFactory.decodeResource(getResources(), R.drawable.campus_map, options);

            float imgW = options.outWidth;  // This is the width you see in the drawing software (e.g., 5000)
            float imgH = options.outHeight; // This is the height you see in the drawing software
            // ------------------------------------------

            float viewW = map.getWidth();
            float viewH = map.getHeight();

            // Recalculate the scale
            float scale = Math.min(viewW / imgW, viewH / imgH);
            float offsetX = (viewW - imgW * scale) / 2f;
            float offsetY = (viewH - imgH * scale) / 2f;

            // Get the original image coordinates you entered
            float[] originXY = getRawPixelCoords(venue);
            float rawX = originXY[0];
            float rawY = originXY[1];

            // Calculate and set the position
            marker.setX((rawX * scale + offsetX) - markerSizePx / 2f);
            marker.setY((rawY * scale + offsetY) - markerSizePx / 2f);
        });
    }

    /**
     * Fill in the [original image real pixel coordinates] you measured in the drawing software here
     */
    private float[] getRawPixelCoords(String venue) {
        float x = 0f, y = 0f;
        if (venue == null) return new float[]{x, y};

        Matcher m = BUILDING_PATTERN.matcher(venue);
        if (m.find()) {
            String building = m.group(1);
            switch (building) {
                // --- Please start filling in your data ---
                case "T1":  x = 4025f; y = 2130f; break; // Sample data, please replace
                case "T2":  x = 3650f;  y = 2165f; break;
                case "T3":  x = 3339f;  y = 2079f;  break;
                case "T4":  x = 4167f; y = 1309f;  break;
                case "T6":  x = 3674f; y = 1501f;  break;
                case "T7":  x = 3363f;  y = 1467f;  break;
                case "T8":  x = 2988f;  y = 1616f;  break;
                case "T29": x = 2645f;  y = 1780f;  break;
                // --- End of filling ---
                default:   x = 500f;  y = 500f;  break;
            }
        }
        return new float[]{x, y};
    }
}
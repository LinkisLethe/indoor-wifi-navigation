package com.example.fingerprintlocation;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.PointF;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NavigationActivity extends AppCompatActivity {

    // UI Controls
    private View btnTabLocate, btnTabNavigate;
    private View btnFloor3, btnFloor4, btnFloor5;
    private ImageView imgFloorMap, imgLocationPin;
    private TextView tvInfo;
    private EditText etStartRoom, etEndRoom;
    private View btnConfirm;
    private LineView pathOverlay;

    // State Variables
    private int currentDisplayFloor = 3;
    private List<MapData.Node> currentWholePath = new ArrayList<>();

    // PDR Variables
    private PdrManager pdrManager;
    private boolean isNavigating = false;
    private float currentUserX = 0f;
    private float currentUserY = 0f;
    private int currentUserFloor = 3;

    // Performance optimization: cache the original map image size
    private float mapRawWidth = 0f;
    private float mapRawHeight = 0f;

    private static final double MAP_NORTH_OFFSET = Math.toRadians(-13);

    // WiFi Variables
    private WifiManager wifiManager;
    private Handler wifiHandler = new Handler();
    private boolean isScanningLoopRunning = false;
    private static final int SCAN_INTERVAL = 2000;

    private final BroadcastReceiver wifiScanReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false);
            if (success) {
                if (ActivityCompat.checkSelfPermission(NavigationActivity.this, Manifest.permission.ACCESS_FINE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED) return;

                List<ScanResult> results = wifiManager.getScanResults();
                // Key: Perform fingerprint library matching in a background thread to avoid interfering with UI drawing
                new Thread(() -> {
                    String bestRoom = computeBestRoom(results);
                    if (bestRoom != null) {
                        // After calculation, switch back to the main thread to update coordinates
                        runOnUiThread(() -> correctPositionByWifi(bestRoom));
                    }
                }).start();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_navigation);

        initViews();
        initListeners();
        checkSensorPermission();
        MapData.initNodes();

        // PDR initialization
        // Start PDR listening in advance to let sensor data stabilize before the user clicks GO
        pdrManager = new PdrManager(this, (stepLength, azimuth) -> {
            // Only update position after navigation has officially started
            if (isNavigating) {
                updateUserPosition(stepLength, azimuth);
            }
        });
        pdrManager.start();
        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);

        // Initialize default floor dimensions
        switchFloor(3);
    }

    private void initViews() {
        btnTabLocate = findViewById(R.id.btnTabLocate);
        btnTabNavigate = findViewById(R.id.btnTabNavigate);
        btnFloor3 = findViewById(R.id.btn_floor3);
        btnFloor4 = findViewById(R.id.btn_floor4);
        btnFloor5 = findViewById(R.id.btn_floor5);
        imgFloorMap = findViewById(R.id.img_floor_map);
        imgLocationPin = findViewById(R.id.img_location_pin);
        tvInfo = findViewById(R.id.layout_info);
        etStartRoom = findViewById(R.id.et_start_room);
        etEndRoom = findViewById(R.id.et_end_room);
        btnConfirm = findViewById(R.id.btn_confirm);
        pathOverlay = findViewById(R.id.path_overlay);

        if (UserActivity.lastLocationResult != null && !UserActivity.lastLocationResult.isEmpty()) {
            etStartRoom.setText(UserActivity.lastLocationResult);
            etEndRoom.requestFocus();
        }
    }

    private void initListeners() {
        btnTabLocate.setOnClickListener(v -> {
            startActivity(new Intent(this, UserActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
            finish();
        });

        btnFloor3.setOnClickListener(v -> switchFloor(3));
        btnFloor4.setOnClickListener(v -> switchFloor(4));
        btnFloor5.setOnClickListener(v -> switchFloor(5));

        // As soon as the input box content changes, try to preview (implementing true "display as you type")
        android.text.TextWatcher watcher = new android.text.TextWatcher() {
            @Override public void afterTextChanged(android.text.Editable s) { previewPath(); }
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
        };
        etStartRoom.addTextChangedListener(watcher);
        etEndRoom.addTextChangedListener(watcher);

        // When clicking GO, if there's no path, show an error; otherwise, start.
        btnConfirm.setOnClickListener(v -> {
            previewPath(); // Make sure everything is perfect before starting
            if (currentWholePath == null || currentWholePath.isEmpty()) {
                Toast.makeText(this, "Please enter valid rooms first", Toast.LENGTH_SHORT).show();
            } else {
                startNavigation();
            }
        });
    }

    private void previewPath() {
        String start = etStartRoom.getText().toString().trim();
        String end = etEndRoom.getText().toString().trim();
        if (start.isEmpty() || end.isEmpty()) return;

        MapData.Node startNode = MapData.getNearestDoor(start);
        MapData.Node endNode = MapData.getNearestDoor(end);

        if (startNode != null && endNode != null) {
            List<MapData.Node> path = PathFinder.findPath(startNode.id, endNode.id);
            if (!path.isEmpty()) {
                currentWholePath = path;
                switchFloor(startNode.floor);
                drawPathForCurrentFloor();
                generateNavigationText(path);
            }
        }
    }

    private void startNavigation() {
        if (currentWholePath == null || currentWholePath.isEmpty()) previewPath();
        if (currentWholePath == null || currentWholePath.isEmpty()) {
            Toast.makeText(this, "Please check room numbers", Toast.LENGTH_SHORT).show();
            return;
        }

        MapData.Node startNode = currentWholePath.get(0);
        currentUserX = startNode.x;
        currentUserY = startNode.y;
        currentUserFloor = startNode.floor;

        isNavigating = true;
        pdrManager.start();
        startWifiScanLoop();

        switchFloor(currentUserFloor);
        updatePinOnScreen(); // Teleport to the start point
        Toast.makeText(this, "Navigation Started!", Toast.LENGTH_SHORT).show();
    }

    private void updateUserPosition(float stepLength, float azimuth) {
        if (!isNavigating) return;

        // Get the scale of the current floor
        float pixelsPerMeter = MapData.floorScales.getOrDefault(currentUserFloor, 12.04f);

        float stepPixels = stepLength * pixelsPerMeter;
        double finalAngle = azimuth + MAP_NORTH_OFFSET;

        // Incremental update
        currentUserX += (float) (stepPixels * Math.sin(finalAngle));
        currentUserY += (float) -(stepPixels * Math.cos(finalAngle));

        // [Core Snapping] Forcefully snap to the red line
        PointF snapped = snapToNearestPath(currentUserX, currentUserY, currentUserFloor);
        currentUserX = snapped.x;
        currentUserY = snapped.y;

        checkFloorSwitch();
        checkArrival();

        runOnUiThread(this::updatePinOnScreen);
    }

    private void updatePinOnScreen() {
        if (currentDisplayFloor != currentUserFloor) {
            imgLocationPin.setVisibility(View.GONE);
            return;
        }

        PointF screenPos = mapImageToScreen(currentUserX, currentUserY);
        float tx = screenPos.x - imgLocationPin.getWidth() / 2f;
        float ty = screenPos.y - imgLocationPin.getHeight() / 2f;

        if (imgLocationPin.getVisibility() != View.VISIBLE) {
            imgLocationPin.setX(tx);
            imgLocationPin.setY(ty);
            imgLocationPin.setVisibility(View.VISIBLE);
        } else {
            imgLocationPin.animate().x(tx).y(ty).setDuration(200)
                    .setInterpolator(new DecelerateInterpolator()).start();
        }
    }

    private void switchFloor(int floor) {
        currentDisplayFloor = floor;
        int resId = (floor == 3) ? R.drawable.floor3 : (floor == 4 ? R.drawable.floor4 : R.drawable.floor5);
        imgFloorMap.setImageResource(resId);

        // [Performance Optimization] Cache the original pixel size of the map to fix lag
        android.graphics.BitmapFactory.Options options = new android.graphics.BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        android.graphics.BitmapFactory.decodeResource(getResources(), resId, options);
        mapRawWidth = options.outWidth;
        mapRawHeight = options.outHeight;

        btnFloor3.setAlpha(floor == 3 ? 1.0f : 0.5f);
        btnFloor4.setAlpha(floor == 4 ? 1.0f : 0.5f);
        btnFloor5.setAlpha(floor == 5 ? 1.0f : 0.5f);

        drawPathForCurrentFloor();
        updatePinOnScreen();
    }

    private PointF mapImageToScreen(float ox, float oy) {
        // Use the cached mapRawWidth/Height
        if (imgFloorMap.getWidth() == 0 || mapRawWidth == 0) return new PointF(ox, oy);

        float vw = imgFloorMap.getWidth();
        float vh = imgFloorMap.getHeight();
        float scale = Math.min(vw / mapRawWidth, vh / mapRawHeight);
        float offX = (vw - mapRawWidth * scale) / 2f;
        float offY = (vh - mapRawHeight * scale) / 2f;

        return new PointF(ox * scale + offX, oy * scale + offY);
    }

    private PointF snapToNearestPath(float x, float y, int floor) {
        if (currentWholePath == null || currentWholePath.size() < 2) return new PointF(x, y);

        double minDistance = Double.MAX_VALUE;
        PointF nearestPoint = new PointF(x, y);

        for (int i = 0; i < currentWholePath.size() - 1; i++) {
            MapData.Node nA = currentWholePath.get(i);
            MapData.Node nB = currentWholePath.get(i + 1);

            if (nA.floor != floor || nB.floor != floor) continue;

            PointF p = getClosestPointOnSegment(nA.x, nA.y, nB.x, nB.y, x, y);
            double dist = Math.sqrt(Math.pow(p.x - x, 2) + Math.pow(p.y - y, 2));

            if (dist < minDistance) {
                minDistance = dist;
                nearestPoint = p;
            }
        }

        // [Modification] Increase the snapping threshold from 200 to 400 pixels to prevent being "abandoned" due to deviation when turning.
        // Also, remove the logic of "return to the original point if it doesn't match" to ensure the position always slides along the path.
        return nearestPoint;
    }

    private PointF getClosestPointOnSegment(float ax, float ay, float bx, float by, float px, float py) {
        float dx = bx - ax, dy = by - ay;
        if (dx == 0 && dy == 0) return new PointF(ax, ay);
        float t = ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy);
        t = Math.max(0, Math.min(1, t));
        return new PointF(ax + t * dx, ay + t * dy);
    }

    private void checkFloorSwitch() {
        if (currentWholePath == null || currentWholePath.size() < 2) return;
        float scale = MapData.floorScales.getOrDefault(currentUserFloor, 12.04f);
        float threshold = 2.75f * scale; // 2.75m switch threshold

        for (int i = 0; i < currentWholePath.size() - 1; i++) {
            MapData.Node curr = currentWholePath.get(i);
            MapData.Node next = currentWholePath.get(i + 1);
            if (curr.floor == currentUserFloor && next.floor != currentUserFloor) {
                if (calculateDistance(currentUserX, currentUserY, curr.x, curr.y) < threshold) {
                    currentUserFloor = next.floor;
                    currentUserX = next.x;
                    currentUserY = next.y;
                    int finalNext = next.floor;
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Switching to " + finalNext + "F", Toast.LENGTH_SHORT).show();
                        switchFloor(finalNext);
                    });
                    break;
                }
            }
        }
    }

    private void checkArrival() {
        if (currentWholePath == null || currentWholePath.isEmpty()) return;
        MapData.Node end = currentWholePath.get(currentWholePath.size() - 1);

        // Physical distance conversion: use the 12.04f scale you defined
        float scale = MapData.floorScales.getOrDefault(currentUserFloor, 12.04f);

        // Set the detection range to 2.0 meters (2.0 * 12.04 px)
        if (currentUserFloor == end.floor && calculateDistance(currentUserX, currentUserY, end.x, end.y) < 1.75f * scale) {
            isNavigating = false;
            pdrManager.stop();
            runOnUiThread(() -> {
                Toast.makeText(this, "Destination Reached!", Toast.LENGTH_LONG).show();
                imgLocationPin.setVisibility(View.GONE);
                pathOverlay.setPathPoints(null); // Clear the route after arrival
            });
        }
    }

    private double calculateDistance(float x1, float y1, float x2, float y2) {
        return Math.sqrt(Math.pow(x1 - x2, 2) + Math.pow(y1 - y2, 2));
    }

    private void drawPathForCurrentFloor() {
        if (currentWholePath == null || currentWholePath.isEmpty()) return;
        List<PointF> floorPoints = new ArrayList<>();
        for (MapData.Node n : currentWholePath) {
            if (n.floor == currentDisplayFloor) {
                floorPoints.add(mapImageToScreen(n.x, n.y));
            }
        }
        pathOverlay.setPathPoints(floorPoints.size() > 1 ? floorPoints : null);
    }

    // WiFi correction and loop maintain original logic
    private void startWifiScanLoop() {
        if (isScanningLoopRunning) return;
        isScanningLoopRunning = true;
        registerReceiver(wifiScanReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
        wifiHandler.post(scanRunnable);
    }

    private void stopWifiScanLoop() {
        isScanningLoopRunning = false;
        wifiHandler.removeCallbacks(scanRunnable);
        try { unregisterReceiver(wifiScanReceiver); } catch (Exception ignored) {}
    }

    private Runnable scanRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isNavigating || !isScanningLoopRunning) return;
            if (wifiManager != null && wifiManager.isWifiEnabled()) {
                if (ActivityCompat.checkSelfPermission(NavigationActivity.this, Manifest.permission.ACCESS_FINE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED) wifiManager.startScan();
            }
            wifiHandler.postDelayed(this, SCAN_INTERVAL);
        }
    };

    private String computeBestRoom(List<ScanResult> results) {
        List<UserActivity.FingerprintRecord> library = UserActivity.fingerprintLibrary;
        if (library == null || library.isEmpty()) return null;
        Map<String, Double> cur = new HashMap<>();
        for (ScanResult r : results) if (r.level > -85) cur.put(r.BSSID, (double) r.level);
        if (cur.size() < 4) return null;

        String best = null; double minDist = Double.MAX_VALUE;
        for (UserActivity.FingerprintRecord rec : library) {
            double d = 0; int matches = 0;
            for (String b : cur.keySet()) {
                if (rec.avgRssiMap.containsKey(b)) {
                    d += Math.pow(cur.get(b) - rec.avgRssiMap.get(b), 2);
                    matches++;
                }
            }
            if (matches >= 4) {
                d = Math.sqrt(d);
                if (d < minDist) { minDist = d; best = rec.room; }
            }
        }
        return (minDist < 25.0) ? best : null;
    }

    private void correctPositionByWifi(String roomId) {
        if (roomId == null || !isNavigating) return;

        // 1. Determine the target node ID: preferentially try to find the door node, if not found, use the original node
        String targetNodeId = roomId.toLowerCase();
        String doorId = "door_" + targetNodeId;

        // If there is a corresponding door point in MapData, use the door point; otherwise, see if there is an original name point
        if (MapData.nodes.containsKey(doorId)) {
            targetNodeId = doorId;
        } else if (!MapData.nodes.containsKey(targetNodeId)) {
            return; // If neither is found, return directly
        }

        MapData.Node targetNode = MapData.nodes.get(targetNodeId);

        // 2. Floor verification and distance calculation
        if (targetNode.floor == currentUserFloor) {
            double dist = calculateDistance(currentUserX, currentUserY, targetNode.x, targetNode.y);
            float scale = MapData.floorScales.getOrDefault(currentUserFloor, 12.04f);
            double distInMeters = dist / scale;

            // 3. Range filtering: solve the sensitivity problem
            if (distInMeters > 5.0 && distInMeters < 15.0) {
                // Perform snap correction
                PointF snapped = snapToNearestPath(targetNode.x, targetNode.y, currentUserFloor);
                currentUserX = snapped.x;
                currentUserY = snapped.y;

                runOnUiThread(this::updatePinOnScreen);
                Log.d("WiFi", "Applied correction for: " + targetNodeId);
            }
        }
    }

    private void generateNavigationText(List<MapData.Node> path) {
        StringBuilder sb = new StringBuilder("Path: ");
        for (int i = 0; i < path.size(); i++) {
            sb.append(path.get(i).id).append(i == path.size() - 1 ? "" : " -> ");
        }
        tvInfo.setText(sb.toString());
    }

    private void checkSensorPermission() {
        List<String> needed = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED)
            needed.add(Manifest.permission.ACTIVITY_RECOGNITION);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            needed.add(Manifest.permission.ACCESS_FINE_LOCATION);
        if (!needed.isEmpty()) ActivityCompat.requestPermissions(this, needed.toArray(new String[0]), 100);
    }

    @Override
    protected void onResume() { super.onResume(); if (isNavigating) { pdrManager.start(); startWifiScanLoop(); } }
    @Override
    protected void onPause() { super.onPause(); pdrManager.stop(); stopWifiScanLoop(); }
}
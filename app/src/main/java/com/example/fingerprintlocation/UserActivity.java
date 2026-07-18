package com.example.fingerprintlocation;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ================================================================
 * User Location Page (Localization + News Placeholder)
 * UserActivity (UI Refactored Version)
 * ================================================================
 */
public class UserActivity extends AppCompatActivity {
    // Used to share location results across pages
    public static String lastLocationResult = "";

    private static final String TAG = "UserActivity";
    private static final int REQ_CODE_LOCATION = 2001;

    // ===== Debug Switches =====
    private static final boolean DEBUG_BYPASS_WIFI = false;
    private static final String DEBUG_FAKE_ROOM = "t7-505";

    // ===== Parameter Settings =====
    private static final int NUM_SCANS_FOR_LOCATE = 4;   // Number of consecutive scans for localization
    private static final int MIN_RSSI_DBM = -85;         // Weak signal filtering
    private static final double MISSING_RSSI = -100.0;
    private static final double DISTANCE_THRESHOLD = 200.0;
    private static final int K_NEIGHBORS = 3;
    private static final int MIN_AP_MATCH_REQUIRED = 6;
    private static final String FP_DB_FILE = "fingerprint_db.json";

    private WifiManager wifiManager;

    // ===== New UI Controls =====
    // Status card area
    private LinearLayout layoutScanning; // Scanning layout
    private LinearLayout layoutResult;   // Result layout
    private ProgressBar progressBarScan; // Progress bar
    private TextView tvStatusTitle;      // Status title (Scanning... / Failed)
    private TextView tvScanCount;        // Scan round (Round 1/4)
    private TextView tvResultBig;        // Large text result (Room T6-301)

    // Top capsule buttons (TextView in XML)
    private TextView btnTabLocate;
    private TextView btnTabNavigate;

    // Floor switching buttons (TextView in XML)
    private TextView btnFloor3, btnFloor4, btnFloor5;

    // News area
    private LinearLayout newsContainer;
    private Button btnRefreshNews; // Retain the refresh button logic (though its position might be adjusted in XML)

    // Map and icon
    private ImageView imgFloor;
    private ImageView imgUserLoc;

    // ===== Logical State =====
    private boolean isLocating = false;
    private int currentLocateScanCount = 0;
    private Map<String, ArrayList<Integer>> locateSamples = new HashMap<>();
    public static ArrayList<FingerprintRecord> fingerprintLibrary = new ArrayList<>();

    private RoomPos currentRoomPos = null;
    private String lastLocatedRoom = null;
    private int iconFloor = -1;
    private Map<String, RoomPos> roomPosMap = new HashMap<>();
    private int currentFloor = 3; // Default to 3rd floor

    // Thread pool for network requests (news)
    private final ExecutorService netPool = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Broadcast receiver for WiFi scan results
    private final BroadcastReceiver wifiScanReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false);
            if (!success) {
                if (isLocating) {
                    isLocating = false;
                    updateLocateUI(2, "Scan throttled/failed", 0);
                }
                return;
            }

            if (ActivityCompat.checkSelfPermission(UserActivity.this,
                    Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                updateLocateUI(2, "No Location Permission", 0);
                return;
            }

            List<ScanResult> results = wifiManager.getScanResults();

            if (!isLocating) return;

            // ========== Localization Mode Logic ==========
            currentLocateScanCount++;

            // Update UI progress
            int progress = (int) ((float) currentLocateScanCount / NUM_SCANS_FOR_LOCATE * 100);
            updateLocateUI(0, "Analyzing Signals...", progress);

            if (results != null) {
                for (ScanResult result : results) {
                    int rssi = result.level;
                    if (rssi < MIN_RSSI_DBM) continue;

                    String bssid = result.BSSID;
                    ArrayList<Integer> list = locateSamples.get(bssid);
                    if (list == null) {
                        list = new ArrayList<>();
                        locateSamples.put(bssid, list);
                    }
                    list.add(rssi);
                }
            }

            if (currentLocateScanCount < NUM_SCANS_FOR_LOCATE) {
                // Continue to the next round
                startWifiScan();
            } else {
                // End scanning, start calculation
                isLocating = false;
                if (locateSamples.isEmpty()) {
                    updateLocateUI(2, "No valid WiFi signals found", 0);
                    return;
                }

                // Calculate average
                Map<String, Double> currentMap = new HashMap<>();
                for (Map.Entry<String, ArrayList<Integer>> entry : locateSamples.entrySet()) {
                    String bssid = entry.getKey();
                    ArrayList<Integer> list = entry.getValue();
                    if (list == null || list.isEmpty()) continue;
                    int sum = 0;
                    for (int v : list) sum += v;
                    currentMap.put(bssid, sum * 1.0 / list.size());
                }

                if (currentMap.size() < MIN_AP_MATCH_REQUIRED) {
                    updateLocateUI(2, "Signal weak (" + currentMap.size() + " APs)", 0);
                    locateSamples.clear();
                    return;
                }

                // Core algorithm for localization
                locateWithCurrentScan(currentMap);
                locateSamples.clear();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user); // Ensure your XML file name is activity_user.xml

        // ===== 1. Bind new UI controls =====
        layoutScanning = findViewById(R.id.layoutScanning);
        layoutResult = findViewById(R.id.layoutResult);
        progressBarScan = findViewById(R.id.progressBarScan);
        tvStatusTitle = findViewById(R.id.tvStatusTitle);
        tvScanCount = findViewById(R.id.tvScanCount);
        tvResultBig = findViewById(R.id.tvResultBig);

        btnTabLocate = findViewById(R.id.btnTabLocate);
        btnTabNavigate = findViewById(R.id.btnTabNavigate);

        btnFloor3 = findViewById(R.id.btn_floor3);
        btnFloor4 = findViewById(R.id.btn_floor4);
        btnFloor5 = findViewById(R.id.btn_floor5);

        newsContainer = findViewById(R.id.newsContainer);
        btnRefreshNews = findViewById(R.id.btnRefreshNews);
        imgFloor = findViewById(R.id.img_floor);

        // ===== 2. Initialize the blue dot icon =====
        imgUserLoc = new ImageView(this);
        imgUserLoc.setImageResource(R.drawable.ic_locate); // Ensure you have the ic_locate image
        int size = (int) (20 * getResources().getDisplayMetrics().density); // 24dp size
        ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(size, size);
        imgUserLoc.setLayoutParams(params);
        imgUserLoc.setAdjustViewBounds(true);
        imgUserLoc.setVisibility(View.GONE);

        // Add the icon to the top layer of the root layout
        ViewGroup root = (ViewGroup) ((ViewGroup) findViewById(android.R.id.content)).getChildAt(0);
        root.addView(imgUserLoc, params);

        // Initialize room position data
        initRoomPositions();

        // Listen for map scrolling to synchronize icon position
        if (imgFloor != null) {
            imgFloor.getViewTreeObserver().addOnScrollChangedListener(() -> {
                if (currentRoomPos != null) {
                    updateUserIconPosition();
                }
            });
        }

        // ===== 3. Set floor click listeners =====
        View.OnClickListener floorClickListener = v -> {
            int id = v.getId();
            if (id == R.id.btn_floor3) {
                switchFloorUI(3);
            } else if (id == R.id.btn_floor4) {
                switchFloorUI(4);
            } else if (id == R.id.btn_floor5) {
                switchFloorUI(5);
            }
        };
        btnFloor3.setOnClickListener(floorClickListener);
        btnFloor4.setOnClickListener(floorClickListener);
        btnFloor5.setOnClickListener(floorClickListener);

        // ===== 4. Initialize WiFi and Permissions =====
        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager == null) {
            updateLocateUI(2, "WiFi Not Supported", 0);
            return;
        }

        loadFingerprintLibraryFromFile();

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        registerReceiver(wifiScanReceiver, intentFilter);

        // Load news
        loadFstEvents();

        // ===== 5. Top button logic =====
        btnTabLocate.setOnClickListener(v -> startLocateProcedure());

        btnTabNavigate.setOnClickListener(v -> {
            Intent intent = new Intent(UserActivity.this, NavigationActivity.class);
            startActivity(intent);
        });

        // Refresh news logic
        if (btnRefreshNews != null) {
            btnRefreshNews.setOnClickListener(v -> loadFstEvents());
        }

        // Automatically start one location scan
        if (!fingerprintLibrary.isEmpty()) {
            startLocateProcedure();
        } else {
            updateLocateUI(2, "Library Empty (Go Admin)", 0);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(wifiScanReceiver);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Receiver not registered", e);
        }
    }

    // ==========================================================
    //                   Core UI Update Methods
    // ==========================================================

    /**
     * Unified update of location status UI
     * @param state 0=scanning, 1=success, 2=failure
     * @param message Text to display (title or result)
     * @param progress Progress (0-100)
     */
    private void updateLocateUI(int state, String message, int progress) {
        // Safety check to prevent null pointers
        if (layoutScanning == null || layoutResult == null) return;

        if (state == 0) {
            // === Scanning ===
            layoutScanning.setVisibility(View.VISIBLE);
            layoutResult.setVisibility(View.GONE);

            tvStatusTitle.setText(message);
            progressBarScan.setProgress(progress);

            // Calculate round
            int currentRound = (int) Math.ceil((progress / 100.0) * NUM_SCANS_FOR_LOCATE);
            if (currentRound == 0) currentRound = 1;
            if (currentRound > NUM_SCANS_FOR_LOCATE) currentRound = NUM_SCANS_FOR_LOCATE;
            tvScanCount.setText("Round " + currentRound + " / " + NUM_SCANS_FOR_LOCATE);

        } else if (state == 1) {
            // === Success ===
            layoutScanning.setVisibility(View.GONE);
            layoutResult.setVisibility(View.VISIBLE);
            tvResultBig.setText(message); // e.g., "Room T6-301"

        } else {
            // === Failure ===
            layoutScanning.setVisibility(View.VISIBLE);
            layoutResult.setVisibility(View.GONE);

            progressBarScan.setProgress(0);
            tvStatusTitle.setText("Status: Failed");
            tvScanCount.setText(message); // Display error details
        }
    }

    private void switchFloorUI(int floor) {
        currentFloor = floor;
        if (floor == 3) imgFloor.setImageResource(R.drawable.floor3);
        else if (floor == 4) imgFloor.setImageResource(R.drawable.floor4);
        else if (floor == 5) imgFloor.setImageResource(R.drawable.floor5);

        // If manually switching floors, check if the icon should be hidden (if the icon is not on the current floor)
        if (lastLocatedRoom != null && currentRoomPos != null) {
            // Recalculate and show/hide
            updateUserIconPosition();
        } else {
            imgUserLoc.setVisibility(View.GONE);
        }
    }

    // ==========================================================
    //                   Localization Flow Logic
    // ==========================================================

    private void startLocateProcedure() {
        if (fingerprintLibrary.isEmpty()) {
            Toast.makeText(this, "Fingerprint DB is empty!", Toast.LENGTH_SHORT).show();
            updateLocateUI(2, "DB Empty", 0);
            return;
        }

        // Initialize UI
        updateLocateUI(0, "Initializing WiFi...", 5);

        if (DEBUG_BYPASS_WIFI) {
            showUserOnRoom(DEBUG_FAKE_ROOM);
            lastLocationResult = DEBUG_FAKE_ROOM;
            updateLocateUI(1, "Room " + DEBUG_FAKE_ROOM, 100);
            return;
        }

        isLocating = true;
        currentLocateScanCount = 0;
        locateSamples.clear();
        checkPermissionAndScan();
    }

    private void checkPermissionAndScan() {
        boolean fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;

        if (!fine || !coarse) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    REQ_CODE_LOCATION);
        } else {
            startWifiScan();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CODE_LOCATION) {
            boolean granted = true;
            for (int r : grantResults) {
                if (r != PackageManager.PERMISSION_GRANTED) granted = false;
            }
            if (granted) startWifiScan();
            else updateLocateUI(2, "Permission Denied", 0);
        }
    }

    private void startWifiScan() {
        if (!wifiManager.isWifiEnabled()) {
            wifiManager.setWifiEnabled(true);
            Toast.makeText(this, "Enabling WiFi...", Toast.LENGTH_SHORT).show();
        }

        boolean success = wifiManager.startScan();
        if (success) {
            Log.d(TAG, "WiFi Scan Started");
        } else {
            // Scan failure is usually due to throttling, retry later or report an error
            Log.e(TAG, "WiFi Scan Failed (Throttled?)");
            // Do not report an error immediately here, wait for the Receiver to time out or for the next round (simplify logic, treat as continuing)
        }
    }

    // ==========================================================
    //                   Algorithm and Matching Logic
    // ==========================================================

    private void locateWithCurrentScan(Map<String, Double> currentMap) {
        if (fingerprintLibrary.isEmpty()) return;

        // 1. Calculate WKNN
        ArrayList<Neighbor> neighbors = new ArrayList<>();
        for (FingerprintRecord rec : fingerprintLibrary) {
            double dist = computeDistance(rec.avgRssiMap, currentMap);
            double cos = computeCosine(rec.avgRssiMap, currentMap);
            neighbors.add(new Neighbor(rec, dist, cos));
        }

        // 2. Sort
        Collections.sort(neighbors, (o1, o2) -> {
            int c = Double.compare(o1.distance, o2.distance);
            if (c != 0) return c;
            return -Double.compare(o1.cosineSim, o2.cosineSim);
        });

        if (neighbors.isEmpty()) {
            runOnUiThread(() -> updateLocateUI(2, "No Neighbors", 0));
            return;
        }

        double bestDist = neighbors.get(0).distance;
        if (bestDist > DISTANCE_THRESHOLD) {
            runOnUiThread(() -> {
                updateLocateUI(2, "Location Uncertain", 0);
                // Can also display "Unknown" but mark the most likely room
            });
            return;
        }

        // 3. Vote (WKNN + Cosine)
        int K = Math.min(K_NEIGHBORS, neighbors.size());
        Map<String, Double> roomVoteMap = new HashMap<>();

        for (int i = 0; i < K; i++) {
            Neighbor n = neighbors.get(i);
            double w = 1.0 / (n.distance + 1e-6);
            if (n.cosineSim > 0) w *= (1 + n.cosineSim);

            String room = n.record.room;
            roomVoteMap.put(room, roomVoteMap.getOrDefault(room, 0.0) + w);
        }

        // 4. Select the best room
        String bestRoom = null;
        double maxScore = -1.0;
        for (Map.Entry<String, Double> e : roomVoteMap.entrySet()) {
            if (e.getValue() > maxScore) {
                maxScore = e.getValue();
                bestRoom = e.getKey();
            }
        }

        if (bestRoom != null) {
            lastLocationResult = bestRoom.toLowerCase();
            final String finalRoom = bestRoom;

            // Update UI and map icon
            runOnUiThread(() -> {
                updateLocateUI(1, "Room " + finalRoom, 100);
                showUserOnRoom(finalRoom);
            });
        } else {
            runOnUiThread(() -> updateLocateUI(2, "Calculation Failed", 0));
        }
    }

    private double computeDistance(Map<String, Double> fp, Map<String, Double> cur) {
        HashSet<String> keys = new HashSet<>();
        keys.addAll(fp.keySet());
        keys.addAll(cur.keySet());

        double sumSq = 0.0;
        for (String bssid : keys) {
            double v1 = fp.getOrDefault(bssid, MISSING_RSSI);
            double v2 = cur.getOrDefault(bssid, MISSING_RSSI);
            sumSq += Math.pow(v1 - v2, 2);
        }
        return Math.sqrt(sumSq);
    }

    private double computeCosine(Map<String, Double> fp, Map<String, Double> cur) {
        HashSet<String> keys = new HashSet<>();
        keys.addAll(fp.keySet());
        keys.addAll(cur.keySet());

        double dot = 0.0, norm1 = 0.0, norm2 = 0.0;
        for (String bssid : keys) {
            double v1 = fp.getOrDefault(bssid, MISSING_RSSI);
            double v2 = cur.getOrDefault(bssid, MISSING_RSSI);
            dot += v1 * v2;
            norm1 += v1 * v1;
            norm2 += v2 * v2;
        }
        if (norm1 == 0 || norm2 == 0) return 0;
        return dot / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }

    // ==========================================================
    //                   File Loading (Fingerprint DB)
    // ==========================================================

    private void loadFingerprintLibraryFromFile() {
        fingerprintLibrary.clear();
        try {
            // Try to read the local file
            FileInputStream fis = openFileInput(FP_DB_FILE);
            String jsonStr = readStreamToString(fis);
            parseJsonAndMerge(jsonStr);
        } catch (Exception e) {
            // If not available locally, try to read from Assets (preset data)
            try {
                java.io.InputStream is = getAssets().open(FP_DB_FILE);
                String jsonStr = readStreamToString(is);
                parseJsonAndMerge(jsonStr);
            } catch (Exception ex) {
                Log.e(TAG, "No DB found.");
            }
        }
    }

    private String readStreamToString(java.io.InputStream is) throws IOException {
        java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line);
        reader.close();
        return sb.toString();
    }

    private void parseJsonAndMerge(String jsonStr) throws JSONException {
        JSONArray arr = new JSONArray(jsonStr);
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.getJSONObject(i);
            String room = obj.getString("room");
            String rp = obj.getString("rp");
            JSONArray aps = obj.getJSONArray("aps");
            Map<String, Double> avgRssiMap = new HashMap<>();
            for (int j = 0; j < aps.length(); j++) {
                JSONObject ap = aps.getJSONObject(j);
                avgRssiMap.put(ap.getString("bssid"), ap.getDouble("rssi"));
            }
            // Simple deduplication and addition
            fingerprintLibrary.add(new FingerprintRecord(room, rp, avgRssiMap));
        }
    }

    // ==========================================================
    //                   Map Icon Logic
    // ==========================================================

    private void showUserOnRoom(String roomName) {
        if (imgFloor == null || imgUserLoc == null) return;

        RoomPos pos = roomPosMap.get(roomName);
        // If the exact room is not found, try fuzzy matching (e.g., t6-301a -> t6-301)
        if (pos == null) {
            // Simple fallback logic, omitted here
            return;
        }

        currentRoomPos = pos;
        iconFloor = parseFloorFromRoomName(roomName);
        lastLocatedRoom = roomName;

        // Automatically switch floor map
        if (iconFloor != -1 && iconFloor != currentFloor) {
            switchFloorUI(iconFloor);
        }

        updateUserIconPosition();
    }

    private void updateUserIconPosition() {
        if (currentFloor != -1 && iconFloor != -1 && currentFloor != iconFloor) {
            imgUserLoc.setVisibility(View.GONE);
            return;
        }
        if (imgFloor == null || imgUserLoc == null || currentRoomPos == null) return;

        imgFloor.post(() -> {
            int w = imgFloor.getWidth();
            int h = imgFloor.getHeight();
            if (w == 0 || h == 0) return;

            int[] floorLoc = new int[2];
            imgFloor.getLocationOnScreen(floorLoc);

            View root = ((ViewGroup) findViewById(android.R.id.content)).getChildAt(0);
            int[] rootLoc = new int[2];
            root.getLocationOnScreen(rootLoc);

            float x = floorLoc[0] - rootLoc[0] + currentRoomPos.xRatio * w - (imgUserLoc.getWidth() / 2f);
            float y = floorLoc[1] - rootLoc[1] + currentRoomPos.yRatio * h - (imgUserLoc.getHeight() / 2f);

            imgUserLoc.setX(x);
            imgUserLoc.setY(y);
            imgUserLoc.setVisibility(View.VISIBLE);
        });
    }

    private int parseFloorFromRoomName(String roomName) {
        if (roomName == null || !roomName.contains("-")) return -1;
        String part = roomName.split("-")[1]; // "301" from "t6-301"
        if (part.length() > 0 && Character.isDigit(part.charAt(0))) {
            return part.charAt(0) - '0';
        }
        return -1;
    }

    private void initRoomPositions() {
        roomPosMap.clear();
        // 3F
        roomPosMap.put("t6-301", new RoomPos(0.49f, 0.82f));
        roomPosMap.put("t6-303", new RoomPos(0.63f, 0.80f));
        roomPosMap.put("t6-304", new RoomPos(0.70f, 0.78f));
        roomPosMap.put("t7-301", new RoomPos(0.15f, 0.45f));
        roomPosMap.put("t7-302", new RoomPos(0.12f, 0.25f));
        roomPosMap.put("t7-303", new RoomPos(0.10f, 0.10f));
        roomPosMap.put("t7-304", new RoomPos(0.30f, 0.40f));
        roomPosMap.put("t7-305", new RoomPos(0.35f, 0.40f));
        roomPosMap.put("t7-306", new RoomPos(0.45f, 0.10f));

        // 4F
        roomPosMap.put("t6-401", new RoomPos(0.49f, 0.85f));
        roomPosMap.put("t6-402", new RoomPos(0.55f, 0.83f));
        roomPosMap.put("t6-404", new RoomPos(0.65f, 0.80f));
        roomPosMap.put("t6-405", new RoomPos(0.74f, 0.79f));
        roomPosMap.put("t7-401", new RoomPos(0.13f, 0.50f));
        roomPosMap.put("t7-402", new RoomPos(0.20f, 0.49f));
        roomPosMap.put("t7-403", new RoomPos(0.28f, 0.47f));
        roomPosMap.put("t7-404", new RoomPos(0.32f, 0.46f));
        roomPosMap.put("t7-405", new RoomPos(0.35f, 0.45f));
        roomPosMap.put("t7-406", new RoomPos(0.38f, 0.44f));
        roomPosMap.put("t7-407", new RoomPos(0.54f, 0.35f));

        // 5F
        roomPosMap.put("t6-501", new RoomPos(0.45f, 0.78f));
        roomPosMap.put("t6-502", new RoomPos(0.52f, 0.77f));
        roomPosMap.put("t6-503", new RoomPos(0.65f, 0.73f));
        roomPosMap.put("t6-504", new RoomPos(0.70f, 0.71f));
        roomPosMap.put("t6-505", new RoomPos(0.87f, 0.61f));
        roomPosMap.put("t7-501", new RoomPos(0.13f, 0.48f));
        roomPosMap.put("t7-502", new RoomPos(0.18f, 0.47f));
        roomPosMap.put("t7-503", new RoomPos(0.30f, 0.43f));
        roomPosMap.put("t7-504", new RoomPos(0.35f, 0.41f));
        roomPosMap.put("t7-505", new RoomPos(0.52f, 0.33f));
    }

    // ==========================================================
    //                   News Module
    // ==========================================================

    private void loadFstEvents() {
        if (newsContainer == null) return;
        newsContainer.removeAllViews();
        TextView loading = new TextView(this);
        loading.setText("Loading events...");
        loading.setPadding(20, 20, 20, 20);
        newsContainer.addView(loading);

        netPool.execute(() -> {
            try {
                // Assuming you have the FstEventScraper class
                List<EventItem> items = FstEventScraper.fetchLatest10();
                mainHandler.post(() -> {
                    newsContainer.removeAllViews();
                    if (items == null || items.isEmpty()) {
                        TextView tv = new TextView(this);
                        tv.setText("No events found.");
                        newsContainer.addView(tv);
                    } else {
                        renderEvents(items);
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    newsContainer.removeAllViews();
                    TextView tv = new TextView(this);
                    tv.setText("Load failed: " + e.getMessage());
                    newsContainer.addView(tv);
                });
            }
        });
    }

    private void renderEvents(List<EventItem> items) {
        // Remove old views
        newsContainer.removeAllViews();

        for (EventItem ev : items) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(10, 20, 10, 20);

            // --- 1. Left side: text information ---
            TextView tv = new TextView(this);
            // Use weight=1 to make the text occupy the remaining space
            tv.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            tv.setText(ev.title + "\n" + ev.date + " | " + ev.venue);
            tv.setTextSize(14);
            tv.setTextColor(0xFF333333);

            // Click text to open webpage
            tv.setOnClickListener(v -> {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(ev.url)));
                } catch (Exception e) { e.printStackTrace(); }
            });

            // --- 2. Right side: ShowLocation button (this is the part that was mistakenly deleted) ---
            Button btn = new Button(this);
            btn.setText("Location");
            // Slightly reduce the font size or adjust padding to avoid layout overflow
            btn.setTextSize(12);
            btn.setOnClickListener(v -> {
                // Jump to CampusMapActivity and pass location information
                Intent i = new Intent(UserActivity.this, CampusMapActivity.class); // Ensure you have this Activity
                i.putExtra("title", ev.title);
                i.putExtra("venue", ev.venue);
                i.putExtra("url", ev.url);
                startActivity(i);
            });

            // --- 3. Add to row ---
            row.addView(tv);
            row.addView(btn); // Add the button to the layout

            // Add a divider line
            View divider = new View(this);
            divider.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
            divider.setBackgroundColor(0xFFEEEEEE);

            newsContainer.addView(row);
            newsContainer.addView(divider);
        }
    }

    // ==========================================================
    //                   Data Class Definitions
    // ==========================================================

    public static class FingerprintRecord {
        public String room, rp;
        public Map<String, Double> avgRssiMap;
        public FingerprintRecord(String room, String rp, Map<String, Double> map) {
            this.room = room; this.rp = rp; this.avgRssiMap = map;
        }
    }

    private static class Neighbor {
        FingerprintRecord record;
        double distance, cosineSim;
        Neighbor(FingerprintRecord r, double d, double c) {
            this.record = r; this.distance = d; this.cosineSim = c;
        }
    }

    private static class RoomPos {
        final float xRatio, yRatio;
        RoomPos(float x, float y) { this.xRatio = x; this.yRatio = y; }
    }
}
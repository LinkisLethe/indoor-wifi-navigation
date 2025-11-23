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
import android.widget.TextView;
import android.widget.Toast;

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

import android.view.ViewGroup;


/**
 * ================================================================
 *                  用户定位页面（定位 + 新闻占位）
 *                  UserActivity
 * ================================================================
 * - 打开页面：读取 fingerprint_db.json，加载指纹库
 * - 打开页面后：自动执行一次定位（如果库非空）
 * - “Locate Me” 按钮：手动再次定位
 * - 底部新闻栏：只是本地占位文本，不做真实爬虫
 * ================================================================
 */
public class UserActivity extends AppCompatActivity {

    private static final String TAG = "UserActivity";
    private static final int REQ_CODE_LOCATION = 2001;

    // ===== 调试开关：true 时完全绕过 WiFi 扫描 =====
    // TODO: 等wifi扫描可以用的时候把DEBUG_BYPASS_WIFI改成false
    private static final boolean DEBUG_BYPASS_WIFI = true;
    // 调试时要强制显示的房间
    private static final String DEBUG_FAKE_ROOM = "405";

    // ===== 与 AdminActivity 保持一致的参数 =====
    private static final int NUM_SCANS_FOR_LOCATE = 4;   // 定位时连续扫描次数
    private static final int MIN_RSSI_DBM = -85;         // 弱信号过滤阈值
    private static final double MISSING_RSSI = -100.0;   // 缺失 AP 的默认 RSSI
    private static final double DISTANCE_THRESHOLD = 70.0;
    private static final int K_NEIGHBORS = 3;
    private static final int MIN_AP_MATCH_REQUIRED = 6;  // 最少 AP 数量阈值
    private static final String FP_DB_FILE = "fingerprint_db.json";

    private WifiManager wifiManager;

    // UI
    private TextView tvResult;

    // tab（暂时只是占位，不做切换逻辑也没关系）
    private Button btnTabLocate;
    private Button btnTabNavigate;

    // 新闻区域占位
    private LinearLayout newsContainer;
    private Button btnRefreshNews;

    // 定位相关状态
    private boolean isLocating = false;
    private int currentLocateScanCount = 0;
    // key: BSSID, value: 多次扫描得到的 RSSI 列表
    private Map<String, ArrayList<Integer>> locateSamples = new HashMap<>();

    // 指纹库
    private ArrayList<FingerprintRecord> fingerprintLibrary = new ArrayList<>();

    private ImageView imgFloor;
    private ImageView imgUserLoc;
    // 房间 -> 在平面图中的相对坐标（0~1）
    private Map<String, RoomPos> roomPosMap = new HashMap<>();
    private int currentFloor = -1;


    // 接收 WiFi 扫描结果的广播
    private final BroadcastReceiver wifiScanReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false);
            if (!success) {
                tvResult.append("\nScan failed or throttled by system.\n");

                if (isLocating) {
                    isLocating = false;
                    tvResult.append("Locate failed due to scan error.\n");
                }
                return;
            }

            if (ActivityCompat.checkSelfPermission(UserActivity.this,
                    Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                    && ActivityCompat.checkSelfPermission(UserActivity.this,
                    Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

                tvResult.append("Location permission not granted. Cannot read scan results.\n");
                return;
            }

            List<ScanResult> results = wifiManager.getScanResults();

            if (!isLocating) {
                // 不是定位模式就忽略
                return;
            }

            // ========== 定位模式 ==========
            currentLocateScanCount++;

            tvResult.append("Locate scan #" + currentLocateScanCount + " finished, got "
                    + (results == null ? 0 : results.size()) + " networks.\n");

            if (results != null) {
                for (ScanResult result : results) {
                    int rssi = result.level;
                    if (rssi < MIN_RSSI_DBM) continue;  // 过滤弱信号

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
                // 继续下一轮扫描
                startWifiScan();
            } else {
                // 扫描结束，计算平均 RSSI 并定位
                isLocating = false;

                if (locateSamples.isEmpty()) {
                    tvResult.append("No valid APs collected for locating.\n");
                    return;
                }

                Map<String, Double> currentMap = new HashMap<>();
                for (Map.Entry<String, ArrayList<Integer>> entry : locateSamples.entrySet()) {
                    String bssid = entry.getKey();
                    ArrayList<Integer> list = entry.getValue();
                    if (list == null || list.isEmpty()) continue;
                    int sum = 0;
                    for (int v : list) sum += v;
                    double avg = sum * 1.0 / list.size();
                    currentMap.put(bssid, avg);
                }

                tvResult.append("Current locating fingerprint has " + currentMap.size() + " APs.\n");

                // AP 数量太少，直接拒绝
                if (currentMap.size() < MIN_AP_MATCH_REQUIRED) {
                    tvResult.append("Too few APs. Signal not stable enough, please try again.\n");
                    locateSamples.clear();
                    return;
                }

                locateWithCurrentScan(currentMap);
                locateSamples.clear();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user);

        // ===== 绑定控件 =====
        tvResult       = findViewById(R.id.tvResult);
        btnTabLocate   = findViewById(R.id.btnTabLocate);
        btnTabNavigate = findViewById(R.id.btnTabNavigate);
        newsContainer  = findViewById(R.id.newsContainer);
        btnRefreshNews = findViewById(R.id.btnRefreshNews);
        imgFloor = findViewById(R.id.img_floor);

        // 新建一个覆盖在平面图上的位置图标
        imgUserLoc = new ImageView(this);
        imgUserLoc.setImageResource(R.drawable.ic_locate);

        // 设置等比例缩放 + 固定大小（例如 24dp）
        int size = (int) (17 * getResources().getDisplayMetrics().density);

        ViewGroup.LayoutParams params =
                new ViewGroup.LayoutParams(size, size);

        imgUserLoc.setLayoutParams(params);
        imgUserLoc.setAdjustViewBounds(true);
        imgUserLoc.setVisibility(View.GONE); // 默认不显示

        // 把图标加到根布局上（会盖在 imgFloor 上面）
        ViewGroup root = (ViewGroup) ((ViewGroup) findViewById(android.R.id.content)).getChildAt(0);
        root.addView(imgUserLoc, params);


        // 初始化每个房间在平面图上的相对坐标
        initRoomPositions();

        Button btn3 = findViewById(R.id.btn_floor3);
        Button btn4 = findViewById(R.id.btn_floor4);
        Button btn5 = findViewById(R.id.btn_floor5);

        View.OnClickListener floorClickListener = v -> {
            int id = v.getId();
            if (id == R.id.btn_floor3) {
                imgFloor.setImageResource(R.drawable.floor3);
                currentFloor = 3;
            } else if (id == R.id.btn_floor4) {
                imgFloor.setImageResource(R.drawable.floor4);
                currentFloor = 4;
            } else if (id == R.id.btn_floor5) {
                imgFloor.setImageResource(R.drawable.floor5);
                currentFloor = 5;
            }
        };

        btn3.setOnClickListener(floorClickListener);
        btn4.setOnClickListener(floorClickListener);
        btn5.setOnClickListener(floorClickListener);

        // WiFi
        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager == null) {
            tvResult.setText("WifiManager is null. This device may not support WiFi.");
            return;
        }

        // 加载指纹库
        loadFingerprintLibraryFromFile();

        // 注册 WiFi 扫描广播
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        registerReceiver(wifiScanReceiver, intentFilter);

        // 底部新闻：先放占位文本
        loadNewsPlaceholder();
        // 🟢 点击“Relocate”按钮 → 手动执行定位
        btnTabLocate.setOnClickListener(v -> startLocateProcedure());

        // “Navigation” 按钮暂时占位
        btnTabNavigate.setOnClickListener(v ->
                Toast.makeText(this, "Navigation function not implemented yet.", Toast.LENGTH_SHORT).show()
        );

        // 点击 "Refresh News" 只是重新显示占位（以后可以换成真实爬虫）
        btnRefreshNews.setOnClickListener(v -> loadNewsPlaceholder());

        // 打开页面后自动定位一次（库不为空才做）
        if (!fingerprintLibrary.isEmpty()) {
            tvResult.append("Auto locating...\n");
            startLocateProcedure();
        } else {
            tvResult.append("Fingerprint DB is empty. Please build it in Admin mode.\n");
        }
    }

    // 封装：开始一轮定位流程
    private void startLocateProcedure() {
        if (fingerprintLibrary.isEmpty()) {
            Toast.makeText(UserActivity.this,
                    "Fingerprint library is empty. Please collect/save fingerprints in Admin mode first.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        tvResult.setText("");

        // ===== 调试模式：完全跳过 WiFi 扫描，只看房间 + 图标是否对得上 =====
        if (DEBUG_BYPASS_WIFI) {
            String room = DEBUG_FAKE_ROOM;
            tvResult.append("[DEBUG] Bypass WiFi scanning. Force show room " + room + ".\n");
            showUserOnRoom(room);
            return;
        }

        // ===== 正常模式：走真实扫描流程 =====
        isLocating = true;
        currentLocateScanCount = 0;
        locateSamples.clear();

        tvResult.append("Start locating...\n");

        checkPermissionAndScan();
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(wifiScanReceiver);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Receiver not registered or already unregistered", e);
        }
    }

    // ======= 权限 & 扫描 =======

    private void checkPermissionAndScan() {
        boolean fineLocationGranted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;

        boolean coarseLocationGranted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;

        if (!fineLocationGranted || !coarseLocationGranted) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    REQ_CODE_LOCATION
            );
        } else {
            startWifiScan();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQ_CODE_LOCATION) {
            boolean granted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    granted = false;
                    break;
                }
            }
            if (granted) {
                Toast.makeText(this, "Location permission granted. You can scan now.", Toast.LENGTH_SHORT).show();
                startWifiScan();
            } else {
                Toast.makeText(this, "Location permission is required for WiFi scanning.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void startWifiScan() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            tvResult.append("Location permission not granted. Please allow it first.\n");
            return;
        }

        if (!wifiManager.isWifiEnabled()) {
            Toast.makeText(this, "WiFi is disabled. Turning on WiFi...", Toast.LENGTH_SHORT).show();
            wifiManager.setWifiEnabled(true);
        }

        boolean success = wifiManager.startScan();
        if (success) {
            tvResult.append("Start scanning...\n");
            Log.d(TAG, "startScan success");
        } else {
            tvResult.append("startScan failed. Maybe scan is throttled.\n");
            Log.d(TAG, "startScan failed");
        }
    }

    // ======= 指纹库加载 =======

    private void loadFingerprintLibraryFromFile() {
        try {
            FileInputStream fis = openFileInput(FP_DB_FILE);
            StringBuilder sb = new StringBuilder();
            byte[] buf = new byte[1024];
            int len;
            while ((len = fis.read(buf)) != -1) {
                sb.append(new String(buf, 0, len, StandardCharsets.UTF_8));
            }
            fis.close();

            String jsonStr = sb.toString();
            JSONArray arr = new JSONArray(jsonStr);

            fingerprintLibrary.clear();

            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                String room = obj.getString("room");
                String rp = obj.getString("rp");

                JSONArray aps = obj.getJSONArray("aps");
                Map<String, Double> avgRssiMap = new HashMap<>();
                for (int j = 0; j < aps.length(); j++) {
                    JSONObject apObj = aps.getJSONObject(j);
                    String bssid = apObj.getString("bssid");
                    double rssi = apObj.getDouble("rssi");
                    avgRssiMap.put(bssid, rssi);
                }

                FingerprintRecord rec = new FingerprintRecord(room, rp, avgRssiMap);
                fingerprintLibrary.add(rec);
            }

            tvResult.setText("Loaded fingerprint DB. Records: " + fingerprintLibrary.size() + "\n");
        } catch (IOException e) {
            tvResult.setText("No saved fingerprint DB file yet. Please use Admin mode to collect.\n");
        } catch (JSONException e) {
            e.printStackTrace();
            tvResult.setText("Load fingerprint DB failed: " + e.getMessage() + "\n");
        }
    }

    // ======= 距离 & 相似度 =======

    private double computeDistance(Map<String, Double> fp, Map<String, Double> cur) {
        HashSet<String> keys = new HashSet<>();
        keys.addAll(fp.keySet());
        keys.addAll(cur.keySet());

        double sumSq = 0.0;
        for (String bssid : keys) {
            double v1 = fp.containsKey(bssid) ? fp.get(bssid) : MISSING_RSSI;
            double v2 = cur.containsKey(bssid) ? cur.get(bssid) : MISSING_RSSI;
            double diff = v1 - v2;
            sumSq += diff * diff;
        }
        return Math.sqrt(sumSq);
    }

    // 计算余弦相似度，返回 [-1, 1]，越接近 1 越相似
    private double computeCosine(Map<String, Double> fp, Map<String, Double> cur) {
        HashSet<String> keys = new HashSet<>();
        keys.addAll(fp.keySet());
        keys.addAll(cur.keySet());

        double dot = 0.0;
        double norm1Sq = 0.0;
        double norm2Sq = 0.0;

        for (String bssid : keys) {
            double v1 = fp.containsKey(bssid) ? fp.get(bssid) : MISSING_RSSI;
            double v2 = cur.containsKey(bssid) ? cur.get(bssid) : MISSING_RSSI;

            dot += v1 * v2;
            norm1Sq += v1 * v1;
            norm2Sq += v2 * v2;
        }

        if (norm1Sq == 0 || norm2Sq == 0) {
            return 0.0;  // 避免除 0，认为相似度为 0
        }

        return dot / (Math.sqrt(norm1Sq) * Math.sqrt(norm2Sq));
    }

    // ======= WKNN + Cosine + Room Voting =======

    private void locateWithCurrentScan(Map<String, Double> currentMap) {
        if (fingerprintLibrary.isEmpty()) {
            tvResult.append("Fingerprint library is empty.\n");
            return;
        }

        // 1. 计算每个指纹的欧氏距离 + 余弦相似度
        ArrayList<Neighbor> neighbors = new ArrayList<>();
        for (FingerprintRecord rec : fingerprintLibrary) {
            double dist = computeDistance(rec.avgRssiMap, currentMap);
            double cos  = computeCosine(rec.avgRssiMap, currentMap);
            neighbors.add(new Neighbor(rec, dist, cos));
        }

        // 2. 排序：先按距离从小到大；距离差不多时按 cos 从大到小
        Collections.sort(neighbors, new Comparator<Neighbor>() {
            @Override
            public int compare(Neighbor o1, Neighbor o2) {
                int c = Double.compare(o1.distance, o2.distance);
                if (c != 0) return c;
                return -Double.compare(o1.cosineSim, o2.cosineSim);
            }
        });

        if (neighbors.isEmpty()) {
            tvResult.append("No neighbors found in fingerprint library.\n");
            return;
        }

        double bestDist = neighbors.get(0).distance;

        // 3. 距离阈值：太远就认为 unknown
        if (bestDist > DISTANCE_THRESHOLD) {
            tvResult.append("\n=== Locate Result (Hybrid) ===\n");
            tvResult.append("Location uncertain. No close match in fingerprint library.\n");
            Neighbor best = neighbors.get(0);
            tvResult.append("Best candidate (but far): Room " + best.record.room
                    + ", RP: " + best.record.rp
                    + "  distance = " + String.format("%.2f", best.distance)
                    + ", cos = " + String.format("%.3f", best.cosineSim) + "\n");
            return;
        }

        // 4. Hybrid WKNN + Cosine：同时做 RP 级和 Room 级投票
        int K = Math.min(K_NEIGHBORS, neighbors.size());

        Map<String, Double> voteMap = new HashMap<>();      // room|rp -> weight
        Map<String, Double> roomVoteMap = new HashMap<>();  // room    -> weight

        for (int i = 0; i < K; i++) {
            Neighbor n = neighbors.get(i);
            double d = n.distance;
            double w = 1.0 / (d + 1e-6); // 距离越小权重越大

            double cos = n.cosineSim;
            if (cos > 0) {
                w *= (1 + cos);          // 结合角度信息
            }

            String room = n.record.room;
            String rp   = n.record.rp;
            String label = room + "|" + rp;

            // RP-level 投票
            voteMap.put(label, voteMap.getOrDefault(label, 0.0) + w);
            // Room-level 投票聚合
            roomVoteMap.put(room, roomVoteMap.getOrDefault(room, 0.0) + w);
        }

        // 5. Room 级别最终输出
        String bestRoom = null;
        double bestRoomScore = -1.0;
        for (Map.Entry<String, Double> e : roomVoteMap.entrySet()) {
            if (e.getValue() > bestRoomScore) {
                bestRoomScore = e.getValue();
                bestRoom = e.getKey();
            }
        }

        // （可选）RP 级别最佳标签，仅用于日志输出
        String bestLabel = null;
        double bestScore = -1.0;
        for (Map.Entry<String, Double> e : voteMap.entrySet()) {
            if (e.getValue() > bestScore) {
                bestScore = e.getValue();
                bestLabel = e.getKey();
            }
        }

        tvResult.append("\n=== Locate Result (Hybrid WKNN + Cosine + Room Voting) ===\n");
        tvResult.append("Probably in Room: " + bestRoom + "\n");
        tvResult.append("Room vote score: " + String.format("%.4f", bestRoomScore) + "\n");
        tvResult.append("Best distance among neighbors: " + String.format("%.2f", bestDist) + "\n");
        tvResult.append("Best RP label weight: " + String.format("%.4f", bestScore)
                + " (" + bestLabel + ")\n\n");

        showUserOnRoom(bestRoom);

        tvResult.append("Top " + K + " neighbors (RP-level):\n");
        for (int i = 0; i < K; i++) {
            Neighbor n = neighbors.get(i);
            tvResult.append(" - Room " + n.record.room
                    + ", RP " + n.record.rp
                    + ", dist = " + String.format("%.2f", n.distance)
                    + ", cos = " + String.format("%.3f", n.cosineSim) + "\n");
        }
    }

    // ======= 辅助数据结构 =======

    // 房间在平面图中的相对位置（0~1）
    private static class RoomPos {
        final float xRatio;
        final float yRatio;

        RoomPos(float xRatio, float yRatio) {
            this.xRatio = xRatio;
            this.yRatio = yRatio;
        }
    }

    // 初始化每个房间的坐标
    private void initRoomPositions() {
        roomPosMap.clear();

        // 在图片上的相对位置（0 左 / 上，1 右 / 下）
        roomPosMap.put("301", new RoomPos(0.25f, 1.1f));
        roomPosMap.put("302", new RoomPos(0.22f, 0.70f));
        roomPosMap.put("303", new RoomPos(0.19f, 0.50f));
        roomPosMap.put("304", new RoomPos(0.50f, 1.0f));
        roomPosMap.put("305", new RoomPos(0.63f, 0.97f));
        roomPosMap.put("306", new RoomPos(0.80f, 0.37f));

        roomPosMap.put("401", new RoomPos(0.25f, 1.1f));
        roomPosMap.put("402", new RoomPos(0.33f, 1.05f));
        roomPosMap.put("403", new RoomPos(0.47f, 1.0f));
        roomPosMap.put("404", new RoomPos(0.54f, 1.0f));
        roomPosMap.put("405", new RoomPos(0.59f, 0.97f));
        roomPosMap.put("406", new RoomPos(0.65f, 0.94f));
        roomPosMap.put("407", new RoomPos(0.90f, 0.80f));

        roomPosMap.put("501", new RoomPos(0.25f, 1.1f));
        roomPosMap.put("502", new RoomPos(0.33f, 1.05f));
        roomPosMap.put("503", new RoomPos(0.54f, 1.0f));
        roomPosMap.put("504", new RoomPos(0.65f, 0.94f));
        roomPosMap.put("505", new RoomPos(0.90f, 0.80f));
    }

    // 根据房间名，把 ic_locate 图标移动到对应位置
    private void showUserOnRoom(String roomName) {
        if (imgFloor == null || imgUserLoc == null) return;

        autoSwitchFloorByRoom(roomName);

        RoomPos pos = roomPosMap.get(roomName);
        if (pos == null) {
            tvResult.append("No position configured for room " + roomName + ".\n");
            imgUserLoc.setVisibility(View.GONE);
            return;
        }

        // 确保 imgFloor 已经完成布局
        imgFloor.post(() -> {
            int w = imgFloor.getWidth();
            int h = imgFloor.getHeight();
            if (w == 0 || h == 0) {
                tvResult.append("Floor image size is zero. Cannot place icon.\n");
                return;
            }

            float x = imgFloor.getX() + pos.xRatio * w;
            float y = imgFloor.getY() + pos.yRatio * h;

            imgUserLoc.setX(x);
            imgUserLoc.setY(y);
            imgUserLoc.setVisibility(View.VISIBLE);
        });
    }

    // 根据房间号自动切换楼层图：
    private void autoSwitchFloorByRoom(String roomName) {
        if (roomName == null || roomName.length() == 0) return;

        int floor;
        try {
            // 比如 301 -> 3, 403 -> 4
            int roomNum = Integer.parseInt(roomName);
            floor = roomNum / 100;
        } catch (NumberFormatException e) {
            // 房间名不是纯数字就算了
            return;
        }

        // 和当前一样就不重复切
        if (floor == currentFloor) return;

        switch (floor) {
            case 3:
                imgFloor.setImageResource(R.drawable.floor3);
                break;
            case 4:
                imgFloor.setImageResource(R.drawable.floor4);
                break;
            case 5:
                imgFloor.setImageResource(R.drawable.floor5);
                break;
            default:
                // 其他楼层你暂时不处理就直接返回
                return;
        }

        currentFloor = floor;
    }


    private static class Neighbor {
        FingerprintRecord record;
        double distance;      // 欧氏距离
        double cosineSim;     // 余弦相似度

        Neighbor(FingerprintRecord r, double d, double c) {
            this.record = r;
            this.distance = d;
            this.cosineSim = c;
        }
    }

    private static class FingerprintRecord {
        String room;
        String rp;
        Map<String, Double> avgRssiMap;

        FingerprintRecord(String room, String rp, Map<String, Double> avgRssiMap) {
            this.room = room;
            this.rp = rp;
            this.avgRssiMap = avgRssiMap;
        }
    }

    // ======= 底部“新闻”占位（不做真实爬虫） =======

    private void loadNewsPlaceholder() {
        if (newsContainer == null) return;

        newsContainer.removeAllViews();

        // 这里随便写几条静态文本，展示布局效果
        String[] demoLines = new String[] {
                "• Campus news module placeholder.",
                "• Later you can replace this with real crawler results.",
                "• For now, this just proves the UI works."
        };

        for (String line : demoLines) {
            TextView tv = new TextView(this);
            tv.setText(line);
            tv.setTextSize(14f);
            tv.setPadding(8, 8, 8, 8);
            newsContainer.addView(tv);
        }
    }
}

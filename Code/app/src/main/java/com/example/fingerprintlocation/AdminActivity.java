package com.example.fingerprintlocation;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;
import android.widget.CheckBox;


/**
 * ================================================================
 *                      Admin / Scan Activity
 *              负责：采集指纹 + 指纹库的管理（B 版增强）
 * ================================================================
 * - 输入 room / rp
 * - Scan：多次 WiFi 扫描取平均（丢弃 1 个异常值）
 * - Show: 显示指纹库概要 + 勾选删除
 * - Save: 保存到本地 JSON 文件（自动合并旧 DB，不覆盖）
 * - Load: 从 JSON 文件读取指纹库
 * - Clear: 清空指纹库并删除文件
 * ================================================================
 */
public class AdminActivity extends AppCompatActivity {

    private static final String TAG = "AdminActivity";
    private static final int REQ_CODE_LOCATION = 1001;

    // 建库：每个位置连续扫描次数（B 版推荐 6）
    private static final int NUM_SCANS_FOR_FINGERPRINT = 6;
    // 过滤太弱的 AP（dBm，小于这个数就当噪声，忽略）
    private static final int MIN_RSSI_DBM = -85;
    // 指纹库文件名
    private static final String FP_DB_FILE = "fingerprint_db.json";

    private WifiManager wifiManager;

    private TextView tvResult;
    private Button btnScan;
    private Button btnShowLibrary;
    private Button btnSaveDb;
    private Button btnLoadDb;
    private Button btnClearDb;
    private EditText etRoom;
    private EditText etRp;

    // 下方 RP 勾选列表 + 删除按钮
    private LinearLayout rpListContainer;
    private Button btnDeleteSelected;
    private ArrayList<CheckBox> rpCheckBoxList = new ArrayList<>();

    // 是否正在采集指纹
    private boolean isCollectingFingerprint = false;
    // 当前采集了几轮
    private int currentScanCount = 0;

    // 采集模式下：key: BSSID，value: RSSI 样本列表
    private Map<String, ArrayList<Integer>> rssiSamples = new HashMap<>();
    // 内存中的指纹库
    private ArrayList<FingerprintRecord> fingerprintLibrary = new ArrayList<>();

    // 接收 WiFi 扫描结果的广播
    private final BroadcastReceiver wifiScanReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false);
            if (!success) {
                tvResult.append("\nScan failed or throttled by system.\n");

                // 采集失败，如果已经有部分数据，就用现有样本算一次指纹
                if (isCollectingFingerprint && currentScanCount > 0) {
                    isCollectingFingerprint = false;
                    showFingerprintResult();
                }
                return;
            }

            // 权限检查
            if (ActivityCompat.checkSelfPermission(AdminActivity.this,
                    Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                    && ActivityCompat.checkSelfPermission(AdminActivity.this,
                    Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                tvResult.append("Location permission not granted. Cannot read scan results.\n");
                return;
            }

            // 拿到一次扫描结果
            List<ScanResult> results = wifiManager.getScanResults();

            // 只在采集模式下处理
            if (!isCollectingFingerprint) return;

            currentScanCount++;
            tvResult.append("Scan #" + currentScanCount + " finished, got "
                    + (results == null ? 0 : results.size()) + " networks.\n");

            if (results != null) {
                for (ScanResult result : results) {
                    int rssi = result.level;
                    if (rssi < MIN_RSSI_DBM) continue; // 过滤弱信号

                    String bssid = result.BSSID;
                    ArrayList<Integer> list = rssiSamples.get(bssid);
                    if (list == null) {
                        list = new ArrayList<>();
                        rssiSamples.put(bssid, list);
                    }
                    list.add(rssi);
                }
            }

            if (currentScanCount < NUM_SCANS_FOR_FINGERPRINT) {
                // 还没达到设定次数，继续下一轮扫描
                startWifiScan();
            } else {
                // 采集完成，计算平均 RSSI，生成一条指纹
                isCollectingFingerprint = false;
                showFingerprintResult();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin);

        // 基本控件
        tvResult       = findViewById(R.id.tvResult);
        btnScan        = findViewById(R.id.btnScan);
        btnShowLibrary = findViewById(R.id.btnShowLibrary);
        btnSaveDb      = findViewById(R.id.btnSaveDb);
        btnLoadDb      = findViewById(R.id.btnLoadDb);
        btnClearDb     = findViewById(R.id.btnClearDb);
        etRoom         = findViewById(R.id.etRoom);
        etRp           = findViewById(R.id.etRp);

        // 下方 RP 列表容器 + 删除按钮
        rpListContainer   = findViewById(R.id.rpListContainer);
        btnDeleteSelected = findViewById(R.id.btnDeleteSelected);
        btnDeleteSelected.setVisibility(View.GONE);

        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager == null) {
            tvResult.setText("WifiManager is null. This device may not support WiFi.");
            return;
        }

        // 启动时静默加载 DB（不 Toast，只 merge）
        loadFingerprintLibraryFromFileSilently();

        // 注册 WiFi 扫描广播
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        registerReceiver(wifiScanReceiver, intentFilter);

        // ========== 采集指纹按钮 ==========
        btnScan.setOnClickListener(v -> {
            String room = etRoom.getText().toString().trim();
            String rp   = etRp.getText().toString().trim();

            if (room.isEmpty() || rp.isEmpty()) {
                Toast.makeText(AdminActivity.this,
                        "Please enter BOTH Room and RP name before scanning.",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            isCollectingFingerprint = true;
            currentScanCount = 0;
            rssiSamples.clear();

            resetRpListUI();
            tvResult.setText("");
            tvResult.append("Start fingerprint collection for ["
                    + room + " - " + rp + "]...\n");

            checkPermissionAndScan();
        });

        // 显示指纹库列表（下方区域）
        btnShowLibrary.setOnClickListener(v -> {
            tvResult.setText("");
            showFingerprintLibrary();
        });

        // 保存指纹库（先 merge 旧 DB，再保存）
        btnSaveDb.setOnClickListener(v -> {
            resetRpListUI();
            tvResult.setText("");

            // 关键：保存前静默加载原 DB，合并旧数据
            loadFingerprintLibraryFromFileSilently();
            saveFingerprintLibraryToFile();
        });

        // 加载指纹库（覆盖内存中的 library）
        btnLoadDb.setOnClickListener(v -> {
            resetRpListUI();
            tvResult.setText("");
            loadFingerprintLibraryFromFile();
            showFingerprintLibrary();
        });

        // 清空指纹库
        btnClearDb.setOnClickListener(v -> {
            clearFingerprintLibrary();
            resetRpListUI();
        });

        // 删除选中 RP
        btnDeleteSelected.setOnClickListener(v -> {
            if (fingerprintLibrary.isEmpty()) {
                Toast.makeText(AdminActivity.this,
                        "No RP to delete.", Toast.LENGTH_SHORT).show();
                return;
            }

            boolean hasSelected = false;
            for (CheckBox cb : rpCheckBoxList) {
                if (cb.isChecked()) {
                    hasSelected = true;
                    break;
                }
            }
            if (!hasSelected) {
                Toast.makeText(AdminActivity.this,
                        "Please select at least one RP.", Toast.LENGTH_SHORT).show();
                return;
            }

            new android.app.AlertDialog.Builder(AdminActivity.this)
                    .setTitle("Confirm delete")
                    .setMessage("Delete selected RP(s) from fingerprint library?")
                    .setPositiveButton("Delete", (dialog, which) -> {
                        for (int i = rpCheckBoxList.size() - 1; i >= 0; i--) {
                            if (rpCheckBoxList.get(i).isChecked()) {
                                fingerprintLibrary.remove(i);
                            }
                        }
                        saveFingerprintLibraryToFile();
                        Toast.makeText(AdminActivity.this,
                                "Deleted selected RP(s).", Toast.LENGTH_SHORT).show();
                        showFingerprintLibrary();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
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

    // ================= 权限 & 扫描 =================

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

    // ================= 指纹生成 & 显示 =================

    // 计算平均 RSSI，并丢弃 1 个偏差最大值（B 版增强）
    private void showFingerprintResult() {
        String room = etRoom.getText().toString().trim();
        String rp   = etRp.getText().toString().trim();

        if (room.isEmpty() || rp.isEmpty()) {
            tvResult.append("Room / RP is empty, skip.\n");
            return;
        }

        Map<String, Double> avgRssiMap = new HashMap<>();

        StringBuilder sb = new StringBuilder();
        sb.append("\n====================\n");
        sb.append("Fingerprint result for [")
                .append(room).append(" - ").append(rp)
                .append("] (average of ")
                .append(NUM_SCANS_FOR_FINGERPRINT)
                .append(" scans, with outlier removal):\n\n");

        for (Map.Entry<String, ArrayList<Integer>> entry : rssiSamples.entrySet()) {
            String bssid = entry.getKey();
            ArrayList<Integer> list = entry.getValue();
            if (list == null || list.isEmpty()) continue;

            // B 版：如果样本数量 ≥ 3，则丢掉一个偏差最大的样本
            if (list.size() >= 3) {
                int sum0 = 0;
                for (int v : list) sum0 += v;
                double mean0 = sum0 * 1.0 / list.size();

                int outlierIndex = -1;
                double maxDev = -1.0;
                for (int i = 0; i < list.size(); i++) {
                    double dev = Math.abs(list.get(i) - mean0);
                    if (dev > maxDev) {
                        maxDev = dev;
                        outlierIndex = i;
                    }
                }
                if (outlierIndex >= 0) {
                    list.remove(outlierIndex);
                }
            }

            int sum = 0;
            for (int v : list) sum += v;
            double avg = sum * 1.0 / list.size();

            // 过滤弱 AP
            if (avg < MIN_RSSI_DBM) continue;

            avgRssiMap.put(bssid, avg);

            sb.append("BSSID: ").append(bssid)
                    .append("  avg RSSI: ").append(String.format("%.2f", avg)).append(" dBm")
                    .append("  (samples used: ").append(list.size()).append(")\n");
        }

        tvResult.append(sb.toString());

        // 检查该 room + rp 是否已经存在
        int existingIndex = -1;
        for (int i = 0; i < fingerprintLibrary.size(); i++) {
            FingerprintRecord r = fingerprintLibrary.get(i);
            if (r.room.equals(room) && r.rp.equals(rp)) {
                existingIndex = i;
                break;
            }
        }

        FingerprintRecord record = new FingerprintRecord(room, rp, avgRssiMap);

        if (existingIndex >= 0) {
            fingerprintLibrary.set(existingIndex, record);
            tvResult.append("\nUpdated existing fingerprint for ["
                    + room + " - " + rp + "]\n");
        } else {
            fingerprintLibrary.add(record);
            tvResult.append("\nSaved NEW fingerprint. Total records = "
                    + fingerprintLibrary.size()
                    + ", APs in last record = " + avgRssiMap.size() + "\n");
        }

        rssiSamples.clear();
    }

    // 保存指纹库到文件
    private void saveFingerprintLibraryToFile() {
        if (fingerprintLibrary.isEmpty()) {
            Toast.makeText(this, "Nothing to save. DB remains unchanged.", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            JSONArray arr = new JSONArray();

            for (FingerprintRecord rec : fingerprintLibrary) {
                JSONObject obj = new JSONObject();
                obj.put("room", rec.room);
                obj.put("rp", rec.rp);

                JSONArray aps = new JSONArray();
                for (Map.Entry<String, Double> e : rec.avgRssiMap.entrySet()) {
                    JSONObject apObj = new JSONObject();
                    apObj.put("bssid", e.getKey());
                    apObj.put("rssi", e.getValue());
                    aps.put(apObj);
                }
                obj.put("aps", aps);
                arr.put(obj);
            }

            String jsonStr = arr.toString();
            FileOutputStream fos = openFileOutput(FP_DB_FILE, MODE_PRIVATE);
            fos.write(jsonStr.getBytes(StandardCharsets.UTF_8));
            fos.close();

            Toast.makeText(this, "Saved to file. Records: " + fingerprintLibrary.size(),
                    Toast.LENGTH_SHORT).show();

        } catch (IOException | JSONException e) {
            e.printStackTrace();
            Toast.makeText(this, "Save failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // 正常「显式加载」DB（会清空当前 library）
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

            Toast.makeText(this, "Loaded from file. Records: " + fingerprintLibrary.size(),
                    Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Toast.makeText(this, "No saved DB file yet.", Toast.LENGTH_SHORT).show();
        } catch (JSONException e) {
            e.printStackTrace();
            Toast.makeText(this, "Load failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // 静默 merge：不会清空当前 library，不弹 Toast，
    // 用于启动 / 保存前先把旧 DB 合并进来，避免覆盖历史数据
    private void loadFingerprintLibraryFromFileSilently() {
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

            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                String room = obj.getString("room");
                String rp   = obj.getString("rp");

                JSONArray aps = obj.getJSONArray("aps");
                Map<String, Double> avgRssiMap = new HashMap<>();
                for (int j = 0; j < aps.length(); j++) {
                    JSONObject apObj = aps.getJSONObject(j);
                    String bssid = apObj.getString("bssid");
                    double rssi = apObj.getDouble("rssi");
                    avgRssiMap.put(bssid, rssi);
                }

                // 不覆盖内存中已有的同名 room+rp，只补充还没有的
                boolean exists = false;
                for (FingerprintRecord r : fingerprintLibrary) {
                    if (r.room.equals(room) && r.rp.equals(rp)) {
                        exists = true;
                        break;
                    }
                }
                if (!exists) {
                    fingerprintLibrary.add(new FingerprintRecord(room, rp, avgRssiMap));
                }
            }

        } catch (IOException | JSONException e) {
            // 静默：文件不存在也没关系，第一次运行会走这里
        }
    }

    // 显示指纹库概要 & 下方 CheckBox 列表
    private void showFingerprintLibrary() {
        rpListContainer.removeAllViews();
        rpCheckBoxList.clear();

        if (fingerprintLibrary.isEmpty()) {
            tvResult.setText("Library is empty.\n");
            btnDeleteSelected.setVisibility(View.GONE);
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Total RPs: ").append(fingerprintLibrary.size()).append("\n");
        tvResult.setText(sb.toString());

        for (int i = 0; i < fingerprintLibrary.size(); i++) {
            FingerprintRecord r = fingerprintLibrary.get(i);
            CheckBox cb = new CheckBox(this);
            cb.setText((i + 1) + ". " + r.room + " - " + r.rp);
            cb.setPadding(8, 8, 8, 8);

            rpListContainer.addView(cb);
            rpCheckBoxList.add(cb);
        }

        btnDeleteSelected.setVisibility(View.VISIBLE);
    }

    // 清空下面的 RP 列表区域，并隐藏“Delete Selected”按钮
    private void resetRpListUI() {
        if (rpListContainer != null) {
            rpListContainer.removeAllViews();
        }
        if (rpCheckBoxList != null) {
            rpCheckBoxList.clear();
        }
        if (btnDeleteSelected != null) {
            btnDeleteSelected.setVisibility(View.GONE);
        }
    }

    private void clearFingerprintLibrary() {
        fingerprintLibrary.clear();
        boolean deleted = deleteFile(FP_DB_FILE);
        tvResult.setText("Fingerprint library cleared. File deleted: " + deleted);
    }

    // 指纹数据结构
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

    // 点击空白处隐藏软键盘
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            View v = getCurrentFocus();
            if (v != null) {
                Rect outRect = new Rect();
                v.getGlobalVisibleRect(outRect);
                if (!outRect.contains((int) ev.getRawX(), (int) ev.getRawY())) {
                    v.clearFocus();
                    InputMethodManager imm =
                            (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                    }
                }
            }
        }
        return super.dispatchTouchEvent(ev);
    }
}

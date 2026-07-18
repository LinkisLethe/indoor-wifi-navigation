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
 *      Responsible for fingerprint collection and library management.
 * ================================================================
 * - Input: room / rp
 * - Scan: Performs multiple WiFi scans and calculates the average, discarding one outlier.
 * - Show: Displays a summary of the fingerprint library and allows for selection and deletion.
 * - Save: Saves the library to a local JSON file, merging with the existing database.
 * - Load: Loads the fingerprint library from the JSON file.
 * - Clear: Clears the fingerprint library and deletes the corresponding file.
 * ================================================================
 */
public class AdminActivity extends AppCompatActivity {

    private static final String TAG = "AdminActivity";
    private static final int REQ_CODE_LOCATION = 1001;

    private static final int NUM_SCANS_FOR_FINGERPRINT = 6;
    private static final int MIN_RSSI_DBM = -85;
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

    private LinearLayout rpListContainer;
    private Button btnDeleteSelected;
    private ArrayList<CheckBox> rpCheckBoxList = new ArrayList<>();

    private boolean isCollectingFingerprint = false;
    private int currentScanCount = 0;
    private Map<String, ArrayList<Integer>> rssiSamples = new HashMap<>();
    private ArrayList<FingerprintRecord> fingerprintLibrary = new ArrayList<>();

    // Broadcast receiving WiFi scan results
    private final BroadcastReceiver wifiScanReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false);
            if (!success) {
                tvResult.append("\nScan failed or throttled by system.\n");

                // Collection failed. If there is already some data available, use the existing samples to calculate one fingerprint.
                if (isCollectingFingerprint && currentScanCount > 0) {
                    isCollectingFingerprint = false;
                    showFingerprintResult();
                }
                return;
            }

            // check
            if (ActivityCompat.checkSelfPermission(AdminActivity.this,
                    Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                    && ActivityCompat.checkSelfPermission(AdminActivity.this,
                    Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                tvResult.append("Location permission not granted. Cannot read scan results.\n");
                return;
            }

            List<ScanResult> results = wifiManager.getScanResults();

            // Process only in the collection mode
            if (!isCollectingFingerprint) return;

            currentScanCount++;
            tvResult.append("Scan #" + currentScanCount + " finished, got "
                    + (results == null ? 0 : results.size()) + " networks.\n");

            if (results != null) {
                for (ScanResult result : results) {
                    int rssi = result.level;
                    if (rssi < MIN_RSSI_DBM) continue; // Filter weak signals

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
                // The set number has not been reached yet. Proceed to the next round of scanning.
                startWifiScan();
            } else {
                // Collection completed. Calculate the average RSSI and generate a fingerprint.
                isCollectingFingerprint = false;
                showFingerprintResult();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin);

        // Basic controls
        tvResult       = findViewById(R.id.tvResult);
        btnScan        = findViewById(R.id.btnScan);
        btnShowLibrary = findViewById(R.id.btnShowLibrary);
        btnSaveDb      = findViewById(R.id.btnSaveDb);
        btnLoadDb      = findViewById(R.id.btnLoadDb);
        btnClearDb     = findViewById(R.id.btnClearDb);
        etRoom         = findViewById(R.id.etRoom);
        etRp           = findViewById(R.id.etRp);

        // Container for the RP list below + delete button
        rpListContainer   = findViewById(R.id.rpListContainer);
        btnDeleteSelected = findViewById(R.id.btnDeleteSelected);
        btnDeleteSelected.setVisibility(View.GONE);

        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager == null) {
            tvResult.setText("WifiManager is null. This device may not support WiFi.");
            return;
        }

        // Silently load the DB on startup (no Toast, just merge)
        loadFingerprintLibraryFromFileSilently();

        // Register WiFi scan broadcast receiver
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        registerReceiver(wifiScanReceiver, intentFilter);

        // ========== Collect Fingerprint Button ==========
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

        // Show fingerprint library list (bottom area)
        btnShowLibrary.setOnClickListener(v -> {
            tvResult.setText("");
            showFingerprintLibrary();
        });

        // Save fingerprint library (merge with old DB first, then save)
        btnSaveDb.setOnClickListener(v -> {
            resetRpListUI();
            tvResult.setText("");

            // [DELETE THIS LINE] Do not reload here, otherwise it will clear the new scan results in memory
            // loadFingerprintLibraryFromFileSilently();

            // Directly save the fingerprintLibrary currently in memory
            saveFingerprintLibraryToFile();
        });

        // Load fingerprint library (overwrites the library in memory)
        btnLoadDb.setOnClickListener(v -> {
            resetRpListUI();
            tvResult.setText("");
            loadFingerprintLibraryFromFile();
            showFingerprintLibrary();
        });

        // Clear fingerprint library
        btnClearDb.setOnClickListener(v -> {
            clearFingerprintLibrary();
            resetRpListUI();
        });

        // Delete selected RP
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

    // ================= Permissions & Scanning =================

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

    // ================= Fingerprint Generation & Display =================

    // Calculate the average RSSI, and discard the value with the largest deviation (Enhanced in version B)
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

            // Version B: If the number of samples is >= 3, discard the sample with the largest deviation.
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

            // Filter weak APs
            if (avg < MIN_RSSI_DBM) continue;

            avgRssiMap.put(bssid, avg);

            sb.append("BSSID: ").append(bssid)
                    .append("  avg RSSI: ").append(String.format("%.2f", avg)).append(" dBm")
                    .append("  (samples used: ").append(list.size()).append(")\n");
        }

        tvResult.append(sb.toString());

        // Check if this room + rp already exists
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

    // Save fingerprint library to file
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

    // Normal "explicit load" of DB (will clear the current library)
    // Load DB: Assets (Factory) + Local (New) -> Merge
    private void loadFingerprintLibraryFromFile() {
        fingerprintLibrary.clear();
        int countAssets = 0;
        int countLocal = 0;

        // 1. Try load from Assets (Old phone data)
        try {
            java.io.InputStream is = getAssets().open(FP_DB_FILE);
            String jsonStr = readStreamToString(is);
            countAssets = parseJsonAndMerge(jsonStr); // Helper method
        } catch (IOException e) {
            // No assets file, normal.
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing assets JSON: " + e.getMessage());
        }

        // 2. Try load from Local Storage (New collected data)
        try {
            FileInputStream fis = openFileInput(FP_DB_FILE);
            String jsonStr = readStreamToString(fis);
            countLocal = parseJsonAndMerge(jsonStr); // Helper method
        } catch (IOException e) {
            // No local file yet, normal.
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing local JSON: " + e.getMessage());
        }

        int total = fingerprintLibrary.size();
        Toast.makeText(this, "Loaded DB. Total: " + total + "\n(From Assets: " + countAssets + ", Local merged: " + countLocal + ")", Toast.LENGTH_LONG).show();
    }

    // Silent merge: does not clear the current library, no Toast,
    // used to merge the old DB before startup / saving to avoid overwriting historical data
    // Silent load for merging before save
    private void loadFingerprintLibraryFromFileSilently() {
        // Note: In this logic, we append to existing memory list,
        // or you can choose to reload everything to be safe.
        // Here we choose to reload fresh to avoid duplicates.
        fingerprintLibrary.clear();

        try {
            java.io.InputStream is = getAssets().open(FP_DB_FILE);
            parseJsonAndMerge(readStreamToString(is));
        } catch (Exception e) { /* Ignore */ }

        try {
            FileInputStream fis = openFileInput(FP_DB_FILE);
            parseJsonAndMerge(readStreamToString(fis));
        } catch (Exception e) { /* Ignore */ }
    }
    // Parse JSON string and merge into fingerprintLibrary
// Returns the number of records added/updated from this string
    private int parseJsonAndMerge(String jsonStr) throws JSONException {
        JSONArray arr = new JSONArray(jsonStr);
        int count = 0;

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

            // Check for duplicates in memory
            int existingIndex = -1;
            for (int k = 0; k < fingerprintLibrary.size(); k++) {
                FingerprintRecord r = fingerprintLibrary.get(k);
                if (r.room.equals(room) && r.rp.equals(rp)) {
                    existingIndex = k;
                    break;
                }
            }

            FingerprintRecord newRecord = new FingerprintRecord(room, rp, avgRssiMap);
            if (existingIndex >= 0) {
                // Update existing (Local overrides Assets usually, or simple overwrite)
                fingerprintLibrary.set(existingIndex, newRecord);
            } else {
                // Add new
                fingerprintLibrary.add(newRecord);
            }
            count++;
        }
        return count;
    }
    // Display fingerprint library summary & CheckBox list below
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

    // Clear the RP list area below and hide the "Delete Selected" button
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

    // Fingerprint data structure
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

    // Hide soft keyboard when clicking on a blank area
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
    // ==========================================================
// Helper: Read InputStream to String (Add this to both Activities)
// ==========================================================
    private String readStreamToString(java.io.InputStream is) throws IOException {
        java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        reader.close();
        return sb.toString();
    }
}

package com.example.fingerprintlocation;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.io.InputStream;
import org.json.JSONArray;
import org.json.JSONObject;
import android.util.Log;
import android.widget.EditText;
import android.graphics.PointF;



public class NavigationActivity extends AppCompatActivity {

    // 顶部 Tab
    private Button btnTabLocate;
    private Button btnTabNavigate;

    // 楼层按钮
    private Button btnFloor3;
    private Button btnFloor4;
    private Button btnFloor5;

    // 地图 & 信息
    private ImageView imgFloorMap;
    private ImageView imgLocationPin;   // 以后用来显示定位点
    private TextView tvInfo;            // 底部“定位信息…”

    // 用于存储路径节点
    private List<String> path = new ArrayList<>();
    private Map<String, List<String>> floorRooms = new HashMap<>();

    private List<String> stairsNodes = new ArrayList<>();

    // 成员变量
    private EditText etStartRoom, etEndRoom;
    private Button btnConfirm;

    private LineView pathOverlay;

    private Map<String, PointF> nodeCoords3F = new HashMap<>();
    private Map<String, PointF> nodeCoords4F = new HashMap<>();
    private Map<String, PointF> nodeCoords5F = new HashMap<>();






    @Override
    protected void onCreate(Bundle savedInstanceState) {
        loadFingerprintData();


        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_navigation);

        initNodeCoords();


        // === 1. 绑定控件 ===
        btnTabLocate    = findViewById(R.id.btnTabLocate);
        btnTabNavigate  = findViewById(R.id.btnTabNavigate);

        btnFloor3       = findViewById(R.id.btn_floor3);
        btnFloor4       = findViewById(R.id.btn_floor4);
        btnFloor5       = findViewById(R.id.btn_floor5);

        imgFloorMap     = findViewById(R.id.img_floor_map);
        imgLocationPin  = findViewById(R.id.img_location_pin);
        tvInfo          = findViewById(R.id.layout_info);

        etStartRoom = findViewById(R.id.et_start_room);
        etEndRoom   = findViewById(R.id.et_end_room);
        btnConfirm  = findViewById(R.id.btn_confirm);

        pathOverlay = findViewById(R.id.path_overlay);

        btnConfirm.setOnClickListener(v -> {
            String start = etStartRoom.getText().toString().trim();
            String end   = etEndRoom.getText().toString().trim();

            if (start.isEmpty() || end.isEmpty()) {
                tvInfo.setText("请先输入起点和终点房间号");
                return;
            }

            // 使用真正的路径规划逻辑（你已经有 computePath）
            List<String> path = computePath(start, end);
            tvInfo.setText("规划路径: " + path.toString());

            // 当前先假设画“起点所在楼层”的路径
            String startFloor = getFloorFromRoom(start);

            // 切换地图到起点楼层（保证图片和线是同一层）
            if (startFloor.equals("3F")) {
                imgFloorMap.setImageResource(R.drawable.floor3);
                highlightFloorButton(3);
            } else if (startFloor.equals("4F")) {
                imgFloorMap.setImageResource(R.drawable.floor4);
                highlightFloorButton(4);
            } else if (startFloor.equals("5F")) {
                imgFloorMap.setImageResource(R.drawable.floor5);
                highlightFloorButton(5);
            }

            // 构造这一层上的折线路径坐标
            List<PointF> pts = buildPathPointsForFloor(path, startFloor);
            pathOverlay.setPathPoints(pts);
        });





        // === 2. 顶部 Tab 的点击事件 ===

        // 当前页面是 Navigation，所以 Navigation 按钮不需要跳转
        btnTabNavigate.setOnClickListener(v -> {
            // 可以什么都不做，或者给个 Toast 提示“已经在 Navigation 页面”
            // Toast.makeText(this, "Already in Navigation", Toast.LENGTH_SHORT).show();
        });

        // 点击 Relocate 回到 UserActivity
        btnTabLocate.setOnClickListener(v -> {
            Intent intent = new Intent(NavigationActivity.this, UserActivity.class);
            // 防止堆太多 Activity，可选：
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            finish();   // 关闭当前 NavigationActivity
        });

        // === 3. 楼层按钮的点击事件（切换图片） ===

        btnFloor3.setOnClickListener(v -> {
            imgFloorMap.setImageResource(R.drawable.floor3);
            tvInfo.setText("当前楼层：3F");
            highlightFloorButton(3);
        });

        btnFloor4.setOnClickListener(v -> {
            imgFloorMap.setImageResource(R.drawable.floor4);
            tvInfo.setText("当前楼层：4F");
            highlightFloorButton(4);
        });

        btnFloor5.setOnClickListener(v -> {
            imgFloorMap.setImageResource(R.drawable.floor5);
            tvInfo.setText("当前楼层：5F");
            highlightFloorButton(5);
        });

        // 默认显示 3F
        highlightFloorButton(3);

    }

    /**
     * 简单高亮当前选择的楼层按钮（可选，不想要可以删除这个方法和调用）
     */
    private void highlightFloorButton(int floor) {
        int selectedColor   = getResources().getColor(android.R.color.holo_purple);
        int normalColor     = getResources().getColor(android.R.color.darker_gray);

        // 文字颜色统一白色
        btnFloor3.setTextColor(getResources().getColor(android.R.color.white));
        btnFloor4.setTextColor(getResources().getColor(android.R.color.white));
        btnFloor5.setTextColor(getResources().getColor(android.R.color.white));

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            btnFloor3.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    floor == 3 ? selectedColor : normalColor));
            btnFloor4.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    floor == 4 ? selectedColor : normalColor));
            btnFloor5.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    floor == 5 ? selectedColor : normalColor));
        }
    }

//    // TODO: 这里的坐标是示例值，你要根据 floor3/floor4/floor5 图片自己改成真实像素坐标
// TODO: 这些坐标是你要自己根据 floor3/floor4/floor5 图片调出来的像素位置
private void initNodeCoords() {
    // ===== 3F 示例 =====
    // 比如 3F 图片里：302 在左边，303 在右边，楼梯在右下角
    nodeCoords3F.put("302", new PointF(200, 400));
    nodeCoords3F.put("303", new PointF(350, 400));
    nodeCoords3F.put("A",   new PointF(180, 300));
    nodeCoords3F.put("B",   new PointF(260, 300));
    nodeCoords3F.put("C",   new PointF(340, 300));
    nodeCoords3F.put("stairs_3F", new PointF(500, 500));

    // ===== 4F 示例 =====
    nodeCoords4F.put("402", new PointF(210, 380));
    nodeCoords4F.put("403", new PointF(360, 380));
    nodeCoords4F.put("A",   new PointF(190, 290));
    nodeCoords4F.put("B",   new PointF(270, 290));
    nodeCoords4F.put("C",   new PointF(350, 290));
    nodeCoords4F.put("stairs_4F", new PointF(500, 500));

    // ===== 5F 示例 =====
    nodeCoords5F.put("502", new PointF(220, 360));
    nodeCoords5F.put("503", new PointF(370, 360));
    nodeCoords5F.put("A",   new PointF(200, 280));
    nodeCoords5F.put("B",   new PointF(280, 280));
    nodeCoords5F.put("C",   new PointF(360, 280));
    nodeCoords5F.put("stairs_5F", new PointF(500, 500));
}



    // 读取每层楼有几个位置
    // 读取每层楼有哪些房间（只按 room 的首位分层）
    private void loadFingerprintData() {
        floorRooms.put("3F", new ArrayList<>());
        floorRooms.put("4F", new ArrayList<>());
        floorRooms.put("5F", new ArrayList<>());

        try {
            InputStream is = getAssets().open("fingerprint_db.json");
            byte[] buffer = new byte[is.available()];
            is.read(buffer);
            is.close();
            String jsonString = new String(buffer, "UTF-8");

            JSONArray arr = new JSONArray(jsonString);

            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);

                // JSON 里实际存在的字段只有 room / rp / aps
                String room = obj.getString("room");

                if (room.startsWith("3")) {
                    if (!floorRooms.get("3F").contains(room)) {
                        floorRooms.get("3F").add(room);
                    }
                } else if (room.startsWith("4")) {
                    if (!floorRooms.get("4F").contains(room)) {
                        floorRooms.get("4F").add(room);
                    }
                } else if (room.startsWith("5")) {
                    if (!floorRooms.get("5F").contains(room)) {
                        floorRooms.get("5F").add(room);
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        // 打印一下（方便你在 Logcat 看每层有哪些房间）
        Log.d("NAV", "楼层教室列表:");
        for (String floor : floorRooms.keySet()) {
            Log.d("NAV", floor + ": " + floorRooms.get(floor).toString());
        }
    }


    // 根据房间号推断楼层标签
    private String getFloorFromRoom(String room) {
        if (room == null || room.length() == 0) return "UNKNOWN";

        // 这里先简单按房间号首位判断
        // 比如 301、3A1 -> 3F； 403 -> 4F； 501 -> 5F
        char c = room.charAt(0);
        if (c == '3') return "3F";
        if (c == '4') return "4F";
        if (c == '5') return "5F";

        // 如果命名比较特殊，你以后可以自己改这一段逻辑
        return "UNKNOWN";
    }

    // 根据起点和终点房间号，生成一条简单的导航路径
    private List<String> computePath(String start, String end) {
        List<String> result = new ArrayList<>();

        String startFloor = getFloorFromRoom(start);
        String endFloor   = getFloorFromRoom(end);

        // 起点一定要先放进去
        result.add(start);

        // 1. 同一楼层：直接从起点到终点（后面你可以加 ABC、走廊点位等）
        if (startFloor.equals(endFloor)) {
            result.add(end);
            return result;
        }

        // 2. 不同楼层：中间要经过楼梯节点
        // 我们用 stairs_3F / stairs_4F / stairs_5F 表示每层的楼梯位置

        String startStairs = "stairs_" + startFloor;  // 比如 stairs_3F
        String endStairs   = "stairs_" + endFloor;    // 比如 stairs_5F

        // 情况 1：3F <-> 4F
        // 比如 3F -> 4F: start -> stairs_3F -> stairs_4F -> end
        if ((startFloor.equals("3F") && endFloor.equals("4F"))
                || (startFloor.equals("4F") && endFloor.equals("3F"))) {

            result.add(startStairs);
            result.add(endStairs);
            result.add(end);
            return result;
        }

        // 情况 2：4F <-> 5F
        if ((startFloor.equals("4F") && endFloor.equals("5F"))
                || (startFloor.equals("5F") && endFloor.equals("4F"))) {

            result.add(startStairs);
            result.add(endStairs);
            result.add(end);
            return result;
        }

        // 情况 3：3F <-> 5F，需要经过 4F 楼梯
        // 例如 3F -> 5F: start -> stairs_3F -> stairs_4F -> stairs_5F -> end
        if ((startFloor.equals("3F") && endFloor.equals("5F"))
                || (startFloor.equals("5F") && endFloor.equals("3F"))) {

            result.add("stairs_3F");
            result.add("stairs_4F");
            result.add("stairs_5F");
            result.add(end);
            return result;
        }

        // 其它情况先简单处理
        result.add(end);
        return result;
    }



    private void drawPathOnMap(List<String> path) {
        // 通过路径绘制蓝色线条
        // 这里可以使用 Canvas 进行绘制
    }

    private void updatePathDistance() {
        // 根据当前定位更新路径长度
        // 将路径根据距离变化调整，显示在地图上
    }

    private List<PointF> buildPathPointsForFloor(List<String> path, String floor) {
        List<PointF> pts = new ArrayList<>();

        Map<String, PointF> coordMap;
        switch (floor) {
            case "3F":
                coordMap = nodeCoords3F;
                break;
            case "4F":
                coordMap = nodeCoords4F;
                break;
            case "5F":
                coordMap = nodeCoords5F;
                break;
            default:
                coordMap = new HashMap<>();
        }

        for (String node : path) {
            // 只画属于这一层的节点 + 本层的楼梯
            if (node.startsWith("stairs_")) {
                // 楼梯只画本层对应的那个
                String stairsName = "stairs_" + floor;
                PointF p = coordMap.get(stairsName);
                if (p != null && !pts.contains(p)) {
                    pts.add(p);
                }
            } else {
                PointF p = coordMap.get(node);
                if (p != null) {
                    pts.add(p);
                }
            }
        }
        return pts;
    }






}

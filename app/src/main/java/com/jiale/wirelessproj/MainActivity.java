package com.jiale.wirelessproj;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends AppCompatActivity {

    private ImageView imgFloor;
    private Button btnFloor3;
    private Button btnFloor4;
    private Button btnFloor5;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        // 处理状态栏/导航栏的内边距（模板自带的逻辑，可以保留）
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // 1. 找到布局里的控件
        imgFloor = findViewById(R.id.img_floor);
        btnFloor3 = findViewById(R.id.btn_floor3);
        btnFloor4 = findViewById(R.id.btn_floor4);
        btnFloor5 = findViewById(R.id.btn_floor5);

        // 2. 默认显示 3 楼
        imgFloor.setImageResource(R.drawable.floor3);
        highlightButton(btnFloor3);

        // 3. 给每个楼层按钮加点击事件
        btnFloor3.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                imgFloor.setImageResource(R.drawable.floor3);
                highlightButton(btnFloor3);
            }
        });

        btnFloor4.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                imgFloor.setImageResource(R.drawable.floor4);
                highlightButton(btnFloor4);
            }
        });

        btnFloor5.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                imgFloor.setImageResource(R.drawable.floor5);
                highlightButton(btnFloor5);
            }
        });
    }

    /**
     * 简单高亮当前选中的按钮
     */
    private void highlightButton(Button selected) {
        // 先恢复三个按钮为默认背景
        btnFloor3.setBackgroundColor(getResources().getColor(android.R.color.darker_gray));
        btnFloor4.setBackgroundColor(getResources().getColor(android.R.color.darker_gray));
        btnFloor5.setBackgroundColor(getResources().getColor(android.R.color.darker_gray));

        // 再把选中的按钮改成浅一点的颜色
        selected.setBackgroundColor(getResources().getColor(android.R.color.white));
    }
}

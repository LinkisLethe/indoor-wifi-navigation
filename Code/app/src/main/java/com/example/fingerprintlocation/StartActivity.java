package com.example.fingerprintlocation;

import android.content.Intent;
import android.os.Bundle;
import android.view.View; // 确保导入了 View
// 如果你以前导入了 android.widget.Button，可以删掉它
import androidx.appcompat.app.AppCompatActivity;

public class StartActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start);

        // 【关键修改点】
        // 这里的类型必须改为 View 或 LinearLayout，不能再是 Button
        View btnUser = findViewById(R.id.btnUser);
        View btnAdmin = findViewById(R.id.btnAdmin);

        // 设置点击事件（View 同样支持 setOnClickListener）
        btnUser.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(StartActivity.this, UserActivity.class);
                startActivity(intent);
            }
        });

        btnAdmin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(StartActivity.this, AdminActivity.class);
                startActivity(intent);
            }
        });
    }
}
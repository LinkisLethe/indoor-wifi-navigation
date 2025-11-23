package com.example.fingerprintlocation;
import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;


public class StartActivity extends AppCompatActivity {

    private Button btnAdmin;
    private Button btnUser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start);

        btnAdmin = findViewById(R.id.btnAdmin);
        btnUser = findViewById(R.id.btnUser);


        btnAdmin.setOnClickListener(v -> {
            Intent intent = new Intent(StartActivity.this, AdminActivity.class);
            startActivity(intent);
        });

        // 跳到定位页面
        btnUser.setOnClickListener(v -> {
            Intent intent = new Intent(StartActivity.this, UserActivity.class);
            startActivity(intent);
        });
    }
}

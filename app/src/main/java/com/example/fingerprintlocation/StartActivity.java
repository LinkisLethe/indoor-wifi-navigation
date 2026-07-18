package com.example.fingerprintlocation;

import android.content.Intent;
import android.os.Bundle;
import android.view.View; // Ensure View is imported
// If you previously imported android.widget.Button, you can remove it
import androidx.appcompat.app.AppCompatActivity;

public class StartActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start);

        // [Key modification]
        // The type here must be changed to View or LinearLayout, not Button anymore
        View btnUser = findViewById(R.id.btnUser);
        View btnAdmin = findViewById(R.id.btnAdmin);

        // Set click listener (View also supports setOnClickListener)
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
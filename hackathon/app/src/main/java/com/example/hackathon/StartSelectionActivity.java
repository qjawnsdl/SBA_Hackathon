package com.example.hackathon;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

public class StartSelectionActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start_selection); // (activity_start_selection.xml은 그대로 사용)

        Button buttonGoToUser = findViewById(R.id.buttonGoToUser);
        Button buttonGoToEnterprise = findViewById(R.id.buttonGoToEnterprise);

        // ★★★ 수정된 부분 ★★★
        // 사용자용 버튼 클릭 시 UserHomeActivity가 아닌 MainActivity로 이동
        buttonGoToUser.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(StartSelectionActivity.this, MainActivity.class);
                startActivity(intent);
            }
        });
        // ★★★ 수정 끝 ★★★

        // 기업용 버튼은 그대로 EnterpriseHomeActivity로 이동
        buttonGoToEnterprise.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(StartSelectionActivity.this, EnterpriseHomeActivity.class);
                startActivity(intent);
            }
        });
    }
}
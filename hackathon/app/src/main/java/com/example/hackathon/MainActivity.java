package com.example.hackathon;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {

    // UI 요소 선언
    private EditText editTextQuery;
    private Button buttonSend;
    private TextView textViewAnswer;

    // Retrofit ApiService 선언
    private ApiService apiService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 1. 액션 바 타이틀 설정 (기존 코드)
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.action_bar_title);
        }

        // 2. UI 요소 연결
        editTextQuery = findViewById(R.id.editTextQuery);
        buttonSend = findViewById(R.id.buttonSend);
        textViewAnswer = findViewById(R.id.textViewAnswer);

        // 3. Retrofit 서비스 초기화
        apiService = RetrofitClient.getApiService();

        // 4. 전송 버튼 클릭 리스너 설정
        buttonSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String query = editTextQuery.getText().toString().trim();
                if (!query.isEmpty()) {
                    sendRequestToServer(query);
                } else {
                    Toast.makeText(MainActivity.this, "질문을 입력해주세요.", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    // 5. 서버로 질문을 전송하는 함수
    private void sendRequestToServer(String query) {
        // 답변 대기 중임을 표시
        textViewAnswer.setText("AI가 답변을 생성 중입니다...");

        // 1. 요청 객체 생성
        ChatRequest request = new ChatRequest(query);

        // 2. API 호출 (비동기 방식)
        apiService.sendChat(request).enqueue(new Callback<ChatResponse>() {
            @Override
            public void onResponse(Call<ChatResponse> call, Response<ChatResponse> response) {
                // 3. 성공적으로 응답을 받았을 때
                if (response.isSuccessful() && response.body() != null) {
                    String aiAnswer = response.body().getAnswer();
                    textViewAnswer.setText(aiAnswer);
                } else {
                    // 서버에서 오류 응답을 보냈을 때
                    textViewAnswer.setText("오류: 답변을 받지 못했습니다. (코드: " + response.code() + ")");
                }
            }

            @Override
            public void onFailure(Call<ChatResponse> call, Throwable t) {
                // 4. 네트워크 오류 등 통신 자체에 실패했을 때
                textViewAnswer.setText("통신 실패: 서버에 연결할 수 없습니다.");
                Log.e("NetworkError", "통신 실패", t);
                Toast.makeText(MainActivity.this, "서버 연결 확인: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }


    // --- 메뉴 관련 코드는 그대로 둡니다 (기존 코드) ---
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_mma) {
            Toast.makeText(this, "병무청 상담을 시작합니다.", Toast.LENGTH_SHORT).show();
            // TODO: 병무청 상담 로직 (예: editTextQuery.setText("병무청 관련 질문: ");)
            return true;
        } else if (id == R.id.menu_samsung) {
            Toast.makeText(this, "삼성 전자 상담을 시작합니다.", Toast.LENGTH_SHORT).show();
            return true;
        } else if (id == R.id.menu_lg) {
            Toast.makeText(this, "LG 가전 상담을 시작합니다.", Toast.LENGTH_SHORT).show();
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }
}
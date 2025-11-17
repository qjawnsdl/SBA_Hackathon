package com.example.hackathon;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {

    private EditText editTextQuery;
    private ImageButton buttonSend;
    private RecyclerView recyclerViewChat;
    private ChatAdapter chatAdapter;
    private List<ChatMessage> messageList;

    private ApiService apiService;
    private CompanyStorage companyStorage; // 기업 목록 저장소

    // 현재 상담 모드를 저장하는 변수
    // null = --- 일반 상담 ---
    // "병무청" = 병무청 RAG 상담
    private String currentCompanyName = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main); // activity_main.xml은 기존 것 사용

        // 1. 저장소 및 툴바 초기화
        companyStorage = new CompanyStorage(this);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // 2. UI 요소 연결 및 RecyclerView 설정
        editTextQuery = findViewById(R.id.editTextQuery);
        buttonSend = findViewById(R.id.buttonSend);
        recyclerViewChat = findViewById(R.id.recyclerViewChat);
        setupRecyclerView(); // (아래 정의된 함수)

        // 3. Retrofit 서비스 초기화
        apiService = RetrofitClient.getApiService();

        // 4. 전송 버튼 클릭 리스너
        buttonSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String query = editTextQuery.getText().toString().trim();
                if (!query.isEmpty()) {
                    addMessage(query, true); // 사용자 메시지 UI에 추가
                    editTextQuery.setText(""); // 입력창 비우기

                    // 현재 상담 모드(null 또는 "병무청") 그대로 서버에 전송
                    sendRequestToServer(query, currentCompanyName);
                }
            }
        });

        // 5. 초기 채팅방 UI 설정 (일반 상담으로 시작)
        updateChatUIForNewSession();
    }

    /**
     * 상담 세션이 변경될 때 (메뉴 선택 시) 호출되어 UI를 초기화하는 함수
     */
    private void updateChatUIForNewSession() {
        // 1. 기존 채팅 내역 클리어
        if (messageList != null && chatAdapter != null) {
            messageList.clear();
            chatAdapter.notifyDataSetChanged();
        }

        // ★★★ 2. (수정) 어댑터에게 현재 상담 모드(이름)를 전달 ★★★
        // (ChatAdapter가 이 값에 따라 로고를 숨기거나 표시함)
        if (chatAdapter != null) {
            chatAdapter.setCompanyName(currentCompanyName);
        }

        String title;
        String welcomeMessage;

        // 3. 상태(currentCompanyName)에 따라 제목과 환영 메시지 설정
        if (currentCompanyName == null) {
            // [일반 상담 모드]
            title = "--- 일반 상담 ---"; // 요청하신 '---' 표시

            // ★★★ 3-1. (수정) "Gemini AI에게" 문구 삭제 ★★★
            welcomeMessage = "무엇이든 물어보세요.";
        } else {
            // [기업 상담 모드]
            title = currentCompanyName + " AI 상담";
            // res/values/strings.xml에 welcome_message가 정의되어 있어야 합니다.
            // (예: <string name="welcome_message">무엇을 도와드릴까요?</string>)
            welcomeMessage = getString(R.string.welcome_message) +
                    " (" + currentCompanyName + " 담당)";
        }

        // 4. 툴바 제목 변경
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(title);
        }

        // 5. 새 환영 메시지 추가
        addMessage(welcomeMessage, false); // AI가 말하는 것으로 추가
    }


    // --- (이하 함수들은 이전과 동일) ---

    /**
     * RecyclerView 초기 설정
     */
    private void setupRecyclerView() {
        messageList = new ArrayList<>();
        chatAdapter = new ChatAdapter(messageList); // 어댑터 생성
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerViewChat.setLayoutManager(layoutManager);
        recyclerViewChat.setAdapter(chatAdapter);
    }

    /**
     * 채팅 목록에 메시지를 추가하고 화면을 갱신
     */
    private void addMessage(String message, boolean isUser) {
        messageList.add(new ChatMessage(message, isUser));
        chatAdapter.notifyItemInserted(messageList.size() - 1);
        recyclerViewChat.scrollToPosition(messageList.size() - 1); // 항상 마지막 메시지로 스크롤
    }

    /**
     * 서버로 질문을 전송하는 함수
     */
    private void sendRequestToServer(String query, String companyName) {
        // ChatRequest는 companyName이 null이어도 정상 작동
        ChatRequest request = new ChatRequest(query, companyName);

        apiService.sendChat(request).enqueue(new Callback<ChatResponse>() {
            @Override
            public void onResponse(Call<ChatResponse> call, Response<ChatResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    String aiAnswer = response.body().getAnswer();
                    addMessage(aiAnswer, false); // AI 답변을 채팅 목록에 추가
                } else {
                    addMessage("오류: 답변을 받지 못했습니다. (코드: " + response.code() + ")", false);
                }
            }

            @Override
            public void onFailure(Call<ChatResponse> call, Throwable t) {
                addMessage("통신 실패: 서버에 연결할 수 없습니다.", false);
                Log.e("NetworkError", "통신 실패", t);
            }
        });
    }

    // --- (메뉴 동적 생성 및 처리) ---

    /**
     * 툴바 메뉴 생성 (res/menu/main_menu.xml 필요)
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    /**
     * 메뉴가 열릴 때마다 CompanyStorage에서 최신 기업 목록을 가져와 메뉴에 동적 추가
     */
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        List<String> companyList = companyStorage.getCompanyList();

        // 동적 그룹(R.id.menu_group_companies)을 찾아 기존 목록을 비움
        menu.removeGroup(R.id.menu_group_companies);

        // CompanyStorage의 목록으로 메뉴 아이템을 동적으로 추가
        for (int i = 0; i < companyList.size(); i++) {
            String companyName = companyList.get(i);
            // (그룹 ID, 아이템 ID, 순서, 제목)
            menu.add(R.id.menu_group_companies, 1000 + i, i, companyName);
        }

        return super.onPrepareOptionsMenu(menu);
    }

    /**
     * 메뉴 아이템 클릭 이벤트 처리
     */
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        // 1. '--- 일반 상담 ---' (R.id.menu_general_chat) 메뉴를 클릭했을 때
        if (id == R.id.menu_general_chat) {
            currentCompanyName = null; // 상태를 '일반'으로 변경
            updateChatUIForNewSession(); // UI 초기화
            return true;
        }

        // 2. 동적으로 추가된 기업(병무청 등) 메뉴를 클릭했을 때 (ID 1000번 이상)
        if (id >= 1000) {
            String selectedCompany = item.getTitle().toString();
            currentCompanyName = selectedCompany; // 상태를 'RAG'로 변경
            updateChatUIForNewSession(); // UI 초기화
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
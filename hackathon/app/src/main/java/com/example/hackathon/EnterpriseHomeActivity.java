package com.example.hackathon;

import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class EnterpriseHomeActivity extends AppCompatActivity {

    private EditText editTextCompanyName;
    private Button buttonSelectFile, buttonUpload;
    private TextView textViewSelectedFile;
    private ProgressBar progressBarUpload;

    private ApiService apiService;
    private CompanyStorage companyStorage;
    private Uri selectedFileUri;

    // 파일 선택기 런처
    private final ActivityResultLauncher<String> filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    selectedFileUri = uri;
                    String fileName = getFileName(uri);
                    textViewSelectedFile.setText("선택됨: " + fileName);
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_enterprise_home);

        apiService = RetrofitClient.getApiService(); // 고정된 5000번 포트 사용
        companyStorage = new CompanyStorage(this);

        editTextCompanyName = findViewById(R.id.editTextCompanyName);
        buttonSelectFile = findViewById(R.id.buttonSelectFile);
        buttonUpload = findViewById(R.id.buttonUpload);
        textViewSelectedFile = findViewById(R.id.textViewSelectedFile);
        progressBarUpload = findViewById(R.id.progressBarUpload);

        // 파일 선택 버튼
        buttonSelectFile.setOnClickListener(v -> {
            filePickerLauncher.launch("text/plain"); // .txt 파일
        });

        // 업로드 버튼
        buttonUpload.setOnClickListener(v -> {
            String companyName = editTextCompanyName.getText().toString().trim();
            if (companyName.isEmpty()) {
                Toast.makeText(this, "기업명을 입력하세요.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (selectedFileUri == null) {
                Toast.makeText(this, "매뉴얼 파일을 선택하세요.", Toast.LENGTH_SHORT).show();
                return;
            }
            uploadFile(companyName, selectedFileUri);
        });
    }

    private void uploadFile(String companyName, Uri fileUri) {
        setLoading(true);

        File file = createCacheFileFromUri(fileUri, "upload_manual");
        if (file == null) {
            Toast.makeText(this, "파일 처리에 실패했습니다.", Toast.LENGTH_SHORT).show();
            setLoading(false);
            return;
        }

        // 1. 파일(MultipartBody.Part) 생성
        RequestBody requestFile = RequestBody.create(
                MediaType.parse(getContentResolver().getType(fileUri)),
                file
        );
        MultipartBody.Part body = MultipartBody.Part.createFormData("file", file.getName(), requestFile);

        // 2. 기업명(RequestBody) 생성
        RequestBody companyNameBody = RequestBody.create(
                MediaType.parse("text/plain"),
                companyName
        );

        // 3. API 호출
        apiService.uploadManual(companyNameBody, body).enqueue(new Callback<UploadResponse>() {
            @Override
            public void onResponse(@NonNull Call<UploadResponse> call, @NonNull Response<UploadResponse> response) {
                setLoading(false);
                if (response.isSuccessful() && response.body() != null) {
                    String registeredName = response.body().getCompanyName();
                    Toast.makeText(EnterpriseHomeActivity.this,
                            registeredName + " 매뉴얼 등록 성공!", Toast.LENGTH_LONG).show();

                    // ★ 성공 시, 기업 목록을 SharedPreferences에 저장
                    companyStorage.addCompany(registeredName);

                    // 입력 필드 초기화
                    editTextCompanyName.setText("");
                    textViewSelectedFile.setText("선택된 파일 없음");
                    selectedFileUri = null;

                } else {
                    Toast.makeText(EnterpriseHomeActivity.this,
                            "업로드 실패: " + response.message(), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<UploadResponse> call, @NonNull Throwable t) {
                setLoading(false);
                Log.e("UploadError", "파일 업로드 실패", t);
                Toast.makeText(EnterpriseHomeActivity.this,
                        "네트워크 오류: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    // --- 파일 처리 유틸리티 (getFileName, createCacheFileFromUri 등) ---
    // (이전 답변의 EnterpriseHomeActivity.java에 있던 유틸리티 함수들을 여기에 복사)

    // Uri에서 파일 이름 가져오기
    private String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            try (android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex != -1) {
                        result = cursor.getString(nameIndex);
                    }
                }
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }

    // Uri를 임시 캐시 파일로 복사 (서버 전송용)
    private File createCacheFileFromUri(Uri uri, String prefix) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(uri);
            if (inputStream == null) return null;

            File tempFile = File.createTempFile(prefix, "." + getFileExtension(uri));
            tempFile.deleteOnExit(); // 앱 종료 시 자동 삭제

            try (OutputStream outputStream = new FileOutputStream(tempFile)) {
                byte[] buffer = new byte[4 * 1024]; // 4K 버퍼
                int read;
                while ((read = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, read);
                }
                outputStream.flush();
            }
            inputStream.close();
            return tempFile;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    // 파일 확장자 가져오기 (간단 버전)
    private String getFileExtension(Uri uri) {
        String fileName = getFileName(uri);
        if(fileName != null && fileName.contains(".")) {
            return fileName.substring(fileName.lastIndexOf(".") + 1);
        }
        return "txt"; // 기본값
    }

    private void setLoading(boolean isLoading) {
        progressBarUpload.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        buttonUpload.setEnabled(!isLoading);
        buttonSelectFile.setEnabled(!isLoading);
    }
}
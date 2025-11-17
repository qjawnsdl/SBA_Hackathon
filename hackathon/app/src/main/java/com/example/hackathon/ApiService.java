package com.example.hackathon;


import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;

public interface ApiService {

    // 사용자용 채팅 API
    @POST("chat")
    Call<ChatResponse> sendChat(@Body ChatRequest request);

    // 기업용 매뉴얼 업로드 API
    @Multipart
    @POST("upload") // 서버에 /upload 엔드포인트가 필요합니다.
    Call<UploadResponse> uploadManual(
            @Part("companyName") RequestBody companyName,
            @Part MultipartBody.Part file
    );
}
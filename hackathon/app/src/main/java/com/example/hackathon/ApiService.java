package com.example.hackathon;


import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface ApiService {
    @POST("chat")
    Call<ChatResponse> sendChat(@Body ChatRequest request);
}

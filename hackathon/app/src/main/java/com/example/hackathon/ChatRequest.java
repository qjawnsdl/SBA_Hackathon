package com.example.hackathon;


import com.google.gson.annotations.SerializedName;

public class ChatRequest {
    @SerializedName("query")
    private String query;

    // ★ 이 부분이 추가되어야 합니다.
    @SerializedName("companyName")
    private String companyName;

    // ★ 생성자가 두 개의 파라미터를 받도록 수정되어야 합니다.
    public ChatRequest(String query, String companyName) {
        this.query = query;
        this.companyName = companyName;
    }
}
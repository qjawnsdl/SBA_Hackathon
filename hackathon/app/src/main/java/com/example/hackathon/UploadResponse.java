package com.example.hackathon;

import com.google.gson.annotations.SerializedName;

// 업로드 성공 시 서버로부터 받을 응답
public class UploadResponse {
    @SerializedName("message")
    private String message;

    @SerializedName("companyName")
    private String companyName;

    public String getMessage() {
        return message;
    }

    public String getCompanyName() {
        return companyName;
    }
}
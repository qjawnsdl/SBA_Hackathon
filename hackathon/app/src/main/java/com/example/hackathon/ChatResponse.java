package com.example.hackathon;


import com.google.gson.annotations.SerializedName;

public class ChatResponse {
    @SerializedName("answer")
    private String answer;

    public String getAnswer() {
        return answer;
    }
}
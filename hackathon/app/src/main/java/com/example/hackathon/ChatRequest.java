package com.example.hackathon;


import com.google.gson.annotations.SerializedName;

public class ChatRequest {
    @SerializedName("query")
    private String query;

    public ChatRequest(String query) {
        this.query = query;
    }
}
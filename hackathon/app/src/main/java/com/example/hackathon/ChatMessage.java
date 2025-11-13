package com.example.hackathon;

public class ChatMessage {
    private String message;
    private boolean isUser; // true면 사용자, false면 AI

    public ChatMessage(String message, boolean isUser) {
        this.message = message;
        this.isUser = isUser;
    }

    public String getMessage() {
        return message;
    }

    public boolean isUser() {
        return isUser;
    }
}
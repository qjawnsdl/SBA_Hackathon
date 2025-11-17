package com.example.hackathon;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView; // ImageView import
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_USER = 1;
    private static final int VIEW_TYPE_AI = 2;

    private List<ChatMessage> messageList;

    // ★ 1. 현재 상담 모드를 저장할 변수 추가
    private String currentCompanyName;

    /**
     * 생성자
     */
    public ChatAdapter(List<ChatMessage> messageList) {
        this.messageList = messageList;
        this.currentCompanyName = null; // 기본값은 '일반 상담' (null)
    }

    /**
     * ★ 2. MainActivity가 호출할 수 있는 세터(Setter) 메서드 ★
     * 상담 모드를 변경합니다.
     */
    public void setCompanyName(String companyName) {
        this.currentCompanyName = companyName;
    }

    @Override
    public int getItemViewType(int position) {
        if (messageList.get(position).isUser()) {
            return VIEW_TYPE_USER;
        } else {
            return VIEW_TYPE_AI;
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view;
        if (viewType == VIEW_TYPE_USER) {
            // item_chat_user.xml 사용
            view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_chat_user, parent, false);
            return new UserViewHolder(view);
        } else {
            // item_chat_ai.xml 사용
            view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_chat_ai, parent, false);
            return new AiViewHolder(view);
        }
    }

    /**
     * ViewHolder에 데이터를 바인딩(연결)할 때 호출됨
     */
    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatMessage message = messageList.get(position);

        if (holder.getItemViewType() == VIEW_TYPE_USER) {
            // [사용자 말풍선]
            ((UserViewHolder) holder).textUserMessage.setText(message.getMessage());
        }
        else {
            // [AI 말풍선]
            // ★★★ 3. AI 말풍선 동적 변경 로직 ★★★
            AiViewHolder aiHolder = (AiViewHolder) holder;
            aiHolder.textAiMessage.setText(message.getMessage());

            if (currentCompanyName == null) {
                // [일반 상담 모드] (companyName이 null일 때)
                aiHolder.textAiSender.setText("AI 상담"); // 발신자 이름을 "AI 상담"으로
                aiHolder.imageAiProfile.setVisibility(View.GONE); // 로고 숨기기
            }
            else {
                // [기업 상담 모드] (companyName이 "병무청" 등일 때)
                aiHolder.textAiSender.setText(currentCompanyName); // 발신자 이름을 기업명으로
                aiHolder.imageAiProfile.setVisibility(View.VISIBLE); // 로고 보이기

                // (참고) 지금은 로고가 R.drawable.logo_mma 하나뿐이므로
                // 어떤 기업이든 이 로고가 나옵니다.
                aiHolder.imageAiProfile.setImageResource(R.drawable.logo_mma);
            }
        }
    }

    @Override
    public int getItemCount() {
        return messageList.size();
    }

    /**
     * 사용자 말풍선 ViewHolder (수정 없음)
     */
    static class UserViewHolder extends RecyclerView.ViewHolder {
        TextView textUserMessage;
        UserViewHolder(@NonNull View itemView) {
            super(itemView);
            textUserMessage = itemView.findViewById(R.id.textUserMessage);
        }
    }

    /**
     * ★ 4. AI 말풍선 ViewHolder (수정) ★
     * (발신자 이름, 로고 이미지를 제어하기 위해 뷰 추가)
     */
    static class AiViewHolder extends RecyclerView.ViewHolder {
        TextView textAiMessage;
        TextView textAiSender;     // ★ 로고 이름을 제어하기 위해 추가
        ImageView imageAiProfile;  // ★ 로고 이미지를 제어하기 위해 추가

        AiViewHolder(@NonNull View itemView) {
            super(itemView);
            // item_chat_ai.xml에 정의된 ID를 찾아 연결
            textAiMessage = itemView.findViewById(R.id.textAiMessage);
            textAiSender = itemView.findViewById(R.id.textAiSender);
            imageAiProfile = itemView.findViewById(R.id.imageAiProfile);
        }
    }
}
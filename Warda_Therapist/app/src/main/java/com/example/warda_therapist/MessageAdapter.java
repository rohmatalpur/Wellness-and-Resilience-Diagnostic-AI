package com.example.warda_therapist;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.viewHolder>{

    List<MessageModel> modelList;

    public MessageAdapter(List<MessageModel> modelList) {
        this.modelList = modelList;
    }

    @NonNull
    @Override
    public viewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        @SuppressLint("InflateParams") View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_message_new, null);
        return new viewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull viewHolder holder, int position) {
        MessageModel model = modelList.get(position);

        if (model.getSentBy().equals(MessageModel.SENT_BY_ME)) {
            holder.leftChatCard.setVisibility(View.GONE);
            holder.rightChatCard.setVisibility(View.VISIBLE);
            holder.rightMsg.setText(model.getMessage());
        } else {
            holder.rightChatCard.setVisibility(View.GONE);
            holder.leftChatCard.setVisibility(View.VISIBLE);
            holder.leftMsg.setText(model.getMessage());
        }
    }

    @Override
    public int getItemCount() {
        return modelList.size();
    }

    public static class viewHolder extends RecyclerView.ViewHolder {
        CardView leftChatCard, rightChatCard;
        TextView leftMsg, rightMsg;

        public viewHolder(@NonNull View itemView) {
            super(itemView);
            leftChatCard = itemView.findViewById(R.id.leftChatCard);
            rightChatCard = itemView.findViewById(R.id.rightChatCard);
            leftMsg = itemView.findViewById(R.id.leftMsg);
            rightMsg = itemView.findViewById(R.id.rightMsg);
        }
    }
}
package com.jippytalk.Chats.Adapter;

/**
 * Developer Name: Vidya Sagar
 * Created on: 10-04-2026
 */

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.jippytalk.Chats.Model.ChatListModel;
import com.jippytalk.R;

import java.util.ArrayList;
import java.util.List;

/**
 * ChatListAdapter - RecyclerView adapter for the main chat list.
 * Displays each chat as a row with profile picture, contact name, last message preview,
 * and timestamp. Tapping a row notifies the listener with the contact ID.
 */
public class ChatListAdapter extends RecyclerView.Adapter<ChatListAdapter.ChatViewHolder> {

    // ---- Fields ----

    private final List<ChatListModel>       chatList;
    private final OnChatClickListener       listener;

    // ---- Constructor ----

    public ChatListAdapter(OnChatClickListener listener) {
        this.chatList   =   new ArrayList<>();
        this.listener   =   listener;
    }

    // -------------------- RecyclerView Overrides Starts Here ---------------------

    @NonNull
    @Override
    public ChatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View    view    =   LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_chat_list, parent, false);
        return new ChatViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChatViewHolder holder, int position) {
        ChatListModel   model   =   chatList.get(position);
        holder.bind(model, listener);
    }

    @Override
    public int getItemCount() {
        return chatList.size();
    }

    // -------------------- Public Methods Starts Here ---------------------

    /**
     * Replaces the adapter's data with a new list and refreshes the view.
     *
     * @param newList the new list of chats to display
     */
    public void submitList(List<ChatListModel> newList) {
        chatList.clear();
        if (newList != null) {
            chatList.addAll(newList);
        }
        notifyDataSetChanged();
    }

    // -------------------- ViewHolder Starts Here ---------------------

    public static class ChatViewHolder extends RecyclerView.ViewHolder {

        private final LinearLayout      llChatRow;
        private final ImageView         ivProfilePic;
        private final TextView          tvContactName;
        private final TextView          tvLastMessage;
        private final TextView          tvTimestamp;

        public ChatViewHolder(@NonNull View itemView) {
            super(itemView);
            llChatRow       =   itemView.findViewById(R.id.llChatRow);
            ivProfilePic    =   itemView.findViewById(R.id.ivProfilePic);
            tvContactName   =   itemView.findViewById(R.id.tvContactName);
            tvLastMessage   =   itemView.findViewById(R.id.tvLastMessage);
            tvTimestamp     =   itemView.findViewById(R.id.tvTimestamp);
        }

        public void bind(ChatListModel model, OnChatClickListener listener) {
            tvContactName.setText(model.getContactName());

            String  lastMessage =   model.getMessage();
            if (lastMessage == null || lastMessage.isEmpty()) {
                tvLastMessage.setText(R.string.no_chats_subtitle);
            } else {
                tvLastMessage.setText(lastMessage);
            }

            // Timestamp is left empty if 0 (no messages yet)
            if (model.getTimestamp() > 0) {
                tvTimestamp.setText("");
            } else {
                tvTimestamp.setText("");
            }

            // Profile pic — use default for now
            ivProfilePic.setImageResource(R.drawable.no_profile);

            llChatRow.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onChatClick(model);
                }
            });
        }
    }

    // -------------------- Click Listener Interface ---------------------

    /**
     * Callback for chat row clicks.
     */
    public interface OnChatClickListener {
        /**
         * Called when a chat row is clicked.
         *
         * @param chat the chat list model representing the clicked chat
         */
        void onChatClick(ChatListModel chat);
    }
}

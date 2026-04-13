package com.jippytalk.Messages.DiffUtil;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;

import com.jippytalk.Messages.Model.MessageModal;

public class MessagesDiffUtil extends DiffUtil.ItemCallback<MessageModal> {
    @Override
    public boolean areItemsTheSame(@NonNull MessageModal oldItem, @NonNull MessageModal newItem) {
        return oldItem.getMessageId().equals(newItem.getMessageId());
    }

    @Override
    public boolean areContentsTheSame(@NonNull MessageModal oldItem, @NonNull MessageModal newItem) {
        return oldItem.getMessageDirection() == newItem.getMessageDirection()
                && oldItem.getMessageId().equals(newItem.getMessageId())
                && oldItem.getReceiverId().equals(newItem.getReceiverId())
                && oldItem.getMessage().equals(newItem.getMessage())
                && oldItem.getMessageStatus() == newItem.getMessageStatus()
                && oldItem.getTimestamp() == newItem.getTimestamp()
                && oldItem.getStarredStatus() == newItem.getStarredStatus()
                && oldItem.getEditedStatus() == newItem.getEditedStatus()
                && oldItem.getMessageType() == newItem.getMessageType()
                && oldItem.isReply() == newItem.isReply()
                && TextUtils.equals(oldItem.getReplyToMessageId(), newItem.getReplyToMessageId())
                && TextUtils.equals(oldItem.getMediaUri(), newItem.getMediaUri())
                && oldItem.getMediaDuration() == newItem.getMediaDuration()
                && TextUtils.equals(oldItem.getFileName(), newItem.getFileName());
    }
}

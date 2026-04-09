package com.jippytalk.Database.MessagesDatabase.Model;

public class MarkMessagesAsSeenModel {

    private String  messageId;
    private long    deliveredTimestamp;

    public MarkMessagesAsSeenModel(String messageId, long deliveredTimestamp) {
        this.messageId              =   messageId;
        this.deliveredTimestamp     =   deliveredTimestamp;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public long getDeliveredTimestamp() {
        return deliveredTimestamp;
    }

    public void setDeliveredTimestamp(long deliveredTimestamp) {
        this.deliveredTimestamp = deliveredTimestamp;
    }
}

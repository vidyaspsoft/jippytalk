package com.jippytalk.Database.MessagesDatabase.Model;

public class MarkMessagesAsDeliveredAndSeenModel {

    private String  messageId;
    private long    deliveredTimestamp;
    private long    readTimestamp;

    public MarkMessagesAsDeliveredAndSeenModel(String messageId, long deliveredTimestamp, long readTimestamp) {
        this.messageId              =   messageId;
        this.deliveredTimestamp     =   deliveredTimestamp;
        this.readTimestamp          =   readTimestamp;
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

    public long getReadTimestamp() {
        return readTimestamp;
    }

    public void setReadTimestamp(long readTimestamp) {
        this.readTimestamp = readTimestamp;
    }
}

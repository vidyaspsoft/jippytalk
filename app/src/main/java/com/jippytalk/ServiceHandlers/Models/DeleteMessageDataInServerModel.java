package com.jippytalk.ServiceHandlers.Models;

public class DeleteMessageDataInServerModel {
    private String messageId;
    private String senderId;
    private String receiverId;


    public DeleteMessageDataInServerModel(String messageId, String senderId, String receiverId) {
        this.messageId  = messageId;
        this.senderId   = senderId;
        this.receiverId = receiverId;
    }

    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    public String getReceiverId() {
        return receiverId;
    }

    public void setReceiverId(String receiverId) {
        this.receiverId = receiverId;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }
}

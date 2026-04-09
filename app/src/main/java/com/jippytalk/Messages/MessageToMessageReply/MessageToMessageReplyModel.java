package com.jippytalk.Messages.MessageToMessageReply;

public class MessageToMessageReplyModel {

    private String  messageId;
    private String  message;
    private String  senderName;

    public MessageToMessageReplyModel(String messageId, String message, String senderName) {
        this.messageId      =   messageId;
        this.message        =   message;
        this.senderName     =   senderName;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getSenderName() {
        return senderName;
    }

    public void setSenderName(String senderName) {
        this.senderName = senderName;
    }
}

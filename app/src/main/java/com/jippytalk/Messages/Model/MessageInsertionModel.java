package com.jippytalk.Messages.Model;

public class MessageInsertionModel {

    private String  messageId;
    private String  message;
    private int     messageType;
    private long    sentTimestamp;
    private int     isReply;
    private String  repliedToMessageId;

    public MessageInsertionModel(String messageId, String message, int messageType,
                                 long sentTimestamp, int isReply, String repliedToMessageId) {
        this.messageId              =   messageId;
        this.message                =   message;
        this.messageType            =   messageType;
        this.sentTimestamp          =   sentTimestamp;
        this.isReply                =   isReply;
        this.repliedToMessageId     =   repliedToMessageId;
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

    public int getMessageType() {
        return messageType;
    }

    public void setMessageType(int messageType) {
        this.messageType = messageType;
    }

    public long getSentTimestamp() {
        return sentTimestamp;
    }

    public void setSentTimestamp(long sentTimestamp) {
        this.sentTimestamp = sentTimestamp;
    }

    public int getIsReply() {
        return isReply;
    }

    public void setIsReply(int isReply) {
        this.isReply = isReply;
    }

    public String getRepliedToMessageId() {
        return repliedToMessageId;
    }

    public void setRepliedToMessageId(String repliedToMessageId) {
        this.repliedToMessageId = repliedToMessageId;
    }
}

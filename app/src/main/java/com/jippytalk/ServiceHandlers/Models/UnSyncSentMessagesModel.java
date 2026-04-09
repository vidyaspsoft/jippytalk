package com.jippytalk.ServiceHandlers.Models;

public class UnSyncSentMessagesModel {

    private String  messageId;
    private String  senderId;
    private String  receivedId;
    private String  message;
    private int     signalMessageType;
    private int     messageType;
    private int     messageStatus;
    private int     isEdited;
    private long    latitude;
    private long    longitude;
    private long    sentTimestamp;
    private int     isReplied;
    private String  repliedToMessageId;


    public UnSyncSentMessagesModel(String messageId, String senderId, String receivedId, String message,
                                   int signalMessageType, int messageType, int messageStatus,
                                   int isEdited, long latitude, long longitude, long sentTimestamp,
                                   int isReplied, String repliedToMessageId) {

        this.messageId          =   messageId;
        this.senderId           =   senderId;
        this.receivedId         =   receivedId;
        this.message            =   message;
        this.signalMessageType  =   signalMessageType;
        this.messageType        =   messageType;
        this.messageStatus      =   messageStatus;
        this.isEdited           =   isEdited;
        this.latitude           =   latitude;
        this.longitude          =   longitude;
        this.sentTimestamp      =   sentTimestamp;
        this.isReplied          =   isReplied;
        this.repliedToMessageId =   repliedToMessageId;

    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    public String getReceivedId() {
        return receivedId;
    }

    public void setReceivedId(String receivedId) {
        this.receivedId = receivedId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public int getSignalMessageType() {
        return signalMessageType;
    }

    public void setSignalMessageType(int signalMessageType) {
        this.signalMessageType = signalMessageType;
    }

    public int getMessageType() {
        return messageType;
    }

    public void setMessageType(int messageType) {
        this.messageType = messageType;
    }

    public int getMessageStatus() {
        return messageStatus;
    }

    public void setMessageStatus(int messageStatus) {
        this.messageStatus = messageStatus;
    }

    public int getIsEdited() {
        return isEdited;
    }

    public void setIsEdited(int isEdited) {
        this.isEdited = isEdited;
    }

    public long getLatitude() {
        return latitude;
    }

    public void setLatitude(long latitude) {
        this.latitude = latitude;
    }

    public long getLongitude() {
        return longitude;
    }

    public void setLongitude(long longitude) {
        this.longitude = longitude;
    }

    public long getSentTimestamp() {
        return sentTimestamp;
    }

    public void setSentTimestamp(long sentTimestamp) {
        this.sentTimestamp = sentTimestamp;
    }

    public int getIsReplied() {
        return isReplied;
    }

    public void setIsReplied(int isReplied) {
        this.isReplied = isReplied;
    }

    public String getRepliedToMessageId() {
        return repliedToMessageId;
    }

    public void setRepliedToMessageId(String repliedToMessageId) {
        this.repliedToMessageId = repliedToMessageId;
    }
}

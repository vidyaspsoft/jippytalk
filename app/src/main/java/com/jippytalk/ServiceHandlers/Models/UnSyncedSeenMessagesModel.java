package com.jippytalk.ServiceHandlers.Models;

public class UnSyncedSeenMessagesModel {

    private String  messageId;
    private String  senderId;
    private String  receiverId;
    private int     messageStatus;
    private long    deliveredTimestamp;
    private long    seenTimestamp;

    public UnSyncedSeenMessagesModel(String messageId, String senderId, String receiverId,
                                     int messageStatus, long deliveredTimestamp, long seenTimestamp) {

        this.messageId          =   messageId;
        this.senderId           =   senderId;
        this.receiverId         =   receiverId;
        this.messageStatus      =   messageStatus;
        this.deliveredTimestamp =   deliveredTimestamp;
        this.seenTimestamp      =   seenTimestamp;
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

    public String getReceiverId() {
        return receiverId;
    }

    public void setReceiverId(String receiverId) {
        this.receiverId = receiverId;
    }

    public int getMessageStatus() {
        return messageStatus;
    }

    public void setMessageStatus(int messageStatus) {
        this.messageStatus = messageStatus;
    }

    public long getDeliveredTimestamp() {
        return deliveredTimestamp;
    }

    public void setDeliveredTimestamp(long deliveredTimestamp) {
        this.deliveredTimestamp = deliveredTimestamp;
    }

    public long getSeenTimestamp() {
        return seenTimestamp;
    }

    public void setSeenTimestamp(long seenTimestamp) {
        this.seenTimestamp = seenTimestamp;
    }
}

package com.jippytalk.MessageInfo.Model;

/**
 * Developer Name: Vidya Sagar
 * Created on: 06-04-2026
 */

public class MessageInfoModel {

    // ---- Fields ----

    private final String        message;
    private final int           messageStatus;
    private final long          sentTime;
    private final long          deliveredTime;
    private final long          seenTime;

    // ---- Constructor ----

    public MessageInfoModel(String message, int messageStatus, long sentTime,
                            long deliveredTime, long seenTime) {
        this.message            =   message;
        this.messageStatus      =   messageStatus;
        this.sentTime           =   sentTime;
        this.deliveredTime      =   deliveredTime;
        this.seenTime           =   seenTime;
    }

    // ---- Getters ----

    public String getMessage() {
        return message;
    }

    public int getMessageStatus() {
        return messageStatus;
    }

    public long getSentTime() {
        return sentTime;
    }

    public long getDeliveredTime() {
        return deliveredTime;
    }

    public long getSeenTime() {
        return seenTime;
    }
}

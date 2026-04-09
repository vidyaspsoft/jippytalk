package com.jippytalk.Messages.Model;

public class MessageModal {

    private String  messageId;
    private int     messageDirection;
    private String  receiverId;
    private String  message;
    private int     messageStatus;
    private long    timestamp;
    private long    receivedTimestamp;
    private long    readTimestamp;
    private int     starredStatus;
    private int     editedStatus;
    private int     messageType;
    private int     isReply;
    private String  replyToMessageId;
    private String  repliedToMessageText;
    private int     repliedToMessageDirection;
    private String  repliedToMessageSenderName;


    public MessageModal(String messageId, int messageDirection, String receiverId, String message,
                        int messageStatus, long timestamp, int starredStatus, int editedStatus,
                        int messageType, int isReply, String replyToMessageId, String repliedToMessageText,
                        int repliedToMessageDirection, String repliedToMessageSenderName) {
        this.messageId                      =   messageId;
        this.messageDirection               =   messageDirection;
        this.receiverId                     =   receiverId;
        this.message                        =   message;
        this.messageStatus                  =   messageStatus;
        this.timestamp                      =   timestamp;
        this.starredStatus                  =   starredStatus;
        this.editedStatus                   =   editedStatus;
        this.messageType                    =   messageType;
        this.isReply                        =   isReply;
        this.replyToMessageId               =   replyToMessageId;
        this.repliedToMessageText           =   repliedToMessageText;
        this.repliedToMessageDirection      =   repliedToMessageDirection;
        this.repliedToMessageSenderName     =   repliedToMessageSenderName;
    }


    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public int getMessageDirection() {
        return messageDirection;
    }

    public void setMessageDirection(int messageDirection) {
        this.messageDirection = messageDirection;
    }

    public String getReceiverId() {
        return receiverId;
    }

    public void setReceiverId(String receiverId) {
        this.receiverId = receiverId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public int getMessageStatus() {
        return messageStatus;
    }

    public void setMessageStatus(int messageStatus) {
        this.messageStatus = messageStatus;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public long getReceivedTimestamp() {
        return receivedTimestamp;
    }

    public void setReceivedTimestamp(long receivedTimestamp) {
        this.receivedTimestamp = receivedTimestamp;
    }

    public long getReadTimestamp() {
        return readTimestamp;
    }

    public void setReadTimestamp(long readTimestamp) {
        this.readTimestamp = readTimestamp;
    }

    public int getEditedStatus() {
        return editedStatus;
    }

    public void setEditedStatus(int editedStatus) {
        this.editedStatus = editedStatus;
    }

    public int getMessageType() {
        return messageType;
    }

    public void setMessageType(int messageType) {
        this.messageType = messageType;
    }

    public int getStarredStatus() {
        return starredStatus;
    }

    public void setStarredStatus(int starredStatus) {
        this.starredStatus = starredStatus;
    }

    public int getIsReply() {
        return isReply;
    }

    public void setIsReply(int isReply) {
        this.isReply = isReply;
    }

    public int isReply() {
        return isReply;
    }

    public void setReply(int reply) {
        isReply = reply;
    }

    public String getReplyToMessageId() {
        return replyToMessageId;
    }

    public void setReplyToMessageId(String replyToMessageId) {
        this.replyToMessageId = replyToMessageId;
    }

    public String getRepliedToMessageText() {
        return repliedToMessageText;
    }

    public void setRepliedToMessageText(String repliedToMessageText) {
        this.repliedToMessageText = repliedToMessageText;
    }

    public int getRepliedToMessageDirection() {
        return repliedToMessageDirection;
    }

    public void setRepliedToMessageDirection(int repliedToMessageDirection) {
        this.repliedToMessageDirection = repliedToMessageDirection;
    }

    public String getRepliedToMessageSenderName() {
        return repliedToMessageSenderName;
    }

    public void setRepliedToMessageSenderName(String repliedToMessageSenderName) {
        this.repliedToMessageSenderName = repliedToMessageSenderName;
    }
}

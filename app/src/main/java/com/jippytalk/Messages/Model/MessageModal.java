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
    private int     repliedToMessageType;
    private String  repliedToMediaUri;

    // ---- Media Attachment Fields ----

    private String  mediaUri;
    private String  thumbnailUri;
    private String  contentSubtype;
    private String  caption;
    private int     mediaWidth;
    private int     mediaHeight;
    private long    mediaDuration;
    private String  fileName;

    // ---- Constructor (text messages) ----

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

    // ---- Constructor (media messages) ----

    public MessageModal(String messageId, int messageDirection, String receiverId, String message,
                        int messageStatus, long timestamp, int starredStatus, int editedStatus,
                        int messageType, int isReply, String replyToMessageId, String repliedToMessageText,
                        int repliedToMessageDirection, String repliedToMessageSenderName,
                        String mediaUri, String thumbnailUri, String contentSubtype,
                        String caption, int mediaWidth, int mediaHeight, long mediaDuration,
                        String fileName) {
        this(messageId, messageDirection, receiverId, message, messageStatus, timestamp,
                starredStatus, editedStatus, messageType, isReply, replyToMessageId,
                repliedToMessageText, repliedToMessageDirection, repliedToMessageSenderName);
        this.mediaUri                       =   mediaUri;
        this.thumbnailUri                   =   thumbnailUri;
        this.contentSubtype                 =   contentSubtype;
        this.caption                        =   caption;
        this.mediaWidth                     =   mediaWidth;
        this.mediaHeight                    =   mediaHeight;
        this.mediaDuration                  =   mediaDuration;
        this.fileName                       =   fileName;
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

    public int getRepliedToMessageType() {
        return repliedToMessageType;
    }

    public void setRepliedToMessageType(int repliedToMessageType) {
        this.repliedToMessageType = repliedToMessageType;
    }

    public String getRepliedToMediaUri() {
        return repliedToMediaUri;
    }

    public void setRepliedToMediaUri(String repliedToMediaUri) {
        this.repliedToMediaUri = repliedToMediaUri;
    }

    // ---- Media Getters and Setters ----

    public String getMediaUri() {
        return mediaUri;
    }

    public void setMediaUri(String mediaUri) {
        this.mediaUri = mediaUri;
    }

    public String getThumbnailUri() {
        return thumbnailUri;
    }

    public void setThumbnailUri(String thumbnailUri) {
        this.thumbnailUri = thumbnailUri;
    }

    public String getContentSubtype() {
        return contentSubtype;
    }

    public void setContentSubtype(String contentSubtype) {
        this.contentSubtype = contentSubtype;
    }

    public String getCaption() {
        return caption;
    }

    public void setCaption(String caption) {
        this.caption = caption;
    }

    public int getMediaWidth() {
        return mediaWidth;
    }

    public void setMediaWidth(int mediaWidth) {
        this.mediaWidth = mediaWidth;
    }

    public int getMediaHeight() {
        return mediaHeight;
    }

    public void setMediaHeight(int mediaHeight) {
        this.mediaHeight = mediaHeight;
    }

    public long getMediaDuration() {
        return mediaDuration;
    }

    public void setMediaDuration(long mediaDuration) {
        this.mediaDuration = mediaDuration;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    // ---- File Size ----

    private long    fileSize;

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    // ---- Media S3 + encryption metadata (v8) ----
    // Populated by populateMediaFieldsFromCursor() so the adapter / receiver
    // auto-fetch / download trigger can read everything from the model
    // without going back to JSON parsing.

    private String  s3Key                   =   "";
    private String  s3Bucket                =   "";
    private String  fileTransferId          =   "";
    private String  remoteThumbnailUrl      =   "";
    private String  encryptedS3Url          =   "";
    private String  encryptionKey           =   "";
    private String  encryptionIv            =   "";
    private String  roomId                  =   "";

    public String getRoomId() {
        return roomId != null ? roomId : "";
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId != null ? roomId : "";
    }

    public String getS3Key() {
        return s3Key != null ? s3Key : "";
    }

    public void setS3Key(String s3Key) {
        this.s3Key = s3Key != null ? s3Key : "";
    }

    public String getS3Bucket() {
        return s3Bucket != null ? s3Bucket : "";
    }

    public void setS3Bucket(String s3Bucket) {
        this.s3Bucket = s3Bucket != null ? s3Bucket : "";
    }

    public String getFileTransferId() {
        return fileTransferId != null ? fileTransferId : "";
    }

    public void setFileTransferId(String fileTransferId) {
        this.fileTransferId = fileTransferId != null ? fileTransferId : "";
    }

    public String getRemoteThumbnailUrl() {
        return remoteThumbnailUrl != null ? remoteThumbnailUrl : "";
    }

    public void setRemoteThumbnailUrl(String remoteThumbnailUrl) {
        this.remoteThumbnailUrl = remoteThumbnailUrl != null ? remoteThumbnailUrl : "";
    }

    public String getEncryptedS3Url() {
        return encryptedS3Url != null ? encryptedS3Url : "";
    }

    public void setEncryptedS3Url(String encryptedS3Url) {
        this.encryptedS3Url = encryptedS3Url != null ? encryptedS3Url : "";
    }

    public String getEncryptionKey() {
        return encryptionKey != null ? encryptionKey : "";
    }

    public void setEncryptionKey(String encryptionKey) {
        this.encryptionKey = encryptionKey != null ? encryptionKey : "";
    }

    public String getEncryptionIv() {
        return encryptionIv != null ? encryptionIv : "";
    }

    public void setEncryptionIv(String encryptionIv) {
        this.encryptionIv = encryptionIv != null ? encryptionIv : "";
    }
}

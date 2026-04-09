package com.jippytalk.Chats.Model;

/**
 * Developer Name: Vidya Sagar
 * Created on: 06-04-2026
 */

public class ChatListModel {

    // ---- Fields ----

    private final String        contactId;
    private final String        contactName;
    private final int           messageDirection;
    private final int           messageType;
    private final String        message;
    private final int           messageStatus;
    private final int           unreadMessagesCount;
    private final long          timestamp;
    private final String        profilePic;
    private final boolean       isSelected;

    // ---- Constructor ----

    public ChatListModel(String contactId, String contactName, int messageDirection,
                         int messageType, String message, int messageStatus,
                         int unreadMessagesCount, long timestamp, String profilePic,
                         boolean isSelected) {
        this.contactId              =   contactId;
        this.contactName            =   contactName;
        this.messageDirection       =   messageDirection;
        this.messageType            =   messageType;
        this.message                =   message;
        this.messageStatus          =   messageStatus;
        this.unreadMessagesCount    =   unreadMessagesCount;
        this.timestamp              =   timestamp;
        this.profilePic             =   profilePic;
        this.isSelected             =   isSelected;
    }

    // ---- Getters ----

    public String getContactId() {
        return contactId;
    }

    public String getContactName() {
        return contactName;
    }

    public int getMessageDirection() {
        return messageDirection;
    }

    public int getMessageType() {
        return messageType;
    }

    public String getMessage() {
        return message;
    }

    public int getMessageStatus() {
        return messageStatus;
    }

    public int getUnreadMessagesCount() {
        return unreadMessagesCount;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getProfilePic() {
        return profilePic;
    }

    public boolean isSelected() {
        return isSelected;
    }
}

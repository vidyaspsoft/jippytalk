package com.jippytalk.ArchiveChat.Model;

/**
 * Developer Name: Vidya Sagar
 * Created on: 06-04-2026
 */

/**
 * ArchiveListModel - Data model representing a chat item in the archived chats
 * and message requests list. Used by ChatListRepository to deliver archived/unknown
 * chat data to the UI via LiveData.
 */
public class ArchiveListModel {

    private final String    contactId;
    private final String    contactName;
    private final int       messageDirection;
    private final int       messageType;
    private final String    lastMessage;
    private final int       messageStatus;
    private final int       unreadMessagesCount;
    private final long      timestamp;
    private final String    profilePic;

    public ArchiveListModel(String contactId, String contactName, int messageDirection,
                            int messageType, String lastMessage, int messageStatus,
                            int unreadMessagesCount, long timestamp, String profilePic) {
        this.contactId              =   contactId;
        this.contactName            =   contactName;
        this.messageDirection       =   messageDirection;
        this.messageType            =   messageType;
        this.lastMessage            =   lastMessage;
        this.messageStatus          =   messageStatus;
        this.unreadMessagesCount    =   unreadMessagesCount;
        this.timestamp              =   timestamp;
        this.profilePic             =   profilePic;
    }

    // ---- Getters ----

    public String   getContactId()          { return contactId; }
    public String   getContactName()        { return contactName; }
    public int      getMessageDirection()    { return messageDirection; }
    public int      getMessageType()        { return messageType; }
    public String   getLastMessage()        { return lastMessage; }
    public int      getMessageStatus()      { return messageStatus; }
    public int      getUnreadMessagesCount() { return unreadMessagesCount; }
    public long     getTimestamp()           { return timestamp; }
    public String   getProfilePic()         { return profilePic; }
}

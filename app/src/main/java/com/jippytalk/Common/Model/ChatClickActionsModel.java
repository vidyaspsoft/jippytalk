package com.jippytalk.Common.Model;

public class ChatClickActionsModel {

    private String contactId;
    private String contactName;
    private int    chatClickActionType;
    private long   chatLockedTimeLeft;
    private String contactPhoneNumber;
    private String profilePicId;

    public ChatClickActionsModel(String contactName, String contactPhoneNumber, String profilePicId) {
        this.contactName        = contactName;
        this.contactPhoneNumber = contactPhoneNumber;
        this.profilePicId       = profilePicId;
    }

    public ChatClickActionsModel(String contactId, String contactName, int chatClickActionType,
                                  long chatLockedTimeLeft) {
        this.contactId          = contactId;
        this.contactName        = contactName;
        this.chatClickActionType = chatClickActionType;
        this.chatLockedTimeLeft = chatLockedTimeLeft;
    }

    public String getContactId() { return contactId; }
    public void setContactId(String contactId) { this.contactId = contactId; }
    public String getContactName() { return contactName; }
    public void setContactName(String contactName) { this.contactName = contactName; }
    public int getChatClickActionType() { return chatClickActionType; }
    public void setChatClickActionType(int chatClickActionType) { this.chatClickActionType = chatClickActionType; }
    public long getChatLockedTimeLeft() { return chatLockedTimeLeft; }
    public void setChatLockedTimeLeft(long chatLockedTimeLeft) { this.chatLockedTimeLeft = chatLockedTimeLeft; }
    public String getContactPhoneNumber() { return contactPhoneNumber; }
    public void setContactPhoneNumber(String contactPhoneNumber) { this.contactPhoneNumber = contactPhoneNumber; }
    public String getProfilePicId() { return profilePicId; }
    public void setProfilePicId(String profilePicId) { this.profilePicId = profilePicId; }
}

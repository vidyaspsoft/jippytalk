package com.jippytalk.MessagesForward.Model;

/**
 * Developer Name: Vidya Sagar
 * Created on: 06-04-2026
 */

public class MessageForwardChatsListModel {

    // ---- Fields ----

    private final String        contactId;
    private final String        contactName;
    private final String        profilePic;
    private boolean             isSelected;

    // ---- Constructor ----

    public MessageForwardChatsListModel(String contactId, String contactName,
                                        String profilePic, boolean isSelected) {
        this.contactId      =   contactId;
        this.contactName    =   contactName;
        this.profilePic     =   profilePic;
        this.isSelected     =   isSelected;
    }

    // ---- Getters ----

    public String getContactId() {
        return contactId;
    }

    public String getContactName() {
        return contactName;
    }

    public String getProfilePic() {
        return profilePic;
    }

    public boolean isSelected() {
        return isSelected;
    }

    // ---- Setters ----

    public void setSelected(boolean selected) {
        isSelected  =   selected;
    }
}

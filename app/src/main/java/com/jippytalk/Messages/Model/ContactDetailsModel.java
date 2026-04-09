package com.jippytalk.Messages.Model;

public class ContactDetailsModel {

    private String  contactPhoneNumber;
    private String  contactProfilePicId;
    private int     contactDeviceId;

    public ContactDetailsModel(String contactPhoneNumber, String contactProfilePicId, int contactDeviceId) {
        this.contactPhoneNumber     =   contactPhoneNumber;
        this.contactProfilePicId    =   contactProfilePicId;
        this.contactDeviceId        =   contactDeviceId;
    }

    public String getContactPhoneNumber() {
        return contactPhoneNumber;
    }

    public void setContactPhoneNumber(String contactPhoneNumber) {
        this.contactPhoneNumber = contactPhoneNumber;
    }

    public String getContactProfilePicId() {
        return contactProfilePicId;
    }

    public void setContactProfilePicId(String contactProfilePicId) {
        this.contactProfilePicId = contactProfilePicId;
    }

    public int getContactDeviceId() {
        return contactDeviceId;
    }

    public void setContactDeviceId(int contactDeviceId) {
        this.contactDeviceId = contactDeviceId;
    }
}

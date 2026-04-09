package com.jippytalk.Contacts.ViewModel;

/**
 * Developer Name: Vidya Sagar
 * Created on: 06-04-2026
 */

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import com.jippytalk.Chats.Repository.ChatsUIRepository;
import com.jippytalk.Contacts.Repository.ContactsUIRepository;
import com.jippytalk.Database.ContactsDatabase.Repository.ContactsRepository;
import com.jippytalk.Database.MessagesDatabase.Repository.ChatListRepository;
import com.jippytalk.Extras;
import com.jippytalk.Messages.Model.ContactDetailsModel;

/**
 * ContactViewModel - ViewModel for contact-related data in MessagingActivity.
 * Exposes contact details via LiveData for the messaging screen.
 */
public class ContactViewModel extends ViewModel {

    // ---- Fields ----

    private final ContactsRepository       contactsRepository;
    private final ChatListRepository       chatListRepository;
    private final ChatsUIRepository        chatsUIRepository;
    private final ContactsUIRepository     contactsUIRepository;

    // ---- Constructor ----

    public ContactViewModel(ContactsRepository contactsRepository, ChatListRepository chatListRepository,
                            ChatsUIRepository chatsUIRepository, ContactsUIRepository contactsUIRepository) {
        this.contactsRepository     =   contactsRepository;
        this.chatListRepository     =   chatListRepository;
        this.chatsUIRepository      =   chatsUIRepository;
        this.contactsUIRepository   =   contactsUIRepository;
    }

    // ---- Contact Details Methods ----

    /**
     * Returns LiveData containing contact details for the chat screen (name, pic, deviceId).
     * Observed by MessagingActivity to update toolbar and send encrypted messages.
     */
    public LiveData<ContactDetailsModel> getContactDetailsForChatScreenFromLiveModel() {
        return contactsRepository.getContactDetailsLiveData();
    }

    /**
     * Triggers retrieval of contact details for the messaging activity.
     * Results are posted to the LiveData returned by getContactDetailsForChatScreenFromLiveModel().
     *
     * @param contactId the contact's user ID
     */
    public void retrieveContactDetailsForMessagingActivity(String contactId) {
        contactsRepository.getContactDetailsForMessagingActivity(contactId);
    }
}

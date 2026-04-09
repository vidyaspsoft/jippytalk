package com.jippytalk.Contacts.ViewModel;

/**
 * Developer Name: Vidya Sagar
 * Created on: 06-04-2026
 */

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.jippytalk.Chats.Repository.ChatsUIRepository;
import com.jippytalk.Contacts.Repository.ContactsUIRepository;
import com.jippytalk.Database.ContactsDatabase.Repository.ContactsRepository;
import com.jippytalk.Database.MessagesDatabase.Repository.ChatListRepository;

public class ContactViewModelFactory implements ViewModelProvider.Factory {

    // ---- Fields ----

    private final ContactsRepository       contactsRepository;
    private final ChatListRepository       chatListRepository;
    private final ChatsUIRepository        chatsUIRepository;
    private final ContactsUIRepository     contactsUIRepository;

    // ---- Constructor ----

    public ContactViewModelFactory(ContactsRepository contactsRepository, ChatListRepository chatListRepository,
                                   ChatsUIRepository chatsUIRepository, ContactsUIRepository contactsUIRepository) {
        this.contactsRepository     =   contactsRepository;
        this.chatListRepository     =   chatListRepository;
        this.chatsUIRepository      =   chatsUIRepository;
        this.contactsUIRepository   =   contactsUIRepository;
    }

    // ---- Factory Method ----

    @NonNull
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> viewModel) {
        if (viewModel.isAssignableFrom(ContactViewModel.class)) {
            return (T) new ContactViewModel(contactsRepository, chatListRepository,
                    chatsUIRepository, contactsUIRepository);
        }
        throw new IllegalArgumentException("Unknown ViewModel class");
    }
}

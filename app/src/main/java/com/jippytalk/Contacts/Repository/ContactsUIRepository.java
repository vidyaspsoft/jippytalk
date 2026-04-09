package com.jippytalk.Contacts.Repository;

/**
 * Developer Name: Vidya Sagar
 * Created on: 06-04-2026
 */

import android.content.SharedPreferences;
import android.util.Log;

import com.jippytalk.Database.MessagesDatabase.Repository.ChatListRepository;
import com.jippytalk.Extras;

public class ContactsUIRepository {

    // ---- Fields ----

    private final ChatListRepository       chatListRepository;
    private final SharedPreferences        sharedPreferences;

    // ---- Constructor ----

    public ContactsUIRepository(ChatListRepository chatListRepository, SharedPreferences sharedPreferences) {
        this.chatListRepository     =   chatListRepository;
        this.sharedPreferences      =   sharedPreferences;
        Log.e(Extras.LOG_MESSAGE, "ContactsUIRepository initialized");
    }
}

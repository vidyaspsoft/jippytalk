package com.jippytalk.ContactProfile.API_Handlers;

/**
 * Developer Name: Vidya Sagar
 * Created on: 06-04-2026
 */

import android.content.SharedPreferences;
import android.util.Log;

import com.jippytalk.Database.ContactsDatabase.Repository.ContactsRepository;
import com.jippytalk.Extras;
import com.jippytalk.TokenRefreshAPI;
import com.jippytalk.UserDetailsRepository;

import java.util.concurrent.ExecutorService;

public class ContactProfilePicAPI {

    // ---- Fields ----

    private final SharedPreferences        sharedPreferences;
    private final ContactsRepository       contactsRepository;
    private final UserDetailsRepository    userDetailsRepository;
    private final TokenRefreshAPI          tokenRefreshAPI;
    private final ExecutorService          executorService;

    // ---- Constructor ----

    public ContactProfilePicAPI(SharedPreferences sharedPreferences,
                                ContactsRepository contactsRepository,
                                UserDetailsRepository userDetailsRepository,
                                TokenRefreshAPI tokenRefreshAPI,
                                ExecutorService executorService) {
        this.sharedPreferences      =   sharedPreferences;
        this.contactsRepository     =   contactsRepository;
        this.userDetailsRepository  =   userDetailsRepository;
        this.tokenRefreshAPI        =   tokenRefreshAPI;
        this.executorService        =   executorService;
        Log.e(Extras.LOG_MESSAGE, "ContactProfilePicAPI initialized");
    }
}

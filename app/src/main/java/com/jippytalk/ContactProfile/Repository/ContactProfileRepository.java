package com.jippytalk.ContactProfile.Repository;

/**
 * Developer Name: Vidya Sagar
 * Created on: 06-04-2026
 */

import android.content.SharedPreferences;
import android.util.Log;

import com.jippytalk.ContactProfile.API_Handlers.ContactProfilePicAPI;
import com.jippytalk.Extras;

import java.util.concurrent.ExecutorService;

public class ContactProfileRepository {

    // ---- Fields ----

    private static volatile ContactProfileRepository   INSTANCE;
    private final ContactProfilePicAPI                 contactProfilePicAPI;
    private final ExecutorService                      executorService;
    private final SharedPreferences                    sharedPreferences;

    // ---- Constructor ----

    private ContactProfileRepository(ContactProfilePicAPI contactProfilePicAPI,
                                     ExecutorService executorService,
                                     SharedPreferences sharedPreferences) {
        this.contactProfilePicAPI   =   contactProfilePicAPI;
        this.executorService        =   executorService;
        this.sharedPreferences      =   sharedPreferences;
    }

    // ---- Singleton ----

    public static ContactProfileRepository getInstance(ContactProfilePicAPI contactProfilePicAPI,
                                                        ExecutorService executorService,
                                                        SharedPreferences sharedPreferences) {
        if (INSTANCE == null) {
            synchronized (ContactProfileRepository.class) {
                if (INSTANCE == null) {
                    INSTANCE    =   new ContactProfileRepository(contactProfilePicAPI,
                                    executorService, sharedPreferences);
                }
            }
        }
        return INSTANCE;
    }
}

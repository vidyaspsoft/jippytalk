package com.jippytalk.Privacy.Repository;

/**
 * Developer Name: Vidya Sagar
 * Created on: 06-04-2026
 */

import android.content.SharedPreferences;
import android.util.Log;

import com.jippytalk.Database.ContactsDatabase.Repository.ContactsRepository;
import com.jippytalk.Extras;
import com.jippytalk.Privacy.APIHandler.PrivacyAPIHandler;
import com.jippytalk.UserDetailsRepository;

import java.util.concurrent.ExecutorService;

public class PrivacyRepository {

    // ---- Fields ----

    private static volatile PrivacyRepository      INSTANCE;
    private final SharedPreferences                sharedPreferences;
    private final UserDetailsRepository            userDetailsRepository;
    private final ContactsRepository               contactsRepository;
    private final PrivacyAPIHandler                privacyAPIHandler;
    private final ExecutorService                  executorService;

    // ---- Constructor ----

    private PrivacyRepository(SharedPreferences sharedPreferences,
                              UserDetailsRepository userDetailsRepository,
                              ContactsRepository contactsRepository,
                              PrivacyAPIHandler privacyAPIHandler,
                              ExecutorService executorService) {
        this.sharedPreferences      =   sharedPreferences;
        this.userDetailsRepository  =   userDetailsRepository;
        this.contactsRepository     =   contactsRepository;
        this.privacyAPIHandler      =   privacyAPIHandler;
        this.executorService        =   executorService;
    }

    // ---- Singleton ----

    public static PrivacyRepository getInstance(SharedPreferences sharedPreferences,
                                                 UserDetailsRepository userDetailsRepository,
                                                 ContactsRepository contactsRepository,
                                                 PrivacyAPIHandler privacyAPIHandler,
                                                 ExecutorService executorService) {
        if (INSTANCE == null) {
            synchronized (PrivacyRepository.class) {
                if (INSTANCE == null) {
                    INSTANCE    =   new PrivacyRepository(sharedPreferences, userDetailsRepository,
                                    contactsRepository, privacyAPIHandler, executorService);
                }
            }
        }
        return INSTANCE;
    }
}

package com.jippytalk.ProfileSetUp.APIs;

/**
 * Developer Name: Vidya Sagar
 * Created on: 06-04-2026
 */

import android.util.Log;

import com.jippytalk.Extras;
import com.jippytalk.UserDetailsRepository;

import java.util.concurrent.ExecutorService;

public class ProfileSetUpAPI {

    // ---- Fields ----

    private final ExecutorService          executorService;
    private final UserDetailsRepository    userDetailsRepository;

    // ---- Constructor ----

    public ProfileSetUpAPI(ExecutorService executorService, UserDetailsRepository userDetailsRepository) {
        this.executorService        =   executorService;
        this.userDetailsRepository  =   userDetailsRepository;
        Log.e(Extras.LOG_MESSAGE, "ProfileSetUpAPI initialized");
    }
}

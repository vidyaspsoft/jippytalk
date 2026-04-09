package com.jippytalk.AboutSetUp.APIs;

/**
 * Developer Name: Vidya Sagar
 * Created on: 06-04-2026
 */

import android.util.Log;

import com.jippytalk.Extras;

import java.util.concurrent.ExecutorService;

public class AboutSetUpAPI {

    // ---- Fields ----

    private final ExecutorService      executorService;

    // ---- Constructor ----

    public AboutSetUpAPI(ExecutorService executorService) {
        this.executorService    =   executorService;
        Log.e(Extras.LOG_MESSAGE, "AboutSetUpAPI initialized");
    }
}

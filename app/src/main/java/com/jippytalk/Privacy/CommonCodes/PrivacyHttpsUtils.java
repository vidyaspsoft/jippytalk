package com.jippytalk.Privacy.CommonCodes;

/**
 * Developer Name: Vidya Sagar
 * Created on: 06-04-2026
 */

import android.util.Log;

import com.jippytalk.Extras;

public class PrivacyHttpsUtils {

    // ---- Fields ----

    private static volatile PrivacyHttpsUtils      INSTANCE;

    // ---- Constructor ----

    private PrivacyHttpsUtils() {
        Log.e(Extras.LOG_MESSAGE, "PrivacyHttpsUtils initialized");
    }

    // ---- Singleton ----

    public static PrivacyHttpsUtils getInstance() {
        if (INSTANCE == null) {
            synchronized (PrivacyHttpsUtils.class) {
                if (INSTANCE == null) {
                    INSTANCE    =   new PrivacyHttpsUtils();
                }
            }
        }
        return INSTANCE;
    }
}

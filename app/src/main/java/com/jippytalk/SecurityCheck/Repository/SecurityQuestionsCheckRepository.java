package com.jippytalk.SecurityCheck.Repository;

/**
 * Developer Name: Vidya Sagar
 * Created on: 06-04-2026
 */

import android.content.Context;
import android.util.Log;

import com.jippytalk.Extras;
import com.jippytalk.SecurityCheck.APIs.SecurityQuestionsCheckAPI;

public class SecurityQuestionsCheckRepository {

    // ---- Fields ----

    private static volatile SecurityQuestionsCheckRepository   INSTANCE;
    private final Context                                      context;
    private final SecurityQuestionsCheckAPI                     securityQuestionsCheckAPI;

    // ---- Constructor ----

    private SecurityQuestionsCheckRepository(Context context,
                                             SecurityQuestionsCheckAPI securityQuestionsCheckAPI) {
        this.context                     =   context.getApplicationContext();
        this.securityQuestionsCheckAPI   =   securityQuestionsCheckAPI;
    }

    // ---- Singleton ----

    public static SecurityQuestionsCheckRepository getInstance(Context context,
                                                                SecurityQuestionsCheckAPI securityQuestionsCheckAPI) {
        if (INSTANCE == null) {
            synchronized (SecurityQuestionsCheckRepository.class) {
                if (INSTANCE == null) {
                    INSTANCE    =   new SecurityQuestionsCheckRepository(context.getApplicationContext(),
                                    securityQuestionsCheckAPI);
                }
            }
        }
        return INSTANCE;
    }
}

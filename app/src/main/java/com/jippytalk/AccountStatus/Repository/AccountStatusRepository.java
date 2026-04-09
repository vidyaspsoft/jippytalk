package com.jippytalk.AccountStatus.Repository;

/**
 * Developer Name: Vidya Sagar
 * Created on: 06-04-2026
 */

import android.content.SharedPreferences;
import android.util.Log;

import com.jippytalk.AccountStatus.AccountStatusAPI.AccountStatusAPIHandler;
import com.jippytalk.Extras;
import com.jippytalk.UserDetailsRepository;

public class AccountStatusRepository {

    // ---- Fields ----

    private static volatile AccountStatusRepository    INSTANCE;
    private final SharedPreferences                    sharedPreferences;
    private final UserDetailsRepository                userDetailsRepository;
    private final AccountStatusAPIHandler              accountStatusAPIHandler;

    // ---- Constructor ----

    private AccountStatusRepository(SharedPreferences sharedPreferences,
                                    UserDetailsRepository userDetailsRepository,
                                    AccountStatusAPIHandler accountStatusAPIHandler) {
        this.sharedPreferences          =   sharedPreferences;
        this.userDetailsRepository      =   userDetailsRepository;
        this.accountStatusAPIHandler    =   accountStatusAPIHandler;
    }

    // ---- Singleton ----

    public static AccountStatusRepository getInstance(SharedPreferences sharedPreferences,
                                                       UserDetailsRepository userDetailsRepository,
                                                       AccountStatusAPIHandler accountStatusAPIHandler) {
        if (INSTANCE == null) {
            synchronized (AccountStatusRepository.class) {
                if (INSTANCE == null) {
                    INSTANCE    =   new AccountStatusRepository(sharedPreferences,
                                    userDetailsRepository, accountStatusAPIHandler);
                }
            }
        }
        return INSTANCE;
    }
}

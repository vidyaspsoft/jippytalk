package com.jippytalk;

/**
 * Developer Name: Vidya Sagar
 * Created on: 06-04-2026
 */

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;

import com.jippytalk.Chats.MainActivity;
import com.jippytalk.Login.RequestOTP.SendOtpActivity;
import com.jippytalk.Managers.AccountManager;
import com.jippytalk.Managers.SharedPreferenceDetails;

/**
 * AppLauncherActivity - The main launcher activity of the application.
 * This is the entry point when the user opens the app.
 *
 * Displays a splash screen while MyApplication initializes databases and services.
 * Once initialization is complete, routes the user based on their registration status:
 * - If registration is complete -> navigates to MainActivity (chat list)
 * - If registration is not complete -> navigates to SendOtpActivity (login flow)
 * - If account is restricted -> navigates to AccessDeniedActivity
 */
public class AppLauncherActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Install splash screen before calling super.onCreate()
        SplashScreen splashScreen   =   SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);

        MyApplication myApplication =   MyApplication.getInstance();

        // Keep splash screen visible until app initialization is complete
        splashScreen.setKeepOnScreenCondition(() -> {
            Boolean initialized =   myApplication.getInitializationState().getValue();
            return initialized == null || !initialized;
        });

        // Observe initialization state and navigate when ready
        myApplication.getInitializationState().observe(this, isInitialized -> {
            if (isInitialized != null && isInitialized) {
                navigateToNextScreen();
            }
        });
    }

    // -------------------- Navigation Logic Starts Here ---------------------

    /**
     * Determines the next screen to navigate to based on the user's registration progress
     * and account status stored in SharedPreferences.
     */
    private void navigateToNextScreen() {
        try {
            SharedPreferences   sharedPreferences       =   getSharedPreferences(
                    SharedPreferenceDetails.SHARED_PREFERENCE_NAME, MODE_PRIVATE);
            int                 registrationProgress    =   sharedPreferences.getInt(
                    SharedPreferenceDetails.REGISTRATION_PROGRESS,
                    AccountManager.INITIAL_SCREENS);
            int                 accountStatus           =   sharedPreferences.getInt(
                    SharedPreferenceDetails.ACCOUNT_STATUS,
                    AccountManager.ACCOUNT_ACTIVE);

            Log.e(Extras.LOG_MESSAGE, "Registration progress: " + registrationProgress
                    + " Account status: " + accountStatus);

            // Check if account is restricted or denied
            if (accountStatus == AccountManager.ACCOUNT_RESTRICTED) {
                navigateToAccessDenied();
                return;
            }

            // Route based on registration progress
            if (registrationProgress == AccountManager.REGISTRATION_DONE) {
                navigateToMainActivity();
            }
            else {
                navigateToLoginFlow(registrationProgress);
            }

        }
        catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "Error navigating from launcher " + e.getMessage());
            navigateToLoginFlow(AccountManager.INITIAL_SCREENS);
        }
    }

    /**
     * Navigates to the main chat list screen (MainActivity).
     * Called when the user has completed registration.
     */
    private void navigateToMainActivity() {
        Intent intent   =   new Intent(AppLauncherActivity.this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    /**
     * Navigates to the login/registration flow (SendOtpActivity).
     * Called when the user has not yet completed registration.
     *
     * @param registrationProgress the current step in the registration process
     */
    private void navigateToLoginFlow(int registrationProgress) {
        Intent intent   =   new Intent(AppLauncherActivity.this, SendOtpActivity.class);
        intent.putExtra(SharedPreferenceDetails.REGISTRATION_PROGRESS, registrationProgress);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    /**
     * Navigates to the access denied screen.
     * Called when the user's account has been restricted.
     */
    private void navigateToAccessDenied() {
        Intent intent   =   new Intent(AppLauncherActivity.this, AccessDeniedActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}

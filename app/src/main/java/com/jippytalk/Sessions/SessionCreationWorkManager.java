package com.jippytalk.Sessions;

/**
 * Developer Name: Vidya Sagar
 * Created on: 06-04-2026
 */

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.jippytalk.Extras;
import com.jippytalk.Messages.Datahandlers.HandleOneTimePreKeyRetrieval;
import com.jippytalk.MyApplication;
import com.jippytalk.ServiceLocators.RepositoryServiceLocator;
import com.jippytalk.TokenRefreshAPI;
import com.jippytalk.UserDetailsRepository;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * SessionCreationWorkManager - A WorkManager Worker that handles background session creation
 * for contacts when no active Signal Protocol session exists.
 *
 * This worker is enqueued when:
 * 1. MessagingActivity detects no session exists for a contact (containsSession returns false)
 * 2. SendUnSyncedDataToServer encounters an encryption failure due to missing session
 *
 * The worker retrieves the contact's one-time pre-key from the server and creates
 * a new Signal Protocol session using SessionCreation.
 *
 * Input Data:
 * - "contactId" (String): The contact's user ID for whom the session needs to be created
 */
public class SessionCreationWorkManager extends Worker {

    public SessionCreationWorkManager(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        String contactId    =   getInputData().getString("contactId");

        if (contactId == null || contactId.isEmpty()) {
            Log.e(Extras.LOG_MESSAGE, "SessionCreationWorkManager - contactId is null or empty");
            return Result.failure();
        }

        Log.e(Extras.LOG_MESSAGE, "SessionCreationWorkManager starting for contact " + contactId);

        try {
            // Wait for MyApplication initialization to complete
            MyApplication myApplication =   MyApplication.getInstance();
            if (myApplication == null) {
                Log.e(Extras.LOG_MESSAGE, "SessionCreationWorkManager - MyApplication is null");
                return Result.failure();
            }

            CountDownLatch initLatch    =   myApplication.getInitLatch();
            if (!initLatch.await(30, TimeUnit.SECONDS)) {
                Log.e(Extras.LOG_MESSAGE, "SessionCreationWorkManager - Timed out waiting for initialization");
                return Result.failure();
            }

            RepositoryServiceLocator    repositoryServiceLocator    =   myApplication.getRepositoryServiceLocator();
            UserDetailsRepository       userDetailsRepository       =   repositoryServiceLocator.getUserDetailsRepository();
            TokenRefreshAPI             tokenRefreshAPI             =   new TokenRefreshAPI(userDetailsRepository);

            // Use a latch to wait for the async pre-key retrieval and session creation
            CountDownLatch  sessionLatch    =   new CountDownLatch(1);
            final boolean[] success         =   {false};

            HandleOneTimePreKeyRetrieval handleOneTimePreKeyRetrieval   =   new HandleOneTimePreKeyRetrieval(
                    getApplicationContext(), userDetailsRepository, tokenRefreshAPI,
                    (createdContactId, deviceId) -> {
                        Log.e(Extras.LOG_MESSAGE, "SessionCreationWorkManager - Session created for " +
                                createdContactId + " device " + deviceId);
                        success[0]  =   true;
                        sessionLatch.countDown();
                    });

            handleOneTimePreKeyRetrieval.getContactOneTimePreKey(contactId);

            // Wait for session creation to complete with a timeout
            if (!sessionLatch.await(60, TimeUnit.SECONDS)) {
                Log.e(Extras.LOG_MESSAGE, "SessionCreationWorkManager - Timed out waiting for session creation");
                return Result.retry();
            }

            if (success[0]) {
                Log.e(Extras.LOG_MESSAGE, "SessionCreationWorkManager completed successfully");
                return Result.success();
            }
            else {
                return Result.retry();
            }

        }
        catch (InterruptedException e) {
            Log.e(Extras.LOG_MESSAGE, "SessionCreationWorkManager interrupted " + e.getMessage());
            Thread.currentThread().interrupt();
            return Result.failure();
        }
        catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "SessionCreationWorkManager failed " + e.getMessage());
            return Result.retry();
        }
    }
}

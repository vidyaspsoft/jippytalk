package com.jippytalk;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.jippytalk.Managers.AccountManager;
import com.jippytalk.Managers.SharedPreferenceDetails;
import com.jippytalk.WebSocketConnection;
import com.jippytalk.ServiceLocators.APIServiceLocator;
import com.jippytalk.ServiceLocators.AppServiceLocator;
import com.jippytalk.ServiceLocators.DatabaseServiceLocator;
import com.jippytalk.ServiceLocators.RepositoryServiceLocator;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class OnlineOfflineDetectorActivity extends AppCompatActivity {

    private DatabaseServiceLocator                  databaseServiceLocator;
    private RepositoryServiceLocator                repositoryServiceLocator;
    private APIServiceLocator                       apiServiceLocator;
    private AppServiceLocator                       appServiceLocator;
    private int                                     registrationProcess;
    private SharedPreferences                       sharedPreferences;
    private ConnectivityManager                     connectivityManager;
    private ConnectivityManager.NetworkCallback     networkCallback;
    private final ExecutorService                   executorService     = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            MyApplication.getInstance().getInitializationState().observe(this, aBoolean -> {
                if (aBoolean != null && aBoolean) {
                    executorService.execute(this::initiateAllMethods);
                }
            });
        } catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE,"Error defining in online offline detector activity " + e.getMessage());
        }
    }

    private void initiateAllMethods() {
        databaseServiceLocator = new DatabaseServiceLocator(getApplicationContext());
        repositoryServiceLocator = new RepositoryServiceLocator(getApplicationContext(), databaseServiceLocator);
        apiServiceLocator = new APIServiceLocator(getApplicationContext(), repositoryServiceLocator);
        appServiceLocator = new AppServiceLocator(getApplicationContext(), databaseServiceLocator,
                repositoryServiceLocator, apiServiceLocator);
        sharedPreferences = repositoryServiceLocator.getSharedPreferences();
        apiServiceLocator.setAppServiceLocator(appServiceLocator);

        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                super.onAvailable(network);
                Log.e("Network", "Network connected");
                WebSocketConnection socket = appServiceLocator.getWebSocketConnection();
                if (socket == null) {
                    Log.e(Extras.LOG_MESSAGE, "WebSocketConnection is null");
                    return;
                }

                if (WebSocketConnection.isConnectedToSocket) {
                    Log.e(Extras.LOG_MESSAGE, "Socket already connected");
                    return;
                }
                if (sharedPreferences == null) {
                    Log.e(Extras.LOG_MESSAGE, "SharedPreferences is null");
                    return;
                }

                registrationProcess = sharedPreferences.getInt(SharedPreferenceDetails.REGISTRATION_PROGRESS,
                        AccountManager.INITIAL_SCREENS);

                if (registrationProcess == AccountManager.REGISTRATION_DONE && !MyApplication.isAppClosed) {
                    socket.connectToWebSocket();
                }
            }

            @Override
            public void onLost(@NonNull Network network) {
                super.onLost(network);
                Log.e("Network", "Network lost");
                if (appServiceLocator != null && appServiceLocator.getWebSocketConnection() != null
                        && WebSocketConnection.isConnectedToSocket) {
                    appServiceLocator.getWebSocketConnection().disconnectWebSocket();
                }
            }
        };
        connectivityManager.registerDefaultNetworkCallback(networkCallback);
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.e(Extras.LOG_MESSAGE,"on pause called");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.e(Extras.LOG_MESSAGE,"on destroy called");
        if (connectivityManager != null && networkCallback != null) {
            connectivityManager.unregisterNetworkCallback(networkCallback);
        }

    }
}
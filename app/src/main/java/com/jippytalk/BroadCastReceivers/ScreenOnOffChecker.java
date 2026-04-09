package com.jippytalk.BroadCastReceivers;

/**
 * Developer Name: Vidya Sagar
 * Created on: 06-04-2026
 */

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.jippytalk.Extras;

public class ScreenOnOffChecker extends BroadcastReceiver {

    // ---- Fields ----

    private final ScreenListener    listener;

    // ---- Constructor ----

    public ScreenOnOffChecker(ScreenListener listener) {
        this.listener   =   listener;
    }

    // ---- BroadcastReceiver Override ----

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            Log.e(Extras.LOG_MESSAGE, "ScreenOnOffChecker received null intent or action");
            return;
        }

        switch (intent.getAction()) {
            case Intent.ACTION_SCREEN_OFF   ->  {
                Log.e(Extras.LOG_MESSAGE, "Screen turned off");
                if (listener != null) {
                    listener.onScreenOff();
                }
            }
            case Intent.ACTION_SCREEN_ON    ->  {
                Log.e(Extras.LOG_MESSAGE, "Screen turned on");
                if (listener != null) {
                    listener.onScreenOn();
                }
            }
            case Intent.ACTION_USER_PRESENT ->  {
                Log.e(Extras.LOG_MESSAGE, "User present after unlock");
                if (listener != null) {
                    listener.onUserPresent();
                }
            }
        }
    }

    // ---- ScreenListener Interface ----

    public interface ScreenListener {
        void onScreenOff();
        void onScreenOn();
        void onUserPresent();
    }
}

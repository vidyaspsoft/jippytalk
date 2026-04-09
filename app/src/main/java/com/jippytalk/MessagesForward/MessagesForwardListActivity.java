package com.jippytalk.MessagesForward;

/**
 * Developer Name: Vidya Sagar
 * Created on: 06-04-2026
 */

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

import com.jippytalk.Extras;

public class MessagesForwardListActivity extends Activity {

    // ---- Lifecycle ----

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.e(Extras.LOG_MESSAGE, "MessagesForwardListActivity onCreate");
    }
}

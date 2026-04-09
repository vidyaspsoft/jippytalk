package com.jippytalk.MessageInfo;

/**
 * Developer Name: Vidya Sagar
 * Created on: 06-04-2026
 */

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

import com.jippytalk.Extras;

public class MessageInfoActivity extends Activity {

    // ---- Lifecycle ----

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.e(Extras.LOG_MESSAGE, "MessageInfoActivity onCreate");
    }
}

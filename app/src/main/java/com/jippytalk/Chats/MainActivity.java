package com.jippytalk.Chats;

/**
 * Developer Name: Vidya Sagar
 * Created on: 06-04-2026
 */

import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.jippytalk.Extras;

public class MainActivity extends AppCompatActivity {

    // ---- Lifecycle ----

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.e(Extras.LOG_MESSAGE, "MainActivity onCreate");
    }
}

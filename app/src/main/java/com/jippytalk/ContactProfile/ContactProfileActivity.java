package com.jippytalk.ContactProfile;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.jippytalk.Extras;

public class ContactProfileActivity extends AppCompatActivity {

    private String contactId;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getIntent().hasExtra(Extras.CHAT_ID)) {
            contactId = getIntent().getStringExtra(Extras.CHAT_ID);
        }
    }
}

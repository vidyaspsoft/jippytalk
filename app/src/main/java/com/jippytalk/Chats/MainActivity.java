package com.jippytalk.Chats;

/**
 * Developer Name: Vidya Sagar
 * Created on: 06-04-2026
 */

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.jippytalk.Chats.Adapter.ChatListAdapter;
import com.jippytalk.Chats.Model.ChatListModel;
import com.jippytalk.Encryption.SignalKeyManager;
import com.jippytalk.Extras;
import com.jippytalk.Managers.MessagesManager;
import com.jippytalk.Managers.SharedPreferenceDetails;
import com.jippytalk.Messages.Api.MessagesApi;
import com.jippytalk.Messages.MessagingActivity;
import com.jippytalk.MyApplication;
import com.jippytalk.R;

import java.util.ArrayList;
import java.util.List;

/**
 * MainActivity - Chat list screen displayed after successful login.
 *
 * Currently shows:
 *   - Toolbar with app name
 *   - Static list of users (alice for testing)
 *   - Empty state shown when the list has no items
 *   - FAB to start a new chat
 *
 * Clicking a chat row opens MessagingActivity with the contact's user ID.
 *
 * TODO: Replace static list with ChatListRepository LiveData once wired.
 */
public class MainActivity extends AppCompatActivity implements ChatListAdapter.OnChatClickListener {

    // ---- Static Test Users ----

    private static final String     TEST_USER_ID_ALICE      =   "a3cef1bb-4af7-4731-9027-13362669ce62";
    private static final String     TEST_USERNAME_ALICE     =   "alice";
    private static final String     TEST_USER_ID_BOB        =   "72e18df8-1477-4ac2-b5f1-e02971e97c19";
    private static final String     TEST_USERNAME_BOB       =   "bob";

    // ---- Views ----

    private MaterialToolbar             toolbar;
    private RecyclerView                rvChatList;
    private LinearLayout                llEmptyState;
    private TextView                    tvEmptyTitle;
    private FloatingActionButton        fabNewChat;

    // ---- Fields ----

    private SharedPreferences           sharedPreferences;
    private ChatListAdapter             chatListAdapter;

    // ---- Lifecycle ----

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.e(Extras.LOG_MESSAGE, "MainActivity onCreate");

        initViews();
        setupToolbar();
        setupRecyclerView();
        loadStaticChats();
        setupClickListeners();
        uploadSignalKeysIfNeeded();
    }

    /**
     * Checks whether Signal Protocol keys have been generated and uploaded yet.
     * If not, kicks off generation + upload in the background. Non-blocking —
     * the chat list is shown immediately while keys upload in parallel.
     */
    private void uploadSignalKeysIfNeeded() {
        SignalKeyManager keyManager =   SignalKeyManager.getInstance(MainActivity.this);
        keyManager.generateAndUploadKeysIfNeeded(new SignalKeyManager.KeyUploadCallback() {
            @Override
            public void onSuccess() {
                Log.e(Extras.LOG_MESSAGE, "Signal keys ready");
            }

            @Override
            public void onFailure(String error) {
                Log.e(Extras.LOG_MESSAGE, "Signal keys upload failed: " + error);
                Toast.makeText(MainActivity.this,
                        "Encryption setup failed: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    // -------------------- View Initialization Starts Here ---------------------

    /**
     * Finds and stores references to all views in the layout.
     */
    private void initViews() {
        toolbar         =   findViewById(R.id.toolbar);
        rvChatList      =   findViewById(R.id.rvChatList);
        llEmptyState    =   findViewById(R.id.llEmptyState);
        tvEmptyTitle    =   findViewById(R.id.tvEmptyTitle);
        fabNewChat      =   findViewById(R.id.fabNewChat);
    }

    /**
     * Configures the toolbar as the action bar and sets the title.
     */
    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.chats);
        }
    }

    /**
     * Sets up the chat list RecyclerView with the adapter.
     */
    private void setupRecyclerView() {
        chatListAdapter     =   new ChatListAdapter(this);
        rvChatList.setLayoutManager(new LinearLayoutManager(this));
        rvChatList.setAdapter(chatListAdapter);
    }

    // -------------------- Data Loading Starts Here ---------------------

    /**
     * Loads the chat list from the server via GET /api/rooms, then falls back to a
     * hardcoded static list if the API call fails (dev mode).
     *
     * Each server room becomes a ChatListModel with the room_id attached, which the
     * chat screen uses to fetch message history.
     */
    private void loadStaticChats() {
        sharedPreferences   =   getSharedPreferences(
                SharedPreferenceDetails.SHARED_PREFERENCE_NAME, MODE_PRIVATE);

        // Show the hardcoded fallback list immediately so the UI isn't blank while the API loads
        showHardcodedChats();

        // Then call /api/rooms to get the real list with room IDs
        fetchRoomsFromServer();
    }

    /**
     * Calls GET /api/rooms and replaces the chat list with server data.
     * Each returned room is mapped to a ChatListModel. The "other" user in the room
     * (not the logged-in user) becomes the displayed contact.
     */
    private void fetchRoomsFromServer() {
        String currentUserId = sharedPreferences.getString(SharedPreferenceDetails.USERID, "");
        if (currentUserId.isEmpty()) {
            Log.e(Extras.LOG_MESSAGE, "Cannot fetch rooms — no current user id");
            return;
        }

        MessagesApi.getInstance(MainActivity.this).fetchRooms(new MessagesApi.RoomsCallback() {
            @Override
            public void onSuccess(List<MessagesApi.Room> rooms) {
                Log.e(Extras.LOG_MESSAGE, "Fetched " + rooms.size() + " rooms from server");
                List<ChatListModel> chats = new ArrayList<>();
                for (MessagesApi.Room room : rooms) {
                    String otherUserId  =   room.getOtherUserId(currentUserId);
                    // Skip self-rooms (shouldn't happen, but guard anyway)
                    if (otherUserId == null || otherUserId.isEmpty()
                            || otherUserId.equals(currentUserId)) {
                        continue;
                    }
                    String displayName  =   lookupDisplayName(otherUserId);
                    String lastMsg      =   room.lastMessageCiphertext != null
                                            ? room.lastMessageCiphertext : "";

                    chats.add(new ChatListModel(
                            otherUserId,
                            displayName,
                            MessagesManager.MESSAGE_OUTGOING,
                            MessagesManager.TEXT_MESSAGE,
                            lastMsg,
                            MessagesManager.MESSAGE_SYNCED_WITH_SERVER,
                            room.unreadCount,
                            0,
                            "",
                            false,
                            room.id
                    ));
                }

                if (!chats.isEmpty()) {
                    chatListAdapter.submitList(chats);
                    updateEmptyStateVisibility(false);
                }
            }

            @Override
            public void onFailure(String error) {
                Log.e(Extras.LOG_MESSAGE, "Rooms fetch failed: " + error + " — keeping static fallback");
            }
        });
    }

    /**
     * Maps known test user IDs to friendly display names.
     * Falls back to a truncated UUID for unknown users.
     */
    private String lookupDisplayName(String userId) {
        if (TEST_USER_ID_ALICE.equals(userId))  return TEST_USERNAME_ALICE;
        if (TEST_USER_ID_BOB.equals(userId))    return TEST_USERNAME_BOB;
        return userId.length() > 8 ? userId.substring(0, 8) + "..." : userId;
    }

    /**
     * Shows hardcoded alice + bob as a fallback until /api/rooms returns real data.
     * Hides the row that matches the currently logged-in user so you never see
     * yourself in your own chat list.
     */
    private void showHardcodedChats() {
        String currentUserId = sharedPreferences.getString(SharedPreferenceDetails.USERID, "");

        List<ChatListModel> staticChats =   new ArrayList<>();

        if (!TEST_USER_ID_ALICE.equals(currentUserId)) {
            staticChats.add(new ChatListModel(
                    TEST_USER_ID_ALICE,
                    TEST_USERNAME_ALICE,
                    MessagesManager.MESSAGE_OUTGOING,
                    MessagesManager.TEXT_MESSAGE,
                    "",
                    MessagesManager.MESSAGE_SYNCED_WITH_SERVER,
                    0, 0, "", false
            ));
        }

        if (!TEST_USER_ID_BOB.equals(currentUserId)) {
            staticChats.add(new ChatListModel(
                    TEST_USER_ID_BOB,
                    TEST_USERNAME_BOB,
                    MessagesManager.MESSAGE_OUTGOING,
                    MessagesManager.TEXT_MESSAGE,
                    "",
                    MessagesManager.MESSAGE_SYNCED_WITH_SERVER,
                    0, 0, "", false
            ));
        }

        chatListAdapter.submitList(staticChats);
        updateEmptyStateVisibility(staticChats.isEmpty());
    }

    /**
     * Shows or hides the empty state based on whether the chat list has items.
     *
     * @param isEmpty true to show the empty state, false to show the list
     */
    private void updateEmptyStateVisibility(boolean isEmpty) {
        if (isEmpty) {
            String  username    =   sharedPreferences.getString(SharedPreferenceDetails.USERNAME, "");
            if (!username.isEmpty()) {
                tvEmptyTitle.setText(getString(R.string.logged_in_as, username));
            }
            llEmptyState.setVisibility(View.VISIBLE);
            rvChatList.setVisibility(View.GONE);
        } else {
            llEmptyState.setVisibility(View.GONE);
            rvChatList.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Sets up click listeners for interactive elements on the chat list screen.
     */
    private void setupClickListeners() {
        fabNewChat.setOnClickListener(v -> Toast.makeText(MainActivity.this,
                "New chat feature coming soon", Toast.LENGTH_SHORT).show());
    }

    // -------------------- Chat Click Handling Starts Here ---------------------

    /**
     * Called when the user taps a chat row.
     * Opens MessagingActivity with the selected contact's user ID.
     *
     * @param chat the clicked chat list model
     */
    @Override
    public void onChatClick(ChatListModel chat) {
        Log.e(Extras.LOG_MESSAGE, "Chat clicked: " + chat.getContactName()
                + " (userId=" + chat.getContactId() + ", roomId=" + chat.getRoomId() + ")");

        Intent  intent  =   new Intent(MainActivity.this, MessagingActivity.class);
        intent.putExtra(Extras.CHAT_ID, chat.getContactId());
        intent.putExtra(Extras.CHAT_ROOM_ID, chat.getRoomId() != null ? chat.getRoomId() : "");
        intent.putExtra(Extras.CONTACT_NAME, chat.getContactName());
        startActivity(intent);
    }

    // -------------------- Lifecycle Notifications Starts Here ---------------------

    @Override
    protected void onResume() {
        super.onResume();
        MyApplication myApplication =   MyApplication.getInstance();
        if (myApplication != null && myApplication.getAppServiceLocator() != null
                && myApplication.getAppServiceLocator().getWebSocketConnection() != null) {
            myApplication.getAppServiceLocator().getWebSocketConnection().setMainActivityVisible(true);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        MyApplication myApplication =   MyApplication.getInstance();
        if (myApplication != null && myApplication.getAppServiceLocator() != null
                && myApplication.getAppServiceLocator().getWebSocketConnection() != null) {
            myApplication.getAppServiceLocator().getWebSocketConnection().setMainActivityVisible(false);
        }
    }
}

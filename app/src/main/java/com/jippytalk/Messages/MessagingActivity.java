package com.jippytalk.Messages;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.OnBackPressedDispatcher;
import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
// NOTE: do NOT import BuildConfig — Android Studio auto-imports
// androidx.multidex.BuildConfig (DEBUG is always false there) which
// silently disables the debug long-press on the chat title. The code
// below uses com.jippytalk.BuildConfig.DEBUG fully qualified instead.
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.WorkRequest;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.jippytalk.API;
import com.jippytalk.Chats.MainActivity;
import com.jippytalk.Encryption.DecryptionFailScenario;
import com.jippytalk.Encryption.KeyBundleFetcher;
import com.jippytalk.Messages.Utils.MessageUtils;
import com.jippytalk.MessagesForward.MessagesForwardListActivity;
import com.jippytalk.WebSocketConnection;
import com.jippytalk.ServiceLocators.APIServiceLocator;
import com.jippytalk.ServiceLocators.AppServiceLocator;
import com.jippytalk.Chats.DataHandlers.ChatDataRetrievalHandler;
import com.jippytalk.Chats.Repository.ChatsUIRepository;
import com.jippytalk.Common.UpdateLastMessageIdForContact;
import com.jippytalk.CommonUtils;
import com.jippytalk.Contacts.Repository.ContactsUIRepository;
import com.jippytalk.Contacts.ViewModel.ContactViewModel;
import com.jippytalk.Contacts.ViewModel.ContactViewModelFactory;
import com.jippytalk.Database.ContactsDatabase.Repository.ContactsRepository;
import com.jippytalk.Database.MessagesDatabase.Repository.ChatListRepository;
import com.jippytalk.Database.MessagesDatabase.Repository.MessagesRepository;
import com.jippytalk.Managers.AccountManager;
import com.jippytalk.Managers.ChatManager;
import com.jippytalk.Managers.MessagesManager;
import com.jippytalk.Managers.SharedPreferenceDetails;
import com.jippytalk.Encryption.MessageEncryptAndDecrypt;
import com.jippytalk.MessageInfo.MessageInfoActivity;
import com.jippytalk.Messages.Adapter.MessageAdapter;
import com.jippytalk.Messages.Datahandlers.MessagingActivityDataRetrieval;
import com.jippytalk.Messages.Model.ContactDetailsModel;
import com.jippytalk.Messages.Model.MessageInsertionModel;
import com.jippytalk.Messages.Model.MessageModal;
import com.jippytalk.Messages.ViewModel.MessagesViewModel;
import com.jippytalk.Messages.ViewModel.MessagesViewModelFactory;
import com.jippytalk.MyApplication;
import com.jippytalk.OnlineOfflineDetectorActivity;
import com.jippytalk.ContactProfile.ContactProfileActivity;
import com.jippytalk.Extras;
import com.jippytalk.R;
import com.jippytalk.SearchMessages.SearchMessagesActivity;
import com.jippytalk.ServiceLocators.RepositoryServiceLocator;
import com.jippytalk.Sessions.SessionCreationWorkManager;
import com.jippytalk.databinding.ActivityMessagingBinding;
import com.jippytalk.databinding.SelectedReceiveMsgBinding;
import com.jippytalk.databinding.SelectedSentMsgBinding;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.jippytalk.Common.AttachmentBottomSheet;
import com.jippytalk.Messages.Attachment.AttachmentCryptoHelper;
import com.jippytalk.Messages.Attachment.AttachmentPickerHandler;
import com.jippytalk.Messages.Attachment.MediaTransferManager;
import com.jippytalk.Messages.Attachment.Model.AttachmentModel;


import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class MessagingActivity extends OnlineOfflineDetectorActivity implements
        MessageAdapter.OnMessageItemClickedListener, WebSocketConnection.MessageCallBacks {

    private ActivityMessagingBinding            activityMessagingBinding;
    private MyApplication                       myApplication;
    private WebSocketConnection                 webSocketConnection;
    private String                              contactId, userId, contactName;
    private String                              roomId;
    private String                              repliedToMessageId;
    private int                                 checkedItem, messageType;
    private SharedPreferences                   sharedPreferences;
    private MessageAdapter                      adapter;
    private Animation                           blink_animation;
    private Handler                             typingHandler;
    private MessagingActivityDataRetrieval      messagingActivityDataRetrieval;
    private MessagesViewModelFactory            messagesViewModelFactory;
    private ContactViewModelFactory             contactViewModelFactory;
    private AppServiceLocator                   appServiceLocator;
    private APIServiceLocator                   apiServiceLocator;
    private RepositoryServiceLocator            repositoryServiceLocator;
    private MessagesViewModel                   messagesViewModel;
    private ContactViewModel                    contactViewModel;
    private DecryptionFailScenario              decryptionFailScenario;
    private MessageEncryptAndDecrypt            messageEncryptAndDecrypt;
    private ContactsUIRepository                contactsUIRepository;
    private ChatsUIRepository                   chatsUIRepository;
    private ContactsRepository                  contactsRepository;
    private MessagesRepository                  messagesRepository;
    private ChatListRepository                  chatListRepository;
    private LinearLayoutManager                 linearLayoutManager;
    private MessageUtils                        messageUtils;
    private boolean                             messageReadReceipts;
    private int                                 archiveStatus                       =   ChatManager.UNKNOWN_CHAT;
    private int                                 contactDeviceId                     =   -1;
    private final long                          typingDelay                         =   2000;
    private boolean                             msgToMsgReplyCardVisible            =   false;
    private boolean                             needScrollToBottom                  =   true;
    private final ExecutorService               executorService                     = Executors.newFixedThreadPool(4);

    // ---- Attachment Handler (registered before onCreate for launcher lifecycle) ----

    private final AttachmentPickerHandler       attachmentPickerHandler             =   new AttachmentPickerHandler(this);
    private MediaTransferManager                mediaTransferManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        EdgeToEdge.enable(this);
        activityMessagingBinding                        =   ActivityMessagingBinding.inflate(getLayoutInflater());
        setContentView(activityMessagingBinding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            int statusBarTop = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            activityMessagingBinding.appBar.setPadding(0, statusBarTop, 0, 0);
            int imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
            int navBarBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            activityMessagingBinding.rlDown.setPadding(0, 0, 0, Math.max(imeBottom, navBarBottom));
            activityMessagingBinding.rlUnknownMsg.setPadding(0, 0, 0, Math.max(imeBottom, navBarBottom));
            return insets;
        });

        setSupportActionBar(activityMessagingBinding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        activityMessagingBinding.rvChats.addOnLayoutChangeListener((v, left, top, right, bottom,
                                                                    oldLeft, oldTop, oldRight, oldBottom) -> {
            if (bottom < oldBottom) {
                activityMessagingBinding.rvChats.postDelayed(() -> activityMessagingBinding.rvChats.scrollToPosition(adapter.getItemCount() - 1), 100);
            }
        });


        if (getIntent().hasExtra(Extras.CHAT_ID)) {
            contactId = getIntent().getStringExtra(Extras.CHAT_ID);
        }

        if (getIntent().hasExtra(Extras.CHAT_ROOM_ID)) {
            roomId = getIntent().getStringExtra(Extras.CHAT_ROOM_ID);
        }

        if (contactId == null) {
            Toast.makeText(this, "Unable to fetch details internal issue", Toast.LENGTH_SHORT).show();
            return;
        }

        // Fetch server-side message history for this room
        fetchRemoteMessageHistory();

        checkMyApplicationInitialization();

        activityMessagingBinding.rvChats.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                hideOrDisplayDownArrow();
            }
        });

        typingHandler = new Handler(Looper.getMainLooper());
        activityMessagingBinding.etMessage.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int start, int before, int count) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int start, int before, int count) {
                typingHandler.removeCallbacks(typingRunnable);
                if (webSocketConnection != null) {
                    webSocketConnection.senderIsTyping(userId, contactId, true, "typing");
                }
            }
            @Override
            public void afterTextChanged(Editable editable) {
                typingHandler.postDelayed(typingRunnable, typingDelay);
            }
        });
    }

    private void checkMyApplicationInitialization() {
        myApplication           =   MyApplication.getInstance();
        myApplication.getInitializationState().observe(this, aBoolean -> {
            if (aBoolean) {
                setUpAllMethods();
            }
        });
    }

    private void setUpAllMethods() {
        defineNecessaryMethods();
        setUpAnimations();
        setUpClickListeners();
        setUpAttachmentCallback();
        defineLiveDataObservers();
        itemTouchHelperForMessagesSwipe();

        // DEBUG: auto-refresh the plaintext mirror of messages.db every time
        // a chat opens, so Android Studio's Database Inspector always shows
        // up-to-date rows without the user having to long-press. The DEBUG
        // gate is inside debugExportMessagesDb itself — no-op in release.
        // Delayed 300ms so the chat UI finishes laying out first (the export
        // runs on a background executor anyway, this is just defensive).
        activityMessagingBinding.getRoot().postDelayed(() ->
                MyApplication.getInstance().debugExportMessagesDb(), 300);
    }

    private void defineNecessaryMethods() {
        appServiceLocator                       =   myApplication.getAppServiceLocator();
        apiServiceLocator                       =   myApplication.getAPIServiceLocator();
        repositoryServiceLocator                =   myApplication.getRepositoryServiceLocator();
        webSocketConnection                     =   appServiceLocator.getWebSocketConnection();
        sharedPreferences                       =   repositoryServiceLocator.getSharedPreferences();
        messagingActivityDataRetrieval          =   new MessagingActivityDataRetrieval(MessagingActivity.this);
        messagesRepository                      =   repositoryServiceLocator.getMessagesRepository();
        chatListRepository                      =   repositoryServiceLocator.getChatListRepository();
        contactsRepository                      =   repositoryServiceLocator.getContactsRepository();
        chatsUIRepository                       =   repositoryServiceLocator.getChatsUIRepository();
        contactsUIRepository                    =   repositoryServiceLocator.getContactsUIRepository();
        decryptionFailScenario                  =   apiServiceLocator.getDecryptionFailScenario();
        messageEncryptAndDecrypt                =   MessageEncryptAndDecrypt.getInstance(MessagingActivity.this,
                                                    appServiceLocator, decryptionFailScenario);
        blink_animation                         =   AnimationUtils.loadAnimation(MessagingActivity.this, R.anim.blink);
        messagesViewModelFactory                =   new MessagesViewModelFactory(messagesRepository, chatListRepository,
                                                    chatsUIRepository, appServiceLocator.getMessagesUIRepository());
        contactViewModelFactory                 =   new ContactViewModelFactory(contactsRepository, chatListRepository,
                                                    chatsUIRepository, contactsUIRepository);
        messagesViewModel                       =   new ViewModelProvider(MessagingActivity.this, messagesViewModelFactory)
                                                    .get(MessagesViewModel.class);
        contactViewModel                        =  new ViewModelProvider(MessagingActivity.this, contactViewModelFactory)
                                                    .get(ContactViewModel.class);
        messageUtils                            =   new MessageUtils(MessagingActivity.this, repositoryServiceLocator.getSharedPreferences());
        adapter                                 =   new MessageAdapter(MessagingActivity.this,
                                    MessagingActivity.this, repositoryServiceLocator.getSharedPreferences(),
                                                        messageUtils);
        linearLayoutManager                     =   new LinearLayoutManager(MessagingActivity.this);
        activityMessagingBinding.rvChats.setItemAnimator(null);
        activityMessagingBinding.rvChats.setAnimation(null);
        activityMessagingBinding.rvChats.setLayoutManager(linearLayoutManager);
        adapter.setHasStableIds(true);
        activityMessagingBinding.rvChats.setAdapter(adapter);
        adapter.submitList(new ArrayList<>());

        // Initialize media transfer manager and wire to adapter
        mediaTransferManager    =   new MediaTransferManager(MessagingActivity.this);
        mediaTransferManager.setAdapter(adapter);
    }

    private void setUpAnimations() {
        activityMessagingBinding.alert2.startAnimation(blink_animation);
        activityMessagingBinding.alert1.startAnimation(blink_animation);
        activityMessagingBinding.tvUserBusy.startAnimation(blink_animation);
    }


    private void setUpClickListeners() {
        onBackPress();
        sendMessageClickListener();
        activityMessagingBinding.lluserBusy.setOnClickListener(view -> InfoAlertDialog());
        activityMessagingBinding.ivProfileImage.setOnClickListener(view -> goToContactProfile(contactId));
        activityMessagingBinding.toolbar.setOnClickListener(view -> goToContactProfile(contactId));
        activityMessagingBinding.ivBackArrow.setOnClickListener(view -> finish());

        // DEBUG: long-press anywhere in the toolbar (title or empty space) to
        // export a plaintext copy of messages.db into /data/data/com.jippytalk/
        // databases/ so Android Studio's Database Inspector can open it.
        // The listener is attached to BOTH the title TextView and the Toolbar
        // itself so long-press works even if one intercepts the touch. The
        // DEBUG gate is inside debugExportMessagesDb itself — keep it here
        // commented out so the listener always wires up regardless of build
        // flavor. Remove before shipping a release build.
        android.view.View.OnLongClickListener debugExportLongClick = view -> {
            Log.e(Extras.LOG_MESSAGE, "debug: long-press captured on "
                    + view.getClass().getSimpleName());
            MyApplication.getInstance().debugExportMessagesDb();
            android.widget.Toast.makeText(this,
                    "Exporting plaintext messages.db — watch logcat (tag: LOG_MESSAGE)",
                    android.widget.Toast.LENGTH_LONG).show();
            return true;  // consume so the normal click doesn't fire
        };
        activityMessagingBinding.ivProfileImage.setLongClickable(true);
        activityMessagingBinding.toolbarTitle.setOnLongClickListener(debugExportLongClick);
        activityMessagingBinding.toolbar.setLongClickable(true);
        activityMessagingBinding.toolbar.setOnLongClickListener(debugExportLongClick);

        activityMessagingBinding.cvAcceptChat.setOnClickListener(view ->
                messagesViewModel.acceptedContactChat(contactId));

        activityMessagingBinding.cvDeclineChat.setOnClickListener(view -> {
            messagesViewModel.declinedContactChat(contactId);
            startActivity(new Intent(MessagingActivity.this, MainActivity.class));
            finish();
        });

        activityMessagingBinding.ivClearBtn.setOnClickListener(view -> {
            activityMessagingBinding.cvMsgToMsgReply.setVisibility(GONE);
            activityMessagingBinding.ivReplyPreviewThumbnail.setVisibility(GONE);
            msgToMsgReplyCardVisible    =   false;
        });

        activityMessagingBinding.cvDownButton.setOnClickListener(view -> {
            activityMessagingBinding.rvChats.scrollToPosition(adapter.getItemCount() - 1);
            activityMessagingBinding.cvDownButton.setVisibility(GONE);
        });

        activityMessagingBinding.ivAttachment.setOnClickListener(view -> showAttachmentBottomSheet());
    }

    private void onBackPress() {
        OnBackPressedDispatcher onBackPressedDispatcher =   getOnBackPressedDispatcher();
        OnBackPressedCallback   onBackPressedCallback   =   new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finish();
            }
        };
        onBackPressedDispatcher.addCallback(this, onBackPressedCallback);
    }

    private void sendMessageClickListener() {
        activityMessagingBinding.cvSendButton.setOnClickListener(v -> {
            int     isReply;
            String  uniqueMessageId     =   UUID.randomUUID().toString().trim().replace("-","");
            String  textMessage         =   activityMessagingBinding.etMessage.getText().toString().trim();
            String  replyToId           =   repliedToMessageId;

            if (textMessage.isEmpty()) {
                Toast.makeText(MessagingActivity.this, "type a message to send", Toast.LENGTH_SHORT).show();
                return;
            }

            if (msgToMsgReplyCardVisible) {
                isReply =   MessagesManager.REPLIED_TO_A_MSG;
            } else {
                isReply = MessagesManager.DEFAULT_MSG_TO_MSG_REPLY;
                repliedToMessageId  =   "";
            }

            if (CommonUtils.isMessageALink(textMessage)) {
                messageType =   MessagesManager.LINK_MESSAGE;
            } else {
                messageType =   MessagesManager.TEXT_MESSAGE;
            }
            // User just hit send — follow the new row to the bottom on the
            // next LiveData refresh. The flag self-clears after one use so
            // it doesn't keep dragging scroll later.
            needScrollToBottom  =   true;
            onSendMessageClick(uniqueMessageId, textMessage, isReply, replyToId, messageType);
        });
    }

    private void defineLiveDataObservers() {
        userIdAndReadReceiptsLiveDataObserver();
        lastVisitPrivacyOptionLiveDataObserver();
        contactDetailsLiveDataObserver();
        contactMessagesLiveDataObserver();
        acceptContactChatLiveDataObserver();
        contactChatArchiveStatusLiveDataObserver();
        contactMessageStatusUpdatesLiveDataObserver();
        contactReadReceiptsStatusLiveDataObserver();
        unSyncSeenMessagesLiveDataObserver();
        unKnownMessagesLiveDataObserver();
        deleteMessageLiveDataObserver();
        starredMessageLiveDataObserver();
        messageInsertionLiveDataObserver();
        contactAccountStatusLiveDataObserver();
    }

    private void userIdAndReadReceiptsLiveDataObserver() {
        messagesViewModel.getUserIdAndReadReceiptsStatusFromViewModel().observe(this,
                stringBooleanPair -> {
            if (stringBooleanPair.first != null) {
                userId                  =   stringBooleanPair.first;
            }

            if (stringBooleanPair.second != null) {
                messageReadReceipts     =   stringBooleanPair.second;
            }
        });
    }

    private void lastVisitPrivacyOptionLiveDataObserver() {
        messagesViewModel.getLastVisitPrivacyOptionFromViewModel().observe(this, integer -> {
            if (integer == AccountManager.MY_CONTACTS) {
                if (webSocketConnection != null && contactId != null && WebSocketConnection.isConnectedToSocket) {
                    webSocketConnection.getContactOnlineStatus(contactId);
                } else {
                    activityMessagingBinding.toolbarSubtitle.setVisibility(GONE);
                }
            } else {
               activityMessagingBinding.toolbarSubtitle.setVisibility(GONE);
            }
        });
    }

    private void contactMessagesLiveDataObserver() {
        messagesViewModel.getMessagesFromViewModel().observe(this, messageModalArrayList -> {
            if (messageModalArrayList == null || messageModalArrayList.isEmpty()) {
                Log.e(Extras.LOG_MESSAGE,"unable to scroll due to null or empty message list");
                adapter.submitList(new ArrayList<>());
                activityMessagingBinding.cvStartChat.setVisibility(VISIBLE);
                activityMessagingBinding.rvChats.setVisibility(GONE);
                return;
            }
            activityMessagingBinding.rvChats.setVisibility(VISIBLE);
            activityMessagingBinding.cvStartChat.setVisibility(GONE);
            // Snapshot current scroll position before submitList so we can
            // detect "user is reading older messages" and avoid yanking
            // them to the bottom on every DB-driven refresh (thumbnail
            // persist, local-file-path persist, S3-key persist all fire
            // LiveData → submitList → here).
            final boolean wasNearBottom = isNearBottomOfChat();
            adapter.submitList(new ArrayList<>(messageModalArrayList), () -> {
                // Two conditions to scroll:
                //   (1) explicit needScrollToBottom flag (set on initial
                //       load + when WE just sent a fresh message), OR
                //   (2) user was already near the bottom when the refresh
                //       fired (so it's safe to follow new content).
                // If the user has scrolled up (wasNearBottom=false), DON'T
                // yank them away — they're reading history.
                if (needScrollToBottom || wasNearBottom) {
                    scrollToLastItem();
                }
                // Sticky flag flips OFF once consumed so subsequent
                // unrelated refreshes (thumbnail/path persist) don't keep
                // forcing scroll-to-bottom on every tick.
                needScrollToBottom = false;
            });

            // Send mark_read for any incoming messages that are DELIVERED but not yet
            // SEEN. The chat is visible so the user has "read" them.
            sendMarkReadForVisibleMessages(messageModalArrayList);

            // Receiver side: auto-download + decrypt encrypted thumbnails so the
            // adapter can display them locally (Glide can't render encrypted
            // bytes from a raw URL).
            autoFetchEncryptedThumbnails(messageModalArrayList);

            // DEBUG: re-export the plaintext mirror so Android Studio's
            // Database Inspector sees the freshly-inserted / updated rows
            // without the user having to leave and re-enter the chat.
            // Throttled so a burst of 20 WS messages in 500ms doesn't
            // trigger 20 full DB exports in a row.
            maybeRefreshDebugPlainDb();
        });
    }

    // Throttle window for the auto-refresh of messages_plain.db. 1.5 seconds
    // is enough to coalesce a burst of rapid-fire status updates (WS
    // delivery_status + read receipt batch) into a single export.
    private static final long DEBUG_EXPORT_MIN_INTERVAL_MS = 1500L;
    private long lastDebugExportMs = 0L;
    private final java.util.concurrent.atomic.AtomicBoolean debugExportPending =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    /**
     * DEBUG: schedules a refresh of messages_plain.db if we haven't exported
     * in the last DEBUG_EXPORT_MIN_INTERVAL_MS. If a refresh is called while
     * another one is "pending", the trailing call is dropped — the pending
     * one will eventually pick up whatever state the DB reaches.
     *
     * This gets called from the messages observer (fires on every DB write),
     * so the Inspector's view stays in sync with messages.db without manual
     * intervention. No-op outside debug builds because
     * debugExportMessagesDb itself is gated on BuildConfig.DEBUG.
     */
    private void maybeRefreshDebugPlainDb() {
        long now = System.currentTimeMillis();
        long elapsed = now - lastDebugExportMs;
        if (elapsed >= DEBUG_EXPORT_MIN_INTERVAL_MS) {
            lastDebugExportMs = now;
            MyApplication.getInstance().debugExportMessagesDb();
        } else if (debugExportPending.compareAndSet(false, true)) {
            // Schedule one trailing-edge export to pick up writes that
            // arrived during the throttle window.
            long delay = DEBUG_EXPORT_MIN_INTERVAL_MS - elapsed + 50;
            activityMessagingBinding.getRoot().postDelayed(() -> {
                debugExportPending.set(false);
                lastDebugExportMs = System.currentTimeMillis();
                MyApplication.getInstance().debugExportMessagesDb();
            }, delay);
        }
        // else: a trailing export is already scheduled → do nothing
    }

    private void contactChatArchiveStatusLiveDataObserver() {
        messagesViewModel.getContactChatArchiveStatusFromViewModel().observe(this, new Observer<Integer>() {
            @Override
            public void onChanged(Integer integer) {
                archiveStatus   =   integer;
                if (integer == ChatManager.UNARCHIVE_CHAT || integer == ChatManager.ARCHIVE_CHAT) {
                    activityMessagingBinding.rlDown.setVisibility(VISIBLE);
                    activityMessagingBinding.rlUnknownMsg.setVisibility(View.GONE);
                }
                else if(integer == ChatManager.UNKNOWN_CHAT) {
                    activityMessagingBinding.rlDown.setVisibility(VISIBLE);
                    activityMessagingBinding.rlUnknownMsg.setVisibility(VISIBLE);
                }
                messagesViewModel.getContactReadReceiptsStatusForMessageScreen(contactId);
            }
        });
    }

    private void acceptContactChatLiveDataObserver() {
        messagesViewModel.getAcceptedContactChatStatusFromViewModel().observe(this, aBoolean -> {
            if (aBoolean) {
                activityMessagingBinding.rlDown.setVisibility(VISIBLE);
                activityMessagingBinding.rlUnknownMsg.setVisibility(View.GONE);
                messagesViewModel.getUnSyncSeenMessagesForContact(contactId);
            } else {
                Toast.makeText(MessagingActivity.this, "Failed to accept chat", Toast.LENGTH_SHORT).show();
                activityMessagingBinding.rlDown.setVisibility(VISIBLE);
                activityMessagingBinding.rlUnknownMsg.setVisibility(VISIBLE);
            }
        });
    }

    private void contactMessageStatusUpdatesLiveDataObserver() {
        messagesViewModel.getMessageStatusUpdatesFromViewModel().observe(this, messageModals -> {
            if (messageModals == null || messageModals.isEmpty()) {
                Log.e(Extras.LOG_MESSAGE,"unable to scroll due to null or empty message list");
                return;
            }
            adapter.submitList(new ArrayList<>(messageModals));
        });
    }

    private void contactReadReceiptsStatusLiveDataObserver() {
        messagesViewModel.getChatLockAndReadReceiptStatusLiveData().observe(this,
                integerIntegerPair -> {
            if (integerIntegerPair == null) {
                Log.e(Extras.LOG_MESSAGE,"Unable to get contact read receipt status");
                return;
            }
            int contactReadReceiptsStatus   =   integerIntegerPair.second;
            Log.e(Extras.LOG_MESSAGE,"read receipts are " + contactReadReceiptsStatus + " archive status is " + archiveStatus);
            if (contactReadReceiptsStatus == ChatManager.READ_RECEIPTS_ON && messageReadReceipts &&
                    (archiveStatus == ChatManager.ARCHIVE_CHAT || archiveStatus == ChatManager.UNARCHIVE_CHAT)) {
                messagesViewModel.getUnSyncSeenMessagesForContact(contactId);
            }
        });
    }

    private void unSyncSeenMessagesLiveDataObserver() {
        messagesViewModel.getUnSyncSeenMessagesFromViewModel().observe(this,
                unSyncedSeenMessagesModelArrayList -> {
            if (webSocketConnection != null && unSyncedSeenMessagesModelArrayList != null) {
                webSocketConnection.updateReceivedMessageAsDeliveredAndSeen(unSyncedSeenMessagesModelArrayList);
            }
        });
    }

    private void unKnownMessagesLiveDataObserver() {
        messagesViewModel.getUnKnownMessagesFromViewModel().observe(this,
                unSyncedSeenMessagesModelArrayList -> {
            if (webSocketConnection != null && unSyncedSeenMessagesModelArrayList != null) {
                webSocketConnection.updateReceivedMessageAsDeliveredAndSeen(unSyncedSeenMessagesModelArrayList);
            }
        });
    }

    private void messageInsertionLiveDataObserver() {
        messagesViewModel.getMessageInsertionStatusFromViewModel().observe(this,
                messageInsertionModel -> {
            if (messageInsertionModel != null) {
                activityMessagingBinding.etMessage.getText().clear();
                msgToMsgReplyCardVisible    =   false;
                activityMessagingBinding.cvMsgToMsgReply.setVisibility(GONE);
                activityMessagingBinding.ivReplyPreviewThumbnail.setVisibility(GONE);
                messagesViewModel.retrieveAllMessagesOfContact(contactId);
                if (contactId.equals(userId)) {
                    Log.e(Extras.LOG_MESSAGE,"User Sending Message to himself");
                    return;
                }
                // ENCRYPTION TEMPORARILY DISABLED — backend key bundle endpoint not ready.
                // Skip Signal Protocol entirely and send plaintext via the new WS envelope.
                // Once /api/keys/bundle/{userId} is live, re-enable the block below.
                //
                // if (contactDeviceId != -1 && contactDeviceId != 0) {
                //     signalProtocolAddress = new SignalProtocolAddress(contactId, contactDeviceId);
                //     if (customSignalProtocolStore.containsSession(signalProtocolAddress)) {
                //         encryptMessageAndSendToServer(messageInsertionModel);
                //     } else {
                //         fetchBundleAndSend(messageInsertionModel);
                //     }
                // } else {
                //     fetchBundleAndSend(messageInsertionModel);
                // }
                Log.e(Extras.LOG_MESSAGE, "Sending text as plaintext (encryption disabled)");
                sendPlaintextFallback(messageInsertionModel);
            }
        });
    }

    private void contactDetailsLiveDataObserver() {
        contactViewModel.getContactDetailsForChatScreenFromLiveModel().observe(this,
                contactDetailsModel -> {
            if (contactDetailsModel != null) {
                setContactDetails(contactDetailsModel);
            }
        });
    }

    private void contactAccountStatusLiveDataObserver() {
        messagesViewModel.getUserAccountStatusLiveData().observe(this, integer -> {
            if (integer == null) {
                Log.e(Extras.LOG_MESSAGE,"unable to display user status due to null value");
                return;
            }
            if (integer == AccountManager.BUSY) {
                activityMessagingBinding.lluserBusy.setVisibility(VISIBLE);
            }
            else {
                activityMessagingBinding.lluserBusy.setVisibility(GONE);
            }
        });
    }

    private void deleteMessageLiveDataObserver() {
        messagesViewModel.getMessageDeleteLiveStatusFromViewModel().observe(this, integer -> {
            if (integer == null) {
                Log.e(Extras.LOG_MESSAGE,"Unable to delete due to null value");
                Toast.makeText(MessagingActivity.this, "Failed to delete" +
                        "message", Toast.LENGTH_SHORT).show();
                return;
            }
            if (integer == MessagesManager.MESSAGE_DELETED_SUCCESSFULLY) {
                Log.e(Extras.LOG_MESSAGE,"Message deteleted");
                needScrollToBottom  =   false;
                messagesViewModel.retrieveAllMessagesOfContact(contactId);
                UpdateLastMessageIdForContact updateLastMessageIdForContact   =   new
                        UpdateLastMessageIdForContact(getApplicationContext());
                updateLastMessageIdForContact.updateContactLastMessageId(contactId);
            }
            else {
                Toast.makeText(MessagingActivity.this, "Unable to delete message", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void starredMessageLiveDataObserver() {
        messagesViewModel.getStarredMessageStatusFromLiveData().observe(this, integer -> {
            if (integer == null) {
                Log.e(Extras.LOG_MESSAGE,"Unable to star/unstar message due to null value");
                return;
            }
            needScrollToBottom  =   false;
            messagesViewModel.retrieveAllMessagesOfContact(contactId);
        });
    }

    private void itemTouchHelperForMessagesSwipe() {
        ItemTouchHelper.SimpleCallback swipeToReplyCallback = new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder,
                                  @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getBindingAdapterPosition();
                adapter.notifyItemChanged(position);
                activityMessagingBinding.rvChats.post(() -> {
                    RecyclerView.ViewHolder vh = activityMessagingBinding.rvChats.findViewHolderForAdapterPosition(position);
                    if (vh != null) {
                        vh.itemView.setTranslationX(0);
                    }
                });

                Vibrator vibrator = (Vibrator)getSystemService(Context.VIBRATOR_SERVICE);
                if (vibrator != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE));
                } else if (vibrator != null) {
                    vibrator.vibrate(50);
                }

                if (position != RecyclerView.NO_POSITION && position < adapter.getCurrentList().size()) {
                    MessageModal    swipedMessage   =   adapter.getCurrentList().get(position);
                    msgToMsgReplyCardVisible        =   true;
                    activityMessagingBinding.cvMsgToMsgReply.setVisibility(VISIBLE);
                    repliedToMessageId              =   swipedMessage.getMessageId();

                    // Set reply text or media type label
                    int     swipedMessageType       =   swipedMessage.getMessageType();
                    String  replyLabel              =   MessageUtils.getReplyLabel(
                            MessagingActivity.this, swipedMessageType, swipedMessage.getMessage());
                    activityMessagingBinding.tvselectedMsg.setText(replyLabel);

                    // Show thumbnail for image/video replies
                    String  swipedMediaUri  =   swipedMessage.getMediaUri();
                    if (MessageUtils.isMediaTypeWithThumbnail(swipedMessageType)
                            && swipedMediaUri != null && !swipedMediaUri.isEmpty()) {
                        activityMessagingBinding.ivReplyPreviewThumbnail.setVisibility(View.VISIBLE);
                        Glide.with(MessagingActivity.this)
                                .load(swipedMediaUri)
                                .placeholder(R.drawable.no_profile)
                                .into(activityMessagingBinding.ivReplyPreviewThumbnail);
                    } else {
                        activityMessagingBinding.ivReplyPreviewThumbnail.setVisibility(View.GONE);
                    }

                    if (swipedMessage.getMessageDirection() == MessagesManager.MESSAGE_INCOMING) {
                        activityMessagingBinding.msgSenderName.setText(contactName);
                    }
                    else {
                        activityMessagingBinding.msgSenderName.setText(R.string.you);
                    }

                    activityMessagingBinding.etMessage.requestFocus();

                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.showSoftInput(activityMessagingBinding.etMessage, InputMethodManager.SHOW_IMPLICIT);
                    }
                }
            }

            @Override
            public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);
                viewHolder.itemView.setTranslationX(0);
            }


            @Override
            public float getSwipeThreshold(@NonNull RecyclerView.ViewHolder viewHolder) {
                return 0.15f;
            }

            public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView,
                                    @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY,
                                    int actionState, boolean isCurrentlyActive) {
                float maxSwipe = viewHolder.itemView.getWidth() * 1f;
                float limitedDX = Math.min(dX, maxSwipe);
                super.onChildDraw(c, recyclerView, viewHolder, limitedDX, dY, actionState, isCurrentlyActive);
            }
        };

        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(swipeToReplyCallback);
        itemTouchHelper.attachToRecyclerView(activityMessagingBinding.rvChats);
    }


    private void setContactDetails(ContactDetailsModel contactDetailsModel) {
        contactName         =   contactDetailsModel.getContactPhoneNumber();
        contactDeviceId     =   contactDetailsModel.getContactDeviceId();

        Log.e(Extras.LOG_MESSAGE,"retrieval contact device id is " + contactDeviceId + " " + contactName);

        if (contactName == null || contactName.isEmpty()) {
            activityMessagingBinding.toolbarTitle.setText("Deleted Account");
        } else {
            activityMessagingBinding.toolbarTitle.setText(contactName);
        }


        int profilePicPrivacy   =   sharedPreferences.getInt(SharedPreferenceDetails.PROFILE_PIC_PRIVACY_OPTION,
                                    AccountManager.MY_CONTACTS);

        if (profilePicPrivacy == AccountManager.NO_ONE) {
            Log.e(Extras.LOG_MESSAGE,"Profile pic is set to none");
            activityMessagingBinding.ivProfileImage.setImageResource(R.drawable.no_profile);
            return;
        }

        String profilePicId = contactDetailsModel.getContactProfilePicId();

        if (profilePicId == null || profilePicId.isEmpty()) {
            Log.e(Extras.LOG_MESSAGE, "Profile pic id is null, clearing ImageView");
            Glide.with(this).clear(activityMessagingBinding.ivProfileImage);
            activityMessagingBinding.ivProfileImage.setImageResource(R.drawable.no_profile);
        } else {
            String imageUrl = API.GET_CONTACT_PROFILE_PIC + profilePicId;
            Glide.with(MessagingActivity.this)
                    .load(imageUrl)
                    .placeholder(R.drawable.no_profile)
                    .error(R.drawable.no_profile)
                    .into(activityMessagingBinding.ivProfileImage);
        }
    }

    private void onSendMessageClick(String messageId, String textMessage, int isReply, String replyToMessageId,
                                    int messageType) {
        int messageStatus   =   MessagesManager.MESSAGE_NOT_SYNCED_WITH_SERVER;

        if (contactId.equals(userId)) {
            messageStatus   =   MessagesManager.MESSAGE_SYNCED_WITH_SERVER;
        }
        messagesViewModel.insertMessageIntoLocalDatabase(
                messageId, MessagesManager.MESSAGE_OUTGOING, contactId,
                textMessage, messageStatus, MessagesManager.NEED_MESSAGE_PUSH,
                System.currentTimeMillis(), MessagesManager.DEFAULT_DELIVERED_TIMESTAMP, MessagesManager.DEFAULT_READ_TIMESTAMP,
                MessagesManager.MESSAGE_NOT_STARRED, MessagesManager.MESSAGE_NOT_EDITED,
                messageType, MessagesManager.DEFAULT_LATITUDE, MessagesManager.DEFAULT_LONGITUDE,
                isReply, replyToMessageId, ChatManager.UNARCHIVE_CHAT);
    }

    /**
     * Fetches the remote message history for this room from the server via
     * GET /api/messages/{roomId}?limit=50&cursor=.
     *
     * For each returned message:
     *   - Inserts directly into the local MessagesDatabase via the DAO (bypassing
     *     the ViewModel's insertion LiveData — we don't want to re-send these to the server)
     *   - The DAO's INSERT OR IGNORE behavior auto-skips duplicates by message_id
     *
     * After all server messages are inserted, triggers a re-read of the DB via
     * retrieveAllMessagesOfContact() which refreshes the adapter.
     */
    private void fetchRemoteMessageHistory() {
        if (roomId == null || roomId.isEmpty()) {
            Log.e(Extras.LOG_MESSAGE, "Skipping message history fetch — no roomId");
            return;
        }

        com.jippytalk.Messages.Api.MessagesApi.getInstance(MessagingActivity.this)
                .fetchMessages(roomId, "", new com.jippytalk.Messages.Api.MessagesApi.MessagesCallback() {
                    @Override
                    public void onSuccess(com.jippytalk.Messages.Api.MessagesApi.MessagesPage page) {
                        Log.e(Extras.LOG_MESSAGE, "Fetched " + page.messages.size()
                                + " messages for room " + roomId
                                + " (next_cursor=" + page.nextCursor + ")");
                        insertServerMessagesIntoLocalDb(page.messages);
                    }

                    @Override
                    public void onFailure(String error) {
                        Log.e(Extras.LOG_MESSAGE, "Failed to fetch message history: " + error);
                    }
                });
    }

    /**
     * Inserts server-fetched messages directly into the local MessagesDatabase via the
     * DAO, bypassing the ViewModel's insertion LiveData. This avoids triggering the
     * send-to-server observer path (which would try to encrypt + re-send these messages).
     *
     * Duplicate detection: the DAO's underlying SQLite UNIQUE constraint on message_id
     * silently ignores rows that already exist, so calling this multiple times is safe.
     *
     * After all inserts, refreshes the adapter by re-querying the local DB.
     *
     * @param serverMessages the list of messages returned from GET /api/messages/{roomId}
     */
    private void insertServerMessagesIntoLocalDb(
            java.util.List<com.jippytalk.Messages.Api.MessagesApi.ServerMessage> serverMessages) {

        if (serverMessages == null || serverMessages.isEmpty()) {
            Log.e(Extras.LOG_MESSAGE, "No server messages to insert");
            return;
        }

        executorService.execute(() -> {
            com.jippytalk.Database.MessagesDatabase.MessagesDatabaseDAO dao =
                    MyApplication.getInstance().getDatabaseServiceLocator().getMessagesDatabaseDAO();

            // The server returns messages newest-first, but the local DB orders
            // messages by `sqlite_message_id ASC` (insert order) — so to get the
            // oldest-at-top / newest-at-bottom layout we need to insert in
            // chronological order (oldest first).
            java.util.List<com.jippytalk.Messages.Api.MessagesApi.ServerMessage> sortedMessages =
                    new java.util.ArrayList<>(serverMessages);
            java.util.Collections.sort(sortedMessages, (a, b) -> {
                long ta = parseIso8601ToMillis(a.createdAt);
                long tb = parseIso8601ToMillis(b.createdAt);
                return Long.compare(ta, tb);
            });

            int insertedCount = 0;
            int updatedCount  = 0;
            for (com.jippytalk.Messages.Api.MessagesApi.ServerMessage msg : sortedMessages) {
                try {
                    // Figure out direction: outgoing if I sent it, incoming otherwise
                    int direction = msg.senderId.equals(userId)
                            ? MessagesManager.MESSAGE_OUTGOING
                            : MessagesManager.MESSAGE_INCOMING;

                    // The "receiver_id" column in the local DB is really the chat partner ID
                    // — always the OTHER user regardless of direction
                    String chatPartnerId = direction == MessagesManager.MESSAGE_OUTGOING
                            ? msg.receiverId   // I sent → partner is receiver
                            : msg.senderId;    // they sent → partner is sender

                    // For outgoing messages: we already have them locally from
                    // insertLocalOutgoingAttachmentRow / messageInsertionLiveDataObserver.
                    // If the backend returns client_message_id, use it to match the local
                    // row (CONFLICT_IGNORE deduplicates). If not, SKIP the row entirely
                    // to avoid creating a duplicate with the server UUID.
                    String dbMessageId;
                    if (direction == MessagesManager.MESSAGE_OUTGOING) {
                        if (msg.clientMessageId != null && !msg.clientMessageId.isEmpty()) {
                            dbMessageId = msg.clientMessageId;
                        } else {
                            // No client_message_id from backend → skip outgoing messages
                            // to avoid duplicates. Our local row already has the correct data.
                            Log.e(Extras.LOG_MESSAGE, "Skipping outgoing msg from REST (no client_message_id): " + msg.id);
                            continue;
                        }
                    } else {
                        dbMessageId = msg.id;
                    }

                    long timestamp = parseIso8601ToMillis(msg.createdAt);

                    // Parse read_at into a real timestamp so read_timestamp
                    // column isn't always 0 on REST-fetched rows. Returns 0
                    // if the field is null / empty / unparseable (safe default).
                    long readTimestamp = parseIso8601OrZero(msg.readAt);

                    // Receive timestamp for INCOMING REST-fetched rows: use
                    // the server's created_at (we didn't live-receive them,
                    // but this is when they arrived on our account). For
                    // outgoing messages we never "receive" our own sends →
                    // leave at 0.
                    long receiveTimestamp = (direction == MessagesManager.MESSAGE_INCOMING)
                            ? timestamp : 0;

                    // Compute status from delivered + read_at flags
                    int status;
                    if (msg.readAt != null && !msg.readAt.isEmpty() && !"null".equals(msg.readAt)) {
                        status = MessagesManager.MESSAGE_SEEN;
                    } else if (msg.delivered) {
                        status = MessagesManager.MESSAGE_DELIVERED;
                    } else {
                        status = MessagesManager.MESSAGE_SYNCED_WITH_SERVER;
                    }

                    // Decrypt ciphertext if encryption_key + iv are present
                    String decryptedBody;
                    if (msg.encryptionKey != null && !msg.encryptionKey.isEmpty()
                            && msg.encryptionIv != null && !msg.encryptionIv.isEmpty()) {
                        String d = com.jippytalk.Encryption.MessageCryptoHelper.decrypt(
                                msg.ciphertext, msg.encryptionKey, msg.encryptionIv);
                        decryptedBody = d != null ? d : (msg.ciphertext != null ? msg.ciphertext : "");
                    } else {
                        decryptedBody = msg.ciphertext != null ? msg.ciphertext : "";
                    }

                    // Map server message_type to the local constant.
                    int    localMessageType;
                    String bodyToStore;
                    if ("file".equals(msg.messageType)) {
                        localMessageType = MessagesManager.DOCUMENT_MESSAGE;

                        // Decrypt encrypted_s3_url and thumbnail using same key+iv as caption
                        String fileDownloadUrl = "";
                        String thumbDisplayUrl = "";
                        if (msg.encryptionKey != null && !msg.encryptionKey.isEmpty()
                                && msg.encryptionIv != null && !msg.encryptionIv.isEmpty()) {
                            if (msg.encryptedS3Url != null && !msg.encryptedS3Url.isEmpty()) {
                                if (msg.encryptedS3Url.startsWith("http")) {
                                    fileDownloadUrl = msg.encryptedS3Url; // backend presigned
                                } else {
                                    String d = com.jippytalk.Encryption.MessageCryptoHelper.decrypt(
                                            msg.encryptedS3Url, msg.encryptionKey, msg.encryptionIv);
                                    fileDownloadUrl = d != null ? d : "";
                                }
                            }
                            if (msg.thumbnail != null && !msg.thumbnail.isEmpty()) {
                                if (msg.thumbnail.startsWith("http")) {
                                    thumbDisplayUrl = msg.thumbnail; // backend presigned
                                } else {
                                    String d = com.jippytalk.Encryption.MessageCryptoHelper.decrypt(
                                            msg.thumbnail, msg.encryptionKey, msg.encryptionIv);
                                    thumbDisplayUrl = d != null ? d : "";
                                }
                            }
                        } else {
                            fileDownloadUrl = msg.encryptedS3Url != null ? msg.encryptedS3Url : "";
                            thumbDisplayUrl = msg.thumbnail != null ? msg.thumbnail : "";
                        }

                        // v8: insert into dedicated columns directly (no JSON blob).
                        // The `message` and `caption` columns get the decrypted text;
                        // chat list shows it directly.
                        boolean insertedMedia = dao.insertMessageWithMedia(
                                dbMessageId,
                                direction,
                                chatPartnerId,
                                decryptedBody != null ? decryptedBody : "",   // message col = caption
                                status,
                                MessagesManager.NO_NEED_TO_PUSH_MESSAGE,
                                timestamp,
                                receiveTimestamp,
                                readTimestamp,
                                MessagesManager.MESSAGE_NOT_STARRED,
                                MessagesManager.MESSAGE_NOT_EDITED,
                                MessagesManager.DOCUMENT_MESSAGE,
                                0,  // latitude
                                0,  // longitude
                                MessagesManager.DEFAULT_MSG_TO_MSG_REPLY,
                                "",
                                ChatManager.UNARCHIVE_CHAT,
                                msg.fileName != null && !msg.fileName.isEmpty()
                                        ? msg.fileName : "(attachment)",
                                msg.contentType != null ? msg.contentType : "document",
                                msg.contentSubtype != null ? msg.contentSubtype : "",
                                decryptedBody != null ? decryptedBody : "",
                                msg.width,
                                msg.height,
                                msg.duration,
                                msg.fileSize,
                                msg.s3Key != null ? msg.s3Key : "",
                                // Backend /api/messages doesn't echo bucket; default to
                                // API.S3_BUCKET so the column is never empty on REST rows.
                                API.S3_BUCKET,
                                msg.fileTransferId != null ? msg.fileTransferId : "",
                                "",                                                 // local_file_path
                                "",                                                 // local_thumbnail_path
                                thumbDisplayUrl,
                                fileDownloadUrl,
                                msg.encryptionKey != null ? msg.encryptionKey : "",
                                msg.encryptionIv != null ? msg.encryptionIv : "",
                                msg.roomId != null ? msg.roomId : ""   // v9: room_id
                        );

                        if (insertedMedia) {
                            insertedCount++;
                        } else {
                            // Row already exists — refresh ONLY server-side
                            // state changes (status + read_at, plus caption
                            // if the sender edited it). Everything else —
                            // URLs, S3 key, encryption keys, local paths —
                            // is intentionally preserved so the local device
                            // never loses downloaded files or thumbnails.
                            boolean u = dao.updateMediaMessageServerFields(
                                    dbMessageId,
                                    decryptedBody != null ? decryptedBody : "",   // message col
                                    status,
                                    readTimestamp,
                                    decryptedBody != null ? decryptedBody : ""    // caption
                            );
                            if (u) updatedCount++;
                        }
                        // Skip the text-message insert path below
                        continue;
                    } else {
                        localMessageType = MessagesManager.TEXT_MESSAGE;
                        bodyToStore = decryptedBody;
                    }

                    boolean inserted = dao.insertMessage(
                            dbMessageId,
                            direction,
                            chatPartnerId,
                            bodyToStore,
                            status,
                            MessagesManager.NO_NEED_TO_PUSH_MESSAGE,
                            timestamp,
                            receiveTimestamp,
                            readTimestamp,
                            MessagesManager.MESSAGE_NOT_STARRED,
                            MessagesManager.MESSAGE_NOT_EDITED,
                            localMessageType,
                            0,  // latitude
                            0,  // longitude
                            MessagesManager.DEFAULT_MSG_TO_MSG_REPLY,
                            "",
                            ChatManager.UNARCHIVE_CHAT,
                            msg.roomId != null ? msg.roomId : ""   // v9: room_id
                    );

                    if (inserted) {
                        insertedCount++;
                    } else {
                        // Text row already exists — refresh only status and
                        // read_timestamp (plus message if edited). Everything
                        // else is preserved.
                        boolean u = dao.updateTextMessageServerFields(
                                dbMessageId, bodyToStore, status, readTimestamp);
                        if (u) updatedCount++;
                    }
                } catch (Exception e) {
                    Log.e(Extras.LOG_MESSAGE, "Failed to insert server message "
                            + msg.id + ": " + e.getMessage());
                }
            }

            Log.e(Extras.LOG_MESSAGE, "REST sync complete — inserted " + insertedCount
                    + " new, updated " + updatedCount + " existing, out of "
                    + serverMessages.size() + " fetched. "
                    + "(Existing rows had only status/read_timestamp refreshed; "
                    + "URLs, keys, local paths preserved.)");

            // Refresh the adapter from the DB on the main thread, then scroll
            // to the bottom so the newest message is visible (server-fetch path
            // is effectively "open chat → load history", so the user expects
            // the view to land on the most recent message).
            runOnUiThread(() -> {
                if (messagesViewModel != null) {
                    messagesViewModel.retrieveAllMessagesOfContact(contactId);
                }
                activityMessagingBinding.rvChats.postDelayed(() -> {
                    int last = adapter.getItemCount() - 1;
                    if (last >= 0) {
                        activityMessagingBinding.rvChats.scrollToPosition(last);
                    }
                }, 150);
            });
        });
    }

    /**
     * Parses an ISO 8601 timestamp string like "2026-04-10T16:36:41.397095+05:30"
     * into milliseconds since epoch. Handles the nanosecond precision + timezone offset
     * format that Go's time.Time serializer produces.
     *
     * Falls back to current time if parsing fails.
     */
    /**
     * Parses an ISO 8601 timestamp and returns 0 for null / empty /
     * unparseable input. Use for optional timestamps (like msg.readAt) where
     * "not known" should map to the DB sentinel 0, not the current time.
     *
     * Runs the same normalisation as parseIso8601ToMillis (truncate nanos,
     * normalise +05:30 → +0530) but uses 0 as the failure default.
     */
    private long parseIso8601OrZero(String iso) {
        if (iso == null || iso.isEmpty() || "null".equals(iso)) return 0L;
        try {
            String cleaned = iso;
            int dotIdx = iso.indexOf('.');
            if (dotIdx >= 0) {
                int tzIdx = -1;
                for (int i = dotIdx + 1; i < iso.length(); i++) {
                    char c = iso.charAt(i);
                    if (c == '+' || c == '-' || c == 'Z') { tzIdx = i; break; }
                }
                if (tzIdx > 0) {
                    String fraction = iso.substring(dotIdx + 1, tzIdx);
                    if (fraction.length() > 3) fraction = fraction.substring(0, 3);
                    cleaned = iso.substring(0, dotIdx + 1) + fraction + iso.substring(tzIdx);
                }
            }
            int len = cleaned.length();
            if (len >= 6) {
                char sign = cleaned.charAt(len - 6);
                if ((sign == '+' || sign == '-') && cleaned.charAt(len - 3) == ':') {
                    cleaned = cleaned.substring(0, len - 3) + cleaned.substring(len - 2);
                }
            }
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(
                    "yyyy-MM-dd'T'HH:mm:ss.SSSZ", java.util.Locale.US);
            return sdf.parse(cleaned).getTime();
        } catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "parseIso8601OrZero: '"
                    + iso + "' unparseable — defaulting to 0");
            return 0L;
        }
    }

    private long parseIso8601ToMillis(String iso) {
        if (iso == null || iso.isEmpty()) return System.currentTimeMillis();
        try {
            // Truncate nanoseconds to milliseconds since SimpleDateFormat doesn't support nanos
            // "2026-04-10T16:36:41.397095+05:30" → "2026-04-10T16:36:41.397+05:30"
            String cleaned = iso;
            int dotIdx = iso.indexOf('.');
            if (dotIdx >= 0) {
                int tzIdx = -1;
                for (int i = dotIdx + 1; i < iso.length(); i++) {
                    char c = iso.charAt(i);
                    if (c == '+' || c == '-' || c == 'Z') { tzIdx = i; break; }
                }
                if (tzIdx > 0) {
                    String fraction = iso.substring(dotIdx + 1, tzIdx);
                    if (fraction.length() > 3) fraction = fraction.substring(0, 3);
                    cleaned = iso.substring(0, dotIdx + 1) + fraction + iso.substring(tzIdx);
                }
            }
            // Normalize timezone "+05:30" → "+0530" for SimpleDateFormat
            int len = cleaned.length();
            if (len >= 6) {
                char sign = cleaned.charAt(len - 6);
                if ((sign == '+' || sign == '-') && cleaned.charAt(len - 3) == ':') {
                    cleaned = cleaned.substring(0, len - 3) + cleaned.substring(len - 2);
                }
            }
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(
                    "yyyy-MM-dd'T'HH:mm:ss.SSSZ", java.util.Locale.US);
            return sdf.parse(cleaned).getTime();
        } catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "Failed to parse timestamp: " + iso + " — " + e.getMessage());
            return System.currentTimeMillis();
        }
    }

    /**
     * Fetches the contact's Signal key bundle from the server, builds a session,
     * then encrypts and sends the message.
     *
     * Dev-mode fallback: if the bundle fetch fails (e.g. the /api/keys/bundle/{userId}
     * endpoint isn't ready yet on the backend), the message is sent plaintext via the
     * new WebSocket envelope format so basic messaging still works. Once the backend
     * endpoint is live, this fallback will never trigger and all messages will be E2E
     * encrypted.
     *
     * @param messageInsertionModel the message to send once the session is ready
     */
    private void fetchBundleAndSend(MessageInsertionModel messageInsertionModel) {
        KeyBundleFetcher.getInstance(MessagingActivity.this).fetchAndBuildSession(
                contactId, contactName, "",
                new KeyBundleFetcher.SessionCallback() {
                    @Override
                    public void onSessionBuilt(String targetUserId, int deviceId) {
                        Log.e(Extras.LOG_MESSAGE, "Session built for " + targetUserId
                                + ", deviceId=" + deviceId + " — now encrypting message");
                        contactDeviceId     =   deviceId;
                        encryptMessageAndSendToServer(messageInsertionModel);
                    }

                    @Override
                    public void onFailure(String error) {
                        // Dev fallback — backend /api/keys/bundle endpoint not ready yet.
                        // Send plaintext via new WS envelope so messaging still works
                        // while encryption is pending.
                        Log.e(Extras.LOG_MESSAGE, "Bundle fetch failed (" + error
                                + "), falling back to plaintext send");
                        sendPlaintextFallback(messageInsertionModel);
                    }
                });
    }

    /**
     * Dev-mode fallback: sends the message plaintext using the new WebSocket envelope
     * format. Used only when the Signal key bundle fetch fails (backend endpoint pending).
     * Will be removed once /api/keys/bundle/{userId} is live on the server.
     */
    @SuppressWarnings("deprecation")
    private void sendPlaintextFallback(MessageInsertionModel messageInsertionModel) {
        executorService.execute(() -> {
            if (webSocketConnection == null) {
                Log.e(Extras.LOG_MESSAGE, "Cannot send fallback — webSocketConnection is null");
                return;
            }
            webSocketConnection.sendPlainTextMessage(
                    messageInsertionModel.getMessageId(),
                    contactId,
                    messageInsertionModel.getMessage()
            );
        });
    }

    /**
     * Encrypts the attachment metadata with Signal Protocol and sends the file message
     * via the new WebSocket envelope format. Called after a successful S3 upload when
     * the Signal session is ready.
     *
     * @param s3Key     the S3 object key returned by the upload
     * @param bucket    the S3 bucket name
     * @param model     the attachment model with all metadata
     * @param fileSize  the file size in bytes
     */
    private void encryptAndSendFileMessage(String s3Key, String bucket, AttachmentModel model, long fileSize) {
        Log.e(Extras.LOG_MESSAGE, "Encrypting attachment metadata for S3 key: " + s3Key);

        AttachmentCryptoHelper.encryptMetadata(messageEncryptAndDecrypt,
                contactId, contactDeviceId, model, s3Key, "", fileSize,
                new AttachmentCryptoHelper.EncryptionResultCallback() {
                    @Override
                    public void onEncrypted(String encryptedCiphertext, int signalMessageType) {
                        Log.e(Extras.LOG_MESSAGE, "Metadata encrypted, sending WebSocket file event");
                        if (webSocketConnection != null) {
                            webSocketConnection.sendFileMessage(
                                    "",         // clientMessageId — TODO: thread through from upload
                                    contactId,
                                    encryptedCiphertext,
                                    "",         // encrypted_s3_url
                                    s3Key,
                                    model.getName(),
                                    fileSize,
                                    model.getContentType(),
                                    model.getContentSubtype(),
                                    model.getCaption(),
                                    model.getWidth(),
                                    model.getHeight(),
                                    model.getDuration(),
                                    model.getThumbnail(),
                                    model.getBucket(),
                                    model.getEncryptionKey(),
                                    model.getEncryptionIv()
                            );
                        }
                    }

                    @Override
                    public void onEncryptionFailed(int exceptionStatus) {
                        Log.e(Extras.LOG_MESSAGE, "Attachment metadata encryption failed, rebuilding session");
                        KeyBundleFetcher.getInstance(MessagingActivity.this).fetchAndBuildSession(
                                contactId, contactName, "",
                                new KeyBundleFetcher.SessionCallback() {
                                    @Override
                                    public void onSessionBuilt(String targetUserId, int deviceId) {
                                        contactDeviceId = deviceId;
                                        encryptAndSendFileMessage(s3Key, bucket, model, fileSize);
                                    }

                                    @Override
                                    public void onFailure(String error) {
                                        Log.e(Extras.LOG_MESSAGE,
                                                "Session rebuild failed (" + error
                                                        + ") — falling back to plaintext metadata");
                                        sendFilePlaintextFallback(model.getFileTransferId(), s3Key, bucket, model, fileSize);
                                    }
                                });
                    }
                });
    }

    /**
     * Dev-mode fallback: when the recipient has no Signal keys uploaded on the server,
     * we skip encryption and send the attachment metadata as plain JSON. The backend
     * still wraps this in a {type: "file"} envelope and forwards it. Remove once the
     * key bundle endpoint is live for all users.
     */
    private void sendFilePlaintextFallback(String localMessageId, String s3Key, String bucket,
                                           AttachmentModel model, long fileSize) {
        if (webSocketConnection == null) {
            Log.e(Extras.LOG_MESSAGE, "Cannot send file fallback — webSocketConnection is null");
            return;
        }
        Log.e(Extras.LOG_MESSAGE, "Sending plaintext file message: " + model.getName()
                + " clientMsgId=" + localMessageId
                + " encKey=" + (!model.getEncryptionKey().isEmpty()));
        webSocketConnection.sendFileMessage(
                localMessageId,               // client_message_id for DB correlation
                contactId,
                "",                           // ciphertext (empty — encryption happens inside sendFileMessage)
                "",                           // encrypted_s3_url
                s3Key,
                model.getName(),
                fileSize,
                model.getContentType(),
                model.getContentSubtype(),
                model.getCaption(),
                model.getWidth(),
                model.getHeight(),
                model.getDuration(),
                model.getThumbnail(),
                bucket != null ? bucket : model.getBucket(),
                model.getEncryptionKey(),
                model.getEncryptionIv()
        );
    }

    private void encryptMessageAndSendToServer(MessageInsertionModel messageInsertionModel) {
        long encryptionStartTime = System.currentTimeMillis();
        Log.e(Extras.LOG_MESSAGE, "Encryption started for messageId: " + messageInsertionModel.getMessageId());

        messageEncryptAndDecrypt.encryptMessage(contactId, contactDeviceId, messageInsertionModel.getMessage(),
                new MessageEncryptAndDecrypt.MessageEncryptionCallBacks() {

                    @Override
                    public void onMessageEncryptSuccessful(String encryptedMessage, int signalMessageType) {
                        long encryptionEndTime = System.currentTimeMillis();
                        Log.e(Extras.LOG_MESSAGE, "Encryption finished for messageId: "
                                + messageInsertionModel.getMessageId()
                                + " in " + (encryptionEndTime - encryptionStartTime) + " ms");

                        if (webSocketConnection != null) {
                            executorService.execute(() -> {
                                // Use the new protocol envelope: {type: "message", payload: {...}}
                                webSocketConnection.sendEncryptedTextMessage(
                                        messageInsertionModel.getMessageId(),
                                        contactId,
                                        encryptedMessage,
                                        signalMessageType
                                );
                            });
                        } else {
                            Log.e(Extras.LOG_MESSAGE, "Unable to send message — webSocketConnection is null");
                        }
                    }

                    @Override
                    public void onMessageEncryptionFail(int exceptionStatus) {
                        Log.e(Extras.LOG_MESSAGE, "Encryption failed, fetching fresh key bundle");
                        // Fetch fresh bundle and retry
                        fetchBundleAndSend(messageInsertionModel);
                    }
                });
    }


    public void startWorkManagerToCreateSessionForContact(String contactId) {
        Data inputData = new Data.Builder()
                .putString("contactId", contactId).build();
        WorkRequest uploadWorkRequest = new OneTimeWorkRequest.Builder(SessionCreationWorkManager.class)
                .setInputData(inputData).build();
        WorkManager.getInstance(MessagingActivity.this).enqueue(uploadWorkRequest);
        Log.e(Extras.LOG_MESSAGE, "session not exists");
    }

    private void hideOrDisplayDownArrow() {
        int itemCount               =   adapter.getItemCount();
        int lastVisiblePosition     =   linearLayoutManager.findLastVisibleItemPosition();

        boolean isLastMessageVisible = lastVisiblePosition >= itemCount - 1;

        if (isLastMessageVisible) {
            activityMessagingBinding.cvDownButton.setVisibility(GONE);
        } else {
            activityMessagingBinding.cvDownButton.setVisibility(VISIBLE);
        }
    }

    private void goToContactProfile(String contactId) {
        Intent intent2 = new Intent(MessagingActivity.this, ContactProfileActivity.class);
        intent2.putExtra(Extras.CHAT_ID, contactId);
        startActivity(intent2);
    }

    private final Runnable typingRunnable = this::onTypingStopped;

    private void onTypingStopped() {
        if (webSocketConnection != null) {
            webSocketConnection.senderIsTyping(userId, contactId,false,"typing");
        }
    }

    private void scrollToLastItem() {
        if (adapter != null && adapter.getItemCount() > 0) {
            activityMessagingBinding.rvChats.scrollToPosition(adapter.getItemCount() - 1);
        }
    }

    /**
     * Returns true when the user is already viewing (or within ~2 rows of)
     * the most recent message. Used to decide whether a DB-refresh-driven
     * submitList should follow the bottom or stay put. Without this, every
     * thumbnail / local-file-path persist (which fires LiveData) yanks the
     * user back to the bottom even when they're scrolled up reading history.
     */
    private boolean isNearBottomOfChat() {
        try {
            androidx.recyclerview.widget.LinearLayoutManager lm =
                    (androidx.recyclerview.widget.LinearLayoutManager)
                            activityMessagingBinding.rvChats.getLayoutManager();
            if (lm == null || adapter == null) return true;
            int last = lm.findLastVisibleItemPosition();
            int total = adapter.getItemCount();
            // "Near the bottom" = the last 2 rows are visible. Tolerant
            // enough that a half-shown last row still counts.
            return total == 0 || last >= total - 2;
        } catch (Throwable t) {
            return true;
        }
    }

    public void InfoAlertDialog() {
        ChatDataRetrievalHandler chatDataRetrievalHandler   =   new ChatDataRetrievalHandler(this,
                chatName -> runOnUiThread(() -> {
            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(MessagingActivity.this);
            builder.setTitle(chatName);
            builder.setMessage(R.string.user_busy_summary);
            builder.setPositiveButton("OK", (dialogInterface, i) -> dialogInterface.dismiss());
            builder.show();
        }));
        chatDataRetrievalHandler.displayChatName(contactId);
    }

    private void copyTextToClipboard(String text) {
        if (text != null) {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Copied Text", text);
            if (clipboard != null) {
                clipboard.setPrimaryClip(clip);
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.message_activity_menu, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId  = item.getItemId();
        if (itemId == R.id.Profile) {
            if (contactId == null || contactId.isEmpty()) {
                Toast.makeText(MessagingActivity.this, "Unable to open profile", Toast.LENGTH_SHORT).show();
                return false;
            }
            Intent intent  = new Intent(MessagingActivity.this,ContactProfileActivity.class);
            intent.putExtra(Extras.CHAT_ID, contactId);
            startActivity(intent);
        }
        else if(itemId == R.id.clear_chat) {
            if (contactId == null || contactId.isEmpty()) {
                Toast.makeText(MessagingActivity.this, "Unable to open profile", Toast.LENGTH_SHORT).show();
                return false;
            }
            showDeleteMessagesDialog(contactId);
        }
        else if(itemId == R.id.search_messages) {
            if (contactId == null || contactId.isEmpty()) {
                Toast.makeText(MessagingActivity.this, "Unable to open profile", Toast.LENGTH_SHORT).show();
                return false;
            }
            Intent intent = new Intent(MessagingActivity.this, SearchMessagesActivity.class);
            intent.putExtra(Extras.CHAT_ID, contactId);
            startActivity(intent);
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();

        messagesViewModel.getUserIdAndReadReceiptsStatus();
        messagesViewModel.getLastVisitPrivacyOption();

        if (webSocketConnection != null && contactId != null) {
            webSocketConnection.setMessagingActivityVisible(true);
            webSocketConnection.setMessageCallback(this);
            webSocketConnection.getOpenedChatId(contactId);
        }

        messagesViewModel.retrieveAllMessagesOfContact(contactId);
        contactViewModel.retrieveContactDetailsForMessagingActivity(contactId);
        messagesViewModel.getEditTextVisibility(contactId);
        chatListRepository.resetUnreadCount(contactId);
        messagesViewModel.getContactBusyStatus(contactId);
        messagingActivityDataRetrieval.getWallpaperFromInternalStorage(activityMessagingBinding);
    }

    @Override
    protected void onStop() {
        super.onStop();
        int lastVisiblePosition = linearLayoutManager.findLastVisibleItemPosition();
        messagesViewModel.updateLastMessagePositionForContact(contactId, lastVisiblePosition);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (webSocketConnection != null) {
            webSocketConnection.setMessageCallback(null);
            webSocketConnection.setMessagingActivityVisible(false);
        }
    }

    public void showDeleteMessagesDialog(String contactId) {
        MaterialAlertDialogBuilder builder  = new MaterialAlertDialogBuilder(MessagingActivity.this);
        builder.setTitle("Delete messages");
        builder.setMessage("Do you really want to delete all messages of this chat...?");
        builder.setPositiveButton("Delete", (dialogInterface, i) -> {
            try {
                if (contactId != null && !contactId.isEmpty()) {
                    messagesRepository.deleteAllMessagesOfContact(contactId);
                }
                else {
                     Toast.makeText(MessagingActivity.this, "Unable to delete the chat please try again later",
                             Toast.LENGTH_SHORT).show();
                }
            }
            catch (Exception e) {
                Log.e(Extras.LOG_MESSAGE,"Unable to delete chat database error " + e.getMessage());
                Toast.makeText(MessagingActivity.this, "Unable to delete the chat please try again later",
                        Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Cancel", (dialogInterface, i) -> dialogInterface.dismiss());
        builder.show();
    }

    @Override
    public void onSentMessageClick(String messageId) {
    }

    @Override
    public void onSentRepliedMessageTextClick(String messageId) {
        int position    =   adapter.findMessagePositionById(messageId);
        if (position == -1) {
            Toast.makeText(this, "Message might be deleted", Toast.LENGTH_SHORT).show();
            return;
        }

        RecyclerView.ViewHolder viewHolder = activityMessagingBinding.rvChats.findViewHolderForAdapterPosition(position);
        if (viewHolder != null) {
            // Message is already visible - highlight immediately
            View targetView = getTargetViewFromViewHolder(viewHolder);
            if (targetView != null) {
               // highlight(targetView);
            }
        } else {
            // Message not visible - scroll and then highlight
            activityMessagingBinding.rvChats.smoothScrollToPosition(position);

            RecyclerView.OnScrollListener scrollListener = new RecyclerView.OnScrollListener() {
                @Override
                public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                    if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                        recyclerView.removeOnScrollListener(this);

                        RecyclerView.ViewHolder vh = recyclerView.findViewHolderForAdapterPosition(position);
                        if (vh != null) {
                            View targetView = getTargetViewFromViewHolder(vh);
                            if (targetView != null) {
                               // highlight(targetView);
                            }
                        }
                    }
                }
            };

            activityMessagingBinding.rvChats.addOnScrollListener(scrollListener);
        }

    }

    @Override
    public void onReceivedRepliedMessageTextClick(String messageId) {
        int position    =   adapter.findMessagePositionById(messageId);
        if (position == -1) {
            Toast.makeText(this, "Internal error", Toast.LENGTH_SHORT).show();
            return;
        }
        RecyclerView.ViewHolder viewHolder = activityMessagingBinding.rvChats.findViewHolderForAdapterPosition(position);

        if (viewHolder != null) {
            // Message is already visible - highlight immediately
            View targetView = getTargetViewFromViewHolder(viewHolder);
            if (targetView != null) {
               // highlight(targetView);
            }
        } else {
            activityMessagingBinding.rvChats.smoothScrollToPosition(position);

            RecyclerView.OnScrollListener scrollListener = new RecyclerView.OnScrollListener() {
                @Override
                public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                    if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                        recyclerView.removeOnScrollListener(this);

                        RecyclerView.ViewHolder vh = recyclerView.findViewHolderForAdapterPosition(position);
                        if (vh != null) {
                            View targetView = getTargetViewFromViewHolder(vh);
                            if (targetView != null) {
                                // highlight(targetView);
                            }
                        }
                    }
                }
            };

            activityMessagingBinding.rvChats.addOnScrollListener(scrollListener);
        }
    }

    private View getTargetViewFromViewHolder(RecyclerView.ViewHolder viewHolder) {
        if (viewHolder instanceof MessageAdapter.SentMessageViewHolder) {
            return ((MessageAdapter.SentMessageViewHolder) viewHolder).senderMessageBinding.llMessageBackgroundView;
        } else if (viewHolder instanceof MessageAdapter.ReceivedMessageViewHolder) {
            return ((MessageAdapter.ReceivedMessageViewHolder) viewHolder).receiverMessageBinding.llReceivedMessageBackground;
        }
        return null;
    }

    @Override
    public void onSentMessageLongClick(String messageId, int position) {

        List<MessageModal>      currentList                 =   adapter.getCurrentList();
        SelectedSentMsgBinding  selectedSentMsgBinding      =   SelectedSentMsgBinding.inflate(getLayoutInflater());
        BottomSheetDialog       bottomSheetDialog           =   new BottomSheetDialog(this,R.style.TransparentDialog);
        bottomSheetDialog.setContentView(selectedSentMsgBinding.getRoot());
        bottomSheetDialog.setCanceledOnTouchOutside(true);

        if (bottomSheetDialog.getWindow() != null) {
            bottomSheetDialog.getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE|
                            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        }
        bottomSheetDialog.show();
        bottomSheetDialog.setOnCancelListener(dialogInterface -> adapter.resetSelectedItem());

        if (currentList.get(position).getStarredStatus() == MessagesManager.MESSAGE_IS_STARRED) {
            selectedSentMsgBinding.tvStarredMessage.setText(R.string.unstar_message);
        }
        else {
            selectedSentMsgBinding.tvStarredMessage.setText(R.string.star_message);
        }

        setClickListener(selectedSentMsgBinding.llDeleteMessage, v -> {
            bottomSheetDialog.dismiss();
            deleteSentMessages(messageId, position);
        });

        setClickListener(selectedSentMsgBinding.llStarMessage, v -> {
            bottomSheetDialog.dismiss();
            if (currentList.get(position).getStarredStatus() == MessagesManager.MESSAGE_IS_STARRED) {
                messagesViewModel.updateMessageStarredStatus(messageId, MessagesManager.MESSAGE_NOT_STARRED);
            }
            else {
                messagesViewModel.updateMessageStarredStatus(messageId, MessagesManager.MESSAGE_IS_STARRED);
            }
        });

        setClickListener(selectedSentMsgBinding.llForwardMessage, view -> {
            bottomSheetDialog.dismiss();
            Intent intent = new Intent(MessagingActivity.this, MessagesForwardListActivity.class);
            intent.putExtra(Extras.MESSAGE_ID, messageId);
            startActivity(intent);
        });

        setClickListener(selectedSentMsgBinding.llCopyMessage, v -> {
            copyTextToClipboard(currentList.get(position).getMessage());
            bottomSheetDialog.dismiss();
            adapter.resetSelectedItem();
        });

        setClickListener(selectedSentMsgBinding.llMessageInfo, v -> {
            Intent intent = new Intent(MessagingActivity.this, MessageInfoActivity.class);
            intent.putExtra(Extras.MESSAGE_ID, messageId);
            startActivity(intent);
            bottomSheetDialog.dismiss();
            adapter.resetSelectedItem();
        });

    }

    @Override
    public void onReceivedMessageLongClick(String messageId, int position) {
        SelectedReceiveMsgBinding   selectedReceiveMsgBinding       =   SelectedReceiveMsgBinding.inflate(getLayoutInflater());
        BottomSheetDialog           bottomSheetDialog               =   new BottomSheetDialog(this,R.style.TransparentDialog);
        bottomSheetDialog.setContentView(selectedReceiveMsgBinding.getRoot());
        bottomSheetDialog.setCanceledOnTouchOutside(true);

        if (bottomSheetDialog.getWindow() != null) {
            bottomSheetDialog.getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE|
                        WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        }
        bottomSheetDialog.show();

        bottomSheetDialog.setOnCancelListener(dialogInterface -> adapter.resetSelectedItem());

        if (adapter.getCurrentList().get(position).getStarredStatus() == MessagesManager.MESSAGE_IS_STARRED) {
            selectedReceiveMsgBinding.tvStarredMessage.setText(R.string.unstar_message);
        }
        else {
            selectedReceiveMsgBinding.tvStarredMessage.setText(R.string.star_message);
        }

        setClickListener(selectedReceiveMsgBinding.llDeleteMessage,v -> {
            messagesViewModel.deleteMessage(messageId);
            bottomSheetDialog.dismiss();
        });

        setClickListener(selectedReceiveMsgBinding.llForwardMessage, view -> {
            bottomSheetDialog.dismiss();
            Intent intent = new Intent(MessagingActivity.this, MessagesForwardListActivity.class);
            intent.putExtra(Extras.MESSAGE_ID, messageId);
            startActivity(intent);
        });

        setClickListener(selectedReceiveMsgBinding.llStarMessage, v -> {
            bottomSheetDialog.dismiss();
            if (adapter.getCurrentList().get(position).getStarredStatus() == MessagesManager.MESSAGE_IS_STARRED) {
                messagesViewModel.updateMessageStarredStatus(messageId, MessagesManager.MESSAGE_NOT_STARRED);
            }
            else {
                messagesViewModel.updateMessageStarredStatus(messageId, MessagesManager.MESSAGE_IS_STARRED);
            }
        });

        setClickListener(selectedReceiveMsgBinding.llCopyMessage, v -> {
            copyTextToClipboard(adapter.getCurrentList().get(position).getMessage());
            bottomSheetDialog.dismiss();
            adapter.resetSelectedItem();
        });
    }

    private void setClickListener(LinearLayout layout, View.OnClickListener listener) {
        if (layout != null) {
            layout.setOnClickListener(listener);
        }
    }

    public void deleteSentMessages(String messageId, int messagePosition) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(MessagingActivity.this);
        builder.setTitle("Delete Message");
        builder.setSingleChoiceItems(R.array.DeleteMessages, checkedItem, (dialog, which) -> checkedItem = which);
        builder.setPositiveButton("OK", (dialogInterface, i) -> {
            switch (checkedItem) {
                case MessagesManager.DELETE_FOR_ME          -> {
                    // Local-only delete — remove from local DB + adapter
                    Log.e(Extras.LOG_MESSAGE, "Delete for me: " + messageId);
                    messagesViewModel.deleteMessage(messageId);
                }
                case MessagesManager.DELETE_FOR_EVERYONE    -> {
                    // Server-side delete via new WS protocol + optimistic local delete
                    Log.e(Extras.LOG_MESSAGE, "Delete for everyone: " + messageId);
                    if (webSocketConnection != null) {
                        webSocketConnection.sendDeleteMessage(messageId);
                    }
                    // Optimistically remove locally — the server will also send back a
                    // message_deleted event that we handle to keep the state consistent.
                    messagesViewModel.deleteMessage(messageId);
                }
            }
        });

        builder.setNegativeButton("Cancel", (dialogInterface, i) -> {
            dialogInterface.dismiss();
            adapter.resetSelectedItem();
        });
        builder.show();
    }

    @Override
    public void contactIdIsTyping(boolean isTyping) {
        Log.e(Extras.LOG_MESSAGE,"typing callback " + isTyping);
        runOnUiThread(() -> {
            if (!WebSocketConnection.isConnectedToSocket) {
                activityMessagingBinding.toolbarSubtitle.setVisibility(GONE);
                return;
            }
            if (isTyping) {
                activityMessagingBinding.toolbarSubtitle.setVisibility(VISIBLE);
                activityMessagingBinding.toolbarSubtitle.setText("typing...");
            } else {
                activityMessagingBinding.toolbarSubtitle.setVisibility(GONE);
                activityMessagingBinding.toolbarSubtitle.setText("");
            }
        });
    }

    @Override
    public void updateUserIsOnlineOrOffline(int isOnline, long lastSeen) {
        Log.e(Extras.LOG_MESSAGE,"call back received " + isOnline);
        runOnUiThread(() -> {
            if (!WebSocketConnection.isConnectedToSocket) {
                activityMessagingBinding.toolbarSubtitle.setVisibility(GONE);
                return;
            }
            String subtitleText = null;
            if (isOnline == 1) {
                subtitleText = "online";
            } else if (lastSeen > 0) {
                subtitleText = "last visit at " + getTimeOrDate(lastSeen);
            }

            if (subtitleText != null) {
                activityMessagingBinding.toolbarSubtitle.setText(subtitleText);
                activityMessagingBinding.toolbarSubtitle.setVisibility(View.VISIBLE);
                activityMessagingBinding.toolbarSubtitle.setSelected(true);
            } else {
                activityMessagingBinding.toolbarSubtitle.setVisibility(View.GONE);
            }
        });
    }

    @Override
    public void updateMessageStatus(String messageId, int messageStatus) {
        runOnUiThread(() -> {
            List<MessageModal> list = adapter.getCurrentList();
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).getMessageId().equals(messageId)) {
                    list.get(i).setMessageStatus(messageStatus);
                    adapter.notifyItemChanged(i, "STATUS");
                    break;
                }
            }
        });
    }

    public void updateMessageStatusAsBatch(List<String> messageIds, int messageStatus) {
        executorService.execute(() -> {
            Set<String> idSet = new HashSet<>(messageIds);
            for (MessageModal msg : adapter.getCurrentList()) {
                if (idSet.contains(msg.getMessageId()) && msg.getMessageStatus() != messageStatus) {
                    msg.setMessageStatus(messageStatus);
                }
            }
            runOnUiThread(() -> {
                RecyclerView.LayoutManager layoutManager = activityMessagingBinding.rvChats.getLayoutManager();
                if (layoutManager instanceof LinearLayoutManager lm) {
                    int                 first   =   lm.findFirstVisibleItemPosition();
                    int                 last    =   lm.findLastVisibleItemPosition();

                    for (int i = first; i <= last; i++) {
                        MessageModal msg = adapter.getCurrentList().get(i);
                        if (idSet.contains(msg.getMessageId())) {
                            adapter.notifyItemChanged(i, "STATUS");
                        }
                    }
                }
            });
        });
    }





    public static String getTimeOrDate(long timestamp) {
        try {
            // Convert seconds → milliseconds
            Date date = new Date(timestamp * 1000);
            Calendar messageCal = Calendar.getInstance();
            messageCal.setTime(date);

            Calendar todayCal = Calendar.getInstance();

            SimpleDateFormat format;

            if (isSameDay(messageCal, todayCal)) {
                // Same day → show time
                format = new SimpleDateFormat("h:mm a", Locale.getDefault());
                return format.format(date);
            }

            // Check if it was yesterday
            Calendar yesterdayCal = Calendar.getInstance();
            yesterdayCal.add(Calendar.DAY_OF_YEAR, -1);

            if (isSameDay(messageCal, yesterdayCal)) {
                return "yesterday at " + new SimpleDateFormat("h:mm a", Locale.getDefault()).format(date);
            }

            // Older → show full date
            format = new SimpleDateFormat("dd MMM yy, h:mm a", Locale.getDefault());
            return format.format(date);

        } catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "Error converting timestamp: " + e.getMessage());
            return "date";
        }
    }

    private static boolean isSameDay(Calendar cal1, Calendar cal2) {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR)
                && cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR);
    }

    // -------------------- Attachment Handling Starts Here ---------------------

    /**
     * Shows the attachment bottom sheet dialog.
     * Delegates option handling entirely to AttachmentPickerHandler.
     */
    private void showAttachmentBottomSheet() {
        AttachmentBottomSheet.show(this, option -> attachmentPickerHandler.handleOption(option));
    }

    /**
     * Sets up the attachment picker callback and media transfer listeners.
     * Called from setUpAllMethods() after initialization.
     *
     * Picker callback: user picks a file → create message → start upload
     * Transfer listener: upload/download done → send WebSocket event
     * Adapter listener: user taps cancel/retry/download → delegate to manager
     */
    /**
     * Inserts a placeholder row for an outgoing attachment immediately when the
     * user picks a file, so the chat UI shows the attachment with an upload
     * spinner before the actual S3 upload has started.
     *
     * The row is inserted via the service-path method (not the ViewModel observer
     * path) so we don't accidentally trigger a send-to-server before the upload
     * has completed.
     */
    private void insertLocalOutgoingAttachmentRow(String localMessageId, AttachmentModel attachmentModel) {
        int     messageType     =   attachmentTypeToMessageType(attachmentModel.getContentType());
        long    now             =   System.currentTimeMillis();

        // v8: media metadata lives in dedicated columns (no JSON blob). The
        // `message` column holds the caption text only — the chat list shows
        // it directly without any JSON parsing.
        String fileName         =   attachmentModel.getName()           != null ? attachmentModel.getName()           : "";
        String contentType      =   attachmentModel.getContentType()    != null ? attachmentModel.getContentType()    : "";
        String contentSubtype   =   attachmentModel.getContentSubtype() != null ? attachmentModel.getContentSubtype() : "";
        String caption          =   attachmentModel.getCaption()        != null ? attachmentModel.getCaption()        : "";
        String localFilePath    =   attachmentModel.getLocalFilePath()  != null ? attachmentModel.getLocalFilePath()
                                                                                : attachmentModel.getMedia();

        // Compute file size from the content URI (plaintext size — what the
        // user sees in the UI; the encrypted on-the-wire size is reported by
        // S3UploadHelper at upload time).
        long size = 0;
        try {
            String mediaStr = attachmentModel.getMedia();
            if (mediaStr != null && !mediaStr.isEmpty()) {
                android.net.Uri mediaUri = android.net.Uri.parse(mediaStr);
                try (android.database.Cursor c = getContentResolver().query(
                        mediaUri, null, null, null, null)) {
                    if (c != null && c.moveToFirst()) {
                        int idx = c.getColumnIndex(android.provider.OpenableColumns.SIZE);
                        if (idx >= 0 && !c.isNull(idx)) size = c.getLong(idx);
                    }
                }
            }
        } catch (Exception ignored) {}

        messagesRepository.insertMediaMessageToLocalStorageFromService(
                localMessageId,
                MessagesManager.MESSAGE_OUTGOING,
                contactId,
                caption,                                            // message column = caption text
                MessagesManager.MESSAGE_NOT_SYNCED_WITH_SERVER,
                MessagesManager.NEED_MESSAGE_PUSH,
                now, 0, 0,
                MessagesManager.MESSAGE_NOT_STARRED,
                MessagesManager.MESSAGE_NOT_EDITED,
                messageType,
                0, 0,
                MessagesManager.DEFAULT_MSG_TO_MSG_REPLY,
                "",
                ChatManager.UNARCHIVE_CHAT,
                fileName, contentType, contentSubtype,
                caption,
                attachmentModel.getWidth(),
                attachmentModel.getHeight(),
                attachmentModel.getDuration(),
                size,
                "",                                                 // s3_key (filled after upload)
                "",                                                 // s3_bucket (filled after upload)
                "",                                                 // file_transfer_id
                localFilePath,
                "",                                                 // local_thumbnail_path (filled after thumb gen)
                "",                                                 // remote_thumbnail_url (filled after thumb upload)
                "",                                                 // encrypted_s3_url
                attachmentModel.getEncryptionKey(),
                attachmentModel.getEncryptionIv(),
                roomId != null ? roomId : "",                       // v9: room_id from intent extra
                () -> messagesViewModel.retrieveAllMessagesOfContact(contactId)
        );
    }

    /** Maps AttachmentModel.contentType to the MessagesManager constant. */
    /** Returns the first non-null, non-empty string from the arguments. */
    private static String getFirstNonEmpty(String... values) {
        for (String v : values) {
            if (v != null && !v.isEmpty()) return v;
        }
        return "";
    }

    private int attachmentTypeToMessageType(String contentType) {
        if (contentType == null) return MessagesManager.DOCUMENT_MESSAGE;
        switch (contentType) {
            case "image":       return MessagesManager.IMAGE_MESSAGE;
            case "video":       return MessagesManager.VIDEO_MESSAGE;
            case "audio":       return MessagesManager.AUDIO_MESSAGE;
            default:            return MessagesManager.DOCUMENT_MESSAGE;
        }
    }

    private void setUpAttachmentCallback() {
        // When user picks a file from the bottom sheet
        attachmentPickerHandler.setCallback(attachmentModel -> {
            String  uniqueMessageId =   UUID.randomUUID().toString().trim().replace("-", "");
            Log.e(Extras.LOG_MESSAGE, "Attachment received: " + attachmentModel);

            // Generate ONE per-message AES-256 key + IV before insertion so the
            // same key+iv is reused for: file bytes, thumbnail bytes, caption,
            // S3 file URL, and S3 thumbnail URL. We persist it to the DB row
            // so retry-after-restart can re-encrypt with the same key.
            com.jippytalk.Encryption.MessageCryptoHelper.EncryptionResult keyPair =
                    com.jippytalk.Encryption.MessageCryptoHelper.generateKeyIv();
            if (keyPair != null) {
                attachmentModel.setEncryptionKey(keyPair.key);
                attachmentModel.setEncryptionIv(keyPair.iv);
            }

            // User just picked a file — they expect the chat to follow the
            // newly-sent row to the bottom. Set the sticky flag so the next
            // LiveData refresh (triggered by insertLocalOutgoingAttachmentRow)
            // scrolls down once. Subsequent refreshes (thumbnail/path/S3
            // persists) won't because the flag self-clears.
            needScrollToBottom  =   true;

            // Insert an outgoing row in the local DB immediately so the sent
            // attachment appears in the chat UI with an upload spinner. The
            // row carries a JSON blob with the attachment metadata; the repo
            // parses it back out in populateMediaFieldsFromJson().
            insertLocalOutgoingAttachmentRow(uniqueMessageId, attachmentModel);

            // Generate the sender's local thumbnail up-front from the picker
            // URI so the chat row shows a preview immediately — no need to
            // wait for the upload to finish. The worker's post-upload
            // pipeline reuses this file (reads LOCAL_THUMBNAIL_PATH from the
            // row) instead of regenerating. Runs on the executor so picker
            // UI doesn't stutter on large PDFs / 4K images.
            final String mediaUriStr = attachmentModel.getMedia();
            final String contentTypeForThumb = attachmentModel.getContentType();
            executorService.execute(() -> {
                try {
                    if (mediaUriStr == null || mediaUriStr.isEmpty()) return;
                    android.net.Uri thumbSourceUri = android.net.Uri.parse(mediaUriStr);
                    java.io.File thumbFile = com.jippytalk.Messages.Attachment.ThumbnailGenerator
                            .generateThumbnail(MessagingActivity.this,
                                    thumbSourceUri, contentTypeForThumb);
                    if (thumbFile != null && thumbFile.exists()) {
                        persistThumbnailPathOnRow(uniqueMessageId,
                                thumbFile.getAbsolutePath());
                    }
                } catch (Throwable t) {
                    Log.e(Extras.LOG_MESSAGE,
                            "Early thumbnail generation failed: " + t.getMessage());
                }
            });

            // Route upload through WorkManager. The worker rebuilds the
            // AttachmentModel from the DB row and does the full encrypt →
            // S3 → thumbnail → WS-send pipeline, surviving the activity
            // being paused, backgrounded, killed, or the phone rebooting.
            // Retries with exponential backoff on transient failures.
            //
            // Deliberately NOT also calling mediaTransferManager.sendAttachment
            // here — that would double-upload (the two MediaTransferManager
            // instances don't share dedup state across process boundaries).
            enqueueMediaUploadWorker(uniqueMessageId, attachmentModel.getName());
        });

        // When upload/download completes → encrypt metadata → send WebSocket events
        mediaTransferManager.setTransferEventListener(new MediaTransferManager.TransferEventListener() {
            @Override
            public void onUploadComplete(String messageId, String s3Key, String bucket, AttachmentModel model) {
                Log.e(Extras.LOG_MESSAGE, "Upload complete for " + messageId + " — generating thumbnail + sending");

                // Compute file size from local copy
                final long fileSize;
                long computedSize   =   0;
                if (model.getLocalFilePath() != null) {
                    java.io.File localFile = new java.io.File(model.getLocalFilePath());
                    if (localFile.exists()) computedSize = localFile.length();
                }
                fileSize = computedSize;

                // Generate thumbnail and upload to S3 in background, then send the WS message
                executorService.execute(() -> {
                    String thumbnailS3Key = "";
                    // Generate thumbnail for PDFs and images
                    java.io.File thumbFile = com.jippytalk.Messages.Attachment.ThumbnailGenerator
                            .generateThumbnail(MessagingActivity.this,
                                    model.getLocalFilePath(), model.getContentType());
                    if (thumbFile != null && thumbFile.exists()) {
                        // Persist local thumbnail path so the sender's own adapter can
                        // display it (via Glide async) without regenerating on main thread.
                        // The sender always views the PLAINTEXT thumbnail.
                        persistThumbnailPathOnRow(messageId, thumbFile.getAbsolutePath());
                        // Upload thumbnail to S3 via presign for receiver side.
                        // Pass the same per-message key+iv so the thumbnail bytes
                        // are AES-256-GCM encrypted before PUT (receiver decrypts).
                        thumbnailS3Key = uploadThumbnailSync(thumbFile, model.getName(),
                                model.getEncryptionKey(), model.getEncryptionIv());
                        Log.e(Extras.LOG_MESSAGE, "Thumbnail uploaded: " + thumbnailS3Key);
                    }
                    model.setThumbnail(thumbnailS3Key);

                    // Send WS message on main thread
                    final long fSize = fileSize;
                    final String fThumbS3Key = thumbnailS3Key;
                    runOnUiThread(() -> {
                        sendFilePlaintextFallback(messageId, s3Key, bucket, model, fSize);
                        if (model.getLocalFilePath() != null && !model.getLocalFilePath().isEmpty()) {
                            persistLocalFilePathOnRow(messageId, model.getLocalFilePath());
                        }
                        // v8: persist S3_KEY / S3_BUCKET / REMOTE_THUMBNAIL_URL on
                        // the row so the chat list + DB stay in sync with the
                        // upload result (no JSON to rewrite).
                        persistMediaUploadResultOnRow(messageId, s3Key, bucket, fThumbS3Key);
                    });
                });
            }

            @Override
            public void onUploadFailed(String messageId, String error) {
                Log.e(Extras.LOG_MESSAGE, "Upload FAILED for " + messageId + ": " + error);
                // Persist failed status so the retry icon shows across app restarts
                if (messagesRepository != null) {
                    messagesRepository.updateMessageAsSyncedWithServer(
                            messageId,
                            MessagesManager.MESSAGE_SEND_FAILED,
                            MessagesManager.NEED_MESSAGE_PUSH,
                            isSuccess -> Log.e(Extras.LOG_MESSAGE,
                                    "DB updated as FAILED for " + messageId + " success=" + isSuccess)
                    );
                }
                // Surface user-friendly errors (like "File too large") as a
                // Toast so the user knows WHY the upload didn't go through,
                // beyond the red retry icon. Filter for the specific strings
                // we want to surface — don't Toast every low-level error.
                if (error != null && (error.contains("File too large")
                        || error.contains("No internet connection"))) {
                    final String msg = error;
                    runOnUiThread(() -> android.widget.Toast.makeText(
                            MessagingActivity.this, msg,
                            android.widget.Toast.LENGTH_LONG).show());
                }
            }

            @Override
            public void onDownloadComplete(String messageId, String localFilePath, String fileTransferId) {
                Log.e(Extras.LOG_MESSAGE, "Download complete for " + messageId + " → " + localFilePath);
                if (webSocketConnection != null && fileTransferId != null && !fileTransferId.isEmpty()) {
                    webSocketConnection.sendFileDownloaded(fileTransferId);
                }
                // Patch the row's metadata JSON with local_file_path and refresh the adapter
                persistLocalFilePathOnRow(messageId, localFilePath);
            }
        });

        // When user taps cancel/retry/download buttons in the adapter
        adapter.setMediaTransferClickListener(new MessageAdapter.OnMediaTransferClickListener() {
            @Override
            public void onCancelUpload(String messageId) {
                // Cancel the WorkManager job. The worker checks isStopped()
                // and aborts; MediaTransferManager inside the worker also
                // sees the cancelled state via its AtomicBoolean.
                try {
                   WorkManager.getInstance(
                            MessagingActivity.this.getApplicationContext())
                            .cancelUniqueWork(com.jippytalk.Messages.Attachment.Upload
                                    .MediaUploadWorker.uniqueNameForMessage(messageId));
                } catch (Exception e) {
                    Log.e(Extras.LOG_MESSAGE, "cancelUpload via WorkManager: "
                            + e.getMessage());
                }
                // Also cancel any in-process upload (belt-and-braces for
                // legacy rows that may still be mid-upload via the old path)
                mediaTransferManager.cancelUpload(messageId);
            }

            @Override
            public void onRetryUpload(String messageId) {
                // Re-enqueue with REPLACE policy — any pending worker for
                // this message is cancelled and a fresh one starts. The
                // fresh worker reads the DB row and reruns the full
                // encrypt → S3 → thumbnail → WS pipeline.
                MessageModal row = null;
                for (MessageModal m : adapter.getCurrentList()) {
                    if (messageId.equals(m.getMessageId())) { row = m; break; }
                }
                String fileNameHint = row != null ? row.getFileName() : "";
                enqueueMediaUploadWorker(messageId, fileNameHint,
                        ExistingWorkPolicy.REPLACE);
            }

            @Override
            public void onDownload(String messageId) {
                triggerAttachmentDownload(messageId);
            }

            @Override
            public void onRetryDownload(String messageId) {
                mediaTransferManager.retryDownload(messageId);
            }

            @Override
            public void onOpenFile(String messageId, String localFilePath) {
                openFileWithSystemViewer(localFilePath);
            }

            @Override
            public void onRetryTextMessage(String messageId, String messageText) {
                Log.e(Extras.LOG_MESSAGE, "Retry text message: " + messageId);
                if (webSocketConnection != null) {
                    webSocketConnection.sendPlainTextMessage(messageId, contactId, messageText);
                }
            }
        });
    }

    /**
     * Looks up a message in the adapter list, parses its media metadata JSON,
     * and kicks off the S3 download. Called when the user taps the download
     * overlay on a received file.
     */
    private void triggerAttachmentDownload(String messageId) {
        MessageModal row = null;
        for (MessageModal m : adapter.getCurrentList()) {
            if (messageId.equals(m.getMessageId())) { row = m; break; }
        }
        if (row == null) {
            Log.e(Extras.LOG_MESSAGE, "Download: no row for " + messageId);
            return;
        }
        try {
            // v8: read directly from the model fields populated from columns.
            String downloadUrl      =   row.getEncryptedS3Url();
            String s3Key            =   row.getS3Key();
            String fileName         =   row.getFileName();
            // Map adapter-side type code back to the bucket folder name
            int    typeCode         =   row.getMessageType();
            String contentType      =   typeCode == MessagesManager.IMAGE_MESSAGE ? "image"
                                      : typeCode == MessagesManager.VIDEO_MESSAGE ? "video"
                                      : typeCode == MessagesManager.AUDIO_MESSAGE ? "audio"
                                      : "document";
            String fileTransferId   =   row.getFileTransferId();
            // Per-message AES key+iv for decrypting the downloaded ciphertext.
            // Empty strings mean the file is plaintext (legacy).
            String encKey           =   row.getEncryptionKey();
            String encIv            =   row.getEncryptionIv();

            // Backend-provided encrypted_s3_url is preferred. If it's empty (not yet
            // wired on the server), construct a bucket/region fallback URL from the
            // s3_key. Remove once backend populates encrypted_s3_url consistently.
            if ((downloadUrl == null || downloadUrl.isEmpty()) && s3Key != null && !s3Key.isEmpty()) {
                downloadUrl = "https://" + API.S3_BUCKET + ".s3." + API.S3_REGION
                        + ".amazonaws.com/" + s3Key;
            }

            if (downloadUrl == null || downloadUrl.isEmpty()) {
                Log.e(Extras.LOG_MESSAGE, "Download: no URL for " + messageId);
                return;
            }

            Log.e(Extras.LOG_MESSAGE, "Download starting for " + messageId
                    + " (" + fileName + ") from " + downloadUrl
                    + " encrypted=" + (encKey != null && !encKey.isEmpty()));
            mediaTransferManager.downloadAttachment(messageId, downloadUrl,
                    fileName, contentType, fileTransferId, encKey, encIv);
        } catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "Failed to start attachment download: " + e.getMessage());
        }
    }

    /**
     * v8: Updates the LOCAL_FILE_PATH column on a message row after a successful
     * upload (sender) or download (receiver). The DAO updates the column, then
     * we refresh the messages list so the adapter rebinds with the new path.
     * Falls back to legacy JSON-blob mutation for any rows that still carry
     * the v7 JSON layout in the `message` column.
     */
    private void persistLocalFilePathOnRow(String messageId, String localFilePath) {
        executorService.execute(() -> {
            try {
                com.jippytalk.Database.MessagesDatabase.MessagesDatabaseDAO dao =
                        MyApplication.getInstance().getDatabaseServiceLocator().getMessagesDatabaseDAO();

                // Update the dedicated LOCAL_FILE_PATH column (v8+ row).
                boolean updated = dao.updateLocalFilePath(messageId, localFilePath);

                // Legacy fallback: if the row still holds a JSON blob in `message`
                // (created on v7), patch the JSON too so old rows keep working.
                String existingBody = dao.getMessageBodyById(messageId);
                if (existingBody != null && existingBody.startsWith("{")) {
                    try {
                        org.json.JSONObject json = new org.json.JSONObject(existingBody);
                        json.put("local_file_path", localFilePath);
                        dao.updateMessageContent(messageId, json.toString());
                    } catch (Exception ignored) {}
                }

                Log.e(Extras.LOG_MESSAGE, "persistLocalFilePath: updated " + messageId
                        + " → " + localFilePath + " columnUpdate=" + updated);

                runOnUiThread(() -> messagesViewModel.retrieveAllMessagesOfContact(contactId));
            } catch (Exception e) {
                Log.e(Extras.LOG_MESSAGE, "Failed to persist local_file_path: " + e.getMessage());
            }
        });
    }

    // Tracks thumbnails currently being fetched + decrypted on the receiver side
    // so we don't re-fetch them on every list refresh.
    private final java.util.concurrent.ConcurrentHashMap<String, Boolean> fetchingThumbnails =
            new java.util.concurrent.ConcurrentHashMap<>();

    // Tracks the active WorkInfo observer per messageId so we can detach a
    // stale observer before attaching a fresh one on retry. Without this, the
    // old work's CANCELLED transition (from ExistingWorkPolicy.REPLACE) fires
    // after the new worker is already seeded as IN_PROGRESS and overrides the
    // row back to FAILED → user sees the retry icon flash right after tapping
    // retry.
    private final java.util.HashMap<String,
            androidx.lifecycle.LiveData<WorkInfo>> uploadWorkLiveData =
                    new java.util.HashMap<>();
    private final java.util.HashMap<String,
            androidx.lifecycle.Observer<WorkInfo>> uploadWorkObservers =
                    new java.util.HashMap<>();

    /**
     * Rebuilds the plaintext S3 thumbnail URL from row fields when the stored
     * REMOTE_THUMBNAIL_URL column doesn't contain a full http URL (e.g. the
     * sender encrypted only the s3 key, or a legacy row was migrated). Falls
     * back to API.S3_BUCKET / API.S3_REGION when the row's bucket is empty.
     */
    private String buildThumbnailUrlFromRow(MessageModal m) {
        try {
            // If the column holds a bare S3 key, promote it to a full URL.
            String raw = m.getRemoteThumbnailUrl();
            String bucket = m.getS3Bucket();
            if (bucket == null || bucket.isEmpty()) bucket = API.S3_BUCKET;
            if (raw != null && !raw.isEmpty() && !raw.startsWith("http")) {
                return "https://" + bucket + ".s3." + API.S3_REGION
                        + ".amazonaws.com/" + raw;
            }
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * Receiver-side: scan messages for incoming file rows whose thumbnail bytes
     * are encrypted on S3 (we have a remote `thumbnail` URL + `encryption_key`
     * + `encryption_iv` but no `local_thumbnail_path`). For each such row,
     * download the ciphertext bytes, decrypt locally, save to cache, and
     * persist the local path so the adapter can display it via Glide async.
     */
    private void autoFetchEncryptedThumbnails(java.util.List<MessageModal> messages) {
        if (messages == null || messages.isEmpty()) return;
        int scanned = 0, launched = 0;
        for (MessageModal m : messages) {
            // Only process incoming file rows
            if (m.getMessageDirection() != MessagesManager.MESSAGE_INCOMING) continue;
            int t = m.getMessageType();
            if (t != MessagesManager.IMAGE_MESSAGE
                    && t != MessagesManager.VIDEO_MESSAGE
                    && t != MessagesManager.DOCUMENT_MESSAGE) continue;

            try {
                // v8: read directly from the model — populateMediaFieldsFromCursor
                // (or populateMediaFieldsFromJson for legacy rows) has already
                // populated these fields from the DB columns / JSON blob.
                String remoteThumbUrl  = m.getRemoteThumbnailUrl();
                String localThumbPath  = m.getThumbnailUri();
                String encKey          = m.getEncryptionKey();
                String encIv           = m.getEncryptionIv();
                scanned++;

                // Already have a local plaintext copy — nothing to do
                if (localThumbPath != null && !localThumbPath.isEmpty()
                        && localThumbPath.startsWith("/")
                        && new java.io.File(localThumbPath).exists()) continue;

                // If the stored remote URL isn't a full http URL, try to
                // reconstruct one from s3_key + bucket on the row. Older
                // sends (and backends that give back only the key) leave
                // the column as an opaque key like "uploads/.../thumb.png".
                if (remoteThumbUrl == null || remoteThumbUrl.isEmpty()
                        || !remoteThumbUrl.startsWith("http")) {
                    String rebuilt = buildThumbnailUrlFromRow(m);
                    if (rebuilt != null && rebuilt.startsWith("http")) {
                        remoteThumbUrl = rebuilt;
                    } else {
                        continue;
                    }
                }
                if (encKey == null || encKey.isEmpty() || encIv == null || encIv.isEmpty()) continue;

                final String messageId = m.getMessageId();
                if (messageId == null || messageId.isEmpty()) continue;
                // De-dupe in-flight fetches across observer refreshes
                if (fetchingThumbnails.putIfAbsent(messageId, Boolean.TRUE) != null) continue;
                launched++;

                final String fThumbUrl = remoteThumbUrl;
                final String fEncKey   = encKey;
                final String fEncIv    = encIv;

                executorService.execute(() -> {
                    java.io.File encTemp = null;
                    try {
                        // Download ciphertext to a cache temp file
                        encTemp = new java.io.File(getCacheDir(),
                                "enc_dlthumb_" + messageId + "_" + System.currentTimeMillis() + ".bin");
                        java.net.HttpURLConnection conn =
                                (java.net.HttpURLConnection) new java.net.URL(fThumbUrl).openConnection();
                        conn.setRequestMethod("GET");
                        conn.setConnectTimeout(8000);
                        conn.setReadTimeout(20000);
                        conn.connect();

                        int code = conn.getResponseCode();
                        if (code < 200 || code >= 300) {
                            Log.e(Extras.LOG_MESSAGE, "Thumb fetch HTTP " + code + " for " + messageId);
                            conn.disconnect();
                            return;
                        }

                        try (java.io.InputStream in = conn.getInputStream();
                             java.io.FileOutputStream out = new java.io.FileOutputStream(encTemp)) {
                            byte[] buf = new byte[8192];
                            int n;
                            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
                        }
                        conn.disconnect();

                        java.io.File plainThumb = new java.io.File(getCacheDir(),
                                "thumb_recv_" + messageId + ".png");

                        // Try to AES-GCM decrypt the downloaded bytes with
                        // the per-message key+iv. If decryption fails with
                        // BAD_DECRYPT, the bytes on S3 were uploaded by an
                        // OLDER client build that didn't encrypt thumbnails —
                        // the file is already plaintext, so just copy it over.
                        boolean decryptedOk = false;
                        try (java.io.FileInputStream cin = new java.io.FileInputStream(encTemp);
                             java.io.FileOutputStream pout = new java.io.FileOutputStream(plainThumb)) {
                            com.jippytalk.Encryption.MessageCryptoHelper.decryptStream(
                                    cin, pout, fEncKey, fEncIv);
                            decryptedOk = true;
                        } catch (javax.crypto.AEADBadTagException badTag) {
                            // Legacy / plaintext thumbnail — fall through to copy
                            Log.e(Extras.LOG_MESSAGE, "Thumb for " + messageId
                                    + " failed GCM auth → treating as plaintext "
                                    + "(uploaded by older client build)");
                        } catch (Exception decryptErr) {
                            // Same fallback for any other decrypt error — the
                            // bytes might still be a valid PNG even if our
                            // key/iv guess is wrong.
                            Log.e(Extras.LOG_MESSAGE, "Thumb for " + messageId
                                    + " decrypt error (" + decryptErr.getClass().getSimpleName()
                                    + ") → treating as plaintext");
                        }

                        if (!decryptedOk) {
                            // Copy the raw downloaded bytes directly as the
                            // "plaintext" thumbnail — if they're a valid image
                            // Glide will render them; if not, Glide will quietly
                            // fail and the adapter will show the doc icon.
                            if (plainThumb.exists()) {
                                //noinspection ResultOfMethodCallIgnored
                                plainThumb.delete();
                            }
                            try (java.io.FileInputStream cin = new java.io.FileInputStream(encTemp);
                                 java.io.FileOutputStream pout = new java.io.FileOutputStream(plainThumb)) {
                                byte[] buf2 = new byte[8192];
                                int n2;
                                while ((n2 = cin.read(buf2)) != -1) pout.write(buf2, 0, n2);
                            }
                        }

                        Log.e(Extras.LOG_MESSAGE, "Thumb ready for " + messageId
                                + " → " + plainThumb.getAbsolutePath()
                                + " (" + plainThumb.length() + " bytes, "
                                + (decryptedOk ? "decrypted" : "plaintext-fallback") + ")");

                        // Persist the local plaintext thumbnail path on the row
                        persistThumbnailPathOnRow(messageId, plainThumb.getAbsolutePath());
                    } catch (Exception e) {
                        Log.e(Extras.LOG_MESSAGE, "Auto-thumb fetch failed for "
                                + messageId + ": " + e.getMessage());
                    } finally {
                        if (encTemp != null && encTemp.exists()) {
                            try { encTemp.delete(); } catch (Exception ignored) {}
                        }
                        fetchingThumbnails.remove(messageId);
                    }
                });
            } catch (Exception ignored) {}
        }
        if (scanned > 0) {
            Log.e(Extras.LOG_MESSAGE, "autoFetchEncryptedThumbnails: scanned="
                    + scanned + " launched=" + launched);
        }
    }

    /**
     * v8: Persists the S3 upload result (s3_key, s3_bucket, remote thumbnail URL)
     * on a message row so the chat list / DB always reflect the current sync
     * state. Called from onUploadComplete on the sender side.
     */
    private void persistMediaUploadResultOnRow(String messageId, String s3Key,
                                               String bucket, String thumbnailS3Key) {
        executorService.execute(() -> {
            try {
                com.jippytalk.Database.MessagesDatabase.MessagesDatabaseDAO dao =
                        MyApplication.getInstance().getDatabaseServiceLocator().getMessagesDatabaseDAO();

                // Build the encrypted_s3_url from the S3 key + bucket. The actual
                // bytes at that URL are AES-GCM ciphertext — receiver decrypts.
                String bucketName = bucket != null && !bucket.isEmpty()
                        ? bucket : API.S3_BUCKET;
                String fileUrl = s3Key != null && !s3Key.isEmpty()
                        ? "https://" + bucketName + ".s3."
                                + API.S3_REGION + ".amazonaws.com/" + s3Key
                        : "";
                dao.updateMessageS3Info(messageId, s3Key != null ? s3Key : "",
                        bucketName, fileUrl);

                if (thumbnailS3Key != null && !thumbnailS3Key.isEmpty()) {
                    String thumbUrl = "https://" + bucketName + ".s3."
                            + API.S3_REGION + ".amazonaws.com/" + thumbnailS3Key;
                    dao.updateRemoteThumbnailUrl(messageId, thumbUrl);
                }

                Log.e(Extras.LOG_MESSAGE, "persistMediaUploadResult: " + messageId
                        + " s3_key=" + s3Key + " thumbKey=" + thumbnailS3Key);

                runOnUiThread(() -> messagesViewModel.retrieveAllMessagesOfContact(contactId));
            } catch (Exception e) {
                Log.e(Extras.LOG_MESSAGE, "Failed to persist media upload result: " + e.getMessage());
            }
        });
    }

    /**
     * v8: Updates the LOCAL_THUMBNAIL_PATH column on a message row after the
     * sender generates a thumbnail OR the receiver auto-fetches and decrypts
     * the encrypted thumbnail from S3. Adapter loads it via Glide async.
     * Falls back to legacy JSON-blob mutation for v7 rows.
     */
    private void persistThumbnailPathOnRow(String messageId, String localThumbPath) {
        executorService.execute(() -> {
            try {
                com.jippytalk.Database.MessagesDatabase.MessagesDatabaseDAO dao =
                        MyApplication.getInstance().getDatabaseServiceLocator().getMessagesDatabaseDAO();

                // Update the dedicated LOCAL_THUMBNAIL_PATH column (v8+ row).
                boolean updated = dao.updateLocalThumbnailPath(messageId, localThumbPath);

                // Legacy fallback: also patch the JSON blob if the row still
                // carries the v7 layout in the `message` column.
                String existingBody = dao.getMessageBodyById(messageId);
                if (existingBody != null && existingBody.startsWith("{")) {
                    try {
                        org.json.JSONObject json = new org.json.JSONObject(existingBody);
                        json.put("local_thumbnail_path", localThumbPath);
                        dao.updateMessageContent(messageId, json.toString());
                    } catch (Exception ignored) {}
                }

                Log.e(Extras.LOG_MESSAGE, "persistThumbnailPath: updated " + messageId
                        + " → " + localThumbPath + " columnUpdate=" + updated);

                runOnUiThread(() -> messagesViewModel.retrieveAllMessagesOfContact(contactId));
            } catch (Exception e) {
                Log.e(Extras.LOG_MESSAGE, "Failed to persist local_thumbnail_path: " + e.getMessage());
            }
        });
    }

    /**
     * Opens a downloaded attachment with the system's default viewer for its
     * MIME type (Intent.ACTION_VIEW via FileProvider for API 24+ compliance).
     */
    /**
     * Enqueues a MediaUploadWorker for the given message. Uses
     * ExistingWorkPolicy.KEEP by default so enqueues are idempotent —
     * duplicate enqueues for the same message are silently ignored.
     * Network-constrained: only runs when a network is connected;
     * WorkManager re-runs it automatically when connectivity returns.
     */
    private void enqueueMediaUploadWorker(String messageId, String fileNameHint) {
        enqueueMediaUploadWorker(messageId, fileNameHint,
                ExistingWorkPolicy.KEEP);
    }

    private void enqueueMediaUploadWorker(String messageId, String fileNameHint,
                                          ExistingWorkPolicy policy) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        OneTimeWorkRequest req = new OneTimeWorkRequest.Builder(
                com.jippytalk.Messages.Attachment.Upload.MediaUploadWorker.class)
                .setConstraints(constraints)
                .setInputData(com.jippytalk.Messages.Attachment.Upload
                        .MediaUploadWorker.buildInput(messageId, contactId, fileNameHint))
                .setBackoffCriteria(
                        BackoffPolicy.EXPONENTIAL,
                        WorkRequest.MIN_BACKOFF_MILLIS,
                        java.util.concurrent.TimeUnit.MILLISECONDS)
                .addTag("media-upload")
                .addTag("msg:" + messageId)
                .build();

        try {
            WorkManager.getInstance(getApplicationContext())
                    .enqueueUniqueWork(
                            com.jippytalk.Messages.Attachment.Upload
                                    .MediaUploadWorker.uniqueNameForMessage(messageId),
                            policy,
                            req);
            Log.e(Extras.LOG_MESSAGE, "enqueueMediaUploadWorker: " + messageId
                    + " (" + fileNameHint + ") policy=" + policy);

            // Seed the adapter's transfer state immediately so the row shows
            // the upload spinner (progress overlay / pill) instead of the
            // retry icon while WorkManager scheduling + the worker cold-start
            // run. Without this, the row sits at NOT_SYNCED with no transfer
            // state, which the adapter renders as "failed → retry".
            if (adapter != null) {
                adapter.updateTransferProgress(messageId, 0,
                        MessageAdapter.TRANSFER_IN_PROGRESS);
            }

            // Observe the WorkInfo for this upload so we can flip the adapter
            // state to COMPLETED / FAILED as the worker transitions. This also
            // covers the case where the worker is running in a separate
            // MediaTransferManager instance (so our activity-level listener
            // never fires) — WorkInfo is the single source of truth.
            observeUploadWorkInfo(messageId, req.getId());
        } catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "enqueueMediaUploadWorker failed: " + e.getMessage());
        }
    }

    /**
     * Observes the given work's LiveData and drives the adapter transfer
     * state from the WorkInfo state transitions. Running → IN_PROGRESS,
     * SUCCEEDED → COMPLETED, FAILED/CANCELLED → FAILED. Observer self-removes
     * once the work reaches a terminal state.
     */
    private void observeUploadWorkInfo(String messageId, java.util.UUID workId) {
        try {
            // Detach any previous observer for this messageId so the old
            // work's terminal-state transition (CANCELLED after REPLACE, or
            // FAILED after the user's first attempt) can't override the
            // IN_PROGRESS we just seeded for the new worker.
            detachUploadWorkObserver(messageId);

            final androidx.lifecycle.LiveData<WorkInfo> live =
                    WorkManager.getInstance(getApplicationContext())
                            .getWorkInfoByIdLiveData(workId);
            final Observer<WorkInfo> obs = new Observer<WorkInfo>() {
                @Override
                public void onChanged(WorkInfo info) {
                    if (info == null || adapter == null) return;
                    switch (info.getState()) {
                        case ENQUEUED:
                        case RUNNING:
                        case BLOCKED:
                            // Read live percentage published by the worker via
                            // setProgressAsync. Falls back to 0 (just shows
                            // the bar at empty) when the worker hasn't
                            // emitted progress yet.
                            int pct = 0;
                            try {
                                pct = info.getProgress().getInt("percent", 0);
                            } catch (Throwable ignored) {}
                            adapter.updateTransferProgress(messageId, pct,
                                    MessageAdapter.TRANSFER_IN_PROGRESS);
                            break;
                        case SUCCEEDED:
                            adapter.markTransferComplete(messageId);
                            detachUploadWorkObserver(messageId);
                            break;
                        case FAILED:
                        case CANCELLED:
                            adapter.markTransferFailed(messageId);
                            detachUploadWorkObserver(messageId);
                            break;
                    }
                }
            };
            uploadWorkLiveData.put(messageId, live);
            uploadWorkObservers.put(messageId, obs);
            live.observe(this, obs);
        } catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "observeUploadWorkInfo: " + e.getMessage());
        }
    }

    /**
     * Detaches and forgets any currently-registered WorkInfo observer for
     * the given messageId. Safe to call when nothing is registered.
     */
    private void detachUploadWorkObserver(String messageId) {
        androidx.lifecycle.LiveData<WorkInfo> oldLive = uploadWorkLiveData.remove(messageId);
        Observer<WorkInfo> oldObs = uploadWorkObservers.remove(messageId);
        if (oldLive != null && oldObs != null) {
            try { oldLive.removeObserver(oldObs); } catch (Exception ignored) {}
        }
    }

    /**
     * Rebuilds an AttachmentModel from the DB row and retries the upload.
     * Used when the app was restarted and the in-memory pendingUploads map is
     * empty. v8: reads media fields from the model (populated from real columns
     * by populateMediaFieldsFromCursor) — no JSON parsing.
     */
    private void rebuildAndRetryUpload(String messageId) {
        MessageModal row = null;
        for (MessageModal m : adapter.getCurrentList()) {
            if (messageId.equals(m.getMessageId())) { row = m; break; }
        }
        if (row == null) {
            Log.e(Extras.LOG_MESSAGE, "Retry: cannot find row for " + messageId);
            return;
        }
        try {
            String  localPath   =   row.getMediaUri();   // local_file_path or s3_key
            String  fileName    =   row.getFileName();
            int     typeCode    =   row.getMessageType();
            String  contentType =   typeCode == MessagesManager.IMAGE_MESSAGE ? "image"
                                  : typeCode == MessagesManager.VIDEO_MESSAGE ? "video"
                                  : typeCode == MessagesManager.AUDIO_MESSAGE ? "audio"
                                  : "document";
            String  subtype     =   row.getContentSubtype();
            String  caption     =   row.getCaption();
            int     width       =   row.getMediaWidth();
            int     height      =   row.getMediaHeight();
            long    duration    =   row.getMediaDuration();
            String  encKey      =   row.getEncryptionKey();
            String  encIv       =   row.getEncryptionIv();

            if (localPath == null || localPath.isEmpty()
                    || !localPath.startsWith("/")
                    || !new java.io.File(localPath).exists()) {
                Log.e(Extras.LOG_MESSAGE, "Retry: local file missing at " + localPath);
                android.widget.Toast.makeText(this, "File not found, cannot retry",
                        android.widget.Toast.LENGTH_SHORT).show();
                return;
            }

            AttachmentModel model = new AttachmentModel(
                    android.net.Uri.fromFile(new java.io.File(localPath)).toString(),
                    "", contentType, subtype, caption,
                    height, width, duration, "", "", fileName
            );
            model.setLocalFilePath(localPath);
            // Re-attach the SAME per-message AES key+iv so the retried upload
            // produces ciphertext that the receiver can still decrypt with the
            // key+iv that will accompany the WS payload.
            model.setEncryptionKey(encKey);
            model.setEncryptionIv(encIv);

            Log.e(Extras.LOG_MESSAGE, "Retry: rebuilt model from DB for " + messageId
                    + " localPath=" + localPath);
            mediaTransferManager.retryUploadWithModel(messageId, model);

        } catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "Retry: failed to rebuild model: " + e.getMessage());
        }
    }

    /**
     * Synchronously uploads a thumbnail file to S3 via presign.
     * Runs on a background thread. Returns the S3 key on success, empty string on failure.
     */
    /**
     * Synchronously uploads a thumbnail file to S3 via presign. If b64Key + b64Iv
     * are non-empty, the bytes are AES-256-GCM encrypted before PUT — the receiver
     * decrypts using the same key+iv from the WS payload. Pass empty strings to
     * upload plaintext (legacy path).
     */
    private String uploadThumbnailSync(java.io.File thumbFile, String originalName,
                                       String b64Key, String b64Iv) {
        java.io.File encThumbFile = null;
        try {
            String jwtToken = sharedPreferences.getString(
                    com.jippytalk.Managers.SharedPreferenceDetails.JWT_TOKEN, "");
            // Thumbnail S3 object name is AES-256-GCM encrypted with this
            // message's per-message key, so neither the original filename
            // nor the file type appears in the S3 console. Fresh IV per
            // call is embedded in the encoded name (content IV cannot be
            // reused — GCM is insecure under nonce reuse). No extension is
            // appended; the S3 PUT already uses application/octet-stream
            // for encrypted payloads, and the receiver reads the real
            // filename from the Signal-encrypted WebSocket metadata.
            // Legacy fallback keeps uploads working when no per-message
            // key is available.
            String thumbEncName = (b64Key != null && !b64Key.isEmpty())
                    ? com.jippytalk.Encryption.MessageCryptoHelper
                        .encryptFilenameForS3(originalName != null ? originalName : "file",
                                              b64Key)
                    : null;
            String thumbName = (thumbEncName != null)
                    ? thumbEncName
                    : ("thumb_" + System.currentTimeMillis() + "_"
                            + (originalName != null ? originalName : "file") + ".png");

            // Encrypt thumbnail bytes to a temp cache file if encryption keys
            // are present. Sender's local plaintext thumbnail is preserved.
            java.io.File uploadFile = thumbFile;
            if (b64Key != null && !b64Key.isEmpty() && b64Iv != null && !b64Iv.isEmpty()) {
                encThumbFile = new java.io.File(getCacheDir(),
                        "enc_thumb_" + System.currentTimeMillis() + ".bin");
                try (java.io.FileInputStream pin = new java.io.FileInputStream(thumbFile);
                     java.io.FileOutputStream cout = new java.io.FileOutputStream(encThumbFile)) {
                    com.jippytalk.Encryption.MessageCryptoHelper.encryptStream(
                            pin, cout, b64Key, b64Iv);
                }
                uploadFile = encThumbFile;
                Log.e(Extras.LOG_MESSAGE, "Thumbnail encrypted: plaintext=" + thumbFile.length()
                        + " ciphertext=" + encThumbFile.length());
            }

            long uploadSize = uploadFile.length();

            java.net.URL presignUrl = new java.net.URL(API.FILES_PRESIGN);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) presignUrl.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + jwtToken);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(10000);

            org.json.JSONObject body = new org.json.JSONObject();
            body.put("file_name", thumbName);
            body.put("file_size", uploadSize);
            try (java.io.OutputStreamWriter w = new java.io.OutputStreamWriter(conn.getOutputStream())) {
                w.write(body.toString());
                w.flush();
            }

            int status = conn.getResponseCode();
            if (status < 200 || status >= 300) {
                Log.e(Extras.LOG_MESSAGE, "Thumb presign failed: " + status);
                conn.disconnect();
                return "";
            }

            StringBuilder resp = new StringBuilder();
            try (java.io.BufferedReader r = new java.io.BufferedReader(
                    new java.io.InputStreamReader(conn.getInputStream()))) {
                String line;
                while ((line = r.readLine()) != null) resp.append(line);
            }
            conn.disconnect();

            org.json.JSONObject presignResp = new org.json.JSONObject(resp.toString());
            String uploadUrl = presignResp.optString("upload_url",
                    presignResp.optString("presigned_url", ""));
            String thumbS3Key = presignResp.optString("s3_key", "");

            if (uploadUrl.isEmpty()) return "";

            java.net.HttpURLConnection s3Conn =
                    (java.net.HttpURLConnection) new java.net.URL(uploadUrl).openConnection();
            s3Conn.setRequestMethod("PUT");
            s3Conn.setDoOutput(true);
            // Encrypted blobs are opaque — use octet-stream
            s3Conn.setRequestProperty("Content-Type",
                    encThumbFile != null ? "application/octet-stream" : "image/png");
            s3Conn.setFixedLengthStreamingMode(uploadSize);
            s3Conn.setConnectTimeout(5000);
            s3Conn.setReadTimeout(30000);

            try (java.io.FileInputStream fis = new java.io.FileInputStream(uploadFile);
                 java.io.OutputStream os = s3Conn.getOutputStream()) {
                byte[] buf = new byte[8192];
                int read;
                while ((read = fis.read(buf)) != -1) os.write(buf, 0, read);
                os.flush();
            }

            int s3Status = s3Conn.getResponseCode();
            s3Conn.disconnect();
            return s3Status == 200 ? thumbS3Key : "";

        } catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "Thumb upload failed: " + e.getMessage());
            return "";
        } finally {
            if (encThumbFile != null && encThumbFile.exists()) {
                try { encThumbFile.delete(); } catch (Exception ignored) {}
            }
        }
    }

    private void openFileWithSystemViewer(String localFilePath) {
        try {
            java.io.File file = new java.io.File(localFilePath);
            if (!file.exists()) {
                android.widget.Toast.makeText(this, "File not found", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(
                    this, getPackageName() + ".fileprovider", file);
            String mime = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                    android.webkit.MimeTypeMap.getFileExtensionFromUrl(localFilePath));
            if (mime == null) mime = "*/*";

            android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW);
            intent.setDataAndType(uri, mime);
            intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "Failed to open file: " + e.getMessage());
            android.widget.Toast.makeText(this, "No app to open this file", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Scans the message list for incoming messages that haven't been marked as read
     * yet, collects their IDs, and sends a single mark_read event to the server.
     * Called each time the messages observer fires (chat load + real-time arrivals).
     */
    private void sendMarkReadForVisibleMessages(java.util.ArrayList<MessageModal> messages) {
        if (webSocketConnection == null || roomId == null || roomId.isEmpty()) return;

        java.util.List<String> unreadIds = new java.util.ArrayList<>();
        for (MessageModal msg : messages) {
            if (msg.getMessageDirection() == MessagesManager.MESSAGE_INCOMING
                    && msg.getMessageStatus() != MessagesManager.MESSAGE_SEEN
                    && msg.getMessageStatus() != MessagesManager.MESSAGE_SEEN_LOCALLY) {
                unreadIds.add(msg.getMessageId());
            }
        }

        if (!unreadIds.isEmpty()) {
            Log.e(Extras.LOG_MESSAGE, "Sending mark_read for " + unreadIds.size() + " messages");
            webSocketConnection.sendMarkRead(roomId, unreadIds);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mediaTransferManager != null) {
            mediaTransferManager.cancelAll();
        }
    }
}
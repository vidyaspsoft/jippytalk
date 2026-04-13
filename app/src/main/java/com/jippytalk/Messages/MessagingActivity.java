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
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
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
import com.jippytalk.UpdatedSignalProtocolStore;
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

import org.signal.libsignal.protocol.SignalProtocolAddress;

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
    private SignalProtocolAddress               signalProtocolAddress;
    private UpdatedSignalProtocolStore          customSignalProtocolStore;
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
    private boolean                             isLoadingMore                       =   false;
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
        customSignalProtocolStore               =   appServiceLocator.getSignalProtocolStore();
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
            isLoadingMore = false;
            Log.e(Extras.LOG_MESSAGE,"getting messages");
            activityMessagingBinding.rvChats.setVisibility(VISIBLE);
            activityMessagingBinding.cvStartChat.setVisibility(GONE);
            adapter.submitList(new ArrayList<>(messageModalArrayList), () -> {
                if (needScrollToBottom) {
                    scrollToLastItem();
                }
            });

            // Send mark_read for any incoming messages that are DELIVERED but not yet
            // SEEN. The chat is visible so the user has "read" them.
            sendMarkReadForVisibleMessages(messageModalArrayList);
        });
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
                        // Build metadata JSON from the server's file fields.
                        // The ciphertext now only contains the encrypted caption,
                        // not the full metadata blob — so we read file fields from
                        // the top-level ServerMessage fields.
                        org.json.JSONObject fileJson = new org.json.JSONObject();
                        try {
                            fileJson.put("file_name", msg.fileName != null && !msg.fileName.isEmpty()
                                    ? msg.fileName : "(attachment)");
                            fileJson.put("s3_key", msg.s3Key != null ? msg.s3Key : "");
                            fileJson.put("content_type", msg.contentType != null ? msg.contentType : "document");
                            fileJson.put("content_subtype", msg.contentSubtype != null ? msg.contentSubtype : "");
                            fileJson.put("file_transfer_id", msg.fileTransferId != null ? msg.fileTransferId : "");
                            fileJson.put("encrypted_s3_url", msg.encryptedS3Url != null ? msg.encryptedS3Url : "");
                            fileJson.put("thumbnail", msg.thumbnail != null ? msg.thumbnail : "");
                            fileJson.put("file_size", msg.fileSize);
                            fileJson.put("caption", decryptedBody);
                            fileJson.put("width", msg.width);
                            fileJson.put("height", msg.height);
                            fileJson.put("duration", msg.duration);
                            fileJson.put("local_file_path", "");
                        } catch (Exception ignored) {}
                        bodyToStore = fileJson.toString();
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
                            0,  // receive_timestamp
                            0,  // read_timestamp
                            MessagesManager.MESSAGE_NOT_STARRED,
                            MessagesManager.MESSAGE_NOT_EDITED,
                            localMessageType,
                            0,  // latitude
                            0,  // longitude
                            MessagesManager.DEFAULT_MSG_TO_MSG_REPLY,
                            "",
                            ChatManager.UNARCHIVE_CHAT
                    );

                    if (inserted) {
                        insertedCount++;
                    }
                } catch (Exception e) {
                    Log.e(Extras.LOG_MESSAGE, "Failed to insert server message "
                            + msg.id + ": " + e.getMessage());
                }
            }

            Log.e(Extras.LOG_MESSAGE, "Inserted " + insertedCount + "/" + serverMessages.size()
                    + " server messages into local DB");

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
                                    model.getBucket()
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
                + " clientMsgId=" + localMessageId);
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
                bucket != null ? bucket : model.getBucket()
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

        // Build the JSON metadata that the repository will parse back into the
        // MessageModal's media fields on re-read. local_file_path is set once
        // the file has been copied into the sent/ folder (we set it below).
        org.json.JSONObject json = new org.json.JSONObject();
        try {
            json.put("file_name", attachmentModel.getName() != null ? attachmentModel.getName() : "");
            json.put("content_type", attachmentModel.getContentType() != null ? attachmentModel.getContentType() : "");
            json.put("content_subtype", attachmentModel.getContentSubtype() != null ? attachmentModel.getContentSubtype() : "");
            json.put("caption", attachmentModel.getCaption() != null ? attachmentModel.getCaption() : "");
            json.put("width", attachmentModel.getWidth());
            json.put("height", attachmentModel.getHeight());
            json.put("duration", attachmentModel.getDuration());
            json.put("thumbnail", attachmentModel.getThumbnail() != null ? attachmentModel.getThumbnail() : "");
            json.put("s3_key", "");
            json.put("local_file_path", attachmentModel.getLocalFilePath() != null
                    ? attachmentModel.getLocalFilePath() : attachmentModel.getMedia());
            json.put("encrypted_s3_url", "");
            // Compute file size from the content URI
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
            json.put("file_size", size);
        } catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "Failed to build local attachment JSON: " + e.getMessage());
        }

        messagesRepository.insertMessageToLocalStorageFromService(
                localMessageId,
                MessagesManager.MESSAGE_OUTGOING,
                contactId,
                json.toString(),
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
                () -> messagesViewModel.retrieveAllMessagesOfContact(contactId)
        );
    }

    /** Maps AttachmentModel.contentType to the MessagesManager constant. */
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

            // Insert an outgoing row in the local DB immediately so the sent
            // attachment appears in the chat UI with an upload spinner. The
            // row carries a JSON blob with the attachment metadata; the repo
            // parses it back out in populateMediaFieldsFromJson().
            insertLocalOutgoingAttachmentRow(uniqueMessageId, attachmentModel);

            // Start the upload flow: copy to sent/ → upload to S3 → callback
            mediaTransferManager.sendAttachment(uniqueMessageId, attachmentModel);
        });

        // When upload/download completes → encrypt metadata → send WebSocket events
        mediaTransferManager.setTransferEventListener(new MediaTransferManager.TransferEventListener() {
            @Override
            public void onUploadComplete(String messageId, String s3Key, String bucket, AttachmentModel model) {
                Log.e(Extras.LOG_MESSAGE, "Upload complete for " + messageId + " — sending plaintext metadata");

                // Compute file size from local copy
                final long fileSize;
                long computedSize   =   0;
                if (model.getLocalFilePath() != null) {
                    java.io.File localFile = new java.io.File(model.getLocalFilePath());
                    if (localFile.exists()) computedSize = localFile.length();
                }
                fileSize = computedSize;

                // ENCRYPTION TEMPORARILY DISABLED — skip key bundle fetch / Signal encryption.
                // Send attachment metadata as plain JSON. Re-enable the block below once the
                // backend key bundle endpoint is live.
                //
                // if (contactDeviceId == -1 || contactDeviceId == 0
                //         || !customSignalProtocolStore.containsSession(
                //                 new SignalProtocolAddress(contactId, contactDeviceId))) {
                //     KeyBundleFetcher.getInstance(MessagingActivity.this).fetchAndBuildSession(
                //             contactId, contactName, "",
                //             new KeyBundleFetcher.SessionCallback() {
                //                 @Override public void onSessionBuilt(String targetUserId, int deviceId) {
                //                     contactDeviceId = deviceId;
                //                     encryptAndSendFileMessage(s3Key, bucket, model, fileSize);
                //                 }
                //                 @Override public void onFailure(String error) {
                //                     sendFilePlaintextFallback(s3Key, bucket, model, fileSize);
                //                 }
                //             });
                // } else {
                //     encryptAndSendFileMessage(s3Key, bucket, model, fileSize);
                // }
                sendFilePlaintextFallback(messageId, s3Key, bucket, model, fileSize);

                // Patch the local row's JSON with the real sent/ folder path now
                // that the copy has completed. Before this point the row's
                // `local_file_path` still held the original content:// picker URI
                // which didn't start with "/", so tap-to-open was disabled.
                if (model.getLocalFilePath() != null && !model.getLocalFilePath().isEmpty()) {
                    persistLocalFilePathOnRow(messageId, model.getLocalFilePath());
                }
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
                mediaTransferManager.cancelUpload(messageId);
            }

            @Override
            public void onRetryUpload(String messageId) {
                // First try the in-memory pending map (current session uploads)
                mediaTransferManager.retryUpload(messageId);

                // If that fails (app was restarted, pending map is empty),
                // rebuild the AttachmentModel from the DB row's metadata JSON.
                if (!mediaTransferManager.hasPendingUpload(messageId)) {
                    rebuildAndRetryUpload(messageId);
                }
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
        if (row == null || row.getMessage() == null || !row.getMessage().startsWith("{")) {
            Log.e(Extras.LOG_MESSAGE, "Download: no metadata row for " + messageId);
            return;
        }
        try {
            org.json.JSONObject json = new org.json.JSONObject(row.getMessage());
            String downloadUrl      =   json.optString("encrypted_s3_url", "");
            String s3Key            =   json.optString("s3_key", "");
            String fileName         =   json.optString("file_name", "");
            String contentType      =   json.optString("content_type", "document");
            String fileTransferId   =   json.optString("file_transfer_id", "");

            // Backend-provided encrypted_s3_url is preferred. If it's empty (not yet
            // wired on the server), construct a bucket/region fallback URL from the
            // s3_key — this only works for public buckets and will likely fail for
            // bank-ster-dev. Remove once backend populates encrypted_s3_url.
            if (downloadUrl.isEmpty() && !s3Key.isEmpty()) {
                downloadUrl = "https://" + API.S3_BUCKET + ".s3." + API.S3_REGION
                        + ".amazonaws.com/" + s3Key;
            }

            if (downloadUrl.isEmpty()) {
                Log.e(Extras.LOG_MESSAGE, "Download: no URL for " + messageId);
                return;
            }

            Log.e(Extras.LOG_MESSAGE, "Download starting for " + messageId
                    + " (" + fileName + ") from " + downloadUrl);
            mediaTransferManager.downloadAttachment(messageId, downloadUrl,
                    fileName, contentType, fileTransferId);
        } catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "Failed to parse download metadata: " + e.getMessage());
        }
    }

    /**
     * Rewrites the row's stored JSON to include the local_file_path after a
     * successful download, then refreshes the adapter so populateMediaFieldsFromJson()
     * picks it up and the download overlay disappears.
     */
    private void persistLocalFilePathOnRow(String messageId, String localFilePath) {
        executorService.execute(() -> {
            try {
                // Read existing JSON directly from DB (not adapter — adapter may be stale)
                com.jippytalk.Database.MessagesDatabase.MessagesDatabaseDAO dao =
                        MyApplication.getInstance().getDatabaseServiceLocator().getMessagesDatabaseDAO();
                String existingBody = dao.getMessageBodyById(messageId);

                if (existingBody == null || !existingBody.startsWith("{")) {
                    Log.e(Extras.LOG_MESSAGE, "persistLocalFilePath: no JSON body found for " + messageId);
                    return;
                }

                org.json.JSONObject json = new org.json.JSONObject(existingBody);
                json.put("local_file_path", localFilePath);
                dao.updateMessageContent(messageId, json.toString());

                Log.e(Extras.LOG_MESSAGE, "persistLocalFilePath: updated " + messageId
                        + " → " + localFilePath);

                runOnUiThread(() -> messagesViewModel.retrieveAllMessagesOfContact(contactId));
            } catch (Exception e) {
                Log.e(Extras.LOG_MESSAGE, "Failed to persist local_file_path: " + e.getMessage());
            }
        });
    }

    /**
     * Opens a downloaded attachment with the system's default viewer for its
     * MIME type (Intent.ACTION_VIEW via FileProvider for API 24+ compliance).
     */
    /**
     * Rebuilds an AttachmentModel from the DB row's metadata JSON and retries
     * the upload. Used when the app was restarted and the in-memory pendingUploads
     * map is empty.
     */
    private void rebuildAndRetryUpload(String messageId) {
        MessageModal row = null;
        for (MessageModal m : adapter.getCurrentList()) {
            if (messageId.equals(m.getMessageId())) { row = m; break; }
        }
        if (row == null || row.getMessage() == null || !row.getMessage().startsWith("{")) {
            Log.e(Extras.LOG_MESSAGE, "Retry: cannot find metadata for " + messageId);
            return;
        }
        try {
            org.json.JSONObject json = new org.json.JSONObject(row.getMessage());
            String  localPath   =   json.optString("local_file_path", "");
            String  fileName    =   json.optString("file_name", "");
            String  contentType =   json.optString("content_type", "document");
            String  subtype     =   json.optString("content_subtype", "");
            String  caption     =   json.optString("caption", "");
            int     width       =   json.optInt("width", 0);
            int     height      =   json.optInt("height", 0);
            long    duration    =   json.optLong("duration", 0);

            if (localPath.isEmpty() || !new java.io.File(localPath).exists()) {
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

            Log.e(Extras.LOG_MESSAGE, "Retry: rebuilt model from DB for " + messageId
                    + " localPath=" + localPath);
            mediaTransferManager.retryUploadWithModel(messageId, model);

        } catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "Retry: failed to rebuild model: " + e.getMessage());
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
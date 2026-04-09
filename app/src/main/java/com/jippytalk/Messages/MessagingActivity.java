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

        if (contactId == null) {
            Toast.makeText(this, "Unable to fetch details internal issue", Toast.LENGTH_SHORT).show();
            return;
        }

        checkMyApplicationInitialization();

        activityMessagingBinding.rvChats.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                hideOrDisplayDownArrow();
            }
        });

        typingHandler = new Handler();
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
            msgToMsgReplyCardVisible    =   false;
        });

        activityMessagingBinding.cvDownButton.setOnClickListener(view -> {
            activityMessagingBinding.rvChats.scrollToPosition(adapter.getItemCount() - 1);
            activityMessagingBinding.cvDownButton.setVisibility(GONE);
        });
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
                messagesViewModel.retrieveAllMessagesOfContact(contactId);
                if (contactId.equals(userId)) {
                    Log.e(Extras.LOG_MESSAGE,"User Sending Message to himself");
                    return;
                }
                if (contactDeviceId != -1 && contactDeviceId != 0) {
                    Log.e(Extras.LOG_MESSAGE, "contact device id is " + contactDeviceId);
                    signalProtocolAddress       =   new SignalProtocolAddress(contactId, contactDeviceId);
                    if (customSignalProtocolStore.containsSession(signalProtocolAddress)) {
                        encryptMessageAndSendToServer(messageInsertionModel);
                        Log.e(Extras.LOG_MESSAGE, "session exists");
                    }
                    else {
                        startWorkManagerToCreateSessionForContact(contactId);
                    }
                } else {
                    Log.e(Extras.LOG_MESSAGE, "session not exists and device id is " + contactDeviceId);
                }
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
                    msgToMsgReplyCardVisible    =   true;
                    activityMessagingBinding.cvMsgToMsgReply.setVisibility(VISIBLE);
                    activityMessagingBinding.tvselectedMsg.setText(adapter.getCurrentList().get(position).getMessage());
                    repliedToMessageId      =   adapter.getCurrentList().get(position).getMessageId();
                    if (adapter.getCurrentList().get(position).getMessageDirection() == MessagesManager.MESSAGE_INCOMING) {
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

    private void encryptMessageAndSendToServer(MessageInsertionModel messageInsertionModel) {
        long encryptionStartTime = System.currentTimeMillis();
        Log.e(Extras.LOG_MESSAGE, "Encryption started for messageId: " + messageInsertionModel.getMessageId());

        messageEncryptAndDecrypt.encryptMessage(contactId, contactDeviceId, messageInsertionModel.getMessage(),
                new MessageEncryptAndDecrypt.MessageEncryptionCallBacks() {

                    @Override
                    public void onMessageEncryptSuccessful(String encryptedMessage, int signalMessageType) {
                        long encryptionEndTime = System.currentTimeMillis();
                        Log.e(Extras.LOG_MESSAGE, "Encryption finished for messageId: " + messageInsertionModel.getMessageId()
                                + " in " + (encryptionEndTime - encryptionStartTime) + " ms");

                        if (webSocketConnection != null) {
                            long sendStartTime = System.currentTimeMillis();
                            Log.e(Extras.LOG_MESSAGE, "Sending message started for messageId: " + messageInsertionModel.getMessageId());

                            executorService.execute(() -> {
                                webSocketConnection.sendMessageToServer(
                                        messageInsertionModel.getMessageId(),
                                        userId,
                                        contactId,
                                        encryptedMessage,
                                        signalMessageType,
                                        messageInsertionModel.getMessageType(),
                                        Extras.MESSAGE_SYNCED_WITH_SERVER,
                                        MessagesManager.MESSAGE_NOT_EDITED,
                                        MessagesManager.DEFAULT_LATITUDE,
                                        MessagesManager.DEFAULT_LONGITUDE,
                                        messageInsertionModel.getSentTimestamp(),
                                        MessagesManager.DEFAULT_DELIVERED_TIMESTAMP,
                                        MessagesManager.DEFAULT_READ_TIMESTAMP,
                                        messageInsertionModel.getIsReply(),
                                        messageInsertionModel.getRepliedToMessageId()
                                );

                                long sendEndTime = System.currentTimeMillis();
                                Log.e(Extras.LOG_MESSAGE, "Sending message finished for messageId: " + messageInsertionModel.getMessageId()
                                        + " in " + (sendEndTime - sendStartTime) + " ms");
                            });
                        } else {
                            Log.e(Extras.LOG_MESSAGE, "Unable to send message as myService is null");
                        }
                    }

                    @Override
                    public void onMessageEncryptionFail(int exceptionStatus) {
                        startWorkManagerToCreateSessionForContact(contactId);
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
                case MessagesManager.DELETE_FOR_ME          ->  messagesViewModel.deleteMessage(messageId);
                case MessagesManager.DELETE_FOR_EVERYONE    ->  {
                    if (webSocketConnection != null) {
                        webSocketConnection.deleteMessageForEveryone(
                                messageId, userId,
                                contactId,
                                Extras.DELETE_MESSAGE_FOR_EVERYONE);
                    }
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
            List<MessageModal> oldList = adapter.getCurrentList();
            List<MessageModal> newList = new ArrayList<>(oldList.size());
            for (MessageModal messageModal : oldList) {
                if (messageModal.getMessageId().equals(messageId)) {
                    MessageModal updated = new MessageModal(messageId, messageModal.getMessageDirection(),
                            messageModal.getReceiverId(),
                            messageModal.getMessage(), messageStatus, messageModal.getTimestamp(),
                            messageModal.getStarredStatus(), messageModal.getEditedStatus(),
                            messageModal.getMessageType(), messageModal.isReply(),
                            messageModal.getReplyToMessageId(), messageModal.getRepliedToMessageText(),
                            messageModal.getRepliedToMessageDirection(), messageModal.getRepliedToMessageSenderName());
                    newList.add(updated);
                } else {
                    newList.add(messageModal);
                }
            }
            adapter.submitList(newList);
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
}
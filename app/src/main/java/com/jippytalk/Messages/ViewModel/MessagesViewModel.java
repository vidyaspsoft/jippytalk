package com.jippytalk.Messages.ViewModel;

import android.util.Pair;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import com.jippytalk.Chats.Repository.ChatsUIRepository;
import com.jippytalk.Database.MessagesDatabase.Repository.ChatListRepository;
import com.jippytalk.Database.MessagesDatabase.Repository.MessagesRepository;
import com.jippytalk.Messages.Model.MessageInsertionModel;
import com.jippytalk.Messages.Model.MessageModal;
import com.jippytalk.Messages.Repository.MessagesUIRepository;
import com.jippytalk.ServiceHandlers.Models.UnSyncedSeenMessagesModel;

import java.util.ArrayList;

public class MessagesViewModel extends ViewModel {

    private final MessagesRepository            messagesRepository;
    private final ChatListRepository            chatListRepository;
    private final ChatsUIRepository             chatsUIRepository;
    private final MessagesUIRepository          messagesUIRepository;


    public MessagesViewModel(MessagesRepository messagesRepository, ChatListRepository chatListRepository,
                             ChatsUIRepository chatsUIRepository, MessagesUIRepository messagesUIRepository) {
        this.messagesRepository             =   messagesRepository;
        this.chatListRepository             =   chatListRepository;
        this.chatsUIRepository              =   chatsUIRepository;
        this.messagesUIRepository           =   messagesUIRepository;
    }

    public LiveData<ArrayList<MessageModal>> getMessagesFromViewModel() {
        return messagesRepository.getMessagesLiveData();
    }

    public LiveData<ArrayList<MessageModal>> getMessageStatusUpdatesFromViewModel() {
        return messagesRepository.getMessageStatusUpdateLiveData();
    }

    public LiveData<ArrayList<UnSyncedSeenMessagesModel>> getUnSyncSeenMessagesFromViewModel() {
        return messagesRepository.getUnsyncSeenMessagesLiveData();
    }

    public LiveData<ArrayList<UnSyncedSeenMessagesModel>> getUnKnownMessagesFromViewModel() {
        return messagesRepository.getUnSyncUnKnownMessagesData();
    }

    public LiveData<Integer> getMessageDeleteLiveStatusFromViewModel() {
        return messagesRepository.getDeleteMessageStatusLiveData();
    }

    public LiveData<Integer> getStarredMessageStatusFromLiveData() {
        return messagesRepository.getMessageStarredStatusLiveData();
    }

    public LiveData<Integer> getUserAccountStatusLiveData() {
        return messagesUIRepository.getContactBusyStatusLiveDataFromRep();
    }

    public LiveData<Pair<Integer, Integer>> getChatLockAndReadReceiptStatusLiveData() {
        return chatListRepository.getChatLockAndReadReceiptsLiveData();
    }

    public LiveData<MessageInsertionModel> getMessageInsertionStatusFromViewModel() {
        return messagesRepository.getMessageInsertionStatusLiveData();
    }

    public LiveData<Integer> getContactChatArchiveStatusFromViewModel() {
        return chatListRepository.getContactChatArchiveStatusLiveData();
    }

    public LiveData<Boolean> getAcceptedContactChatStatusFromViewModel() {
        return messagesRepository.getAcceptedContactChatLiveData();
    }

    public LiveData<Integer> getLastVisitPrivacyOptionFromViewModel() {
        return messagesUIRepository.getLastVisitPrivacyOptionLiveData();
    }

    public LiveData<Pair<String, Boolean>> getUserIdAndReadReceiptsStatusFromViewModel() {
        return messagesUIRepository.getUserIdAndReadReceiptsLiveData();
    }


    public void insertMessageIntoLocalDatabase(String messageId, int messageDirection, String receiverId,
                                               String message, int messageStatus, int needPush,
                                               long sentTimestamp, long receivedTimestamp, long readTimestamp,
                                               int isStarred, int editedStatus, int messageType,
                                               double latitude, double longitude, int isReply,
                                               String replyToMsgId, int chatArchive) {
        messagesRepository.insertMessageToLocalStorage(messageId, messageDirection, receiverId,
                message, messageStatus, needPush, sentTimestamp, receivedTimestamp, readTimestamp,
                isStarred, editedStatus, messageType, latitude, longitude,
                isReply, replyToMsgId, chatArchive);
    }


    public void retrieveAllMessagesOfContact(String contactId) {
        messagesRepository.getMessagesForContact(contactId);
    }

    public void retrieveAllMessagesWithPagination(String contactId, int limit, int offset) {
      //  messagesRepository.getMessagesForContactChatScreen(contactId, limit, offset);
    }

    public void getContactBusyStatus(String contactId) {
        messagesUIRepository.getContactBusyStatus(contactId);
    }

    public void updateMessageStarredStatus(String messageId, int starredStatus) {
        messagesRepository.updateMessageStarStatus(messageId, starredStatus);
    }

    public void deleteMessage(String messageId) {
        messagesRepository.deleteMessage(messageId);
    }


    public void updateLastMessagePositionForContact(String contactId, int lastPosition) {
        messagesRepository.updateLastMessagePositionForTheContact(contactId, lastPosition);
    }

    public void getContactReadReceiptsStatusForMessageScreen(String contactId) {
        chatListRepository.getChatLockAndReadReceiptStatus(contactId);
    }

    public void getUnSyncSeenMessagesForContact(String contactId) {
        messagesRepository.getContactUnSyncSeenMessages(contactId);
    }

    public void getEditTextVisibility(String contactId) {
        chatListRepository.getContactChatArchiveStatusFromDB(contactId);
    }

    public void acceptedContactChat(String contactId) {
        messagesRepository.acceptContactChat(contactId);
    }

    public void declinedContactChat(String contactId) {
        chatListRepository.deleteChatAndMessages(contactId);
    }

    public void getLastVisitPrivacyOption() {
        messagesUIRepository.getLastVisitPrivacyOption();
    }

    public void getUserIdAndReadReceiptsStatus() {
        messagesUIRepository.getUserIdAndReadReceiptsStatus();
    }
}

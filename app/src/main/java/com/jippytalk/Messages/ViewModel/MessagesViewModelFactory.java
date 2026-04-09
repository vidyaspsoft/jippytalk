package com.jippytalk.Messages.ViewModel;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.jippytalk.Chats.Repository.ChatsUIRepository;
import com.jippytalk.Database.MessagesDatabase.Repository.ChatListRepository;
import com.jippytalk.Database.MessagesDatabase.Repository.MessagesRepository;
import com.jippytalk.Messages.Datahandlers.HandleMessagesNetworkingAPI;
import com.jippytalk.Messages.Repository.MessagesUIRepository;

public class MessagesViewModelFactory implements ViewModelProvider.Factory {

    private final MessagesRepository            messagesRepository;
    private final ChatListRepository            chatListRepository;
    private final ChatsUIRepository             chatsUIRepository;
    private final MessagesUIRepository          messagesUIRepository;

    public MessagesViewModelFactory(MessagesRepository messagesRepository, ChatListRepository chatListRepository,
                                       ChatsUIRepository chatsUIRepository, MessagesUIRepository messagesUIRepository) {
        this.messagesRepository             =   messagesRepository;
        this.chatListRepository             =   chatListRepository;
        this.chatsUIRepository              =   chatsUIRepository;
        this.messagesUIRepository           =   messagesUIRepository;
    }

    @NonNull
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> viewModel) {
        if (viewModel.isAssignableFrom(MessagesViewModel.class)) {
            return (T) new MessagesViewModel(messagesRepository, chatListRepository, chatsUIRepository, messagesUIRepository);
        }
        throw new IllegalArgumentException("Unknown ViewModel class");
    }

}

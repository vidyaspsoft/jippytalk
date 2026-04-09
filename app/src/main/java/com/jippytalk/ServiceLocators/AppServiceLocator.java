package com.jippytalk.ServiceLocators;

import android.content.Context;

import com.jippytalk.AccountStatus.Repository.AccountStatusRepository;
import com.jippytalk.ContactProfile.Repository.ContactProfileRepository;
import com.jippytalk.Messages.Repository.MessagesUIRepository;
import com.jippytalk.Privacy.Repository.PrivacyRepository;
import com.jippytalk.SecurityCheck.APIs.SecurityQuestionsCheckAPI;
import com.jippytalk.SecurityCheck.Repository.SecurityQuestionsCheckRepository;
import com.jippytalk.WebSocketConnection;
import com.jippytalk.UpdatedSignalProtocolStore;
import com.jippytalk.UserDetailsRepository;

public class AppServiceLocator {

    private final Context                           context;
    private final DatabaseServiceLocator            databaseServiceLocator;
    private final RepositoryServiceLocator          repositoryServiceLocator;
    private final APIServiceLocator                 apiServiceLocator;
    private final AccountStatusRepository           accountStatusRepository;
    private final PrivacyRepository                 privacyRepository;
    private final ContactProfileRepository          contactProfileRepository;
    private final MessagesUIRepository              messagesUIRepository;
    private final WebSocketConnection               webSocketConnection;
    private UpdatedSignalProtocolStore              updatedSignalProtocolStore;
    private final SecurityQuestionsCheckRepository  securityQuestionsCheckRepository;
    private final SecurityQuestionsCheckAPI         securityQuestionsCheckAPI;


    public AppServiceLocator(Context context, DatabaseServiceLocator databaseServiceLocator,
                             RepositoryServiceLocator repositoryServiceLocator, APIServiceLocator apiServiceLocator) {
        this.context                        =   context.getApplicationContext();
        this.databaseServiceLocator         =   databaseServiceLocator;
        this.repositoryServiceLocator       =   repositoryServiceLocator;
        this.apiServiceLocator              =   apiServiceLocator;
        accountStatusRepository             =   AccountStatusRepository.getInstance(repositoryServiceLocator.getSharedPreferences(),
                                                repositoryServiceLocator.getUserDetailsRepository(),
                                                apiServiceLocator.getAccountStatusAPIHandler());
        privacyRepository                   =   PrivacyRepository.getInstance(repositoryServiceLocator.getSharedPreferences(),
                                                repositoryServiceLocator.getUserDetailsRepository(),
                                                repositoryServiceLocator.getContactsRepository(), apiServiceLocator.getPrivacyAPIHandler(),
                                                apiServiceLocator.getApiLevelExecutorService());
        contactProfileRepository            =   ContactProfileRepository.getInstance(apiServiceLocator.getContactProfilePicAPI(),
                                                apiServiceLocator.getApiLevelExecutorService(),
                                                repositoryServiceLocator.getSharedPreferences());

        messagesUIRepository                =   MessagesUIRepository.getInstance(apiServiceLocator.getHandleMessagesNetworkingAPI(),
                                                repositoryServiceLocator.getSharedPreferences());

        webSocketConnection                 =   WebSocketConnection.getInstance(context.getApplicationContext(),
                                                repositoryServiceLocator.getSharedPreferences());

        securityQuestionsCheckAPI           =   new SecurityQuestionsCheckAPI(apiServiceLocator.getApiLevelExecutorService(),
                                                repositoryServiceLocator.getUserDetailsRepository());

        securityQuestionsCheckRepository    =   SecurityQuestionsCheckRepository.getInstance(context.getApplicationContext(),
                                                securityQuestionsCheckAPI);

    }

    public AccountStatusRepository getAccountStatusRepository() {
        return accountStatusRepository;
    }

    public PrivacyRepository getPrivacyRepository() {
        return privacyRepository;
    }

    public ContactProfileRepository getContactProfileRepository() { return  contactProfileRepository; }

    public MessagesUIRepository getMessagesUIRepository() { return messagesUIRepository; }

    public WebSocketConnection getWebSocketConnection() { return webSocketConnection; }

    public synchronized UpdatedSignalProtocolStore getSignalProtocolStore() {
        if (updatedSignalProtocolStore == null) {
            updatedSignalProtocolStore = new UpdatedSignalProtocolStore(
                    context.getApplicationContext(),
                    databaseServiceLocator,
                    repositoryServiceLocator
            );
        }
        return updatedSignalProtocolStore;
    }

    public SecurityQuestionsCheckAPI getSecurityQuestionsCheckAPI() { return  securityQuestionsCheckAPI;}

    public SecurityQuestionsCheckRepository getSecurityQuestionsCheckRepository() { return  securityQuestionsCheckRepository;}
}

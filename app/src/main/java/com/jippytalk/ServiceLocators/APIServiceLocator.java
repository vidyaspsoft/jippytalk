package com.jippytalk.ServiceLocators;

import android.content.Context;

import com.jippytalk.AboutSetUp.APIs.AboutSetUpAPI;
import com.jippytalk.AccountStatus.AccountStatusAPI.AccountStatusAPIHandler;
import com.jippytalk.TokenRefreshAPI;
import com.jippytalk.ContactProfile.API_Handlers.ContactProfilePicAPI;
import com.jippytalk.Encryption.DecryptionFailScenario;
import com.jippytalk.Login.RequestOTP.APIs.RequestOTPHandler;
import com.jippytalk.Login.VerifyOTP.APIs.VerifyOTPAPI;
import com.jippytalk.Messages.Datahandlers.HandleMessagesNetworkingAPI;
import com.jippytalk.Privacy.APIHandler.PrivacyAPIHandler;
import com.jippytalk.Privacy.CommonCodes.PrivacyHttpsUtils;
import com.jippytalk.ProfileSetUp.APIs.ProfileSetUpAPI;
import com.jippytalk.Profiles.Profile.API.ProfileUpdatesAPI;
import com.jippytalk.SecurityQA.APIs.SecurityQAAPI;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class APIServiceLocator {

    private final Context                       context;
    private AppServiceLocator                   appServiceLocator;
    private final RequestOTPHandler             requestOTPHandler;
    private final VerifyOTPAPI                  verifyOTPAPI;
    private final ProfileSetUpAPI               profileSetUpAPI;
    private final SecurityQAAPI                 securityQAAPI;
    private final AboutSetUpAPI                 aboutSetUpAPI;
    private final TokenRefreshAPI               tokenRefreshAPI;
    private final AccountStatusAPIHandler       accountStatusAPIHandler;
    private final PrivacyAPIHandler             privacyAPIHandler;
    private final PrivacyHttpsUtils             privacyHttpsUtils;
    private final ProfileUpdatesAPI             profileUpdatesAPI;
    private final ContactProfilePicAPI          contactProfilePicAPI;
    private final HandleMessagesNetworkingAPI   handleMessagesNetworkingAPI;
    private DecryptionFailScenario              decryptionFailScenario;
    private ExecutorService                     apiLevelExecutorService;
    private final RepositoryServiceLocator      repositoryServiceLocator;


    public void setAppServiceLocator(AppServiceLocator appServiceLocator) {
        this.appServiceLocator  =   appServiceLocator;
        decryptionFailScenario  =   new DecryptionFailScenario(getApiLevelExecutorService(),
                                    repositoryServiceLocator.getUserDetailsRepository(),
                                    repositoryServiceLocator.getContactsRepository(), getTokenRefreshAPI(),
                                    appServiceLocator.getSignalProtocolStore());
    }


    public APIServiceLocator(Context context, RepositoryServiceLocator repositoryServiceLocator) {
        this.context                        =   context.getApplicationContext();
        this.repositoryServiceLocator       =   repositoryServiceLocator;
        requestOTPHandler                   =   new RequestOTPHandler(getApiLevelExecutorService());
        verifyOTPAPI                        =   new VerifyOTPAPI(getApiLevelExecutorService(),
                                                repositoryServiceLocator.getUserDetailsRepository());
        profileSetUpAPI                     =   new ProfileSetUpAPI(getApiLevelExecutorService(),
                                                repositoryServiceLocator.getUserDetailsRepository());
        securityQAAPI                       =   new SecurityQAAPI(getApiLevelExecutorService());
        aboutSetUpAPI                       =   new AboutSetUpAPI(getApiLevelExecutorService());
        tokenRefreshAPI                     =   new TokenRefreshAPI(repositoryServiceLocator.getUserDetailsRepository());
        accountStatusAPIHandler             =   new AccountStatusAPIHandler(getApiLevelExecutorService());
        privacyHttpsUtils                   =   PrivacyHttpsUtils.getInstance();
        privacyAPIHandler                   =   new PrivacyAPIHandler(privacyHttpsUtils, getTokenRefreshAPI());
        profileUpdatesAPI                   =   new ProfileUpdatesAPI(context.getApplicationContext());
        handleMessagesNetworkingAPI         =   new HandleMessagesNetworkingAPI(getApiLevelExecutorService());
        contactProfilePicAPI                =   new ContactProfilePicAPI(repositoryServiceLocator.getSharedPreferences(),
                                                repositoryServiceLocator.getContactsRepository(),
                                                repositoryServiceLocator.getUserDetailsRepository(),
                                                getTokenRefreshAPI(),
                                                repositoryServiceLocator.getAppLevelExecutorService());
    }

    public RequestOTPHandler getRequestOTPHandler() {
        return requestOTPHandler;
    }

    public VerifyOTPAPI getVerifyOTPAPI() {
        return verifyOTPAPI;
    }

    public ProfileSetUpAPI getProfileSetUpAPI() {
        return profileSetUpAPI;
    }

    public SecurityQAAPI getSecurityQAAPI() {
        return securityQAAPI;
    }

    public AboutSetUpAPI getAboutSetUpAPI() {
        return aboutSetUpAPI;
    }

    public TokenRefreshAPI getTokenRefreshAPI() {
        return tokenRefreshAPI;
    }

    public AccountStatusAPIHandler getAccountStatusAPIHandler() {
        return accountStatusAPIHandler;
    }

    public PrivacyHttpsUtils getPrivacyHttpsUtils() {
        return privacyHttpsUtils;
    }

    public PrivacyAPIHandler getPrivacyAPIHandler() {
        return privacyAPIHandler;
    }

    public ProfileUpdatesAPI getProfileUpdatesAPI() {
        return profileUpdatesAPI;
    }

    public ContactProfilePicAPI getContactProfilePicAPI() { return contactProfilePicAPI; }

    public HandleMessagesNetworkingAPI getHandleMessagesNetworkingAPI() { return handleMessagesNetworkingAPI; }

    public DecryptionFailScenario getDecryptionFailScenario() { return decryptionFailScenario; }


    public synchronized ExecutorService getApiLevelExecutorService() {
        if (apiLevelExecutorService == null) {
            apiLevelExecutorService = Executors.newFixedThreadPool(6);
        }
        return apiLevelExecutorService;
    }

}

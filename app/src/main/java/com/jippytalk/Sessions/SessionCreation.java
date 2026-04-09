package com.jippytalk.Sessions;

/**
 * Developer Name: Vidya Sagar
 * Created on: 06-04-2026
 */

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.util.Base64;
import android.util.Log;

import com.jippytalk.Database.ContactsDatabase.ContactsDatabase;
import com.jippytalk.Database.ContactsDatabase.DAO.ContactsDatabaseDAO;
import com.jippytalk.Database.SessionDatabase.Repository.SessionsRepository;
import com.jippytalk.Extras;
import com.jippytalk.Managers.SharedPreferenceDetails;
import com.jippytalk.MyApplication;
import com.jippytalk.ServiceLocators.DatabaseServiceLocator;
import com.jippytalk.UpdatedSignalProtocolStore;

import org.signal.libsignal.protocol.IdentityKey;
import org.signal.libsignal.protocol.InvalidKeyException;
import org.signal.libsignal.protocol.SessionBuilder;
import org.signal.libsignal.protocol.SignalProtocolAddress;
import org.signal.libsignal.protocol.ecc.ECPublicKey;
import org.signal.libsignal.protocol.kem.KEMPublicKey;
import org.signal.libsignal.protocol.state.PreKeyBundle;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * SessionCreation - Builds a new Signal Protocol session with a contact using their
 * pre-key bundle retrieved from the server.
 *
 * This class fetches the contact's stored keys from the local contacts database,
 * combines them with the one-time pre-key and Kyber key data from the server,
 * and uses Signal Protocol's SessionBuilder to create a secure session.
 *
 * Used by HandleOneTimePreKeyRetrieval after successfully fetching the contact's
 * one-time pre-key from the server.
 */
public class SessionCreation {

    private final Context                           context;
    private final SessionCreationCallback           callback;
    private final ExecutorService                   executorService     =   Executors.newSingleThreadExecutor();

    /**
     * Creates a new SessionCreation instance.
     *
     * @param context   application context for accessing databases and SharedPreferences
     * @param callback  callback to notify when session creation is complete
     */
    public SessionCreation(Context context, SessionCreationCallback callback) {
        this.context    =   context.getApplicationContext();
        this.callback   =   callback;
    }

    // -------------------- Session Creation Methods Starts Here ---------------------

    /**
     * Retrieves the contact's stored keys from the local database and builds a Signal Protocol
     * session using the provided one-time pre-key and Kyber key data from the server.
     *
     * The process:
     * 1. Fetches contact's identity key, signed pre-key, registration ID, and device ID from local DB
     * 2. Combines with the one-time pre-key and Kyber key data from the server
     * 3. Creates a PreKeyBundle with all the key material
     * 4. Uses SessionBuilder to process the bundle and establish a session
     * 5. Notifies the callback with the contactId and deviceId on success
     *
     * @param contactId         the contact's user ID
     * @param oneTimePreKeyID   the one-time pre-key ID from the server
     * @param oneTimePreKey     the Base64-encoded one-time pre-key public key from the server
     * @param kyberPreKeyId     the Kyber pre-key ID from the server
     * @param kyberPublicKey    the Base64-encoded Kyber public key from the server
     * @param kyberSignature    the Base64-encoded Kyber signature from the server
     */
    public void getContactKeysData(String contactId, int oneTimePreKeyID, String oneTimePreKey,
                                   int kyberPreKeyId, String kyberPublicKey, String kyberSignature) {
        executorService.execute(() -> {
            try {
                MyApplication               myApplication           =   MyApplication.getInstance();
                DatabaseServiceLocator      databaseServiceLocator  =   myApplication.getDatabaseServiceLocator();
                ContactsDatabaseDAO         contactsDatabaseDAO     =   databaseServiceLocator.getContactsDatabaseDAO();
                UpdatedSignalProtocolStore  signalProtocolStore     =   myApplication.getAppServiceLocator().getSignalProtocolStore();
                SharedPreferences           sharedPreferences       =   myApplication.getRepositoryServiceLocator().getSharedPreferences();

                // Retrieve the contact's stored keys from the local contacts database
                int     registrationId          =   0;
                int     deviceId                =   1;
                String  contactPublicKeyBase64  =   null;
                int     signedPreKeyId          =   0;
                String  signedPrePublicKeyBase64 =  null;
                String  signatureBase64         =   null;

                try (Cursor cursor = contactsDatabaseDAO.getKeysForSessionCreation(contactId)) {
                    if (cursor != null && cursor.moveToFirst()) {
                        registrationId              =   cursor.getInt(cursor.getColumnIndexOrThrow(
                                ContactsDatabase.CONTACT_REGISTRATION_ID));
                        deviceId                    =   cursor.getInt(cursor.getColumnIndexOrThrow(
                                ContactsDatabase.CONTACT_DEVICE_ID));
                        contactPublicKeyBase64      =   cursor.getString(cursor.getColumnIndexOrThrow(
                                ContactsDatabase.CONTACT_PUBLIC_KEY));
                        signedPreKeyId              =   cursor.getInt(cursor.getColumnIndexOrThrow(
                                ContactsDatabase.CONTACT_SIGNED_KEY_ID));
                        signedPrePublicKeyBase64    =   cursor.getString(cursor.getColumnIndexOrThrow(
                                ContactsDatabase.CONTACT_SIGNED_PRE_PUBLIC_KEY));
                        signatureBase64             =   cursor.getString(cursor.getColumnIndexOrThrow(
                                ContactsDatabase.CONTACT_SIGNATURE));
                    }
                    else {
                        Log.e(Extras.LOG_MESSAGE, "No keys found for contact " + contactId);
                        return;
                    }
                }

                if (contactPublicKeyBase64 == null || signedPrePublicKeyBase64 == null || signatureBase64 == null) {
                    Log.e(Extras.LOG_MESSAGE, "Contact keys are incomplete for " + contactId);
                    return;
                }

                // Decode all the key material from Base64
                byte[]      contactPublicKeyBytes       =   Base64.decode(contactPublicKeyBase64, Base64.NO_WRAP);
                byte[]      signedPrePublicKeyBytes     =   Base64.decode(signedPrePublicKeyBase64, Base64.NO_WRAP);
                byte[]      signatureBytes              =   Base64.decode(signatureBase64, Base64.NO_WRAP);
                byte[]      oneTimePreKeyBytes          =   Base64.decode(oneTimePreKey, Base64.NO_WRAP);
                byte[]      kyberPublicKeyBytes         =   Base64.decode(kyberPublicKey, Base64.NO_WRAP);
                byte[]      kyberSignatureBytes         =   Base64.decode(kyberSignature, Base64.NO_WRAP);

                // Create Signal Protocol key objects
                IdentityKey     contactIdentityKey      =   new IdentityKey(contactPublicKeyBytes);
                ECPublicKey     signedPrePublicKeyObj    =   new ECPublicKey(signedPrePublicKeyBytes);
                ECPublicKey     oneTimePreKeyPublic      =   new ECPublicKey(oneTimePreKeyBytes);
                KEMPublicKey    kyberPreKeyPublic        =   new KEMPublicKey(kyberPublicKeyBytes);

                // Build the PreKeyBundle with all collected key material (11 params for libsignal 0.86.5)
                PreKeyBundle preKeyBundle   =   new PreKeyBundle(
                        registrationId,
                        deviceId,
                        oneTimePreKeyID,
                        oneTimePreKeyPublic,
                        signedPreKeyId,
                        signedPrePublicKeyObj,
                        signatureBytes,
                        contactIdentityKey,
                        kyberPreKeyId,
                        kyberPreKeyPublic,
                        kyberSignatureBytes
                );

                // Create the Signal Protocol address and build the session
                SignalProtocolAddress    address         =   new SignalProtocolAddress(contactId, deviceId);
                SessionBuilder          sessionBuilder  =   new SessionBuilder(signalProtocolStore, address);

                sessionBuilder.process(preKeyBundle);

                Log.e(Extras.LOG_MESSAGE, "Session created successfully for contact " + contactId + " device " + deviceId);

                // Notify callback that session creation was successful
                if (callback != null) {
                    callback.onSessionCreated(contactId, deviceId);
                }

            }
            catch (InvalidKeyException e) {
                Log.e(Extras.LOG_MESSAGE, "Invalid key when building session " + e.getMessage());
            }
            catch (org.signal.libsignal.protocol.UntrustedIdentityException e) {
                Log.e(Extras.LOG_MESSAGE, "Untrusted identity when building session " + e.getMessage());
            }
            catch (Exception e) {
                Log.e(Extras.LOG_MESSAGE, "Failed to create session for contact " + contactId + " " + e.getMessage());
            }
        });
    }

    // -------------------- Callback Interface ---------------------

    /**
     * Callback interface for session creation results.
     * Maps to HandleOneTimePreKeyRetrieval.HandleOneTimePreKeyCallback.
     */
    public interface SessionCreationCallback {
        /**
         * Called when a Signal Protocol session has been successfully created.
         *
         * @param contactId the contact's user ID
         * @param deviceId  the contact's device ID
         */
        void onSessionCreated(String contactId, int deviceId);
    }
}

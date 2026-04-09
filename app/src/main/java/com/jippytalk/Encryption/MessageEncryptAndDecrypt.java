package com.jippytalk.Encryption;

/**
 * Developer Name: Vidya Sagar
 * Created on: 06-04-2026
 */

import android.content.Context;
import android.util.Base64;
import android.util.Log;

import com.jippytalk.Extras;
import com.jippytalk.Managers.SessionManager;
import com.jippytalk.ServiceLocators.AppServiceLocator;
import com.jippytalk.UpdatedSignalProtocolStore;

import org.signal.libsignal.protocol.DuplicateMessageException;
import org.signal.libsignal.protocol.InvalidKeyException;
import org.signal.libsignal.protocol.InvalidMessageException;
import org.signal.libsignal.protocol.InvalidVersionException;
import org.signal.libsignal.protocol.LegacyMessageException;
import org.signal.libsignal.protocol.NoSessionException;
import org.signal.libsignal.protocol.SessionCipher;
import org.signal.libsignal.protocol.SignalProtocolAddress;
import org.signal.libsignal.protocol.UntrustedIdentityException;
import org.signal.libsignal.protocol.message.CiphertextMessage;
import org.signal.libsignal.protocol.message.PreKeySignalMessage;
import org.signal.libsignal.protocol.message.SignalMessage;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * MessageEncryptAndDecrypt - Core encryption and decryption engine for the messaging system.
 * Uses Signal Protocol's SessionCipher to encrypt outgoing messages and decrypt incoming messages.
 *
 * This class is a singleton that is initialized with the AppServiceLocator and DecryptionFailScenario.
 * It handles both regular Signal messages and PreKey Signal messages (used during initial session setup).
 *
 * Message types:
 * - CiphertextMessage.WHISPER_TYPE (2): Regular message within an established session
 * - CiphertextMessage.PREKEY_TYPE (3): First message that includes pre-key information
 */
public class MessageEncryptAndDecrypt {

    private static volatile MessageEncryptAndDecrypt    INSTANCE;
    private final Context                               context;
    private final UpdatedSignalProtocolStore            signalProtocolStore;
    private final DecryptionFailScenario                decryptionFailScenario;
    private final ExecutorService                       encryptionExecutor      =   Executors.newSingleThreadExecutor();

    private MessageEncryptAndDecrypt(Context context, AppServiceLocator appServiceLocator,
                                     DecryptionFailScenario decryptionFailScenario) {
        this.context                    =   context.getApplicationContext();
        this.signalProtocolStore        =   appServiceLocator.getSignalProtocolStore();
        this.decryptionFailScenario     =   decryptionFailScenario;
    }

    /**
     * Returns the singleton instance of MessageEncryptAndDecrypt.
     * Thread-safe double-checked locking pattern.
     *
     * @param context                   application context
     * @param appServiceLocator         provides access to the Signal Protocol store
     * @param decryptionFailScenario    handles decryption failure scenarios
     * @return the singleton instance
     */
    public static MessageEncryptAndDecrypt getInstance(Context context, AppServiceLocator appServiceLocator,
                                                        DecryptionFailScenario decryptionFailScenario) {
        if (INSTANCE == null) {
            synchronized (MessageEncryptAndDecrypt.class) {
                if (INSTANCE == null) {
                    INSTANCE    =   new MessageEncryptAndDecrypt(context, appServiceLocator, decryptionFailScenario);
                }
            }
        }
        return INSTANCE;
    }

    // -------------------- Encryption Methods Starts Here ---------------------

    /**
     * Encrypts a plaintext message for a specific contact using their Signal Protocol session.
     * The encrypted message is Base64 encoded before being returned via the callback.
     *
     * @param contactId          the receiver's contact ID
     * @param contactDeviceId    the receiver's device ID
     * @param message            the plaintext message to encrypt
     * @param callback           callback to receive the encrypted message or failure notification
     */
    public void encryptMessage(String contactId, int contactDeviceId, String message,
                               MessageEncryptionCallBacks callback) {
        encryptionExecutor.execute(() -> {
            try {
                SignalProtocolAddress   address         =   new SignalProtocolAddress(contactId, contactDeviceId);
                SessionCipher          sessionCipher   =   new SessionCipher(signalProtocolStore, address);

                // Encrypt the message bytes using the session cipher
                CiphertextMessage   ciphertextMessage  =   sessionCipher.encrypt(
                        message.getBytes(StandardCharsets.UTF_8));

                // Base64 encode the encrypted message for transport
                String  encryptedMessage    =   Base64.encodeToString(
                        ciphertextMessage.serialize(), Base64.NO_WRAP);

                int signalMessageType   =   ciphertextMessage.getType();

                Log.e(Extras.LOG_MESSAGE, "Message encrypted successfully, type: " + signalMessageType);

                if (callback != null) {
                    callback.onMessageEncryptSuccessful(encryptedMessage, signalMessageType);
                }
            }
            catch (UntrustedIdentityException e) {
                Log.e(Extras.LOG_MESSAGE, "Encryption failed - untrusted identity " + e.getMessage());
                if (callback != null) {
                    callback.onMessageEncryptionFail(SessionManager.UNTRUSTED_IDENTITY_EXCEPTION);
                }
            }
            catch (NoSessionException e) {
                Log.e(Extras.LOG_MESSAGE, "Encryption failed - no session exists " + e.getMessage());
                if (callback != null) {
                    callback.onMessageEncryptionFail(SessionManager.NO_SESSION_EXCEPTION);
                }
            }
            catch (Exception e) {
                Log.e(Extras.LOG_MESSAGE, "Encryption failed " + e.getMessage());
                if (callback != null) {
                    callback.onMessageEncryptionFail(SessionManager.INVALID_MESSAGE_EXCEPTION);
                }
            }
        });
    }

    // -------------------- Decryption Methods Starts Here ---------------------

    /**
     * Decrypts an incoming encrypted message from a contact using their Signal Protocol session.
     * Handles both regular Signal messages (WHISPER_TYPE) and PreKey messages (PREKEY_TYPE).
     *
     * If decryption fails with certain exceptions, the failure is reported via the callback
     * so that session rebuild can be triggered.
     *
     * @param senderId              the sender's contact ID
     * @param deviceId              the sender's device ID
     * @param decodedMessage        the Base64-decoded encrypted message bytes
     * @param signalMessageType     the type of Signal message (WHISPER_TYPE or PREKEY_TYPE)
     * @param keysChanged           flag indicating if keys have changed
     * @param maxDecryptionTries    maximum number of retry attempts for decryption
     * @param callback              callback to receive the decrypted message or failure notification
     */
    public void decryptMessage(String senderId, int deviceId, byte[] decodedMessage,
                               int signalMessageType, boolean keysChanged,
                               int maxDecryptionTries, MessageDecryptionCallBack callback) {
        encryptionExecutor.execute(() -> {
            try {
                SignalProtocolAddress   address         =   new SignalProtocolAddress(senderId, deviceId);
                SessionCipher          sessionCipher   =   new SessionCipher(signalProtocolStore, address);

                byte[] decryptedBytes;

                // Determine message type and decrypt accordingly
                if (signalMessageType == CiphertextMessage.PREKEY_TYPE) {
                    // PreKey message - first message in a new session
                    PreKeySignalMessage preKeySignalMessage =   new PreKeySignalMessage(decodedMessage);
                    decryptedBytes  =   sessionCipher.decrypt(preKeySignalMessage);
                    Log.e(Extras.LOG_MESSAGE, "PreKey message decrypted successfully");
                }
                else {
                    // Regular whisper message within an established session
                    SignalMessage   signalMessage   =   new SignalMessage(decodedMessage);
                    decryptedBytes  =   sessionCipher.decrypt(signalMessage);
                    Log.e(Extras.LOG_MESSAGE, "Signal message decrypted successfully");
                }

                String decryptedMessage =   new String(decryptedBytes, StandardCharsets.UTF_8);

                if (callback != null) {
                    callback.onMessageDecryptSuccessful(decryptedMessage, keysChanged);
                }
            }
            catch (UntrustedIdentityException e) {
                Log.e(Extras.LOG_MESSAGE, "Decryption failed - untrusted identity " + e.getMessage());
                if (callback != null) {
                    callback.onDecryptionFail(SessionManager.UNTRUSTED_IDENTITY_EXCEPTION);
                }
            }
            catch (InvalidMessageException e) {
                Log.e(Extras.LOG_MESSAGE, "Decryption failed - invalid message " + e.getMessage());
                if (callback != null) {
                    callback.onDecryptionFail(SessionManager.INVALID_MESSAGE_EXCEPTION);
                }
            }
            catch (NoSessionException e) {
                Log.e(Extras.LOG_MESSAGE, "Decryption failed - no session " + e.getMessage());
                if (callback != null) {
                    callback.onDecryptionFail(SessionManager.NO_SESSION_EXCEPTION);
                }
            }
            catch (InvalidKeyException e) {
                Log.e(Extras.LOG_MESSAGE, "Decryption failed - invalid key " + e.getMessage());
                if (callback != null) {
                    callback.onDecryptionFail(SessionManager.INVALID_KEY_EXCEPTION);
                }
            }
            catch (DuplicateMessageException | InvalidVersionException | LegacyMessageException e) {
                Log.e(Extras.LOG_MESSAGE, "Decryption failed - message error " + e.getMessage());
                if (callback != null) {
                    callback.onDecryptionFail(SessionManager.INVALID_MESSAGE_EXCEPTION);
                }
            }
            catch (Exception e) {
                Log.e(Extras.LOG_MESSAGE, "Decryption failed - unexpected error " + e.getMessage());
                if (callback != null) {
                    callback.onDecryptionFail(SessionManager.INVALID_MESSAGE_EXCEPTION);
                }
            }
        });
    }

    // -------------------- Callback Interfaces ---------------------

    /**
     * Callback interface for message encryption results.
     * Called on the encryption executor thread.
     */
    public interface MessageEncryptionCallBacks {
        /**
         * Called when message encryption succeeds.
         *
         * @param encryptedMessage  the Base64-encoded encrypted message
         * @param signalMessageType the Signal Protocol message type (WHISPER_TYPE or PREKEY_TYPE)
         */
        void onMessageEncryptSuccessful(String encryptedMessage, int signalMessageType);

        /**
         * Called when message encryption fails.
         *
         * @param exceptionStatus the type of exception that occurred (see SessionManager constants)
         */
        void onMessageEncryptionFail(int exceptionStatus);
    }

    /**
     * Callback interface for message decryption results.
     * Called on the encryption executor thread.
     */
    public interface MessageDecryptionCallBack {
        /**
         * Called when message decryption succeeds.
         *
         * @param decryptedMessage  the plaintext decrypted message
         * @param keysChanged       true if the sender's keys have changed
         */
        void onMessageDecryptSuccessful(String decryptedMessage, boolean keysChanged);

        /**
         * Called when message decryption fails.
         *
         * @param exceptionStatus the type of exception that occurred (see SessionManager constants)
         */
        void onDecryptionFail(int exceptionStatus);
    }
}

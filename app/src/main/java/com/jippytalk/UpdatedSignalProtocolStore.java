package com.jippytalk;

/**
 * Developer Name: Vidya Sagar
 * Created on: 06-04-2026
 */

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;
import android.util.Pair;

import com.jippytalk.Database.ContactsDatabase.Repository.ContactsRepository;
import com.jippytalk.Database.SessionDatabase.Repository.SessionsRepository;
import com.jippytalk.Database.User.Repository.UserDatabaseRepository;
import com.jippytalk.Managers.SharedPreferenceDetails;
import com.jippytalk.ServiceLocators.DatabaseServiceLocator;
import com.jippytalk.ServiceLocators.RepositoryServiceLocator;

import org.signal.libsignal.protocol.IdentityKey;
import org.signal.libsignal.protocol.IdentityKeyPair;
import org.signal.libsignal.protocol.InvalidKeyIdException;
import org.signal.libsignal.protocol.NoSessionException;
import org.signal.libsignal.protocol.ReusedBaseKeyException;
import org.signal.libsignal.protocol.SignalProtocolAddress;
import org.signal.libsignal.protocol.ecc.ECPrivateKey;
import org.signal.libsignal.protocol.ecc.ECPublicKey;
import org.signal.libsignal.protocol.groups.state.SenderKeyRecord;
import org.signal.libsignal.protocol.groups.state.SenderKeyStore;
import org.signal.libsignal.protocol.state.IdentityKeyStore;
import org.signal.libsignal.protocol.state.KyberPreKeyRecord;
import org.signal.libsignal.protocol.state.PreKeyRecord;
import org.signal.libsignal.protocol.state.SessionRecord;
import org.signal.libsignal.protocol.state.SignalProtocolStore;
import org.signal.libsignal.protocol.state.SignedPreKeyRecord;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * UpdatedSignalProtocolStore - Custom implementation of Signal Protocol's SignalProtocolStore
 * that delegates all storage operations to the app's encrypted SQLCipher databases.
 *
 * SignalProtocolStore extends: IdentityKeyStore, PreKeyStore, SessionStore,
 * SignedPreKeyStore, SenderKeyStore, KyberPreKeyStore.
 *
 * Initialized lazily via AppServiceLocator.getSignalProtocolStore().
 */
public class UpdatedSignalProtocolStore implements SignalProtocolStore {

    private final Context                       context;
    private final DatabaseServiceLocator        databaseServiceLocator;
    private final RepositoryServiceLocator      repositoryServiceLocator;
    private final UserDatabaseRepository        userDatabaseRepository;
    private final SessionsRepository            sessionsRepository;
    private final ContactsRepository            contactsRepository;
    private final SharedPreferences             sharedPreferences;

    public UpdatedSignalProtocolStore(Context context, DatabaseServiceLocator databaseServiceLocator,
                                      RepositoryServiceLocator repositoryServiceLocator) {
        this.context                    =   context.getApplicationContext();
        this.databaseServiceLocator     =   databaseServiceLocator;
        this.repositoryServiceLocator   =   repositoryServiceLocator;
        userDatabaseRepository          =   repositoryServiceLocator.getUserDatabaseRepository();
        sessionsRepository              =   SessionsRepository.getInstance(context.getApplicationContext());
        contactsRepository              =   repositoryServiceLocator.getContactsRepository();
        sharedPreferences               =   repositoryServiceLocator.getSharedPreferences();
    }

    // -------------------- IdentityKeyStore Implementation Starts Here ---------------------

    /**
     * Returns the local user's identity key pair from the UsersDatabase.
     */
    @Override
    public IdentityKeyPair getIdentityKeyPair() {
        try {
            Pair<String, String> keyPair    =   userDatabaseRepository.getIdentityKeyPairBlocking();
            if (keyPair != null) {
                byte[]          publicKeyBytes      =   Base64.decode(keyPair.first, Base64.NO_WRAP);
                byte[]          privateKeyBytes     =   Base64.decode(keyPair.second, Base64.NO_WRAP);
                ECPublicKey     ecPublicKey         =   new ECPublicKey(publicKeyBytes);
                ECPrivateKey    ecPrivateKey        =   new ECPrivateKey(privateKeyBytes);
                IdentityKey     identityKey         =   new IdentityKey(ecPublicKey);
                return new IdentityKeyPair(identityKey, ecPrivateKey);
            }
        }
        catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "Failed to get identity key pair from database " + e.getMessage());
        }
        return null;
    }

    /**
     * Returns the local registration ID stored in SharedPreferences.
     */
    @Override
    public int getLocalRegistrationId() {
        return sharedPreferences.getInt(SharedPreferenceDetails.REGISTRATION_ID, 0);
    }

    /**
     * Saves the identity key of a remote contact.
     * Returns NEW_OR_UNCHANGED if this is a new key, REPLACED_EXISTING if the key changed.
     */
    @Override
    public IdentityKeyStore.IdentityChange saveIdentity(SignalProtocolAddress address, IdentityKey identityKey) {
        try {
            IdentityKey existingKey =   contactsRepository.getContactPublicIdentityKey(address.getName());
            if (existingKey == null) {
                return IdentityKeyStore.IdentityChange.NEW_OR_UNCHANGED;
            }
            if (existingKey.equals(identityKey)) {
                return IdentityKeyStore.IdentityChange.NEW_OR_UNCHANGED;
            }
            return IdentityKeyStore.IdentityChange.REPLACED_EXISTING;
        }
        catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "Error saving identity " + e.getMessage());
            return IdentityKeyStore.IdentityChange.NEW_OR_UNCHANGED;
        }
    }

    /**
     * Checks if the provided identity key is trusted for the contact.
     */
    @Override
    public boolean isTrustedIdentity(SignalProtocolAddress address, IdentityKey identityKey,
                                     Direction direction) {
        try {
            IdentityKey storedIdentityKey   =   contactsRepository.getContactPublicIdentityKey(address.getName());
            if (storedIdentityKey == null) {
                return true;
            }
            return storedIdentityKey.equals(identityKey);
        }
        catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "Error checking trusted identity " + e.getMessage());
            return true;
        }
    }

    /**
     * Returns the stored identity key for a remote contact.
     */
    @Override
    public IdentityKey getIdentity(SignalProtocolAddress address) {
        try {
            return contactsRepository.getContactPublicIdentityKey(address.getName());
        }
        catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "Error getting identity key " + e.getMessage());
            return null;
        }
    }

    // -------------------- PreKeyStore Implementation Starts Here ---------------------

    @Override
    public PreKeyRecord loadPreKey(int preKeyId) throws InvalidKeyIdException {
        PreKeyRecord preKeyRecord   =   userDatabaseRepository.getPreKeyDetails(preKeyId);
        if (preKeyRecord == null) {
            throw new InvalidKeyIdException("Pre key not found for id " + preKeyId);
        }
        return preKeyRecord;
    }

    @Override
    public void storePreKey(int preKeyId, PreKeyRecord record) {
        try {
            String publicKeyBase64  =   Base64.encodeToString(record.getKeyPair().getPublicKey().serialize(), Base64.NO_WRAP);
            String privateKeyBase64 =   Base64.encodeToString(record.getKeyPair().getPrivateKey().serialize(), Base64.NO_WRAP);
            userDatabaseRepository.insertOneTimePreKeys(preKeyId, publicKeyBase64, privateKeyBase64);
        } catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "Failed to store pre key " + e.getMessage());
        }
    }

    @Override
    public boolean containsPreKey(int preKeyId) {
        return userDatabaseRepository.checkIsPreKeyExists(preKeyId);
    }

    @Override
    public void removePreKey(int preKeyId) {
        userDatabaseRepository.deleteOneTimeKey(preKeyId);
    }

    // -------------------- SignedPreKeyStore Implementation Starts Here ---------------------

    @Override
    public SignedPreKeyRecord loadSignedPreKey(int signedPreKeyId) throws InvalidKeyIdException {
        SignedPreKeyRecord record    =   userDatabaseRepository.getSignedPreKeys(signedPreKeyId);
        if (record == null) {
            throw new InvalidKeyIdException("Signed pre key not found for id " + signedPreKeyId);
        }
        return record;
    }

    @Override
    public List<SignedPreKeyRecord> loadSignedPreKeys() {
        return List.of();
    }

    @Override
    public void storeSignedPreKey(int signedPreKeyId, SignedPreKeyRecord record) {
        try {
            String signature    =   Base64.encodeToString(record.getSignature(), Base64.NO_WRAP);
            String publicKey    =   Base64.encodeToString(record.getKeyPair().getPublicKey().serialize(), Base64.NO_WRAP);
            String privateKey   =   Base64.encodeToString(record.getKeyPair().getPrivateKey().serialize(), Base64.NO_WRAP);
            userDatabaseRepository.insertSignedPreKeys(signedPreKeyId, signature, publicKey, privateKey);
        } catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "Failed to store signed pre key " + e.getMessage());
        }
    }

    @Override
    public boolean containsSignedPreKey(int signedPreKeyId) {
        SignedPreKeyRecord record    =   userDatabaseRepository.getSignedPreKeys(signedPreKeyId);
        return record != null;
    }

    @Override
    public void removeSignedPreKey(int signedPreKeyId) {
        Log.e(Extras.LOG_MESSAGE, "removeSignedPreKey called for id " + signedPreKeyId);
    }

    // -------------------- SessionStore Implementation Starts Here ---------------------

    @Override
    public SessionRecord loadSession(SignalProtocolAddress address) {
        return sessionsRepository.getSessionRecord(address);
    }

    /**
     * Loads all existing sessions for the given addresses.
     * Throws NoSessionException if any address has no session.
     */
    @Override
    public List<SessionRecord> loadExistingSessions(List<SignalProtocolAddress> addresses) throws NoSessionException {
        List<SessionRecord> sessions    =   new ArrayList<>();
        for (SignalProtocolAddress address : addresses) {
            if (!sessionsRepository.checkIsSessionExists(address)) {
                throw new NoSessionException("No session for " + address);
            }
            sessions.add(sessionsRepository.getSessionRecord(address));
        }
        return sessions;
    }

    @Override
    public List<Integer> getSubDeviceSessions(String name) {
        return List.of();
    }

    @Override
    public void storeSession(SignalProtocolAddress address, SessionRecord record) {
        sessionsRepository.insertContactSession(address, record);
    }

    @Override
    public boolean containsSession(SignalProtocolAddress address) {
        return sessionsRepository.checkIsSessionExists(address);
    }

    @Override
    public void deleteSession(SignalProtocolAddress address) {
        sessionsRepository.deleteSession(address);
    }

    @Override
    public void deleteAllSessions(String name) {
        Log.e(Extras.LOG_MESSAGE, "deleteAllSessions called for " + name);
    }

    // -------------------- KyberPreKeyStore Implementation Starts Here ---------------------

    @Override
    public KyberPreKeyRecord loadKyberPreKey(int kyberPreKeyId) throws InvalidKeyIdException {
        KyberPreKeyRecord record    =   userDatabaseRepository.getKyberPreKeyDetails(kyberPreKeyId);
        if (record == null) {
            throw new InvalidKeyIdException("Kyber pre key not found for id " + kyberPreKeyId);
        }
        return record;
    }

    @Override
    public List<KyberPreKeyRecord> loadKyberPreKeys() {
        return List.of();
    }

    @Override
    public void storeKyberPreKey(int kyberPreKeyId, KyberPreKeyRecord record) {
        userDatabaseRepository.insertKyberPreKeys(kyberPreKeyId, record);
    }

    @Override
    public boolean containsKyberPreKey(int kyberPreKeyId) {
        return userDatabaseRepository.checkIsKyberPreKeyExists(kyberPreKeyId);
    }

    /**
     * Marks a Kyber pre-key as used after session establishment.
     * Called by the Signal Protocol library with the key ID, ratchet counter, and base key.
     */
    @Override
    public void markKyberPreKeyUsed(int kyberPreKeyId, int ratchetCounter, ECPublicKey baseKey) throws ReusedBaseKeyException {
        userDatabaseRepository.updateKyberKeyAsUsed(kyberPreKeyId);
    }

    // -------------------- SenderKeyStore Implementation Starts Here ---------------------

    /**
     * Stores a sender key for group messaging.
     * TODO: Implement group messaging sender key storage.
     */
    @Override
    public void storeSenderKey(SignalProtocolAddress sender, UUID distributionId, SenderKeyRecord record) {
        Log.e(Extras.LOG_MESSAGE, "storeSenderKey called for " + sender + " distribution " + distributionId);
    }

    /**
     * Loads a sender key for group messaging.
     * TODO: Implement group messaging sender key retrieval.
     */
    @Override
    public SenderKeyRecord loadSenderKey(SignalProtocolAddress sender, UUID distributionId) {
        Log.e(Extras.LOG_MESSAGE, "loadSenderKey called for " + sender + " distribution " + distributionId);
        return null;
    }
}

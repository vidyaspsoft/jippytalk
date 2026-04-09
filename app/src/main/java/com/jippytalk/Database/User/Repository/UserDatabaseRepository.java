package com.jippytalk.Database.User.Repository;

import android.content.Context;
import android.database.Cursor;
import android.util.Base64;
import android.util.Log;
import android.util.Pair;

import com.jippytalk.ServiceLocators.DatabaseServiceLocator;
import com.jippytalk.CryptoKeys.KeyStoreHelper;
import com.jippytalk.Database.User.UsersDatabase;
import com.jippytalk.Database.User.UsersDatabaseDAO;
import com.jippytalk.Extras;
import com.jippytalk.MyApplication;

import org.signal.libsignal.protocol.ecc.ECKeyPair;
import org.signal.libsignal.protocol.ecc.ECPrivateKey;
import org.signal.libsignal.protocol.ecc.ECPublicKey;
import org.signal.libsignal.protocol.state.KyberPreKeyRecord;
import org.signal.libsignal.protocol.state.PreKeyRecord;
import org.signal.libsignal.protocol.state.SignedPreKeyRecord;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.FutureTask;

public class UserDatabaseRepository {

    private final Context                           context;
    private static volatile UserDatabaseRepository  userDatabaseRepository;
    private final DatabaseServiceLocator            databaseServiceLocator;
    private final UsersDatabaseDAO                  usersDatabaseDAO;
    private final ExecutorService                   userDatabaseWriteExecutor;

    public UserDatabaseRepository(Context context, DatabaseServiceLocator databaseServiceLocator) {
        this.context                    =   context.getApplicationContext();
        this.databaseServiceLocator     =   databaseServiceLocator;
        userDatabaseWriteExecutor       =   databaseServiceLocator.getUserDatabaseWriteExecutor();
        usersDatabaseDAO                =   databaseServiceLocator.getUsersDatabaseDAO();
    }

    public static UserDatabaseRepository getInstance(Context context, DatabaseServiceLocator databaseServiceLocator) {
        if (userDatabaseRepository == null) {
            synchronized (UserDatabaseRepository.class) {
                if (userDatabaseRepository == null) {
                    userDatabaseRepository = new UserDatabaseRepository(context, databaseServiceLocator);
                }
            }
        }
        return userDatabaseRepository;
    }

    // -------------------- insert methods starts here ------------------------------------------


    public void insertIdentityAndSignedKeysInDatabase(String identityPublicKey, String identityPrivateKey, int signedPreKeyId,
                                                      String signedPreKeySignature, String signedPublicKey, String signedPrivateKey) {
        userDatabaseWriteExecutor.execute(() -> {
            boolean keysInsertion   =   usersDatabaseDAO.insertUserIdentityKeysAndSignedKeys(
                                        identityPublicKey, identityPrivateKey, signedPreKeyId,
                                        signedPreKeySignature, signedPublicKey, signedPrivateKey);
            if (keysInsertion) {
                Log.e(Extras.LOG_MESSAGE,"signal identity and signed Keys inserted Successfully");
            }
            else {
                Log.e(Extras.LOG_MESSAGE,"Failed to insert signal identity and signed Keys");
            }
        });
    }

    public void insertOTAndKyber(List<PreKeyRecord> oneTimePreKeys, List<KyberPreKeyRecord> kyberPreKeyRecords) {
        userDatabaseWriteExecutor.execute(() -> {
            boolean preKeyInsertion    =   usersDatabaseDAO.insertOneTimeAndKyberPreKeys(oneTimePreKeys, kyberPreKeyRecords);
            if (preKeyInsertion) {
                Log.e(Extras.LOG_MESSAGE,"one time and kyber pre keys inserted Successfully");
            }
        });
    }

    public void insertKyberPreKeys(int kyberPreKeyId, KyberPreKeyRecord kyberPreKeyRecord) {
        userDatabaseWriteExecutor.execute(() -> {
            boolean kyberPreKeyInsertion    =   usersDatabaseDAO.insertUserKyberPreKeys(kyberPreKeyId, kyberPreKeyRecord);
            if (kyberPreKeyInsertion) {
                Log.e(Extras.LOG_MESSAGE,"kyber pre key inserted Successfully");
            }
            else {
                Log.e(Extras.LOG_MESSAGE,"Failed to insert kyber pre key");
            }
        });
    }

    public void insertOneTimePreKeys(int oneTimePreKeyId, String oneTimePublicPreKey, String oneTimePrivatePreKey) {
        userDatabaseWriteExecutor.execute(() -> {
            boolean oneTimePreKeyInsertion    =     usersDatabaseDAO.insertUserOneTimePreKeys(
                                                    oneTimePreKeyId, oneTimePublicPreKey, oneTimePrivatePreKey);
            if (oneTimePreKeyInsertion) {
                Log.e(Extras.LOG_MESSAGE,"one time pre key inserted Successfully");
            }
            else {
                Log.e(Extras.LOG_MESSAGE,"Failed to insert one time pre key");
            }
        });
    }

    public boolean insertSignedPreKeys(int signedPreKeyId, String signedPreKeySignature, String signedPublicKey,
                                       String signedPrivateKey) {
        FutureTask<Boolean> insertSignedPreKeyTask  =   new FutureTask<>(() -> {
            return usersDatabaseDAO.insertUserSignedPreKeyRecord(signedPreKeyId, signedPreKeySignature,
                    signedPublicKey, signedPrivateKey);
        });
        userDatabaseWriteExecutor.execute(insertSignedPreKeyTask);
        try {
            return insertSignedPreKeyTask.get(); // Blocks until result is ready
        } catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "unable to insert signed pre key error in contact rep " + e.getMessage());
            return false;
        }
    }


    // ---------------------- read operations starts here ----------------------------------------

    public Pair<String, String> getIdentityKeyPairBlocking() {
        FutureTask<Pair<String, String>> task = new FutureTask<>(() -> {
            try (Cursor cursor = usersDatabaseDAO.getUserIdentityKeyPair()) {
                if (cursor != null && cursor.moveToFirst()) {
                    String publicKey = cursor.getString(cursor.getColumnIndexOrThrow(UsersDatabase.IDENTITY_KEY_PUBLIC));
                    String privateKey = cursor.getString(cursor.getColumnIndexOrThrow(UsersDatabase.IDENTITY_KEY_PRIVATE));
                    Log.e("KeyRepo", "Loaded identity key pair: " + publicKey + ", " + privateKey);
                    return new Pair<>(publicKey, privateKey);
                }
                else {
                    Log.e("KeyRepo", "No identity key pair found");
                }
            } catch (Exception e) {
                Log.e("KeyRepo", "Failed to load identity key pair", e);
            }
            return null;
        });

        userDatabaseWriteExecutor.execute(task);

        try {
            return task.get(); // Blocks until result is ready
        } catch (Exception e) {
            Log.e("KeyRepo", "Executor task failed", e);
            return null;
        }
    }

    public PreKeyRecord getPreKeyDetails(int preKeyId) {
        FutureTask<PreKeyRecord> preKeyRecordFutureTask =   new FutureTask<>(() -> {
            try( Cursor cursor = usersDatabaseDAO.getUserPreKeyDetails(preKeyId)) {
                if (cursor != null && cursor.moveToFirst()) {
                    String publicKeyBase64  =   cursor.getString(cursor.getColumnIndexOrThrow(UsersDatabase.ONE_TIME_PUBLIC_PRE_KEY));
                    String privateKeyBase64 =   cursor.getString(cursor.getColumnIndexOrThrow(UsersDatabase.ONE_TIME_PRIVATE_PRE_KEY));

                    byte[] publicKeyBytes   =   Base64.decode(publicKeyBase64, Base64.NO_WRAP);
                    byte[] privateKeyBytes  =   Base64.decode(privateKeyBase64, Base64.NO_WRAP);

                    byte[] decryptedPrivateKey  =   KeyStoreHelper.decrypt(privateKeyBytes);

                    ECPublicKey ecPublicKey     =   new ECPublicKey(publicKeyBytes);
                    ECPrivateKey ecPrivateKey    =   new ECPrivateKey(decryptedPrivateKey);

                    ECKeyPair ecKeyPair       =   new ECKeyPair(ecPublicKey, ecPrivateKey);
                    return new PreKeyRecord(preKeyId, ecKeyPair);
                }
            }
            catch (Exception e) {
                Log.e(Extras.LOG_MESSAGE, "Failed to load pre key details " + e.getMessage(), e);
            }
            return null;
        });

        userDatabaseWriteExecutor.execute(preKeyRecordFutureTask);

        try {
            return preKeyRecordFutureTask.get(); // Blocks until result is ready
        } catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "Executor task failed for getting pre key details " + e.getMessage());
            return null;
        }
    }

    public SignedPreKeyRecord getSignedPreKeys(int signedPreKeyId) {
        FutureTask<SignedPreKeyRecord> getSignedPreKeyTask  =   new FutureTask<>(() -> {
            try (Cursor cursor  =   usersDatabaseDAO.getUserSignedPreKeyRecordDetailsFromDatabase(signedPreKeyId)) {
                if (cursor != null && cursor.moveToFirst()) {
                    String signedPublicKey      =   cursor.getString(
                            cursor.getColumnIndexOrThrow(UsersDatabase.SIGNED_PUBLIC_KEY));
                    String signedPrivateKey     =   cursor.getString(
                            cursor.getColumnIndexOrThrow(UsersDatabase.SIGNED_PRIVATE_KEY));
                    String signedPreSignature   =   cursor.getString(
                            cursor.getColumnIndexOrThrow(UsersDatabase.SIGNED_PRE_KEY_SIGNATURE));

                    byte[]  signedPublicKeyBytes        =   Base64.decode(signedPublicKey, Base64.NO_WRAP);
                    byte[]  signedPrivateKeyBytes       =   Base64.decode(signedPrivateKey, Base64.NO_WRAP);
                    byte[]  decryptedSignedPrivateKey   =   KeyStoreHelper.decrypt(signedPrivateKeyBytes);
                    byte[]  signedPreSignatureBytes     =   Base64.decode(signedPreSignature, Base64.NO_WRAP);

                    ECPublicKey     ecPublicKey         =   new ECPublicKey(signedPublicKeyBytes);
                    ECPrivateKey    ecPrivateKey        =   new ECPrivateKey(decryptedSignedPrivateKey);

                    ECKeyPair       ecKeyPair           =   new ECKeyPair(ecPublicKey, ecPrivateKey);
                    return new SignedPreKeyRecord(signedPreKeyId, System.currentTimeMillis(), ecKeyPair, signedPreSignatureBytes);
                }
            }
            catch (Exception e) {
                Log.e(Extras.LOG_MESSAGE, "Executor task failed for getting pre key details " + e.getMessage());
            }
            return null;
        });

        userDatabaseWriteExecutor.execute(getSignedPreKeyTask);

        try {
            return getSignedPreKeyTask.get();
        } catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "task running failed to get signedPreKeyRecord error in contact rep " + e.getMessage());
            return null;
        }
    }

    public KyberPreKeyRecord getKyberPreKeyDetails(int kyberPreKeyId) {
        FutureTask<KyberPreKeyRecord> kyberPreKeyRecordFutureTask   =   new FutureTask<>(() -> {
            try (Cursor cursor = usersDatabaseDAO.getUserKyberPreKeyRecordDetails(kyberPreKeyId)) {
                if (cursor != null && cursor.moveToFirst()) {
                    byte[]  kyberPreSerializedRecord    =   cursor.getBlob(cursor.getColumnIndexOrThrow(
                            UsersDatabase.KYBER_SERIALIZED_KEY));
                    return new KyberPreKeyRecord(kyberPreSerializedRecord);
                }
            }
            catch (Exception e) {
                Log.e(Extras.LOG_MESSAGE, "task running failed to get kyber pre key record error in contact rep " + e.getMessage());
            }
            return null;
        });

        userDatabaseWriteExecutor.execute(kyberPreKeyRecordFutureTask);

        try {
            return kyberPreKeyRecordFutureTask.get();
        } catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "task running failed to get kyber pre key record error in contact rep " + e.getMessage());
            return null;
        }

    }


    // -------------------- checking the data exists code starts here ----------------------------

    public boolean checkIsPreKeyExists(int preKeyId) {
        FutureTask<Boolean> preKeyExistsTask    =   new FutureTask<>(() -> {
            return usersDatabaseDAO.checkIsUserPreKeyExists(preKeyId);
        });

        userDatabaseWriteExecutor.execute(preKeyExistsTask);

        try {
            return preKeyExistsTask.get();
        }
        catch (Exception e) {
            return false;
        }
    }

    public boolean checkIsKyberPreKeyExists(int kyberPreKeyId) {
        FutureTask<Boolean> kyberPreKeyExistsTask    =   new FutureTask<>(() -> {
            try (Cursor cursor = usersDatabaseDAO.checkIsUserKyberPreKeyExists(kyberPreKeyId)) {
                if (cursor != null && cursor.getCount() > 0) {
                    return true;
                }
            }
            catch (Exception e) {
                return false;
            }
            return false;
        });

        userDatabaseWriteExecutor.execute(kyberPreKeyExistsTask);

        try {
            return kyberPreKeyExistsTask.get();
        }
        catch (Exception e) {
            return false;
        }
    }

    public int checkOneTimePreKeysAndGetLastKeyId() {
        return usersDatabaseDAO.getUserLastOneTimePreKeyId();
    }

    public void updateKyberKeyAsUsed(int kyberPreKeyId) {
        userDatabaseWriteExecutor.execute(() -> {
            boolean isUpdated = usersDatabaseDAO.updateKyberPreKeyAsUsed(kyberPreKeyId);
            if (isUpdated) {
                Log.e(Extras.LOG_MESSAGE, "successfully updated kyber pre key as used");
            } else {
                Log.e(Extras.LOG_MESSAGE, "Failed to update kyber pre key as used");
            }
        });
    }

    // --------------------------- delete method starts here ----------------------------------

    public boolean deleteOneTimeKey(int preKeyId) {
        FutureTask<Boolean> deleteOneTimePreKeyTask =   new FutureTask<>(() -> {
            return usersDatabaseDAO.deletePreKey(preKeyId);
        });

        userDatabaseWriteExecutor.execute(deleteOneTimePreKeyTask);

        try {
            return deleteOneTimePreKeyTask.get();
        } catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE,"unable to delete preKey error in rep " + e.getMessage());
            return false;
        }
    }

}

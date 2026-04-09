package com.jippytalk.Database.User;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.util.Base64;
import android.util.Log;


import com.jippytalk.CryptoKeys.KeyStoreHelper;
import com.jippytalk.Extras;

import net.zetetic.database.sqlcipher.SQLiteDatabase;
import net.zetetic.database.sqlcipher.SQLiteNotADatabaseException;

import org.signal.libsignal.protocol.state.KyberPreKeyRecord;
import org.signal.libsignal.protocol.state.PreKeyRecord;

import java.util.List;

public class UsersDatabaseDAO {

    private final Context       context;
    private final UsersDatabase usersDatabase;

    public UsersDatabaseDAO(Context context, UsersDatabase usersDatabase) {
        this.context        =   context.getApplicationContext();
        this.usersDatabase  =   usersDatabase;
    }

//  -------------------- insert code starts here ---------------------------------------

    public boolean insertUserIdentityKeysAndSignedKeys(String identityPublicKey, String identityPrivateKey, int signedPreKeyId,
                                                       String signedPreKeySignature, String signedPublicKey,
                                                       String signedPrivateKey) {
        boolean isSuccess   =   false;
        SQLiteDatabase  sqLiteDatabase      =   null;
        try {
            sqLiteDatabase      =   usersDatabase.getWritableDb();
            sqLiteDatabase.beginTransaction();
            try {
                ContentValues contentValues   =   new ContentValues();
                contentValues.put(UsersDatabase.IDENTITY_KEY_PUBLIC, identityPublicKey);
                contentValues.put(UsersDatabase.IDENTITY_KEY_PRIVATE, identityPrivateKey);

                long insertIdentityKeys     =   sqLiteDatabase.insert(UsersDatabase.IDENTITY_KEYS_TABLE,
                        null, contentValues);

                if (insertIdentityKeys != -1) {
                    ContentValues signedKeyValues   =   new ContentValues();
                    signedKeyValues.put(UsersDatabase.SIGNED_PRE_KEY_ID, signedPreKeyId);
                    signedKeyValues.put(UsersDatabase.SIGNED_PRE_KEY_SIGNATURE, signedPreKeySignature);
                    signedKeyValues.put(UsersDatabase.SIGNED_PUBLIC_KEY, signedPublicKey);
                    signedKeyValues.put(UsersDatabase.SIGNED_PRIVATE_KEY, signedPrivateKey);

                    long insertSignedKeys   =   sqLiteDatabase.insert(UsersDatabase.SIGNED_KEYS_TABLE,
                            null, signedKeyValues);

                    if (insertSignedKeys != -1) {
                        sqLiteDatabase.setTransactionSuccessful();
                        isSuccess    =  true;
                    }
                }
            }
            catch (Exception e) {
                logMessage("failed to insert user identity and signed keys caught SQL exception " + e.getMessage());
            }
            finally {
                sqLiteDatabase.endTransaction();
            }
        }
        catch (Exception e) {
            logMessage("failed to insert user identity and signed keys caught exception " + e.getMessage());
        }
        return isSuccess;
    }

    public boolean insertUserSignedPreKeyRecord(int signedPreKeyId, String signedPreKeySignature,
                                                String signedPublicKey, String signedPrivateKey ) {
        boolean isSuccess   =   false;
        SQLiteDatabase sqLiteDatabase  =   usersDatabase.getWritableDb();
        try {
            sqLiteDatabase.beginTransaction();
            try {
                ContentValues signedKeyValues   =   new ContentValues();
                signedKeyValues.put(UsersDatabase.SIGNED_PRE_KEY_ID, signedPreKeyId);
                signedKeyValues.put(UsersDatabase.SIGNED_PRE_KEY_SIGNATURE, signedPreKeySignature);
                signedKeyValues.put(UsersDatabase.SIGNED_PUBLIC_KEY, signedPublicKey);
                signedKeyValues.put(UsersDatabase.SIGNED_PRIVATE_KEY, signedPrivateKey);

                long insertSignedKeys   =   sqLiteDatabase.insert(UsersDatabase.SIGNED_KEYS_TABLE,
                        null, signedKeyValues);

                if (insertSignedKeys != -1) {
                    sqLiteDatabase.setTransactionSuccessful();
                    isSuccess    =  true;
                }
            }
            catch (Exception e) {
                logMessage("failed to insert signed pre keys error in db file caught exception "+e.getMessage());
            }
            finally {
                sqLiteDatabase.endTransaction();
            }
        } catch (Exception e) {
            logMessage("failed to insert signed pre keys error in db file caught exception " + e.getMessage());
        }
        return isSuccess;
    }

    public boolean insertUserKyberPreKeys(int kyberPreKeyId, KyberPreKeyRecord kyberPreKeyRecord) {
        boolean isSuccess   =   false;
        SQLiteDatabase sqLiteDatabase  =   usersDatabase.getWritableDb();
        try {
            sqLiteDatabase.beginTransaction();
            try {
                ContentValues   contentValues   =   new ContentValues();
                contentValues.put(UsersDatabase.KYBER_PRE_KEY_ID, kyberPreKeyId);
                contentValues.put(UsersDatabase.KYBER_SERIALIZED_KEY, kyberPreKeyRecord.serialize());

                long result     =   sqLiteDatabase.insert(UsersDatabase.KYBER_PRE_KEYS_TABLE, null, contentValues);
                if (result != -1) {
                    sqLiteDatabase.setTransactionSuccessful();
                    isSuccess   =   true;
                }
            }
            catch (SQLException e) {
                logMessage("Unable to insert kyberPreKeys error in DAO caught SQL exception" + e.getMessage());
            }
            finally {
                sqLiteDatabase.endTransaction();
            }
        }
        catch (Exception e) {
            logMessage("Unable to insert kyberPreKeys error exception in DAO " + e.getMessage());
        }
        return isSuccess;
    }

    public boolean insertUserOneTimePreKeys(int oneTimePreKeyId, String publicPreKey, String privatePreKey) {
        boolean isSuccess   =   false;
        SQLiteDatabase sqLiteDatabase   =   usersDatabase.getWritableDb();
        try {
            sqLiteDatabase.beginTransaction();
            try {
                ContentValues values = new ContentValues();
                values.put(UsersDatabase.ONE_TIME_PRE_KEY_ID, oneTimePreKeyId);
                values.put(UsersDatabase.ONE_TIME_PUBLIC_PRE_KEY, publicPreKey);
                values.put(UsersDatabase.ONE_TIME_PRIVATE_PRE_KEY, privatePreKey);
                long result  =    sqLiteDatabase.insert(UsersDatabase.ONE_TIME_PRE_KEYS_TABLE, null, values);
                if (result != -1) {
                    sqLiteDatabase.setTransactionSuccessful();
                    isSuccess    =   true;
                }
            }
            catch (SQLiteNotADatabaseException e) {
                logMessage("failed to insert one pre keys exception " + e.getMessage());
            }
            finally {
                sqLiteDatabase.endTransaction();
            }
        }
        catch (SQLException e) {
            logMessage("failed to insert one pre keys exception caught SQL exception " + e.getMessage());
        }
        return isSuccess;
    }

    public boolean insertOneTimeAndKyberPreKeys(List<PreKeyRecord> oneTimePreKeys, List<KyberPreKeyRecord> kyberPreKeyRecords) {

        boolean         isSuccess       =   false;
        SQLiteDatabase  sqLiteDatabase  =   usersDatabase.getWritableDb();

        try {
            sqLiteDatabase.beginTransaction();

            try {
                for (PreKeyRecord preKey : oneTimePreKeys) {
                    int keyId = preKey.getId();
                    byte[] encryptedOneTimePrivatePreKey = KeyStoreHelper.encrypt(preKey.getKeyPair().getPrivateKey().serialize());
                    byte[] publicKeyBytes = preKey.getKeyPair().getPublicKey().serialize();

                    if (publicKeyBytes.length != 33) {
                        Log.e(Extras.LOG_MESSAGE, "Skipping preKey with invalid public key length: " + publicKeyBytes.length);
                        continue;
                    }

                    String publicKeyBase64  = Base64.encodeToString(publicKeyBytes, Base64.NO_WRAP);
                    String privateKeyBase64 = Base64.encodeToString(encryptedOneTimePrivatePreKey, Base64.NO_WRAP);

                    ContentValues values = new ContentValues();
                    values.put(UsersDatabase.ONE_TIME_PRE_KEY_ID, keyId);
                    values.put(UsersDatabase.ONE_TIME_PUBLIC_PRE_KEY, publicKeyBase64);
                    values.put(UsersDatabase.ONE_TIME_PRIVATE_PRE_KEY, privateKeyBase64);

                    long oneTimePreKeysInsertion    =   sqLiteDatabase.insert(UsersDatabase.ONE_TIME_PRE_KEYS_TABLE, null, values);
                    if (oneTimePreKeysInsertion != -1) {
                        logMessage("One time pre keys inserted successfully");
                    } else {
                        logMessage("Failed to insert one time pre keys");
                    }
                }


                for (KyberPreKeyRecord record : kyberPreKeyRecords) {
                    ContentValues contentValues = new ContentValues();
                    contentValues.put(UsersDatabase.KYBER_PRE_KEY_ID, record.getId());
                    contentValues.put(UsersDatabase.KYBER_SERIALIZED_KEY, record.serialize());
                    long kyberPreKeysInsertion = sqLiteDatabase.insert(UsersDatabase.KYBER_PRE_KEYS_TABLE, null, contentValues);
                    if (kyberPreKeysInsertion != -1) {
                        logMessage("Kyber pre keys inserted successfully");
                    } else {
                        logMessage("Failed to insert kyber pre keys");
                    }
                }

                sqLiteDatabase.setTransactionSuccessful();
                isSuccess = true;
                logMessage("All pre keys inserted successfully in a single transaction.");

            } catch (SQLException e) {
                logMessage("Failed to insert pre keys due to a SQLite error: " + e.getMessage());
            } finally {
                sqLiteDatabase.endTransaction();
            }
        } catch (Exception e) {
            logMessage("An unexpected error occurred while processing pre key insertions: " + e.getMessage());
        }
        return isSuccess;
    }

    //  -------------------- insert code Ends here ---------------------------------------

    //  -------------------- Read code starts here ---------------------------------------

    public Cursor getUserIdentityKeyPair() {
        try {
            SQLiteDatabase sqLiteDatabase      =   usersDatabase.getReadableDb();
            String          query               =   "SELECT " + UsersDatabase.IDENTITY_KEY_PUBLIC + " ,"
                    + UsersDatabase.IDENTITY_KEY_PRIVATE
                    + " FROM " + UsersDatabase.IDENTITY_KEYS_TABLE;
            return sqLiteDatabase.rawQuery(query, null);
        }
        catch (SQLException e) {
            logMessage("Unable to get user identity key pair Error in DAO: " + e.getMessage());
        }
        return null;
    }

    public Cursor getUserSignedPreKeyRecordDetailsFromDatabase(int signedPreKeyId) {
        try {
            SQLiteDatabase  sqLiteDatabase      =   usersDatabase.getReadableDb();
            String          query               =   "SELECT * FROM " + UsersDatabase.SIGNED_KEYS_TABLE + " WHERE "
                    + UsersDatabase.SIGNED_PRE_KEY_ID + " =?";
            return sqLiteDatabase.rawQuery(query, new String[]{String.valueOf(signedPreKeyId)});
        }
        catch (SQLException e) {
            logMessage("failed to get signed pre key details caught SQL exception error in DAO "+e.getMessage());
        }
        return null;
    }

    public Cursor getUserIdentityAndSignedKeys() {
        try {
            SQLiteDatabase sqLiteDatabase      =   usersDatabase.getReadableDb();
            String query = "SELECT " +
                    "ik." + UsersDatabase.IDENTITY_KEY_PUBLIC + ", " +
                    "sk." + UsersDatabase.SIGNED_PRE_KEY_ID + ", " +
                    "sk." + UsersDatabase.SIGNED_PRE_KEY_SIGNATURE + ", " +
                    "sk." + UsersDatabase.SIGNED_PUBLIC_KEY +
                    " FROM " + UsersDatabase.IDENTITY_KEYS_TABLE + " AS ik " +
                    "JOIN " + UsersDatabase.SIGNED_KEYS_TABLE + " AS sk ON sk." +
                    UsersDatabase.SIGNED_KEYS_SQLITE_ID + " = (" +
                    "SELECT MAX(" + UsersDatabase.SIGNED_KEYS_SQLITE_ID + ") FROM " +
                    UsersDatabase.SIGNED_KEYS_TABLE + ") " +
                    "LIMIT 1";

            return sqLiteDatabase.rawQuery(query, null);
        } catch (SQLException e) {
            Log.e(Extras.LOG_MESSAGE, "Unable to get identity and signed keys. Error in DAO: " + e.getMessage());
        }
        return null;
    }

    public Cursor getUserPreKeyDetails(int preKeyId) {
        try {
            SQLiteDatabase  sqLiteDatabase      =   usersDatabase.getReadableDb();
            String          query               =   "SELECT * FROM " + UsersDatabase.ONE_TIME_PRE_KEYS_TABLE +
                    " WHERE " + UsersDatabase.ONE_TIME_PRE_KEY_ID + " =?";
            return  sqLiteDatabase.rawQuery(query, new String[]{String.valueOf(preKeyId)});
        }
        catch (SQLException e) {
            Log.e(Extras.LOG_MESSAGE, "Unable to load preKey caught SQL " + e.getMessage());
        }
        return null;
    }

    public Cursor getUserOneTimePreKeys() {
        try {
            SQLiteDatabase sqLiteDatabase  =   usersDatabase.getReadableDb();
            String          query           =   "SELECT " + UsersDatabase.ONE_TIME_PRE_KEY_ID + ", "
                    + UsersDatabase.ONE_TIME_PUBLIC_PRE_KEY + " FROM " + UsersDatabase.ONE_TIME_PRE_KEYS_TABLE;
            return sqLiteDatabase.rawQuery(query, null);
        }
        catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE,"Unable to retrieve one time preKeys "+e.getMessage());
        }
        return null;
    }

    public Cursor getUserKyberPreKeys() {
        try {
            SQLiteDatabase sqLiteDatabase  =   usersDatabase.getReadableDb();
            String          query           =   "SELECT " + UsersDatabase.KYBER_PRE_KEY_ID + ", " + UsersDatabase.KYBER_SERIALIZED_KEY +
                    " FROM " + UsersDatabase.KYBER_PRE_KEYS_TABLE + " WHERE " + UsersDatabase.IS_KYBER_KEY_USED + " = ?";
            return sqLiteDatabase.rawQuery(query, new String[]{"0"});
        }
        catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE,"Unable to retrieve one time preKeys "+e.getMessage());
        }
        return null;
    }

    public Cursor getUserKyberPreKeyRecordDetails(int kyberPreKeyId) {
        try {
            SQLiteDatabase sqLiteDatabase  =   usersDatabase.getReadableDb();
            return sqLiteDatabase.rawQuery("SELECT * FROM " + UsersDatabase.KYBER_PRE_KEYS_TABLE +
                    " WHERE "+ UsersDatabase.KYBER_PRE_KEY_ID  + "=?", new String[]{String.valueOf(kyberPreKeyId)});
        }
        catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE,"Unable to get user details error logged in DAO file "+e.getMessage());
        }
        return null;
    }

    public boolean  checkIsUserPreKeyExists(int preKeyId) {
        boolean isExist     =   false;

        SQLiteDatabase sqLiteDatabase  =   usersDatabase.getReadableDb();
        try (Cursor cursor = sqLiteDatabase.rawQuery("SELECT 1 FROM " + UsersDatabase.ONE_TIME_PRE_KEYS_TABLE
                + " WHERE " + UsersDatabase.ONE_TIME_PRE_KEY_ID + "=?", new String[] {String.valueOf(preKeyId)})) {
            if (cursor.getCount() > 0) {
                isExist = true;
            }
        } catch (Exception e)
        {
            Log.e(Extras.LOG_MESSAGE, "Error checking if contact is blocked: " + e.getMessage());
        }
        return isExist;
    }

    public Cursor checkIsUserKyberPreKeyExists(int kyberPreKeyId) {
        try {
            SQLiteDatabase  sqLiteDatabase  =   usersDatabase.getReadableDb();
            String          query           =   "SELECT 1 FROM " + UsersDatabase.KYBER_PRE_KEYS_TABLE
                    + " WHERE " + UsersDatabase.KYBER_PRE_KEY_ID + "=?";
            return sqLiteDatabase.rawQuery(query, new String[]{String.valueOf(kyberPreKeyId)});
        }
        catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "Error checking if kyber key exists or not " + e.getMessage());
        }
        return null;
    }

    public int getUserLastOneTimePreKeyId() {
        try {
            SQLiteDatabase  sqLiteDatabase  =   usersDatabase.getReadableDb();
            Cursor          cursor          =   sqLiteDatabase.rawQuery(
                    "SELECT MAX(" + UsersDatabase.ONE_TIME_PRE_KEY_ID + ") AS " + UsersDatabase.ONE_TIME_PRE_KEY_ID +
                            " FROM " + UsersDatabase.ONE_TIME_PRE_KEYS_TABLE,
                    null);
            int lastId = 0;
            if (cursor.moveToFirst()) {
                int columnIndex = cursor.getColumnIndexOrThrow(UsersDatabase.ONE_TIME_PRE_KEY_ID);
                if (!cursor.isNull(columnIndex)) {
                    lastId = cursor.getInt(columnIndex);
                }
            }
            cursor.close();
            return lastId;
        } catch (Exception e) {
            logMessage("failed to get last pre key id caught exception " + e.getMessage());
        }
        return -1;
    }

    //  --------------------- Read code Ends here ---------------------------------------


    // ------------------- Update Code Starts here ---------------------------------------


    public boolean updateKyberPreKeyAsUsed(int kyberPreKeyId) {
        boolean         isUpdated       =   false;
        SQLiteDatabase  sqLiteDatabase  =   usersDatabase.getWritableDb();
        try {
            sqLiteDatabase.beginTransaction();
            ContentValues   contentValues   =   new ContentValues();
            contentValues.put(UsersDatabase.IS_KYBER_KEY_USED, 1);
            int rowsEffected    =   sqLiteDatabase.update(UsersDatabase.KYBER_PRE_KEYS_TABLE, contentValues,
                    UsersDatabase.KYBER_PRE_KEY_ID + "=?", new String[]{String.valueOf(kyberPreKeyId)});
            if (rowsEffected > 0) {
                isUpdated   =   true;
                sqLiteDatabase.setTransactionSuccessful();
            }
        }
        catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "Error accessing the database " + e.getMessage());
        }
        finally {
            sqLiteDatabase.endTransaction();
        }
        return isUpdated;
    }

    // ------------------- delete methods starts here ------------------------------------------

    public boolean deletePreKey(int preKeyId) {

        boolean isDeleted = false;
        SQLiteDatabase  sqLiteDatabase  =   usersDatabase.getReadableDb();
        try
        {
            try {
                sqLiteDatabase.beginTransaction();
                int rowsEffected = sqLiteDatabase.delete(UsersDatabase.ONE_TIME_PRE_KEYS_TABLE,
                        UsersDatabase.ONE_TIME_PRE_KEY_ID +" =?",new String[] {String.valueOf(preKeyId)});
                if (rowsEffected > 0) {
                    sqLiteDatabase.setTransactionSuccessful();
                    isDeleted = true;
                }
                else {
                    Log.e(Extras.LOG_MESSAGE,"unable to delete the message");
                }
            }
            catch (SQLException e) {
                Log.e(Extras.LOG_MESSAGE,"unable to delete the blocked contacts "+e.getMessage());
            }
            finally {
                sqLiteDatabase.endTransaction();
            }
        }
        catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE,"Error accessing the database "+e.getMessage());
        }
        return isDeleted;
    }


    private void logMessage(String message) {
        Log.e(Extras.LOG_MESSAGE, message);
    }

}

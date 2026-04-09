package com.jippytalk.Database.SessionDatabase;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.SQLException;
import android.util.Log;

import com.jippytalk.Extras;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import org.signal.libsignal.protocol.SignalProtocolAddress;
import org.signal.libsignal.protocol.state.SessionRecord;

import java.util.ArrayList;
import java.util.List;

public class SessionStoreDAO {


    private final SessionsDatabase sessionsDatabase;

    public SessionStoreDAO(SessionsDatabase sessionsDatabase) {
        this.sessionsDatabase = sessionsDatabase;
    }

     /*
    inserting details into sessions table starts here -------------------------------------
     */

    public boolean storeSession(SignalProtocolAddress address, SessionRecord sessionRecord) {

        boolean isSuccess = false;
        SQLiteDatabase sqLiteDatabase = sessionsDatabase.getWritableDatabase();
        try  {

            ContentValues contentValues = new ContentValues();
            contentValues.put(SessionsDatabase.SESSION_CONTACT_ID, address.getName());
            contentValues.put(SessionsDatabase.SESSION_DEVICE_ID, address.getDeviceId());
            contentValues.put(SessionsDatabase.SESSION_OBJECT, sessionRecord.serialize());

            long result = sqLiteDatabase.insertWithOnConflict(
                    SessionsDatabase.SESSION_RECORDS_TABLE,
                    null, // Use nullColumnHack if you have a nullable column
                    contentValues,
                    SQLiteDatabase.CONFLICT_REPLACE); // Use CONFLICT_REPLACE

            if (result != -1) {
                isSuccess = true;
                Log.d("SessionStore", "Session stored (inserted or updated) for " + address);
            } else {
                Log.e("SessionStore", "Failed to store session for " + address);
            }

        } catch (SQLException e) {
            Log.e(Extras.LOG_MESSAGE, "Unable to store session due to SQL exception: " + e.getMessage());
        }
        return isSuccess;
    }



     /*
    inserting details into sessions table ends here -------------------------------------
     */


    /*
    Retrieving details from sessions table starts here -------------------------------------
     */


    public Cursor loadSessionFromDatabase(SignalProtocolAddress signalProtocolAddress) {
        try {
            SQLiteDatabase sqLiteDatabase = sessionsDatabase.getReadableDb();
            String query = "SELECT " + SessionsDatabase.SESSION_OBJECT +
                    " FROM " + SessionsDatabase.SESSION_RECORDS_TABLE +
                    " WHERE " + SessionsDatabase.SESSION_CONTACT_ID + " = ? AND " + SessionsDatabase.SESSION_DEVICE_ID + " = ? LIMIT 1";
            return sqLiteDatabase.rawQuery(query, new String[] {signalProtocolAddress.getName(), String.valueOf(signalProtocolAddress.getDeviceId())});

        } catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "Unable to retrieve session record caught exception " + e.getMessage(), e);
        }
        return null;
    }


    public List<SessionRecord> getAllSessionsForUser(String contactId) {
        List<SessionRecord> sessions = new ArrayList<>();
        SQLiteDatabase sqLiteDatabase = sessionsDatabase.getReadableDb();
        try  {
            String query = "SELECT " + SessionsDatabase.SESSION_OBJECT +
                    " FROM " + SessionsDatabase.SESSION_RECORDS_TABLE +
                    " WHERE " + SessionsDatabase.SESSION_CONTACT_ID + " = ?";

            try (Cursor cursor = sqLiteDatabase.rawQuery(query, new String[] {contactId})) {
                while (cursor.moveToNext()) {
                    byte[] sessionBytes = cursor.getBlob(cursor.getColumnIndexOrThrow(SessionsDatabase.SESSION_OBJECT));
                    sessions.add(new SessionRecord(sessionBytes));
                }
            }
        } catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "Error retrieving sessions for user: " + contactId, e);
        }

        return sessions;
    }




     /*
    Retrieving details from sessions table ends here -------------------------------------
     */


    public Cursor checkIsSessionExists(SignalProtocolAddress signalProtocolAddress) {
        try {
            SQLiteDatabase sqLiteDatabase = sessionsDatabase.getReadableDb();
            return sqLiteDatabase.rawQuery("SELECT 1 FROM " + SessionsDatabase.SESSION_RECORDS_TABLE
                    + " WHERE " + SessionsDatabase.SESSION_CONTACT_ID + "=? AND " + SessionsDatabase.SESSION_DEVICE_ID
                    +  " =? ", new String[] {signalProtocolAddress.getName(), String.valueOf(signalProtocolAddress.getDeviceId())});
        } catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "Error checking if session exists " + e.getMessage());
        }
        return null;
    }

     /*
    updating details into sessions table starts here -------------------------------------
     */

    public boolean updateSessionDetails(String contactId, byte[] sessionRecord) {
        boolean isSuccess   =   false;
        SQLiteDatabase sqLiteDatabase = sessionsDatabase.getWritableDb();
        try
        {
            sqLiteDatabase.beginTransaction();
            try {
                ContentValues   contentValues   =   new ContentValues();
                contentValues.put(SessionsDatabase.SESSION_OBJECT, sessionRecord);

                int rowsAffected    =   sqLiteDatabase.update(SessionsDatabase.SESSION_RECORDS_TABLE, contentValues,
                        SessionsDatabase.SESSION_CONTACT_ID + "=?", new String[]{contactId});

                if (rowsAffected > 0) {
                    sqLiteDatabase.setTransactionSuccessful();
                    isSuccess   =   true;
                }
            }
            catch (Exception e) {
                Log.e(Extras.LOG_MESSAGE, "Unable to update session record caught exception " + e.getMessage());
            }
            finally {
                sqLiteDatabase.endTransaction();
            }
        }
        catch (SQLException e)
        {
            Log.e(Extras.LOG_MESSAGE,"Unable to update session record caught sql exception " + e.getMessage());
        }
        return isSuccess;
    }

       /*
    updating details into sessions table ends here -------------------------------------
     */

    public boolean deleteSession(SignalProtocolAddress signalProtocolAddress) {
        boolean isSuccess = false;
        SQLiteDatabase sqLiteDatabase = sessionsDatabase.getWritableDb();
        try  {
            sqLiteDatabase.beginTransaction();
            try {
                int rowsDeleted = sqLiteDatabase.delete(
                        SessionsDatabase.SESSION_RECORDS_TABLE,
                        SessionsDatabase.SESSION_CONTACT_ID + "=? AND " +
                                SessionsDatabase.SESSION_DEVICE_ID  +   " =?",
                        new String[] {signalProtocolAddress.getName(), String.valueOf(signalProtocolAddress.getDeviceId())}
                );

                if (rowsDeleted > 0) {
                    sqLiteDatabase.setTransactionSuccessful();
                    Log.e(Extras.LOG_MESSAGE,"Session deleted succefully " + signalProtocolAddress.getName());
                    isSuccess = true;
                }
            } catch (Exception e) {
                Log.e(Extras.LOG_MESSAGE, "Unable to delete session caught exception " + e.getMessage());
            } finally {
                sqLiteDatabase.endTransaction();
            }
        } catch (SQLException e) {
            Log.e(Extras.LOG_MESSAGE, "Unable to delete session due to SQL exception " + e.getMessage());
        }

        return isSuccess;
    }

}

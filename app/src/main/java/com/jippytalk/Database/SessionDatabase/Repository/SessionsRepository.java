package com.jippytalk.Database.SessionDatabase.Repository;

import android.content.Context;
import android.database.Cursor;
import android.util.Log;

import com.jippytalk.ServiceLocators.DatabaseServiceLocator;
import com.jippytalk.Database.SessionDatabase.SessionStoreDAO;
import com.jippytalk.Database.SessionDatabase.SessionsDatabase;
import com.jippytalk.Extras;
import com.jippytalk.MyApplication;

import org.signal.libsignal.protocol.SignalProtocolAddress;
import org.signal.libsignal.protocol.state.SessionRecord;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.FutureTask;

public class SessionsRepository {


    private static SessionsRepository   sessionsRepository;
    private final SessionStoreDAO       sessionStoreDAO;
    private ExecutorService             readExecutor;
    private ExecutorService             writeExecutor;



    public SessionsRepository(Context context) {
        DatabaseServiceLocator databaseServiceLocator =   ((MyApplication) context.getApplicationContext()).getDatabaseServiceLocator();
        sessionStoreDAO                         =   databaseServiceLocator.getSessionStoreDAO();
        readExecutor                            =   databaseServiceLocator.getReadExecutor();
        writeExecutor                           =   databaseServiceLocator.getWriteExecutor();
    }


    public static SessionsRepository getInstance(Context context) {
        if (sessionsRepository == null) sessionsRepository  =   new SessionsRepository(context.getApplicationContext());
        return sessionsRepository;
    }


    public boolean insertContactSession(SignalProtocolAddress signalProtocolAddress, SessionRecord sessionRecord) {
        FutureTask<Boolean> insertSessionTask   =   new FutureTask<>(() -> {
            return sessionStoreDAO.storeSession(signalProtocolAddress, sessionRecord);
        });

        writeExecutor.execute(insertSessionTask);

        try {
            return insertSessionTask.get();
        } catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE,"Unable to insert session error in rep " + e.getMessage());
            return false;
        }
    }


    public SessionRecord getSessionRecord(SignalProtocolAddress signalProtocolAddress) {
        FutureTask<SessionRecord> sessionRecordFutureTask   =   new FutureTask<>(() -> {
            try (Cursor cursor = sessionStoreDAO.loadSessionFromDatabase(signalProtocolAddress))
            {
                if (cursor != null && cursor.moveToFirst()) {
                    byte[] sessionBlob = cursor.getBlob(cursor.getColumnIndexOrThrow(SessionsDatabase.SESSION_OBJECT));
                    if (sessionBlob == null || sessionBlob.length == 0) {
                        Log.e("getSession", "Session blob is null or empty");
                        return null;
                    }
                    return new SessionRecord(sessionBlob);
                }
                else {
                    return new SessionRecord();
                }
            } catch (Exception e) {
                Log.e(Extras.LOG_MESSAGE,"Unable to get session record error in session rep " + e.getMessage());
                return new SessionRecord();
            }
        });
        readExecutor.execute(sessionRecordFutureTask);

       try {
           return sessionRecordFutureTask.get();
       } catch (Exception e) {
           Log.e(Extras.LOG_MESSAGE,"Unable to run the get session record task error in session rep " + e.getMessage());
       }
        return new SessionRecord();
    }

    public boolean checkIsSessionExists(SignalProtocolAddress signalProtocolAddress) {
        FutureTask<Boolean> checkSessionExistsTask  =   new FutureTask<>(() -> {
            boolean isExists    =   false;
            try (Cursor cursor = sessionStoreDAO.checkIsSessionExists(signalProtocolAddress))
            {
                if (cursor != null && cursor.getCount() > 0) {
                    isExists    =   true;
                }
            }
            catch (Exception e)
            {
                Log.e(Extras.LOG_MESSAGE, "Error checking if contact is blocked: " + e.getMessage());
            }
            return isExists;
        });

        writeExecutor.execute(checkSessionExistsTask);

        try {
            return checkSessionExistsTask.get();
        } catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE,"unable to check is session exists error in rep " + e.getMessage());
            return false;
        }
    }

    public boolean deleteSession(SignalProtocolAddress signalProtocolAddress) {
        FutureTask<Boolean> deleteSessionTask   =   new FutureTask<>(() -> {
            return sessionStoreDAO.deleteSession(signalProtocolAddress);
        });
        writeExecutor.execute(deleteSessionTask);

        try {
            return deleteSessionTask.get();
        } catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE,"unable to delete session error in rep " + e.getMessage());
            return false;
        }
    }


}

package com.jippytalk.Database;

import net.zetetic.database.sqlcipher.SQLiteConnection;
import net.zetetic.database.sqlcipher.SQLiteDatabaseHook;

public class SqliteDatabaseHook implements SQLiteDatabaseHook {
    @Override
    public void preKey(SQLiteConnection connection) {
        connection.execute("PRAGMA cipher_default_kdf_iter = 1;", null, null);
        connection.execute("PRAGMA cipher_default_page_size = 4096;", null, null);
    }

    @Override
    public void postKey(SQLiteConnection connection) {
//        connection.execute("PRAGMA cipher_default_kdf_iter = 1;", null, null);
        connection.execute("PRAGMA cipher_default_page_size = 4096;", null, null);
    }
}

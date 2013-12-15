/**
 The MIT License (MIT)

 Copyright (c) 2013 Valentin Konovalov

 Permission is hereby granted, free of charge, to any person obtaining a copy
 of this software and associated documentation files (the "Software"), to deal
 in the Software without restriction, including without limitation the rights
 to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 copies of the Software, and to permit persons to whom the Software is
 furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in
 all copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 THE SOFTWARE.*/
package ru.valle.safetrade;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;

import java.util.ArrayList;

public final class DatabaseHelper extends SQLiteOpenHelper {

    private static final int DATABASE_VERSION = 1;

    public static final String TABLE_HISTORY = "history";

    public static final String COLUMN_TYPE = "type";
    public static final String COLUMN_NAME = "name";
    public static final String COLUMN_PASSWORD = "password";
    public static final String COLUMN_INTERMEDIATE_CODE = "intermediate_code";
    public static final String COLUMN_CONFIRMATION_CODE = "confirmation_code";
    public static final String COLUMN_ADDRESS = "address";
    public static final String COLUMN_FINAL_ADDRESS = "final_address";
    public static final String COLUMN_ENCRYPTED_PRIVATE_KEY = "encrypted_private_key";
    public static final String COLUMN_PRIVATE_KEY = "private_key";
    public static final String COLUMN_BALANCE = "balance";
    public static final String COLUMN_RECEIVED_ITEM = "item_received";
    private static DatabaseHelper databaseHelper;


    public synchronized static DatabaseHelper getInstance(Context context) {
        if (databaseHelper == null) {
            databaseHelper = new DatabaseHelper(context.getApplicationContext());
        }
        return databaseHelper;
    }

    private DatabaseHelper(Context context) {
        super(context, "main", null, DATABASE_VERSION);
    }

    public static ArrayList<TradeRecord> readTradeRecords(Cursor cursor) {
        ArrayList<TradeRecord> tradeRecords = new ArrayList<TradeRecord>();
        if (cursor != null) {
            try {
                int columnIndexId = cursor.getColumnIndex(BaseColumns._ID);
                int columnIndexType = cursor.getColumnIndex(COLUMN_TYPE);
                int columnIndexName = cursor.getColumnIndex(COLUMN_NAME);
                int columnIndexPassword = cursor.getColumnIndex(COLUMN_PASSWORD);
                int columnIndexIntermediateCode = cursor.getColumnIndex(COLUMN_INTERMEDIATE_CODE);
                int columnIndexConfirmationCode = cursor.getColumnIndex(COLUMN_CONFIRMATION_CODE);
                int columnIndexAddress = cursor.getColumnIndex(COLUMN_ADDRESS);
                int columnIndexFinalAddress = cursor.getColumnIndex(COLUMN_FINAL_ADDRESS);
                int columnIndexEncryptedPrivateKey = cursor.getColumnIndex(COLUMN_ENCRYPTED_PRIVATE_KEY);
                int columnIndexPrivateKey = cursor.getColumnIndex(COLUMN_PRIVATE_KEY);
                int columnIndexBalance = cursor.getColumnIndex(COLUMN_BALANCE);
                int columnIndexIsReceived = cursor.getColumnIndex(COLUMN_RECEIVED_ITEM);
                while (cursor.moveToNext()) {
                    tradeRecords.add(new TradeRecord(
                            cursor.getLong(columnIndexId),
                            cursor.getInt(columnIndexType),
                            cursor.getString(columnIndexName),
                            cursor.getString(columnIndexPassword),
                            cursor.getString(columnIndexIntermediateCode),
                            cursor.getString(columnIndexConfirmationCode),
                            cursor.getString(columnIndexAddress),
                            cursor.getString(columnIndexFinalAddress),
                            cursor.getString(columnIndexEncryptedPrivateKey),
                            cursor.getString(columnIndexPrivateKey),
                            cursor.getLong(columnIndexBalance),
                            cursor.getInt(columnIndexIsReceived) == 1
                    ));
                }
            } finally {
                cursor.close();
            }
        }
        return tradeRecords;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_HISTORY + " ("
                + BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + COLUMN_NAME + " TEXT,"
                + COLUMN_TYPE + " INTEGER,"
                + COLUMN_BALANCE + " INTEGER DEFAULT -1,"
                + COLUMN_RECEIVED_ITEM + " INTEGER DEFAULT 0,"
                + COLUMN_PASSWORD + " TEXT,"
                + COLUMN_INTERMEDIATE_CODE + " TEXT,"
                + COLUMN_CONFIRMATION_CODE + " TEXT,"
                + COLUMN_ADDRESS + " TEXT,"
                + COLUMN_FINAL_ADDRESS + " TEXT,"
                + COLUMN_ENCRYPTED_PRIVATE_KEY + " TEXT,"
                + COLUMN_PRIVATE_KEY + " TEXT"
                + ");");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

    }
}


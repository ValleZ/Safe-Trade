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

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class QRCodesProvider extends ContentProvider {
    public static final String FILENAME_SUFFIX = "_qrcode.png";
    public static final String INTERMEDIATE_FEATURE = "intermediate";
    public static final String CONFIRMATION_FEATURE = "confirmation";
    public static final String PRIVATE_KEY_FEATURE = "encrypted_private_key";
    private static final String TAG = "QRCodesProvider";

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        List<String> segments = uri.getPathSegments();
        if (segments != null && segments.size() >= 1) {
            String fileName = segments.get(0);
            if (fileName.endsWith(FILENAME_SUFFIX)) {
                File file = new File(getContext().getFilesDir(), Uri.encode(fileName));
                return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
            }
        }
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return "image/png";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        try {
            if (projection == null) {
                projection = new String[]{"_id", "_data", "mime_type", "_display_name", "_size"};
            }
            MatrixCursor cursor = new MatrixCursor(projection);
            Object[] row = new Object[projection.length];
            String fileName = uri.getPathSegments().get(0);
            if (fileName.endsWith(FILENAME_SUFFIX)) {
                for (int i = 0; i < projection.length; i++) {
                    String name = projection[i];
                    if ("_id".equals(name)) {
                        row[i] = "1";
                    } else if ("_data".equals(name)) {
                        FileInputStream fis = getContext().openFileInput(fileName);
                        byte[] data = readStream(fis);
                        row[i] = data;
                    } else if ("mime_type".equals(name)) {
                        row[i] = getType(uri);
                    } else if ("_display_name".equals(name)) {
                        if (fileName.contains(INTERMEDIATE_FEATURE)) {
                            row[i] = "intermediate_code.png";
                        } else if (fileName.contains(CONFIRMATION_FEATURE)) {
                            row[i] = "confirmation_code.png";
                        } else if (fileName.contains(PRIVATE_KEY_FEATURE)) {
                            row[i] = "encrypted_private_key.png";
                        }
                    } else if ("_size".equals(name)) {
                        File file = getContext().getFileStreamPath(fileName);
                        row[i] = file.length();
                    } else {
                        row[i] = null;
                    }
                }
                cursor.addRow(row);
            }
            return cursor;
        } catch (Throwable th) {
            Log.e(TAG, "qrcode share error", th);
        }

        return null;


    }

    public static byte[] readStream(InputStream fis) throws IOException {
        if (fis != null) {
            byte[] buf = new byte[4096];
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try {
                while (true) {
                    int readed = fis.read(buf);
                    if (readed == -1) {
                        baos.close();
                        byte[] data = baos.toByteArray();
                        baos = null;
                        return data;
                    } else if (readed > 0) {
                        baos.write(buf, 0, readed);
                    }
                }
            } finally {
                if (baos != null) {
                    baos.close();
                }
            }
        } else {
            return null;
        }
    }

    public static String getUri(String fileName) {
        return "content://ru.valle.safetrade.QRCodesProvider/" + Uri.encode(fileName);
    }

}

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

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.BaseColumns;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import com.d_project.qrcode.ErrorCorrectLevel;
import com.d_project.qrcode.QRCode;
import ru.valle.btc.BTCUtils;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;

public class BuyActivity extends FragmentActivity {
    private long rowId;
    private AsyncTask<Void, Void, TradeRecord> loadStateTask;
    private TextView intermediateCodeView;
    private TextView encryptedPrivateKeyView;
    private TextView addressView;
    private TextView confirmationCodeView;
    private TradeRecord tradeInfo;
    private TextView balanceView;
    private static final String TAG = "BuyActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.buy);
        intermediateCodeView = (TextView) findViewById(R.id.intermediate_code);
        confirmationCodeView = (TextView) findViewById(R.id.confirmation_code);
        addressView = (TextView) findViewById(R.id.address);
        encryptedPrivateKeyView = (TextView) findViewById(R.id.encrypted_private_key);
        balanceView = (TextView) findViewById(R.id.balance);
        findViewById(R.id.send_confirmation_code_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendConfirmationCode();
            }
        });
        findViewById(R.id.send_encrypted_private_key_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendEncryptedPrivateKey();
            }
        });

        rowId = getIntent().getLongExtra(BaseColumns._ID, -1);
    }

    private void sendEncryptedPrivateKey() {
        final String encryptedPrivateKey = tradeInfo.encryptedPrivateKey;
        new AsyncTask<Void, Void, Uri>() {
            @Override
            protected Uri doInBackground(Void... params) {
                try {
                    QRCode qr = new QRCode();
                    qr.setTypeNumber(5);
                    qr.setErrorCorrectLevel(ErrorCorrectLevel.M);
                    qr.addData(encryptedPrivateKey);
                    qr.make();
                    Bitmap bmp = qr.createImage(16, 24);
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, baos);
                    byte[] pngBytes = baos.toByteArray();
                    if (pngBytes != null && pngBytes.length > 0) {
                        String fileName = BTCUtils.toHex(BTCUtils.doubleSha256(encryptedPrivateKey.getBytes())) + QRCodesProvider.PRIVATE_KEY_FEATURE + QRCodesProvider.FILENAME_SUFFIX;
                        deleteFile(fileName);
                        FileOutputStream fos = openFileOutput(fileName, Context.MODE_PRIVATE);
                        fos.write(pngBytes);
                        fos.close();
                        return Uri.parse(QRCodesProvider.getUri(fileName));
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Unable to create QR code", e);
                }
                return null;
            }

            @Override
            protected void onPostExecute(Uri uri) {
                Intent intent = new Intent(Intent.ACTION_SEND);
                if (uri != null) {
                    intent.putExtra(Intent.EXTRA_STREAM, uri);
                }
                intent.putExtra(Intent.EXTRA_SUBJECT, "Encrypted private key");
                intent.putExtra(Intent.EXTRA_TEXT, encryptedPrivateKey);
                intent.setType("message/rfc822");
                try {
                    startActivity(intent);
                } catch (ActivityNotFoundException ex) {
                    Toast.makeText(BuyActivity.this, getString(R.string.no_email_clients), Toast.LENGTH_SHORT).show();
                }
            }
        }.execute();

    }

    @Override
    protected void onResume() {
        super.onResume();
        if (rowId != -1) {
            loadState(rowId);
        }
    }


    private void loadState(final long id) {
        cancelAllTasks();
        loadStateTask = new AsyncTask<Void, Void, TradeRecord>() {
            @Override
            protected TradeRecord doInBackground(Void... params) {
                SQLiteDatabase db = DatabaseHelper.getInstance(BuyActivity.this).getReadableDatabase();
                Cursor cursor = db.query(DatabaseHelper.TABLE_HISTORY, null, BaseColumns._ID + "=?", new String[]{String.valueOf(id)}, null, null, null);
                ArrayList<TradeRecord> tradeRecords = DatabaseHelper.readTradeRecords(cursor);
                return tradeRecords.isEmpty() ? null : tradeRecords.get(0);
            }

            @Override
            protected void onPostExecute(TradeRecord tradeRecord) {
                tradeInfo = tradeRecord;
                loadStateTask = null;
                rowId = tradeRecord.id;
                encryptedPrivateKeyView.setText(tradeRecord.encryptedPrivateKey);
                addressView.setText(tradeRecord.address);
                confirmationCodeView.setText(tradeRecord.confirmationCode);
                intermediateCodeView.setText(tradeRecord.intermediateCode);
                MainActivity.updateBalance(BuyActivity.this, id, tradeInfo.address, new Listener<AddressState>() {
                    @Override
                    public void onSuccess(AddressState result) {
                        if (result != null) {
                            balanceView.setText(BTCUtils.formatValue(result.getBalance()) + " BTC");
                        }
                    }
                });
            }
        };
        if (Build.VERSION.SDK_INT >= 11) {
            loadStateTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        } else {
            loadStateTask.execute();
        }
    }

    private void cancelAllTasks() {
        if (loadStateTask != null) {
            loadStateTask.cancel(true);
            loadStateTask = null;
        }
    }

    private void sendConfirmationCode() {
        if (tradeInfo != null) {
            final String confirmationCode = tradeInfo.confirmationCode;
            new AsyncTask<Void, Void, Uri>() {
                @Override
                protected Uri doInBackground(Void... params) {
                    try {
                        QRCode qr = new QRCode();
                        qr.setTypeNumber(5);
                        qr.setErrorCorrectLevel(ErrorCorrectLevel.M);
                        qr.addData(confirmationCode);
                        qr.make();
                        Bitmap bmp = qr.createImage(16, 24);
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        bmp.compress(Bitmap.CompressFormat.PNG, 100, baos);
                        byte[] pngBytes = baos.toByteArray();
                        if (pngBytes != null && pngBytes.length > 0) {
                            String fileName = BTCUtils.toHex(BTCUtils.doubleSha256(confirmationCode.getBytes())) + QRCodesProvider.CONFIRMATION_FEATURE + QRCodesProvider.FILENAME_SUFFIX;
                            deleteFile(fileName);
                            FileOutputStream fos = openFileOutput(fileName, Context.MODE_PRIVATE);
                            fos.write(pngBytes);
                            fos.close();
                            return Uri.parse(QRCodesProvider.getUri(fileName));
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Unable to create QR code", e);
                    }
                    return null;
                }

                @Override
                protected void onPostExecute(Uri uri) {
                    Intent intent = new Intent(Intent.ACTION_SEND);
                    if (uri != null) {
                        intent.putExtra(Intent.EXTRA_STREAM, uri);
                    }
                    intent.putExtra(Intent.EXTRA_SUBJECT, "Confirmation code");
                    intent.putExtra(Intent.EXTRA_TEXT, confirmationCode);
                    intent.setType("message/rfc822");
                    try {
                        startActivity(intent);
                    } catch (ActivityNotFoundException ex) {
                        Toast.makeText(BuyActivity.this, getString(R.string.no_email_clients), Toast.LENGTH_SHORT).show();
                    }
                }
            }.execute();
        }
    }
}

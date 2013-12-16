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

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
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
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import com.d_project.qrcode.ErrorCorrectLevel;
import com.d_project.qrcode.QRCode;
import ru.valle.btc.BTCUtils;
import ru.valle.btc.KeyPair;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;

public final class SellActivity extends FragmentActivity {
    private static final String TAG = "SellActivity";
    private static final int REQUEST_SCAN_CONFIRMATION_CODE = 2;
    private static final int REQUEST_SCAN_PRIVATE_KEY = 3;
    private static final int REQUEST_SCAN_FINAL_ADDRESS = 4;
    private TextView passwordView;
    private TextView intermediateCodeView;
    private long rowId;
    private AsyncTask<Void, Void, TradeRecord> loadStateTask;
    private TradeRecord tradeInfo;
    private TextView balanceView;
    private AsyncTask<Void, String, String> confirmationCodeDecodingTask;
    private TextView addressView;
    private EditText confirmationCodeView;
    private EditText privateKeyView;
    private AsyncTask<Void, String, KeyPair> privateKeyDecodingTask;
    private TextView takeButton;
    private TextView finalAddressView;
    private final Listener<AddressState> onAddressStateReceivedListener = new Listener<AddressState>() {
        @Override
        public void onSuccess(AddressState result) {
            if (result != null && balanceView != null && takeButton != null && !isDestroyed()) {
                long balance = result.getBalance();
                String balanceStr = BTCUtils.formatValue(balance);
                balanceView.setText(balanceStr + " BTC");
                if (balance == 0) {
                    takeButton.setText(getString(R.string.take_no_btc_button));
                } else {
                    takeButton.setText(getString(R.string.take_button, balanceStr));
                }
                takeButton.setEnabled(balance > 0 && tradeInfo != null && !TextUtils.isEmpty(tradeInfo.privateKey));
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.sell);
        passwordView = (TextView) findViewById(R.id.password);
        intermediateCodeView = (TextView) findViewById(R.id.intermediate_code);
        balanceView = (TextView) findViewById(R.id.balance);
        addressView = (TextView) findViewById(R.id.address);
        confirmationCodeView = (EditText) findViewById(R.id.confirmation_code);
        privateKeyView = (EditText) findViewById(R.id.private_key);
        finalAddressView = (TextView) findViewById(R.id.final_address);
        takeButton = (TextView) findViewById(R.id.take_button);

        findViewById(R.id.send_intermediate_code_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendIntermediateCode();
            }
        });
        findViewById(R.id.scan_confirmation_code_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(SellActivity.this, ScanActivity.class).putExtra(ScanActivity.EXTRA_TITLE, getString(R.string.scan_confirmation_code_title));
                startActivityForResult(intent, REQUEST_SCAN_CONFIRMATION_CODE);
            }
        });

        findViewById(R.id.scan_encrypted_private_key_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(SellActivity.this, ScanActivity.class).putExtra(ScanActivity.EXTRA_TITLE, getString(R.string.scan_encrypted_private_key_title));
                startActivityForResult(intent, REQUEST_SCAN_PRIVATE_KEY);
            }
        });
        findViewById(R.id.scan_final_address_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(SellActivity.this, ScanActivity.class).putExtra(ScanActivity.EXTRA_TITLE, getString(R.string.scan_final_address_title));
                startActivityForResult(intent, REQUEST_SCAN_FINAL_ADDRESS);
            }
        });

        rowId = getIntent().getLongExtra(BaseColumns._ID, -1);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (rowId != -1) {
            loadState(rowId);
        }
    }

    private static final String SCHEME_BITCOIN = "bitcoin:";

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            String scannedResult = data.getStringExtra("data");
            if (scannedResult != null) {
                switch (requestCode) {
                    case REQUEST_SCAN_CONFIRMATION_CODE: {
                        if (scannedResult.startsWith("cfrm")) {
                            checkConfirmationCodeToDecodeAddress(scannedResult);
                        } else if (scannedResult.startsWith("passphrase")) {
                            new AlertDialog.Builder(SellActivity.this).setMessage(getString(R.string.not_confirmation_but_intermediate_code)).setPositiveButton(android.R.string.ok, null).show();
                        } else {
                            new AlertDialog.Builder(SellActivity.this).setMessage(getString(R.string.not_confirmation_code)).setPositiveButton(android.R.string.ok, null).show();
                        }
                    }
                    break;
                    case REQUEST_SCAN_PRIVATE_KEY: {
                        if (scannedResult.startsWith("6P")) {
                            savePrivateKey(scannedResult);
                        } else {
                            new AlertDialog.Builder(SellActivity.this).setMessage(getString(R.string.not_encrypted_private_key)).setPositiveButton(android.R.string.ok, null).show();
                        }
                    }
                    break;
                    case REQUEST_SCAN_FINAL_ADDRESS: {
                        final String address;
                        if (scannedResult.startsWith(SCHEME_BITCOIN)) {
                            scannedResult = scannedResult.substring(SCHEME_BITCOIN.length());
                            int queryStartIndex = scannedResult.indexOf('?');
                            address = queryStartIndex == -1 ? scannedResult : scannedResult.substring(0, queryStartIndex);
                        } else if (scannedResult.startsWith("1")) {
                            address = scannedResult;
                        } else {
                            new AlertDialog.Builder(SellActivity.this).setMessage(getString(R.string.not_address)).setPositiveButton(android.R.string.ok, null).show();
                            address = null;
                        }
                        if (!TextUtils.isEmpty(address)) {
                            finalAddressView.setText(address);
                            final long id = rowId;
                            new AsyncTask<Void, Void, Void>() {
                                @Override
                                protected Void doInBackground(Void... params) {
                                    SQLiteDatabase db = DatabaseHelper.getInstance(SellActivity.this).getWritableDatabase();
                                    ContentValues cv = new ContentValues();
                                    cv.put(DatabaseHelper.COLUMN_FINAL_ADDRESS, address);
                                    db.update(DatabaseHelper.TABLE_HISTORY, cv, BaseColumns._ID + "=?", new String[]{String.valueOf(id)});
                                    return null;
                                }
                            }.execute();
                        }
                    }
                    break;
                }

            }
        }
    }

    private void savePrivateKey(final String encryptedPrivateKey) {
        final String password = tradeInfo.password;
        final long id = rowId;
        privateKeyDecodingTask = new AsyncTask<Void, String, KeyPair>() {
            public ProgressDialog progressDialog;

            @Override
            protected void onPreExecute() {
                privateKeyView.setText(encryptedPrivateKey);
                progressDialog = ProgressDialog.show(SellActivity.this, null, getString(R.string.decrypting_private_key), true);
                progressDialog.setCancelable(true);
                progressDialog.setCanceledOnTouchOutside(false);
                progressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        privateKeyDecodingTask.cancel(true);
                        if (rowId != -1) {
                            loadState(rowId);
                        }
                    }
                });
            }

            @Override
            protected void onProgressUpdate(String... values) {
                try {
                    progressDialog.setMessage(values[0]);
                } catch (Exception ignored) {
                }
            }

            @Override
            protected KeyPair doInBackground(Void... params) {
                try {
                    KeyPair keyPair = BTCUtils.bip38Decrypt(encryptedPrivateKey, password);
                    if (keyPair != null) {
                        SQLiteDatabase db = DatabaseHelper.getInstance(SellActivity.this).getWritableDatabase();
                        ContentValues cv = new ContentValues();
                        cv.put(DatabaseHelper.COLUMN_ADDRESS, keyPair.address);
                        cv.put(DatabaseHelper.COLUMN_ENCRYPTED_PRIVATE_KEY, encryptedPrivateKey);
                        cv.put(DatabaseHelper.COLUMN_PRIVATE_KEY,
                                BTCUtils.encodeWifKey(keyPair.privateKey.isPublicKeyCompressed, BTCUtils.getPrivateKeyBytes(keyPair.privateKey.privateKeyDecoded)));
                        db.update(DatabaseHelper.TABLE_HISTORY, cv, BaseColumns._ID + "=?", new String[]{String.valueOf(id)});
                    }
                    return keyPair;
                } catch (Throwable e) {
                    Log.e(TAG, "pk decrypt", e);
                    return null;
                }
            }

            @Override
            protected void onPostExecute(final KeyPair keyPair) {
                try {
                    progressDialog.dismiss();
                } catch (Exception ignored) {
                }
                privateKeyDecodingTask = null;
                if (keyPair != null) {
                    new AlertDialog.Builder(SellActivity.this).setMessage("private key for address " + keyPair.address + " has been decrypted!").setPositiveButton(android.R.string.ok, null).show();
                    takeButton.setEnabled(false);
                } else {
                    new AlertDialog.Builder(SellActivity.this).setMessage(getString(R.string.unable_to_decrypt_private_key)).setPositiveButton(android.R.string.ok, null).show();
                }
            }
        }.execute();


    }

    private void checkConfirmationCodeToDecodeAddress(final String confirmationCode) {
        final String password = tradeInfo.password;
        final long id = rowId;
        confirmationCodeDecodingTask = new AsyncTask<Void, String, String>() {
            public ProgressDialog progressDialog;

            @Override
            protected void onPreExecute() {
                confirmationCodeView.setText(confirmationCode);
                progressDialog = ProgressDialog.show(SellActivity.this, null, getString(R.string.decoding_confirmation_code_using_password), true);
                progressDialog.setCancelable(true);
                progressDialog.setCanceledOnTouchOutside(false);
                progressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        confirmationCodeDecodingTask.cancel(true);
                        confirmationCodeView.setText("");
                        if (rowId != -1) {
                            loadState(rowId);
                        }
                    }
                });
            }

            @Override
            protected void onProgressUpdate(String... values) {
                try {
                    progressDialog.setMessage(values[0]);
                } catch (Exception ignored) {
                }
            }

            @Override
            protected String doInBackground(Void... params) {
                try {
                    String address = BTCUtils.bip38DecryptConfirmation(confirmationCode, password);
                    if (address != null) {
                        SQLiteDatabase db = DatabaseHelper.getInstance(SellActivity.this).getWritableDatabase();
                        ContentValues cv = new ContentValues();
                        cv.put(DatabaseHelper.COLUMN_ADDRESS, address);
                        cv.put(DatabaseHelper.COLUMN_CONFIRMATION_CODE, confirmationCode);
                        db.update(DatabaseHelper.TABLE_HISTORY, cv, BaseColumns._ID + "=?", new String[]{String.valueOf(id)});
                    }
                    return address;
                } catch (Throwable e) {
                    return null;
                }
            }

            @Override
            protected void onPostExecute(final String address) {
                try {
                    progressDialog.dismiss();
                } catch (Exception ignored) {
                }
                confirmationCodeDecodingTask = null;
                if (!TextUtils.isEmpty(address)) {
                    confirmationCodeView.setText(confirmationCode);
                    addressView.setText(address);
                    MainActivity.updateBalance(SellActivity.this, id, address, onAddressStateReceivedListener);
                } else {
                    loadState(rowId);
                    new AlertDialog.Builder(SellActivity.this).setMessage(getString(R.string.confirmation_code_doesnt_match)).setPositiveButton(android.R.string.ok, null).show();
                }
            }
        }.execute();
    }

    private void sendIntermediateCode() {
        if (tradeInfo != null) {
            final String intermediateCode = tradeInfo.intermediateCode;
            new AsyncTask<Void, Void, Uri>() {
                @Override
                protected Uri doInBackground(Void... params) {
                    try {
                        QRCode qr = new QRCode();
                        qr.setTypeNumber(5);
                        qr.setErrorCorrectLevel(ErrorCorrectLevel.M);
                        qr.addData(intermediateCode);
                        qr.make();
                        Bitmap bmp = qr.createImage(640);
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        bmp.compress(Bitmap.CompressFormat.PNG, 100, baos);
                        byte[] pngBytes = baos.toByteArray();
                        if (pngBytes != null && pngBytes.length > 0) {
                            String fileName = BTCUtils.toHex(BTCUtils.doubleSha256(intermediateCode.getBytes())) + QRCodesProvider.INTERMEDIATE_FEATURE + QRCodesProvider.FILENAME_SUFFIX;
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
                    intent.putExtra(Intent.EXTRA_SUBJECT, "Intermediate code");
                    intent.putExtra(Intent.EXTRA_TEXT, tradeInfo.intermediateCode);
                    intent.setType("message/rfc822");
                    try {
                        startActivity(intent);
                    } catch (ActivityNotFoundException ex) {
                        Toast.makeText(SellActivity.this, getString(R.string.no_email_clients), Toast.LENGTH_SHORT).show();
                    }
                }
            }.execute();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancelAllTasks();
    }

    private void cancelAllTasks() {
        if (loadStateTask != null) {
            loadStateTask.cancel(true);
            loadStateTask = null;
        }
    }

    private void loadState(final long id) {
        cancelAllTasks();
        loadStateTask = new AsyncTask<Void, Void, TradeRecord>() {
            @Override
            protected TradeRecord doInBackground(Void... params) {
                SQLiteDatabase db = DatabaseHelper.getInstance(SellActivity.this).getReadableDatabase();
                Cursor cursor = db.query(DatabaseHelper.TABLE_HISTORY, null, BaseColumns._ID + "=?", new String[]{String.valueOf(id)}, null, null, null);
                ArrayList<TradeRecord> tradeRecords = DatabaseHelper.readTradeRecords(cursor);
                return tradeRecords.isEmpty() ? null : tradeRecords.get(0);
            }

            @Override
            protected void onPostExecute(final TradeRecord tradeRecord) {
                tradeInfo = tradeRecord;
                loadStateTask = null;
                rowId = tradeRecord.id;
                passwordView.setText(tradeRecord.password);
                intermediateCodeView.setText(tradeRecord.intermediateCode);
                if (confirmationCodeDecodingTask == null) {
                    confirmationCodeView.setText(tradeRecord.confirmationCode);
                }
                addressView.setText(tradeRecord.address);
                finalAddressView.setText(tradeRecord.destinationAddress);
                privateKeyView.setText(tradeRecord.encryptedPrivateKey);
                MainActivity.updateBalance(SellActivity.this, id, tradeInfo.address, onAddressStateReceivedListener);
            }
        };
        if (Build.VERSION.SDK_INT >= 11) {
            loadStateTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        } else {
            loadStateTask.execute();
        }
    }

}

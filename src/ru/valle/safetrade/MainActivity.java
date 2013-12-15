package ru.valle.safetrade;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.BaseColumns;
import android.support.v4.app.FragmentActivity;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ListView;
import org.json.JSONArray;
import org.json.JSONObject;
import ru.valle.btc.BTCUtils;
import ru.valle.btc.BitcoinException;
import ru.valle.btc.KeyPair;
import ru.valle.btc.Transaction;
import ru.valle.btc.UnspentOutputInfo;

import java.io.FileNotFoundException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

public final class MainActivity extends FragmentActivity {
    private static final String TAG = "MainActivity";
    private static final int REQUEST_SCAN_INTERMEDIATE_CODE = 1;
    private AsyncTask<Void, String, Long> passwordAndCodeGenerationTask, intermediateCodeDecodingTask;
    private HistoryAdapter adapter;
    private AsyncTask<Void, Void, Collection<TradeRecord>> historyLoader;
    private AlertDialog enterIntermediateCodeDialog;

    public static void updateBalance(final Context context, final long id, final String address, final Listener<AddressState> listener) {
        if (!TextUtils.isEmpty(address)) {
            new AsyncTask<Void, Void, AddressState>() {
                @Override
                protected AddressState doInBackground(Void... params) {
                    try {
                        URL url = new URL("https://blockchain.info/unspent?active=" + address);
                        HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                        ArrayList<UnspentOutputInfo> unspentOutputs = new ArrayList<UnspentOutputInfo>();
                        try {
                            JSONObject jsonObject = new JSONObject(new String(QRCodesProvider.readStream(urlConnection.getInputStream()), "UTF-8"));
                            JSONArray unspentOutputsArray = jsonObject.getJSONArray("unspent_outputs");
                            for (int i = 0; i < unspentOutputsArray.length(); i++) {
                                JSONObject unspentOutput = unspentOutputsArray.getJSONObject(i);
                                byte[] txHash = BTCUtils.reverse(BTCUtils.fromHex(unspentOutput.getString("tx_hash")));
                                Transaction.Script script = new Transaction.Script(BTCUtils.fromHex(unspentOutput.getString("script")));
                                long value = unspentOutput.getLong("value");
                                int outputIndex = unspentOutput.getInt("tx_output_n");
                                unspentOutputs.add(new UnspentOutputInfo(txHash, script, value, outputIndex));
                            }
                        } catch (FileNotFoundException e) {
                            Log.d(TAG, "no unspent outputs: " + e.getMessage());
                        } finally {
                            urlConnection.disconnect();
                        }
                        AddressState addressState = new AddressState(unspentOutputs);
                        long balance = addressState.getBalance();
                        SQLiteDatabase db = DatabaseHelper.getInstance(context).getWritableDatabase();
                        ContentValues cv = new ContentValues();
                        cv.put(DatabaseHelper.COLUMN_BALANCE, balance);
                        db.update(DatabaseHelper.TABLE_HISTORY, cv, BaseColumns._ID + "=?", new String[]{String.valueOf(id)});
                        return addressState;
                    } catch (Exception e) {
                        Log.w(TAG, "obtain balance error", e);
                        return null;
                    }
                }

                @Override
                protected void onPostExecute(AddressState addressState) {
                    if (listener != null) {
                        listener.onSuccess(addressState);
                    }
                }
            }.execute();
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        ListView listView = (ListView) findViewById(R.id.history_listview);
        listView.setEmptyView(findViewById(R.id.empty_view));
        adapter = new HistoryAdapter();
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                TradeRecord tradeRecord = adapter.getItem(position);
                Intent intent = new Intent(MainActivity.this, tradeRecord.type == TradeRecord.TYPE_SELL ? SellActivity.class : BuyActivity.class);
                intent.putExtra(BaseColumns._ID, tradeRecord.id);
                startActivity(intent);
            }
        });


        findViewById(R.id.seller_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //(1) ask password,send intermediate code
                //(2) ask for confirmation code, show balance
                //(3) ask for encrypted private key and address to transfer funds

                onSellClick();
            }
        });
        findViewById(R.id.buyer_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //(1) ask for intermediate code, generate _address_, confirmation code and encrypted private key
                //show bitcoin link to deposit funds/show button to send all this
                //(2) when funds will be deposited, generate email with confirmation code
                //(3) when you as a buyer receive item, send encrypted private key.
                onBuyClick();
            }
        });
        reloadHistory();
    }

    private void reloadHistory() {
        historyLoader = new AsyncTask<Void, Void, Collection<TradeRecord>>() {
            @Override
            protected Collection<TradeRecord> doInBackground(Void... params) {
                SQLiteDatabase database = DatabaseHelper.getInstance(MainActivity.this).getReadableDatabase();
                Cursor cursor = database.query(DatabaseHelper.TABLE_HISTORY, null, null, null, null, null, null);
                return DatabaseHelper.readTradeRecords(cursor);
            }

            @Override
            protected void onPostExecute(Collection<TradeRecord> tradeRecords) {
                historyLoader = null;
                adapter.setItems(tradeRecords);
            }
        }.execute();
    }

    private void onBuyClick() {
        cancelAllTasks();
        //ask for intermediate code// or scan it, then generate address, confirmation code and encrypted key. open buy activity
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        final View view = LayoutInflater.from(this).inflate(R.layout.dialog_get_intermediate_code, null);
        view.findViewById(R.id.scan_intermediate_code_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivityForResult(new Intent(MainActivity.this, ScanActivity.class), REQUEST_SCAN_INTERMEDIATE_CODE);
            }
        });
        builder.setTitle(getString(R.string.dialog_get_intermediate_code_title));
        builder.setView(view);
        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                final EditText intermediateCodeField = (EditText) view.findViewById(R.id.intermediate_code_edit);
                final String intermediateCode = intermediateCodeField.getEditableText().toString().trim();
                ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(intermediateCodeField.getWindowToken(), 0);
                checkIntermediateCodeToCreateBuyRecord(intermediateCode);
                dialog.dismiss();
            }
        });

        builder.setNegativeButton(android.R.string.cancel, null);
        enterIntermediateCodeDialog = builder.create();
        enterIntermediateCodeDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                enterIntermediateCodeDialog = null;
            }
        });
        final EditText intermediateCodeField = (EditText) view.findViewById(R.id.intermediate_code_edit);
        if (intermediateCodeField != null) {
            enterIntermediateCodeDialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            intermediateCodeField.addTextChangedListener(new TextWatcher() {


                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    updateOkButtonVisibility(s, enterIntermediateCodeDialog);
                }

                @Override
                public void afterTextChanged(Editable s) {

                }
            });
            enterIntermediateCodeDialog.setOnShowListener(new DialogInterface.OnShowListener() {

                @Override
                public void onShow(DialogInterface dialog) {
                    updateOkButtonVisibility(intermediateCodeField.getText().toString(), (AlertDialog) dialog);
                }
            });
        }
        enterIntermediateCodeDialog.show();
    }

    private void updateOkButtonVisibility(CharSequence s, AlertDialog dialog) {
        View okButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        okButton.setEnabled(s != null && s.toString().startsWith("passphrase"));
    }

    private void checkIntermediateCodeToCreateBuyRecord(final String intermediateCode) {
        intermediateCodeDecodingTask = new AsyncTask<Void, String, Long>() {
            public ProgressDialog progressDialog;

            @Override
            protected void onPreExecute() {
                progressDialog = ProgressDialog.show(MainActivity.this, null, getString(R.string.generating_address_and_encrypted_private_key), true);
                progressDialog.setCancelable(true);
                progressDialog.setCanceledOnTouchOutside(false);
                progressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        intermediateCodeDecodingTask.cancel(true);
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
            protected Long doInBackground(Void... params) {
                try {
                    SQLiteDatabase database = DatabaseHelper.getInstance(MainActivity.this).getWritableDatabase();
                    Cursor cursor = database.query(DatabaseHelper.TABLE_HISTORY, new String[]{BaseColumns._ID},
                            DatabaseHelper.COLUMN_INTERMEDIATE_CODE + "=? AND " + DatabaseHelper.COLUMN_TYPE + "=?",
                            new String[]{intermediateCode, String.valueOf(TradeRecord.TYPE_BUY)}, null, null, null, "1");
                    if (cursor != null) {
                        try {
                            if (cursor.moveToFirst()) {
                                return cursor.getLong(0);
                            }
                        } finally {
                            cursor.close();
                        }
                    }
                    KeyPair keyPair = BTCUtils.bip38GenerateKeyPair(intermediateCode, true);
                    ContentValues cv = new ContentValues();
                    cv.put(DatabaseHelper.COLUMN_TYPE, TradeRecord.TYPE_BUY);
                    cv.put(DatabaseHelper.COLUMN_INTERMEDIATE_CODE, intermediateCode);
                    cv.put(DatabaseHelper.COLUMN_ENCRYPTED_PRIVATE_KEY, keyPair.privateKey.privateKeyEncoded);
                    cv.put(DatabaseHelper.COLUMN_ADDRESS, keyPair.address);
                    cv.put(DatabaseHelper.COLUMN_BALANCE, 0);
                    cv.put(DatabaseHelper.COLUMN_CONFIRMATION_CODE, ((BTCUtils.Bip38PrivateKeyInfo) keyPair.privateKey).confirmationCode);
                    return database.insert(DatabaseHelper.TABLE_HISTORY, BaseColumns._ID, cv);
                } catch (InterruptedException e) {
                    return null;
                } catch (BitcoinException e) {
                    return null;
                }
            }

            @Override
            protected void onPostExecute(final Long rowId) {
                try {
                    progressDialog.dismiss();
                } catch (Exception ignored) {
                }
                passwordAndCodeGenerationTask = null;

                Log.d(TAG, "created buy record " + rowId);
                if (rowId != null && rowId != -1) {
                    Intent intent = new Intent(MainActivity.this, BuyActivity.class);
                    intent.putExtra(BaseColumns._ID, rowId);
                    startActivity(intent);
                }
                reloadHistory();
            }
        }.execute();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            String scannedResult = data.getStringExtra("data");

            if (requestCode == REQUEST_SCAN_INTERMEDIATE_CODE && scannedResult != null && scannedResult.startsWith("passphrase")) {
                if (enterIntermediateCodeDialog != null && enterIntermediateCodeDialog.isShowing()) {
                    enterIntermediateCodeDialog.dismiss();
                }
                checkIntermediateCodeToCreateBuyRecord(scannedResult);
            }
        }
    }

    private void onSellClick() {
        cancelAllTasks();
        passwordAndCodeGenerationTask = new AsyncTask<Void, String, Long>() {

            private final char[] CHARSET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz".toCharArray();
            private final int PASSWORD_LENGTH = 10;//no need for super-strong passwords
            public ProgressDialog progressDialog;

            @Override
            protected void onPreExecute() {
                progressDialog = ProgressDialog.show(MainActivity.this, null, getString(R.string.generating_password), true);
                progressDialog.setCanceledOnTouchOutside(false);
                progressDialog.setCancelable(true);
                progressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        passwordAndCodeGenerationTask.cancel(true);
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
            protected Long doInBackground(Void... params) {
                BTCUtils.SECURE_RANDOM.addSeedMaterial(SystemClock.elapsedRealtime());
                StringBuilder sb = new StringBuilder(PASSWORD_LENGTH);
                for (int i = 0; i < PASSWORD_LENGTH; i++) {
                    sb.append(CHARSET[BTCUtils.SECURE_RANDOM.nextInt(CHARSET.length - 1)]);
                }
                final String password = sb.toString();
                publishProgress(getString(R.string.generating_intermediate_code));
                try {
                    String intermediateCode = BTCUtils.bip38GetIntermediateCode(password);
                    ContentValues cv = new ContentValues();
                    cv.put(DatabaseHelper.COLUMN_TYPE, TradeRecord.TYPE_SELL);
                    cv.put(DatabaseHelper.COLUMN_PASSWORD, password);
                    cv.put(DatabaseHelper.COLUMN_INTERMEDIATE_CODE, intermediateCode);
                    SQLiteDatabase database = DatabaseHelper.getInstance(MainActivity.this).getWritableDatabase();
                    return database.insert(DatabaseHelper.TABLE_HISTORY, BaseColumns._ID, cv);
                } catch (InterruptedException e) {
                    return null;
                }
            }

            @Override
            protected void onPostExecute(final Long rowId) {
                try {
                    progressDialog.dismiss();
                } catch (Exception ignored) {
                }
                passwordAndCodeGenerationTask = null;

                Log.d(TAG, "created sell record " + rowId);
                if (rowId != null && rowId != -1) {
                    Intent intent = new Intent(MainActivity.this, SellActivity.class);
                    intent.putExtra(BaseColumns._ID, rowId);
                    startActivity(intent);
                }
                reloadHistory();
            }
        }.execute();

    }

    private void cancelAllTasks() {
        if (passwordAndCodeGenerationTask != null) {
            passwordAndCodeGenerationTask.cancel(true);
            passwordAndCodeGenerationTask = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancelAllTasks();
        if (historyLoader != null) {
            historyLoader.cancel(true);
            historyLoader = null;
        }
    }
}

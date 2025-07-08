package com.github.skjolber.nfc.external.hceclient;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.nfc.cardemulation.CardEmulation;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import com.github.skjolber.nfc.util.Broadcast;

import java.util.HashMap;
import java.util.Map;

/**
 * Activity for this app to echo the HCE service using internal NFC.
 */


public class HceActivity extends DialogActivity {

    private static String TAG = HceActivity.class.getName();

    private static final Map<Class, Integer> services;

    static {
        services = new HashMap<>();
        services.put(EchoHostApduService.class, R.xml.echoservice);
        services.put(HelloHostApduService.class, R.xml.helloservice);
    }

    private boolean receiving = false;
    private CardEmulation cardEmulation;

    private final BroadcastReceiver hostCardEmulationBroadcastReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, final Intent intent) {

            String action = intent.getAction();

            if (Broadcast.HOST_CARD_EMULATION_ACTION_PROCESS_COMMAND_ADPU.equals(action)) {
                final byte[] command = intent.getByteArrayExtra(Broadcast.HOST_CARD_EMULATION_EXTRA_COMMAND);
                final byte[] response = intent.getByteArrayExtra(Broadcast.HOST_CARD_EMULATION_EXTRA_RESPONSE);
                runOnUiThread(new Runnable() {
                    public void run() {
                        TextView textView = (TextView) findViewById(R.id.text);

                        textView.setText(textView.getText() + " <- " + EchoHostApduService.toHexString(command) + "\n -> " + EchoHostApduService.toHexString(response) + "\n");
                    }
                });
            } else if (Broadcast.HOST_CARD_EMULATION_ACTION_DEACTIVATED.equals(action)) {

                runOnUiThread(new Runnable() {
                    public void run() {
                        TextView textView = (TextView) findViewById(R.id.text);

                        textView.setText(textView.getText() + "Deactivated (tag lost or IsoDep.close() called)" + "\n");
                    }
                });
            } else if ("android.intent.action.MAIN".equals(action)) {
                // ignore
            } else throw new IllegalArgumentException("Unexpected action " + action);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

        setContentView(R.layout.activity_main);

        cardEmulation = CardEmulation.getInstance(NfcAdapter.getDefaultAdapter(this));

        for (Map.Entry<Class, Integer> entry : services.entrySet()) {
            String aid = Utils.parseAid(this, entry.getValue());

            if (!cardEmulation.isDefaultServiceForAid(new ComponentName(this, entry.getKey()), aid)) {
                Log.d(TAG, "This application is NOT the preferred service for aid " + aid);
            } else {
                Log.d(TAG, "This application is the preferred service for aid " + aid);
            }
        }

        enableBroadcast();

        showHelpfulDialog();

        Intent intent = getIntent();

        hostCardEmulationBroadcastReceiver.onReceive(this, getIntent());
    }

    private void showHelpfulDialog() {
        final AlertDialog.Builder alert = new AlertDialog.Builder(this);
        alert.setTitle(R.string.dialogTitle);
        alert.setMessage(R.string.dialogMessage);

        alert.setNegativeButton(R.string.dialogClose, new DialogInterface.OnClickListener() {

            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();

                return;
            }
        });

        final AlertDialog dialog = alert.create();

        dialog.setCanceledOnTouchOutside(true);
        dialog.setCancelable(true); // back button

        show(dialog);
    }

    @Override
    protected void onResume() {
        super.onResume();

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        // note: this will only work for a single HCE service
        for (Map.Entry<Class, Integer> entry : services.entrySet()) {
            boolean preferred = prefs.getBoolean(PreferencesActivity.PREFERENCE_HOST_CARD_EMULATION_PREFERENCE + "_" + entry.getKey().getSimpleName(), true);

            if (preferred) {
                if (cardEmulation.setPreferredService(this, new ComponentName(this, entry.getKey()))) {
                    Log.d(TAG, "Set " + entry.getKey().getName() + " to be the preferred HCE service for this activity");
                } else {
                    Log.w(TAG, "Unable to set " + entry.getKey().getName() + " to be the preferred HCE service for this activity");
                }
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (cardEmulation.unsetPreferredService(this)) {
            Log.d(TAG, "Unset preferred service for " + getClass().getName());
        } else {
            Log.d(TAG, "Unable to unset preferred service for " + getClass().getName());
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }


    public void enableBroadcast() {
        if (!receiving) {
            IntentFilter serviceFilter = new IntentFilter();
            serviceFilter.addAction(Broadcast.HOST_CARD_EMULATION_ACTION_PROCESS_COMMAND_ADPU);
            serviceFilter.addAction(Broadcast.HOST_CARD_EMULATION_ACTION_DEACTIVATED);
            registerReceiver(hostCardEmulationBroadcastReceiver, serviceFilter);

            receiving = true;
        }
    }

    public void disableBroadcast() {
        if (receiving) {
            unregisterReceiver(hostCardEmulationBroadcastReceiver);

            receiving = false;
        }
    }

    public void showHelp(View view) {
        String url = "https://developer.android.com/guide/topics/connectivity/nfc/hce.html#ScreenOffBehavior";
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setData(Uri.parse(url));
        startActivity(i);
    }

    public void clear(View view) {
        TextView textView = (TextView) findViewById(R.id.text);
        textView.setText("");
    }

    @Override
    protected void onDestroy() {
        disableBroadcast();

        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.menu_server) {
            Intent intent = new Intent(this, HceInvokerActivity.class);
            startActivity(intent);
            return true;
        } else if (itemId == R.id.action_preferences) {
            Intent intent = new Intent(this, PreferencesActivity.class);
            startActivity(intent);
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

}
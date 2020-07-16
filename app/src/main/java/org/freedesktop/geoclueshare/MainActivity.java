package org.freedesktop.geoclueshare;

import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.Settings;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.SwitchCompat;
import android.util.Log;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;

import java.util.Locale;

/*
 * Copyright (C) 2015 Ankit (Verma)
 *
 * GeoclueShare is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 2 of the License, or (at your option)
 * any later version.
 *
 * GeoclueShare is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along
 * with GeoclueShare; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 * Author: Ankit (Verma) <ankitstarski@gmail.com>
 */

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    /**
     * The code used to depict Location Settings activity when prompting for High accuracy.
     */
    private static final int LOCATION_MODE_CHANGE = 0;

    /**
     * The message code to prompt for High accuracy location settings. This constant is supposed to
     * be used by {@link #handler}.
     */
    private static final int MESSAGE_PROMPT_LOCATION = 0;

    /**
     * The message code used by {@link #handler} in order to update the number of clients in the
     * UI.
     */
    private static final int MESSAGE_CONNECTED_DEVICES = 1;

    /**
     * The key string used by Handler data bundle for updating the number of clients to the
     * MainActivity's UI.
     */
    private static final String KEY_CONNECTED_DEVICES = "n_clients";


    private SwitchCompat toggleService;
    private TextView locationMode;
    private TextView connectedDevices;
    private static Handler handler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //noinspection ConstantConditions
        getSupportActionBar().setTitle(R.string.main_activity_label);
        final Intent locationServiceIntent = new Intent(this, LocationService.class);

        toggleService = (SwitchCompat) findViewById(R.id.service_toggle);
        locationMode = (TextView) findViewById(R.id.location_mode);
        connectedDevices = (TextView) findViewById(R.id.connected_devices);

        handler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message message) {
                switch (message.what) {
                    case MESSAGE_PROMPT_LOCATION:
                        showLocationPrompt();
                        Log.d(TAG, "Location Prompt");
                        break;
                    case MESSAGE_CONNECTED_DEVICES:
                        Bundle bundle = message.getData();
                        int n_clients = bundle.getInt(KEY_CONNECTED_DEVICES);
                        connectedDevices.setText(String.format(Locale.US, "%d", n_clients));
                        Log.d(TAG, "Connected devices changed");
                        break;
                }
            }
        };

        if (isLocationAccuracyHigh()) {
            locationMode.setText(R.string.location_high_accuracy);
        } else {
            locationMode.setText(R.string.location_low_accuracy);
            toggleService.setClickable(false);
            showLocationPrompt();
        }

        if (isServiceRunning(LocationService.class)) {
            connectedDevices.setText(String.format(Locale.US, "%d", NetworkListener.numberOfClients));
        }

        toggleService.setChecked(isServiceRunning(LocationService.class));

        toggleService.setOnCheckedChangeListener(new Switch.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    startService(locationServiceIntent);
                } else {
                    stopService(locationServiceIntent);
                }
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode,
                                    Intent data) {
        if (requestCode == LOCATION_MODE_CHANGE) {
            if (isLocationAccuracyHigh()) {
                locationMode.setText(R.string.location_high_accuracy);
                toggleService.setClickable(true);
            } else {
                locationMode.setText(R.string.location_low_accuracy);
                showLocationPrompt();
            }
        }
    }
    /**
     * FIXME: This need to be rewritten since getRunningServices has been deprecated
     */
    private boolean isServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    private boolean isLocationAccuracyHigh() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            try {
                int locationAccuracy = Settings.Secure.getInt(getContentResolver(),
                        Settings.Secure.LOCATION_MODE);
                if (locationAccuracy == Settings.Secure.LOCATION_MODE_HIGH_ACCURACY ||
                        locationAccuracy == Settings.Secure.LOCATION_MODE_SENSORS_ONLY) {

                    return true;
                }
            } catch (Settings.SettingNotFoundException e) {
                Log.d(TAG, "Error in detecting location settings");
            }
        } else {
            //noinspection deprecation - this is only used on pre KITKAT devices so can be used
            String locationProviders = Settings.Secure.getString(getContentResolver(),
                    Settings.Secure.LOCATION_PROVIDERS_ALLOWED);
            return locationProviders.contains("gps");
        }

        return false;
    }

    private void showLocationPrompt() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.location_prompt_dialog)
                .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        startActivityForResult(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS),
                                LOCATION_MODE_CHANGE);
                    }
                })
                .setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        // User cancelled the dialog
                    }
                });

        builder.show();
    }

    public static void promptForLocation() {
        Message message = handler.obtainMessage(MESSAGE_PROMPT_LOCATION);
        message.sendToTarget();
    }

    public static void setConnectedDevices(int n_clients) {
        Bundle bundle = new Bundle();
        Message message = handler.obtainMessage(MESSAGE_CONNECTED_DEVICES);
        bundle.putInt(KEY_CONNECTED_DEVICES, n_clients);
        message.setData(bundle);
        message.sendToTarget();
    }
}

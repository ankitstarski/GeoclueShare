package org.freedesktop.geoclueshare;

import android.app.Service;
import android.content.Intent;
import android.content.ServiceConnection;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.Nullable;
import android.util.Log;

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

/**
 * <p>
 * {@code LocationService} is responsible for fetching and handling location data from the GPS in
 * the background.
 * </p>
 * FIXME: It's just a dummy class yet. Add functionality.
 */
public class LocationService extends Service implements LocationListener, GpsStatus.NmeaListener {

    private static final String TAG = "LocationService";
    private LocationManager locationManager;
    private NetworkListener networkListener;

    /**
     * The minimum time between updates in milliseconds.
     */
    private static final long MIN_TIME_BW_UPDATES_GPS = 1000;

    /**
     * The minimum distance to change Updates in meters.
     */
    private static final long MIN_DISTANCE_CHANGE_FOR_UPDATES = 1;

    /**
     * The code for stopping Location updates.
     */
    public static final int MESSAGE_STOP_GPS = 0;

    /**
     * The code for starting Location updates.
     */
    public static final int MESSAGE_START_GPS = 1;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service started");

        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        Handler handler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message message) {
                switch (message.what) {
                    case MESSAGE_START_GPS:
                        startGps();
                        Log.d(TAG, "GPS start");
                        break;
                    case MESSAGE_STOP_GPS:
                        stopGps();
                        Log.d(TAG, "GPS stop");
                }
            }
        };

        networkListener = new NetworkListener(handler);
        networkListener.execute();

        return Service.START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void unbindService(ServiceConnection conn) {
        super.unbindService(conn);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        stopGps();
        networkListener.cancel(true);

        Log.d(TAG, "Service destroyed");
    }

    @Override
    public void onNmeaReceived(long timestamp, String nmea) {
        String nmeaTimestamp = nmea.split(",")[1];
        if (nmea.startsWith("$GPGGA") && nmeaTimestamp.matches("[0-9]{6}")) {
            NetworkListener.sendData(nmea);
        }
    }

    private void startGps() {
        locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                MIN_TIME_BW_UPDATES_GPS,
                MIN_DISTANCE_CHANGE_FOR_UPDATES,
                this);
        locationManager.addNmeaListener(this);
    }

    private void stopGps() {
        locationManager.removeUpdates(this);
        locationManager.removeNmeaListener(this);
    }

    @Override
    public void onLocationChanged(Location location) {

    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onProviderDisabled(String provider) {

    }
}

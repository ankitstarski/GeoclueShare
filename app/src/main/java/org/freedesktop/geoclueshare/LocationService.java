package org.freedesktop.geoclueshare;

import android.app.Service;
import android.content.Intent;
import android.content.ServiceConnection;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;
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

    /**
     * The minimum time between updates in milliseconds.
     */
    private static final long MIN_TIME_BW_UPDATES_GPS = 1000;

    /**
     * The minimum distance to change Updates in meters.
     */
    private static final long MIN_DISTANCE_CHANGE_FOR_UPDATES = 1;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service started");

        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                MIN_TIME_BW_UPDATES_GPS,
                MIN_DISTANCE_CHANGE_FOR_UPDATES,
                this);
        locationManager.addNmeaListener(this);

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

        locationManager.removeUpdates(this);
        locationManager.removeNmeaListener(this);

        Log.d(TAG, "Service destroyed");
    }

    @Override
    public void onNmeaReceived(long timestamp, String nmea) {
        String nmeaTimestamp = nmea.split(",")[1];
        if (nmea.startsWith("$GPGGA") && nmeaTimestamp.matches("[0-9]{6}")) {
            Log.d(TAG, "GGA Sentence: " + nmea);
        }
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

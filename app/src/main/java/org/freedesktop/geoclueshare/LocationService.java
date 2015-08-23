package org.freedesktop.geoclueshare;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

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
    private String ggaSentence;
    private NotificationCompat.Builder builder;

    /**
     * The unique identifier for current Android device.
     */
    public static String deviceId;

    /**
     * The that value goes into the `accuracy` feild of mDNS service's TXT record.
     */
    public static String accuracy = "exact";

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

    /**
     * Notification id.
     */
    private static final int NOTIFICATION_ID = 007;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service started");
        createNotification();

        Zeroconf.attainLock(this);

        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        deviceId = Settings.Secure.getString(getApplicationContext().getContentResolver(),
                Settings.Secure.ANDROID_ID);

        Handler handler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message message) {
                switch (message.what) {
                    case MESSAGE_START_GPS:
                        startGps();
                        Log.d(TAG, "GPS start");
                        Location loc = locationManager
                                .getLastKnownLocation(LocationManager.GPS_PROVIDER);
                        if (loc != null)
                            NetworkListener.sendData(getGgaFromLocation(loc));
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
        Zeroconf.releaseLock();

        Log.d(TAG, "Service destroyed");
        removeNotification();
    }

    @Override
    public void onNmeaReceived(long timestamp, String nmea) {
        String nmeaTimestamp = nmea.split(",")[1];
        if (nmea.startsWith("$GPGGA") && nmeaTimestamp.matches("[0-9]{6}")) {
            NetworkListener.sendData(nmea);
            ggaSentence = null;
        } else if (nmea.startsWith("$GPGGA")) {
            if (ggaSentence == null || ggaSentence.length() == 0)
                return;
            nmeaTimestamp = ggaSentence.split(",")[1];

            if (!nmeaTimestamp.matches("[0-9]{6}"))
                return;
            NetworkListener.sendData(ggaSentence);
            ggaSentence = null;
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
        ggaSentence = getGgaFromLocation(location);
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onProviderDisabled(String provider) {
        if (provider.equals(LocationManager.GPS_PROVIDER)) {
            MainActivity.promptForLocation();
        }
    }

    private String getGgaFromLocation(Location location) {
        String gga;
        Boolean hasAltitude = location.hasAltitude();

        Date date = new Date(location.getTime());
        DateFormat format = new SimpleDateFormat("HHmmss");
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        String time = format.format(date);

        if (hasAltitude) {
            gga = "$GPGGA,%s,%s,%s,1,,%.1f,%.1f,M,,M,,";
            gga = String.format(gga,
                    time,
                    getLatitudeString(location.getLatitude()),
                    getLongitudeString(location.getLongitude()),
                    getHdopFromAccuracy(location.getAccuracy()),
                    location.getAltitude());
        } else {
            gga = "$GPGGA,%s,%s,%s,1,,%.1f,,M,,M,,";
            gga = String.format(gga,
                    time,
                    getLatitudeString(location.getLatitude()),
                    getLongitudeString(location.getLongitude()),
                    getHdopFromAccuracy(location.getAccuracy()));
        }

        gga = addChecksumToGga(gga);

        return gga;
    }

    private double getHdopFromAccuracy(double accuracy) {
        /* FIXME: These are rough estimates based on the information given in the link below:
         *        http://en.wikipedia.org/wiki/Dilution_of_precision_%28GPS%29#Meaning_of_DOP_Values
         */
        if (accuracy <= 0.5)
            return 0.5;
        else if (accuracy <= 1.0)
            return 1.5;
        else if (accuracy <= 3.0)
            return 3.5;
        else if (accuracy <= 50.0)
            return 7.5;
        else if (accuracy <= 100.0)
            return 15.0;
        else
            return 30.0;
    }

    private String getLatitudeString(double lat) {
        String latStr = "%02d%06.3f,%s";

        int degrees = (int) Math.abs(lat);
        double minutes = Math.abs((lat - (int) lat) * 60);
        String symbol = lat >= 0 ? "N" : "S";

        latStr = String.format(latStr, degrees, minutes, symbol);
        return latStr;
    }

    private String getLongitudeString(double lon) {
        String lonStr = "%03d%06.3f,%s";

        int degrees = (int) Math.abs(lon);
        double minutes = Math.abs((lon - (int) lon) * 60);
        String symbol = lon >= 0 ? "E" : "W";

        lonStr = String.format(lonStr, degrees, minutes, symbol);
        return lonStr;
    }

    private String addChecksumToGga(String gga) {
        int checksum = 0;

        for (int i = 1, n = gga.length(); i < n; i++) {
            checksum ^= (int) gga.charAt(i);
        }

        return String.format("%s*%02X", gga, checksum);
    }

    private void createNotification() {
        Bitmap icon = BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher);

        builder = new NotificationCompat.Builder(this)
                .setSmallIcon(R.mipmap.ic_location_share)
                .setLargeIcon(icon)
                .setContentTitle(getString(R.string.notif_title))
                .setContentText(getString(R.string.notif_content))
                .setOngoing(true);

        Intent resultIntent = new Intent(this, MainActivity.class);
        resultIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent resultPendingIntent = PendingIntent.getActivity(
                this,
                0,
                resultIntent,
                PendingIntent.FLAG_UPDATE_CURRENT
        );
        builder.setContentIntent(resultPendingIntent);

        NotificationManager notoficationManager =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        notoficationManager.notify(NOTIFICATION_ID, builder.build());
    }

    private void removeNotification() {
        NotificationManager notoficationManager =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        notoficationManager.cancel(NOTIFICATION_ID);
    }
}

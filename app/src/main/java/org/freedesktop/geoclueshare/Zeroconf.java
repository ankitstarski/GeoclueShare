package org.freedesktop.geoclueshare;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.util.Log;

import java.io.IOException;
import java.util.HashMap;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;

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

public class Zeroconf {
    private static final String TAG = "Zeroconf";
    private static WifiManager.MulticastLock multicastLock;
    private JmDNS jmdns;
    private ServiceInfo serviceInfo;

    /**
     * The default tag string for Multicast, used in {@link Zeroconf#attainLock(Context)}.
     */
    private static final String DEFAULT_MULTICAST_TAG_STRING = "GeoclueShare";

    /**
     * This function is to be called before Using {@link Zeroconf} class is to be used. <br/>
     * This attains Multicast lock from WiFi service which is requred for mDNS broadcasting or
     * listening.
     *
     * @param context the context from where it is being called.
     */
    public static void attainLock(Context context) {
        multicastLock = ((WifiManager) context.getSystemService(Context.WIFI_SERVICE))
                .createMulticastLock(DEFAULT_MULTICAST_TAG_STRING);
        multicastLock.setReferenceCounted(true);
        multicastLock.acquire();
    }

    /**
     * Same as {@link Zeroconf#attainLock(Context)} but Tag string for the lock can be set.
     *
     * @param context the context from where it is being called.
     * @param tag     a tag for the MulticastLock to identify it in debugging
     *                messages.
     */
    public static void attainLock(Context context, String tag) {
        multicastLock = ((WifiManager) context.getSystemService(Context.WIFI_SERVICE))
                .createMulticastLock(tag);
        multicastLock.setReferenceCounted(true);
        multicastLock.acquire();
    }

    /**
     * Releases the lock attained by {@link Zeroconf#attainLock}.
     */
    public static void releaseLock() {
        if (multicastLock != null) {
            multicastLock.release();
            multicastLock = null;
        }
    }

    public Zeroconf() {
        try {
            jmdns = JmDNS.create();
        } catch (IOException e) {
            Log.d(TAG, "Can't start mDNS service");
        }
    }

    /**
     * Broadcasts mDNS service, {@code "_nmea-0183._tcp.local."} at a particular port number.
     *
     * @param serviceName name of the service to be shoown to other devices.
     * @param port port number
     */
    public void broadcastService(String serviceName, int port) {
        try {

            /*
             * Beware of this bug in JmDNS
             * http://stackoverflow.com/questions/12726801/avahi-not-able-to-find-service-creted-by-jmdns
             */
            HashMap<String, byte[]> properties = new HashMap<String, byte[]>();
            String serviceText = "Location Server for Geoclue";
            properties.put("description", serviceText.getBytes());
            properties.put("accuracy", LocationService.accuracy.getBytes());

            if (serviceName == null || serviceName.length() == 0)
                serviceName = DEFAULT_MULTICAST_TAG_STRING;

            serviceInfo = ServiceInfo.create("_nmea-0183._tcp.local.",
                    serviceName, port, 0, 0, true,
                    properties);

            jmdns.registerService(serviceInfo);

        } catch (Exception e) {
            Log.d(TAG, "Can't register Service");
        }
    }

    /**
     * Unregisters the service being broadcasted by {@link Zeroconf#broadcastService}.
     */
    public void unregisterService() {
        if(serviceInfo != null) {
            try {
                jmdns.unregisterService(serviceInfo);
            } catch (NullPointerException e) {
                Log.w(TAG, "Can't close a null mDNS service.");
            }
        }
    }
}

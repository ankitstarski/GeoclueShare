package org.freedesktop.geoclueshare;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.util.Log;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteOrder;
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

class Zeroconf {
    private static final String TAG = "Zeroconf";
    private static WifiManager.MulticastLock multicastLock;
    private JmDNS jmdns;
    private ServiceInfo serviceInfo;
    private static String ip;

    /**
     * The default tag string for Multicast, used in {@link Zeroconf#attainLock(Context)}.
     */
    private static final String DEFAULT_MULTICAST_TAG_STRING = "GeoclueShare";


    private static byte[] convertToBytes(int value, ByteOrder order)
    {
        byte[] byteArray = new byte[4];
        int shift;
        for (int i = 0; i < byteArray.length;
             i++) {

            if (order == ByteOrder.BIG_ENDIAN)
                shift = (byteArray.length - 1 - i) * 8; // 24, 16, 8, 0
            else
                shift = i * 8; // 0,8,16,24

            byteArray[i] = (byte) (value >>> shift);
        }
        return byteArray;

    }


    /**
     * This function is to be called before Using {@link Zeroconf} class is to be used. <br/>
     * This attains Multicast lock from WiFi service which is requred for mDNS broadcasting or
     * listening.
     *
     * @param context the context from where it is being called.
     */
    static void attainLock(Context context) {
        WifiManager wifi = (WifiManager)context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);

        byte[] ipAddress = convertToBytes(wifi.getConnectionInfo().getIpAddress(), ByteOrder.LITTLE_ENDIAN);

        InetAddress myaddr = null;
        try {
            myaddr = InetAddress.getByAddress(ipAddress);
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        assert myaddr != null;
        ip =  myaddr.getHostAddress();

        multicastLock = wifi.createMulticastLock(DEFAULT_MULTICAST_TAG_STRING);
        multicastLock.setReferenceCounted(true);
        multicastLock.acquire();
    }

    /**
     * Releases the lock attained by {@link Zeroconf#attainLock}.
     */
    static void releaseLock() {
        if (multicastLock != null) {
            multicastLock.release();
            multicastLock = null;
        }
    }

    Zeroconf() {
        try {
            jmdns = JmDNS.create(InetAddress.getByName(ip), LocationService.deviceId);
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
    void broadcastService(String serviceName, int port) {
        try {

            /*
             * Beware of this bug in JmDNS
             * http://stackoverflow.com/questions/12726801/avahi-not-able-to-find-service-creted-by-jmdns
             */
            HashMap<String, byte[]> properties = new HashMap<>();
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
    void unregisterService() {
        if(serviceInfo != null) {
            try {
                jmdns.unregisterService(serviceInfo);
            } catch (NullPointerException e) {
                Log.w(TAG, "Can't close a null mDNS service.");
            }
        }
    }
}

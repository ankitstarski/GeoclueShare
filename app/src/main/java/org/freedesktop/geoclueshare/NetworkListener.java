package org.freedesktop.geoclueshare;

import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

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
 * {@code NetworkListener} creates a new Thread (using AsyncTask) for network tasks. It performs
 * tasks such as client connection, disconnection, send or receive data.
 * <p>Call {@link NetworkListener#sendData} to broadcast data to all the clients.</p>
 */
public class NetworkListener extends AsyncTask<Void, Void, Void> {

    private static final String TAG = "NetworkListener";
    private ServerSocketChannel server = null;
    private Handler handler;
    private static HashMap<String, String> pendingData;
    Zeroconf mdns;

    /**
     * Total number of clients currently connected to the application.
     */
    public static int numberOfClients = 0;

    /**
     * The TCP/IP port used for Socket communication.
     */
    private static final int PORT = 10110;

    /**
     * The maximum length of a GGA sentence.
     */
    private static final int GGA_LENGTH_MAX = 80;

    public NetworkListener(Handler handler) {
        this.handler = handler;
    }

    @Override
    protected Void doInBackground(Void... params) {
        Selector selector;
        Iterator i;

        Log.d(TAG, "Started Listening");

        pendingData = new HashMap<String, String>();

        try {
            startServer();

            mdns = new Zeroconf();
            mdns.broadcastService(LocationService.deviceId, PORT);

            selector = Selector.open();
            server.register(selector, SelectionKey.OP_ACCEPT);
        } catch (IOException e) {
            Log.d(TAG, "Unable to create ServerSocket for port: " + PORT);
            Log.d(TAG, e.getMessage());
            return null;
        }

        while (!isCancelled()) {
            try {
                selector.select();
                Set keys = selector.selectedKeys();
                i = keys.iterator();
            } catch (IOException e) {
                Log.d(TAG, "Selector can't select");
                continue;
            }

            while (i.hasNext()) {
                SelectionKey key = (SelectionKey) i.next();
                i.remove();

                if (isCancelled()) {
                    return null;
                }

                try {
                    if (key.isAcceptable()) {
                        SocketChannel client = server.accept();
                        addClient(client, selector);

                        continue;
                    }

                    if (key.isReadable()) {
                        SocketChannel client = (SocketChannel) key.channel();
                        disconnectClient(client);

                        continue;
                    }

                    if (key.isWritable()) {
                        SocketChannel client = (SocketChannel) key.channel();
                        sendDataToClient(client);
                    }
                } catch (IOException e) {
                    continue;
                }
            }
        }

        return null;
    }

    /**
     * Broadcasts data to all the connected clients at port number stored inside
     * {@link NetworkListener#PORT}
     *
     * @param data The data that is to be broadcasted to all the connected clients at port number
     *             stored inside {@link NetworkListener#PORT}.
     */
    public static void sendData(String data) {
        String ggaSentence = data + "\r\n";

        Iterator it = pendingData.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry) it.next();
            pair.setValue(ggaSentence);
        }
    }

    private void startServer() throws IOException {
        server = ServerSocketChannel.open();
        server.configureBlocking(false);
        server.socket().bind(new java.net.InetSocketAddress(PORT));
    }

    private void addClient(SocketChannel client, Selector selector) throws IOException {
        client.configureBlocking(false);
        client.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
        pendingData.put(client.socket().getInetAddress().toString(), "");

        Log.d(TAG, "Client connected");
        numberOfClients++;
        MainActivity.setConnectedDevices(numberOfClients);

        Log.d(TAG, "Number of clients: " + numberOfClients);
        if (numberOfClients == 1) {
            Message message = handler.obtainMessage(
                    LocationService.MESSAGE_START_GPS);
            message.sendToTarget();
        }
    }

    private void disconnectClient(SocketChannel client) throws IOException {
        int BUFFER_SIZE = 32;
        ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);

        try {
            if (client.read(buffer) == -1) {
                throw new Exception();
            }
        } catch (Exception e) {
            pendingData.remove(client.socket().getInetAddress().toString());
            client.close();
            Log.d(TAG, "Client disconnected");

            numberOfClients--;
            MainActivity.setConnectedDevices(numberOfClients);

            Log.d(TAG, "Number of clients: " + numberOfClients);
            if (numberOfClients == 0) {
                Message message = handler.obtainMessage(
                        LocationService.MESSAGE_STOP_GPS);
                message.sendToTarget();
            }
        }
    }

    private void sendDataToClient(SocketChannel client) throws IOException {
        String data = pendingData.get(client.socket().getInetAddress().toString());

        if (data == null || data.length() == 0)
            return;

        ByteBuffer buf = ByteBuffer.allocate(GGA_LENGTH_MAX);
        buf.clear();

        buf.put(data.getBytes());
        buf.flip();

        while (buf.hasRemaining()) {
            client.write(buf);
        }

        pendingData.put(client.socket().getInetAddress().toString(), "");
    }

    @Override
    protected void onCancelled() {
        super.onCancelled();
        try {
            if (mdns != null)
                mdns.unregisterService();
            if (server != null)
                server.close();
            numberOfClients = 0;
            MainActivity.setConnectedDevices(numberOfClients);

            if (pendingData != null)
                pendingData.clear();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

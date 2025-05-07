package com.hiro.localbackend;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

public class BackendServerService extends Service {
    private static final String TAG = "BackendServerService";
    private static final int NOTIFICATION_ID = 1;
    private static final String CHANNEL_ID = "BackendServerChannel";
    private static final int SERVER_PORT = 8080;

    private static final String PACKAGE_NAME = "com.hiro.localbackend.";

    // Action constants for service control
    public static final String ACTION_START_SERVER = PACKAGE_NAME + "START_SERVER";
    public static final String ACTION_STOP_SERVER = PACKAGE_NAME + "STOP_SERVER";
    public static final String ACTION_GET_STATUS = PACKAGE_NAME + "GET_STATUS";

    // Broadcast actions for status updates
    public static final String BROADCAST_SERVER_STATUS = PACKAGE_NAME + "SERVER_STATUS";
    public static final String EXTRA_SERVER_RUNNING = "server_running";
    public static final String EXTRA_SERVER_URL = "server_url";

    private AndroidBackendServer server;
    private boolean isServerRunning = false;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (action != null) {
                switch (action) {
                    case ACTION_START_SERVER:
                        startServer();
                        break;
                    case ACTION_STOP_SERVER:
                        stopServer();
                        stopSelf();
                        break;
                    case ACTION_GET_STATUS:
                        broadcastStatus();
                        break;
                }
            }
        }

        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;  // No binding needed for modern foreground service approach
    }

    @Override
    public void onDestroy() {
        stopServer();
        super.onDestroy();
    }

    private void startServer() {
        if (isServerRunning) {
            Log.d(TAG, "Server is already running");
            return;
        }

        // Start as foreground service first with initial notification
        startForeground(NOTIFICATION_ID, createNotification("Starting server..."));

        // Then start the actual server
        server = new AndroidBackendServer(getApplicationContext(), SERVER_PORT);
        try {
            server.start();
            isServerRunning = true;

            String ipAddress = getLocalIpAddress();
            String serverUrl = "http://" + ipAddress + ":" + SERVER_PORT;

            Log.i(TAG, "Server started at " + serverUrl);
            updateNotification("Server running at " + serverUrl);

            // Broadcast the status update
            broadcastStatus();
        } catch (IOException e) {
            Log.e(TAG, "Failed to start server", e);
            updateNotification("Failed to start server: " + e.getMessage());
            stopSelf();
        }
    }

    private void stopServer() {
        if (server != null && isServerRunning) {
            server.stop();
            isServerRunning = false;
            Log.i(TAG, "Server stopped");

            // Broadcast the status update
            broadcastStatus();
        }
    }

    private void broadcastStatus() {
        Intent statusIntent = new Intent(BROADCAST_SERVER_STATUS);
        statusIntent.putExtra(EXTRA_SERVER_RUNNING, isServerRunning);
        if (isServerRunning) {
            statusIntent.putExtra(EXTRA_SERVER_URL, "http://" + getLocalIpAddress() + ":" + SERVER_PORT);
        }
        sendBroadcast(statusIntent);
    }

    private String getLocalIpAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                // Filter out loopback interfaces and interfaces that are down
                if (iface.isLoopback() || !iface.isUp()) {
                    continue;
                }

                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    // Filter out IPv6 addresses and loopback addresses
                    if (addr.isLoopbackAddress() || addr.getHostAddress().contains(":")) {
                        continue;
                    }
                    return addr.getHostAddress();
                }
            }
        } catch (SocketException e) {
            Log.e(TAG, "Failed to get local IP address", e);
        }

        return "127.0.0.1"; // Default to localhost if no network available
    }

    private void createNotificationChannel() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = channel = new NotificationChannel(
                        CHANNEL_ID,
                        "Backend Server Service",
                        NotificationManager.IMPORTANCE_LOW);
                channel.setDescription("Background service for running the backend server");
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createNotification(String text) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Android Backend Server")
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void updateNotification(String text) {
        Notification notification = createNotification(text);
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, notification);
        }
    }
}
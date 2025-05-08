package com.hiro.localbackend;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;

import com.hiro.localbackend.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_CODE = 1001;

    private ActivityMainBinding ui;

    private boolean isServerRunning = false;

    // BroadcastReceiver to receive status updates from the service
    private final BroadcastReceiver serverStatusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (BackendServerService.BROADCAST_SERVER_STATUS.equals(intent.getAction())) {
                isServerRunning = intent.getBooleanExtra(BackendServerService.EXTRA_SERVER_RUNNING, false);
                String serverUrl = intent.getStringExtra(BackendServerService.EXTRA_SERVER_URL);

                updateUI(isServerRunning, serverUrl);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ui = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(ui.getRoot());

        ui.btnStartServer.setOnClickListener(v -> startServer());
        ui.btnStopServer.setOnClickListener(v -> stopServer());

        checkPermissions();

        // Register the broadcast receiver for server status updates - compatible with all Android versions
        registerReceiverCompat();

        // Query current status when activity starts
        checkServerStatus();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Unregister the broadcast receiver
        unregisterReceiver(serverStatusReceiver);
    }

    /**
     * Register the broadcast receiver in a way that's compatible with all Android versions
     */
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerReceiverCompat() {
        IntentFilter filter = new IntentFilter(BackendServerService.BROADCAST_SERVER_STATUS);

        try {
            // First try using the new API (Android 13+)
            if (Build.VERSION.SDK_INT >= 33) { // Build.VERSION_CODES.TIRAMISU
                // Using reflection to avoid direct reference that might cause problems in some IDEs
                // This is equivalent to: registerReceiver(serverStatusReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
                this.getClass().getMethod("registerReceiver",
                                BroadcastReceiver.class,
                                IntentFilter.class,
                                int.class)
                        .invoke(this, serverStatusReceiver, filter, 1); // 1 = RECEIVER_NOT_EXPORTED
            } else {
                // Fall back to the old API for older Android versions
                registerReceiver(serverStatusReceiver, filter);
            }
        } catch (Exception e) {
            // If anything goes wrong with reflection, fall back to the standard method
            registerReceiver(serverStatusReceiver, filter);
        }
    }

    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.INTERNET)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.INTERNET},
                    PERMISSION_REQUEST_CODE);
        }

        // For Android 13+ (API 33+), we need to request notification permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        PERMISSION_REQUEST_CODE + 1);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE || requestCode == PERMISSION_REQUEST_CODE + 1) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permission granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Permission denied. App may not function properly.",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    private void startServer() {
        Intent intent = new Intent(this, BackendServerService.class);
        intent.setAction(BackendServerService.ACTION_START_SERVER);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private void stopServer() {
        Intent intent = new Intent(this, BackendServerService.class);
        intent.setAction(BackendServerService.ACTION_STOP_SERVER);
        startService(intent);
    }

    private void checkServerStatus() {
        Intent intent = new Intent(this, BackendServerService.class);
        intent.setAction(BackendServerService.ACTION_GET_STATUS);
        startService(intent);
    }

    private void updateUI(boolean isRunning, String serverUrl) {
        ui.tvServerStatus.setText(isRunning ? "Server Status: Running" : "Server Status: Stopped");

        if (isRunning && serverUrl != null) {
            ui.tvServerUrl.setText("URL: " + serverUrl);
            ui.tvServerUrl.setVisibility(View.VISIBLE);
        } else {
            ui.tvServerUrl.setVisibility(View.GONE);
        }

        ui.btnStartServer.setEnabled(!isRunning);
        ui.btnStopServer.setEnabled(isRunning);
    }
}

package com.example.tracker;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private EditText etServer, etDevice;
    private Button btnStart;

    private final ActivityResultLauncher<String[]> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                Boolean fine = result.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false);
                Boolean coarse = result.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false);
                if (!(fine || coarse)) {
                    Toast.makeText(this, "Location permission required", Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        etServer = new EditText(this);
        etServer.setHint("Server URL");
        etDevice = new EditText(this);
        etDevice.setHint("Device ID");
        btnStart = new Button(this);
        btnStart.setText("Start Tracking");

        btnStart.setOnClickListener(v -> {
            String server = etServer.getText().toString().trim();
            String device = etDevice.getText().toString().trim();
            if (server.isEmpty()) {
                Toast.makeText(this, "Enter server URL", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent i = new Intent(this, LocationService.class);
            i.putExtra("serverUrl", server);
            i.putExtra("deviceId", device.isEmpty() ? "device-default" : device);
            ContextCompat.startForegroundService(this, i);
            Toast.makeText(this, "Tracking started", Toast.LENGTH_SHORT).show();
        });

        setContentView(btnStart);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
        }
    }
}


package com.example.tracker;

import android.Manifest;
import android.app.*;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import com.google.android.gms.location.*;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class LocationService extends Service {

    private FusedLocationProviderClient fusedClient;
    private LocationRequest locationRequest;
    private String serverUrl;
    private String deviceId;

    @Override
    public void onCreate() {
        super.onCreate();
        fusedClient = LocationServices.getFusedLocationProviderClient(this);
        locationRequest = LocationRequest.create()
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .setInterval(60000) // every 60s
                .setFastestInterval(30000);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        serverUrl = intent.getStringExtra("serverUrl");
        deviceId = intent.getStringExtra("deviceId");

        startForeground(1, createNotification());
        startUpdates();
        int START_STICKY;
        return START_STICKY;
    }

    private Notification createNotification() {
        String channelId = "tracker_channel";
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(channelId, "Tracker",
                    NotificationManager.IMPORTANCE_LOW);
            nm.createNotificationChannel(ch);
        }
        return new NotificationCompat.Builder(this, channelId)
                .setContentTitle("Tracking location")
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .build();
    }

    private void startUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) return;

        fusedClient.requestLocationUpdates(locationRequest, new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult result) {
                for (Location loc : result.getLocations()) {
                    sendLocation(loc);
                }
            }
        }, getMainLooper());
    }

    private void sendLocation(Location loc) {
        new Thread(() -> {
            try {
                JSONObject json = new JSONObject();
                json.put("deviceId", deviceId);
                json.put("lat", loc.getLatitude());
                json.put("lon", loc.getLongitude());
                json.put("accuracy", loc.getAccuracy());
                json.put("timestamp", System.currentTimeMillis());

                URL url = new URL(serverUrl + "/report");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(json.toString().getBytes());
                }
                conn.getResponseCode();
                conn.disconnect();
            } catch (Exception ignored) {}
        }).start();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }
}

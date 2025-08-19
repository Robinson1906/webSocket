package com.example.websocket;

import androidx.appcompat.app.AppCompatActivity;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private WebSocketService wsService;
    private boolean bound = false;

    private TextView statusText, messagesText;
    private EditText inputMessage;
    private Button sendButton, connectButton, disconnectButton, setUrlButton;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            WebSocketService.LocalBinder binder = (WebSocketService.LocalBinder) service;
            wsService = binder.getService();
            bound = true;

            wsService.setActivityCallback(new WebSocketService.ActivityCallback() {
                @Override
                public void onStatusChanged(String status, boolean isConnected) {
                    runOnUiThread(() -> {
                        statusText.setText(status);
                        statusText.setTextColor(isConnected ? Color.GREEN : Color.RED);
                    });
                }

                @Override
                public void onStatusChanged(String status, int color) {
                    runOnUiThread(() -> {
                        statusText.setText(status);
                        statusText.setTextColor(color);
                    });
                }

                @Override
                public void onMessageReceived(String message) {
                    runOnUiThread(() -> messagesText.append(message + "\n"));
                }
            });
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            bound = false;
            wsService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusText = findViewById(R.id.statusText);
        messagesText = findViewById(R.id.messagesText);
        inputMessage = findViewById(R.id.inputMessage);
        sendButton = findViewById(R.id.sendButton);
        connectButton = findViewById(R.id.connectBottom);
        disconnectButton = findViewById(R.id.disconnectButton);
        setUrlButton = findViewById(R.id.setUrlButton);

        Intent serviceIntent = new Intent(this, WebSocketService.class);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE);

        connectButton.setOnClickListener(v -> {
            if (bound && wsService != null) {
                wsService.reconnect();
            }
        });

        disconnectButton.setOnClickListener(v -> {
            if (bound && wsService != null) {
                wsService.setActivityCallback(null);
                wsService.stopSelf();
            }
        });

        sendButton.setOnClickListener(v -> {
            String msg = inputMessage.getText().toString().trim();
            if (!msg.isEmpty() && bound && wsService != null) {
                wsService.sendMessage(msg);
                messagesText.append("Yo: " + msg + "\n");
                inputMessage.setText("");
            }
        });

        setUrlButton.setOnClickListener(v -> {
            if (bound && wsService != null) {
                wsService.setServerUrl("ws://PrintConToda:8889/");
                Log.d(TAG, "URL de servidor cambiada a 127.0.0.1");
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bound) {
            try {
                unbindService(connection);
            } catch (Exception ignored) {}
            bound = false;
        }
    }
}

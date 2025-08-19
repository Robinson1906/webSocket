package com.example.websocket;

import android.util.Log;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class WebSocketManager {

    private static final String TAG = "WebSocketManager";
    private WebSocket webSocket;
    private String serverUrl;
    private WebSocketListenerCustom listener;

    // Cliente con pingInterval para mantener conexión viva
    private final OkHttpClient client = new OkHttpClient.Builder()
            .pingInterval(5, TimeUnit.SECONDS) // 🔹 Ping cada 30 segundos
            .build();

    public interface WebSocketListenerCustom {
        void onMessageReceived(String message);
        void onStatusChanged(String status);
    }

    public WebSocketManager(String serverUrl) {
        this.serverUrl = serverUrl;
    }

    public void setWebSocketListener(WebSocketListenerCustom listener) {
        this.listener = listener;
    }

    public void connect() {
        Request request = new Request.Builder().url(serverUrl).build();

        client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                WebSocketManager.this.webSocket = webSocket;
                Log.d(TAG, "Conectado al servidor WebSocket ✅");
                if (listener != null) listener.onStatusChanged("Conectado ✅");
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                Log.d(TAG, "Mensaje recibido: " + text);
                if (listener != null) listener.onMessageReceived(text);
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                Log.d(TAG, "Conexión cerrada: " + reason);
                if (listener != null) listener.onStatusChanged("Desconectado ❌");
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                Log.e(TAG, "Error WebSocket", t);
                if (listener != null) listener.onStatusChanged("Error ⚠️");
            }
        });
    }

    public void sendMessage(String message) {
        if (webSocket != null) {
            webSocket.send(message);
        }
    }

    public void close() {
        if (webSocket != null) {
            webSocket.close(1000, "Cierre manual");
        }
    }
}

package com.example.websocket;

import android.util.Log;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;

public class SimpleWebSocketServer extends WebSocketServer {

    private static final String TAG = "SimpleWebSocketServer";
    private WebSocketService service;

    public SimpleWebSocketServer(int port, WebSocketService service) {
        super(new InetSocketAddress(port));
        this.service = service;
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        Log.d(TAG, "Cliente conectado: " + conn.getRemoteSocketAddress());
        conn.send("Hola mundo desde el servidor WS ✅");
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        Log.d(TAG, "Cliente desconectado: " + conn.getRemoteSocketAddress());
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        Log.d(TAG, "Mensaje recibido: " + message);
        // Notifica al servicio para reenviar a la Activity
        if (service != null) {
            service.notifyFromHttp("[WS] " + message);
        }
        // Opcional: eco al cliente
        conn.send("Eco: " + message);
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        Log.e(TAG, "Error WS: " + ex.getMessage(), ex);
    }

    @Override
    public void onStart() {
        Log.d(TAG, "Servidor WebSocket iniciado en ws://127.0.0.1:8889 ✅");
    }
}

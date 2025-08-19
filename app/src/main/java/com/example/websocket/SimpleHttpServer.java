package com.example.websocket;

import android.content.Context;
import android.util.Log;

import fi.iki.elonen.NanoHTTPD;

public class SimpleHttpServer extends NanoHTTPD {

    private static final String TAG = "SimpleHttpServer";
    private final Context context;

    public SimpleHttpServer(int port, Context context) {
        super(port);
        this.context = context;
    }

    public void startServer() {
        try {
            start(SOCKET_READ_TIMEOUT, false);
            Log.d(TAG, "Servidor HTTP iniciado en http://127.0.0.1:" + getListeningPort() + "/ ✅");
        } catch (Exception e) {
            Log.e(TAG, "Error al iniciar servidor HTTP", e);
        }
    }

    public void stopServer() {
        stop();
        Log.d(TAG, "Servidor HTTP detenido 🛑");
    }

    @Override
    public Response serve(IHTTPSession session) {
        Log.d(TAG, "Petición recibida: " + session.getUri());
        return newFixedLengthResponse("Hola mundo");
    }
}

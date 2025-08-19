package com.example.websocket;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Dns;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

/**
 * Servicio en primer plano que mantiene una conexión WebSocket activa,
 * expone callbacks a la Activity y levanta un servidor HTTP embebido.
 */
public class WebSocketService extends Service {
    private static final String TAG = "WebSocketService";

    // Notificación / Foreground
    public static final String CHANNEL_ID = "WebSocketServiceChannel";
    private static final int NOTIFICATION_ID = 1;

    // WS
    private static final int NORMAL_CLOSURE = 1000;
    private static final long RECONNECT_DELAY = 5000L;
    private static final int CONNECT_TIMEOUT = 15;  // seg
    private static final int PING_INTERVAL = 25;    // seg

    // Cliente y socket
    private OkHttpClient client;
    private WebSocket webSocket;

    /**
     * Alias PrintConToda para el cliente WS hacia sí mismo
     */
    private String wsUrl = "ws://PrintConToda:8889/";

    private boolean isConnected = false;

    // Comunicación con la Activity
    private ActivityCallback activityCallback;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Servidores embebidos
    private SimpleHttpServer httpServer;
    private SimpleWebSocketServer wsServer;

    // ======== Interfaz para comunicación con Activity ========
    public interface ActivityCallback {
        void onStatusChanged(String status, boolean isConnected);
        void onStatusChanged(String status, int color);
        void onMessageReceived(String message);
    }

    // ======== Binder para conectar Service con Activity ========
    public class LocalBinder extends Binder {
        public WebSocketService getService() {
            return WebSocketService.this;
        }
    }
    private final IBinder binder = new LocalBinder();

    // ======== Ciclo de vida del Service ========

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Servicio WebSocket creado ✅");
        startForegroundServiceWithNotification();
        initializeClient();
        startHttpServer();
        startWsServer();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand recibido. flags=" + flags + " startId=" + startId);
        if (webSocket == null || !isConnected) {
            connectWebSocket();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (webSocket != null) {
            try {
                webSocket.close(NORMAL_CLOSURE, "Servicio terminado");
            } catch (Exception ignored) {}
            webSocket = null;
        }
        if (client != null) {
            try {
                client.dispatcher().executorService().shutdown();
            } catch (Exception ignored) {}
        }
        if (httpServer != null) {
            httpServer.stopServer();
            httpServer = null;
        }
        if (wsServer != null) {
            try {
                wsServer.stop();
            } catch (Exception ignored) {}
            wsServer = null;
        }
        Log.d(TAG, "Servicio WebSocket destruido 🛑");
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    // ======== Inicialización del cliente OkHttp con DNS personalizado ========

    private void initializeClient() {
        Dns customDns = new Dns() {
            @Override
            public List<InetAddress> lookup(String hostname) throws UnknownHostException {
                if ("PrintConToda".equalsIgnoreCase(hostname)) {
                    return Arrays.asList(InetAddress.getByName("127.0.0.1"));
                }
                return Dns.SYSTEM.lookup(hostname);
            }
        };

        client = new OkHttpClient.Builder()
                .dns(customDns)
                .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .pingInterval(PING_INTERVAL, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();
    }

    // ======== Notificación / Foreground ========

    private void startForegroundServiceWithNotification() {
        createNotificationChannel();
        Notification notification = buildNotification("Iniciando conexión...", Color.GRAY);
        startForeground(NOTIFICATION_ID, notification);
    }

    private Notification buildNotification(String text, int color) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Servicio WebSocket")
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(color)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build();
    }

    private void updateNotification(String text, int color) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.notify(NOTIFICATION_ID, buildNotification(text, color));
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "WebSocket Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Canal para el servicio WebSocket");
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    // ======== Conexión WebSocket como cliente ========

    private void connectWebSocket() {
        Log.d(TAG, "Conectando a: " + wsUrl);
        updateStatus("Conectando...", false, Color.YELLOW);

        Request request = new Request.Builder().url(wsUrl).build();

        webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                isConnected = true;
                Log.i(TAG, "Conexión establecida. Código: " + response.code());
                updateStatus("Conectado", true, Color.GREEN);
                sendMessageToActivity("Conexión WS exitosa");
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                Log.d(TAG, "Mensaje recibido: " + text);
                sendMessageToActivity(text);
            }

            @Override
            public void onMessage(WebSocket webSocket, ByteString bytes) {
                Log.d(TAG, "Datos binarios (" + bytes.size() + " bytes)");
                sendMessageToActivity("[Datos binarios]");
            }

            @Override
            public void onClosing(WebSocket webSocket, int code, String reason) {
                isConnected = false;
                Log.d(TAG, "onClosing: code=" + code + " reason=" + reason);
                updateStatus("Desconectando...", false, Color.YELLOW);
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                isConnected = false;
                Log.d(TAG, "onClosed: code=" + code + " reason=" + reason);
                updateStatus("Desconectado", false, Color.RED);
                if (code != NORMAL_CLOSURE) {
                    scheduleReconnect();
                }
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, @Nullable Response response) {
                isConnected = false;
                Log.e(TAG, "Error WS: " + (t != null ? t.getMessage() : "desconocido"), t);
                updateStatus("Error: " + (t != null ? t.getMessage() : "WS failure"), false, Color.RED);
                showToast("Error de conexión");
                scheduleReconnect();
            }
        });
    }

    private void scheduleReconnect() {
        Log.d(TAG, "Reintentando conexión en " + RECONNECT_DELAY + " ms…");
        mainHandler.postDelayed(this::connectWebSocket, RECONNECT_DELAY);
    }

    // ======== Utilidades UI / Activity callbacks ========

    private void updateStatus(String status, boolean connected, int color) {
        mainHandler.post(() -> {
            if (activityCallback != null) {
                activityCallback.onStatusChanged(status, connected);
            }
            updateNotification(status, color);
        });
    }

    private void sendMessageToActivity(String message) {
        mainHandler.post(() -> {
            if (activityCallback != null) {
                activityCallback.onMessageReceived(message);
            }
        });
    }

    private void showToast(String message) {
        mainHandler.post(() ->
                Toast.makeText(WebSocketService.this, message, Toast.LENGTH_SHORT).show()
        );
    }

    // ======== Servidores embebidos ========

    private void startHttpServer() {
        httpServer = new SimpleHttpServer(8888, this);
        httpServer.startServer();
        Log.d(TAG, "Servidor HTTP embebido iniciado en http://127.0.0.1:8888/ ✅");
    }

    private void startWsServer() {
        wsServer = new SimpleWebSocketServer(8889, this);
        wsServer.start();
        Log.d(TAG, "Servidor WebSocket embebido iniciado en ws://127.0.0.1:8889/ ✅");
    }

    // ======== API pública del Service ========

    public void sendMessage(String message) {
        if (webSocket != null && isConnected) {
            webSocket.send(message);
        } else {
            showToast("No conectado");
        }
    }

    public void setServerUrl(String url) {
        this.wsUrl = url;
        reconnect();
    }

    public void reconnect() {
        if (webSocket != null) {
            try {
                webSocket.close(NORMAL_CLOSURE, "Reconexión manual");
            } catch (Exception ignored) {}
            webSocket = null;
        }
        connectWebSocket();
    }

    public boolean isConnected() {
        return isConnected;
    }

    public void setActivityCallback(ActivityCallback callback) {
        this.activityCallback = callback;
    }

    // Método llamado por HTTP o WS para notificar a la UI
    public void notifyFromHttp(String msg) {
        sendMessageToActivity(msg);
        sendMessage(msg);
    }
}

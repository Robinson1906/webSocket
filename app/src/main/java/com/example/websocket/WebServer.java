package com.example.websocket;

import fi.iki.elonen.NanoWSD;
import fi.iki.elonen.NanoWSD.WebSocket;
import fi.iki.elonen.NanoWSD.WebSocketFrame;
import fi.iki.elonen.NanoHTTPD.Response;
import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Response.Status;

import java.io.IOException;

public class WebServer extends NanoWSD {

    public WebServer(int port) {
        super(port);
    }

    @Override
    protected WebSocket openWebSocket(IHTTPSession handshake) {
        return new MyWebSocket(handshake);
    }

    @Override
    public Response serveHttp(IHTTPSession session) {
        String uri = session.getUri();

        if ("/".equals(uri)) {
            return newFixedLengthResponse(Status.OK, "text/html",
                    "<h1>Hola Mundo desde Android!</h1>" +
                            "<p>Servidor WebSocket corriendo en <b>/ws</b></p>");
        } else if ("/ws".equals(uri)) {
            return super.serveHttp(session);
        } else {
            return newFixedLengthResponse(Status.NOT_FOUND, "text/plain", "Not Found");
        }
    }

    private static class MyWebSocket extends WebSocket {
        public MyWebSocket(IHTTPSession handshakeRequest) {
            super(handshakeRequest);
        }

        @Override
        protected void onOpen() {
            System.out.println("✅ WebSocket abierto");
        }

        @Override
        protected void onClose(WebSocketFrame.CloseCode code, String reason, boolean initiatedByRemote) {
            System.out.println("❌ WebSocket cerrado: " + reason);
        }

        @Override
        protected void onMessage(WebSocketFrame message) {
            try {
                String msg = message.getTextPayload();
                System.out.println("📩 Mensaje recibido: " + msg);
                send("Echo: " + msg);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        protected void onPong(WebSocketFrame pong) {
            System.out.println("📡 Pong recibido");
        }

        @Override
        protected void onException(IOException exception) {
            exception.printStackTrace();
        }
    }
}

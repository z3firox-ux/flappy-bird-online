package com.xili7.game.network;

import com.xili7.game.network.MessageParser.ParsedMessage;
import com.xili7.game.network.MessageParser.PlayerSnapshot;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Plain Java TCP client for LibGDX multiplayer.
 *
 * Responsibilities:
 * - Connect/disconnect from server.
 * - Send INPUT messages from local player.
 * - Listen for STATE snapshots in a background thread.
 * - Emit callbacks so game code can integrate updates in render/update loop.
 */
public class Client {
    public interface Listener {
        void onConnected(String playerId);

        void onStateReceived(Map<String, PlayerSnapshot> players);

        void onDisconnected();

        void onError(Exception exception);
    }

    private final String host;
    private final int port;
    private final Listener listener;

    private Socket socket;
    private PrintWriter writer;
    private BufferedReader reader;
    private ExecutorService readExecutor;

    private volatile boolean connected;
    private volatile String localPlayerId;

    public Client(String host, int port, Listener listener) {
        this.host = host;
        this.port = port;
        this.listener = listener;
    }

    public void connect() throws IOException {
        if (connected) {
            return;
        }

        socket = new Socket(host, port);
        socket.setTcpNoDelay(true);
        writer = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8);
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

        connected = true;
        readExecutor = Executors.newSingleThreadExecutor();
        readExecutor.submit(this::readLoop);
    }

    public void disconnect() {
        connected = false;

        if (readExecutor != null) {
            readExecutor.shutdownNow();
        }

        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // Ignore close errors.
            }
        }

        listener.onDisconnected();
    }

    public boolean isConnected() {
        return connected;
    }

    public String localPlayerId() {
        return localPlayerId;
    }

    /**
     * Sends local player state to the server.
     * Message format: INPUT|playerId|x|y|movementState
     */
    public void sendInput(float x, float y, String movementState) {
        if (!connected || localPlayerId == null) {
            return;
        }

        String payload = MessageParser.serializeInput(localPlayerId, x, y, movementState);
        writer.println(payload);
        if (writer.checkError()) {
            disconnect();
        }
    }

    private void readLoop() {
        try {
            String line;
            while (connected && (line = reader.readLine()) != null) {
                handleMessage(line);
            }
        } catch (Exception e) {
            if (connected) {
                listener.onError(e);
            }
        } finally {
            if (connected) {
                disconnect();
            }
        }
    }

    private void handleMessage(String rawLine) {
        ParsedMessage message = MessageParser.parse(rawLine);

        switch (message.command()) {
            case "WELCOME" -> {
                if (message.paramCount() >= 1) {
                    localPlayerId = message.param(0);
                    listener.onConnected(localPlayerId);
                }
            }
            case "STATE" -> listener.onStateReceived(MessageParser.parseState(message));
            default -> {
                // Unknown commands are ignored to keep protocol forward-compatible.
            }
        }
    }
}

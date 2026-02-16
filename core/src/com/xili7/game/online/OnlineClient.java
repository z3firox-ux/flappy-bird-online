package com.xili7.game.online;

import com.xili7.game.online.MessageParser.ParsedMessage;
import com.xili7.game.online.MessageParser.PlayerState;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * TCP client used by LibGDX screens to communicate with {@link OnlineServer}.
 */
public class OnlineClient {
    public interface Listener {
        default void onConnected(String playerId) {
        }

        default void onPlayerState(PlayerState state) {
        }

        default void onSnapshot(List<PlayerState> states) {
        }

        default void onPlayerJump(String playerId) {
        }

        default void onPlayerLeft(String playerId) {
        }

        default void onDisconnected() {
        }

        default void onError(Exception exception) {
        }
    }

    private final String host;
    private final int port;

    private volatile Listener listener;
    private volatile boolean connected;
    private volatile String playerId;

    private Socket socket;
    private PrintWriter writer;
    private BufferedReader reader;
    private ExecutorService readExecutor;

    public OnlineClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public synchronized void connect() throws IOException {
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

        writer.println(MessageParser.join());
    }

    public synchronized void disconnect() {
        connected = false;

        if (readExecutor != null) {
            readExecutor.shutdownNow();
        }

        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // ignored
            }
        }

        Listener current = listener;
        if (current != null) {
            current.onDisconnected();
        }
    }

    public boolean isConnected() {
        return connected;
    }

    public String getPlayerId() {
        return playerId;
    }

    public void sendJump() {
        if (!connected || playerId == null) {
            return;
        }
        writer.println(MessageParser.jump(playerId));
        if (writer.checkError()) {
            disconnect();
        }
    }

    public void sendState(float x, float y, int score) {
        if (!connected || playerId == null) {
            return;
        }
        writer.println(MessageParser.state(playerId, x, y, score));
        if (writer.checkError()) {
            disconnect();
        }
    }

    private void readLoop() {
        try {
            String line;
            while (connected && (line = reader.readLine()) != null) {
                handle(line);
            }
        } catch (Exception e) {
            Listener current = listener;
            if (connected && current != null) {
                current.onError(e);
            }
        } finally {
            if (connected) {
                disconnect();
            }
        }
    }

    private void handle(String line) {
        ParsedMessage message = MessageParser.parse(line);
        Listener current = listener;
        if (current == null) {
            return;
        }

        switch (message.command()) {
            case "WELCOME" -> {
                if (message.size() > 0) {
                    playerId = message.arg(0);
                    current.onConnected(playerId);
                }
            }
            case "STATE" -> current.onPlayerState(MessageParser.parseState(message));
            case "BULK_STATE" -> current.onSnapshot(MessageParser.parseBulkState(message));
            case "JUMP" -> {
                if (message.size() > 0) {
                    current.onPlayerJump(message.arg(0));
                }
            }
            case "LEFT" -> {
                if (message.size() > 0) {
                    current.onPlayerLeft(message.arg(0));
                }
            }
            default -> {
                // ignore unknown commands
            }
        }
    }
}

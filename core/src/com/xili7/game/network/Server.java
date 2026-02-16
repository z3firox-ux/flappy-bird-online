package com.xili7.game.network;

import com.xili7.game.network.MessageParser.ParsedMessage;
import com.xili7.game.network.MessageParser.PlayerSnapshot;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Plain Java TCP game server for LibGDX multiplayer.
 *
 * Responsibilities:
 * - Accept multiple clients.
 * - Receive INPUT updates from each client.
 * - Keep a global player-state map.
 * - Broadcast STATE snapshots to all connected clients at fixed intervals.
 */
public class Server {
    private final int port;
    private final int tickRate;

    private final AtomicInteger idSequence = new AtomicInteger(1);
    private final Map<String, PlayerSnapshot> playerStates = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<ClientHandler> clients = new CopyOnWriteArrayList<>();

    private volatile boolean running;
    private ServerSocket serverSocket;
    private Thread acceptThread;
    private ScheduledExecutorService broadcaster;

    public Server(int port, int tickRate) {
        this.port = port;
        this.tickRate = tickRate;
    }

    public void start() throws IOException {
        if (running) {
            return;
        }

        running = true;
        serverSocket = new ServerSocket(port);

        broadcaster = Executors.newSingleThreadScheduledExecutor();
        long frameMillis = Math.max(1L, 1000L / Math.max(1, tickRate));
        broadcaster.scheduleAtFixedRate(this::broadcastState, frameMillis, frameMillis, TimeUnit.MILLISECONDS);

        acceptThread = new Thread(this::acceptLoop, "server-accept-loop");
        acceptThread.start();

        System.out.println("Server listening on port " + port);
    }

    public void stop() {
        running = false;

        if (broadcaster != null) {
            broadcaster.shutdownNow();
        }

        for (ClientHandler client : clients) {
            client.close();
        }
        clients.clear();

        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
                // Ignore errors while shutting down.
            }
        }

        System.out.println("Server stopped.");
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                socket.setTcpNoDelay(true);

                String playerId = "P" + idSequence.getAndIncrement();
                ClientHandler client = new ClientHandler(playerId, socket);
                clients.add(client);

                // Send assigned player ID to new client.
                client.send(MessageParser.serialize("WELCOME", playerId));

                // Spawn default state so all clients can render the new player immediately.
                playerStates.put(playerId, new PlayerSnapshot(playerId, 0f, 0f, "IDLE"));

                Thread t = new Thread(client, "client-handler-" + playerId);
                t.start();

                System.out.println("Client connected: " + playerId + " from " + socket.getRemoteSocketAddress());
            } catch (IOException e) {
                if (running) {
                    System.err.println("Accept loop error: " + e.getMessage());
                }
            }
        }
    }

    private void broadcastState() {
        if (!running) {
            return;
        }

        String payload = MessageParser.serializeState(playerStates);
        for (ClientHandler client : clients) {
            client.send(payload);
        }
    }

    private void handleClientMessage(String senderId, String rawLine) {
        ParsedMessage message;
        try {
            message = MessageParser.parse(rawLine);
        } catch (RuntimeException parseError) {
            System.err.println("Invalid message from " + senderId + ": " + rawLine);
            return;
        }

        if ("INPUT".equals(message.command())) {
            // Expected format: INPUT|playerId|x|y|movementState
            if (message.paramCount() < 4) {
                return;
            }

            String claimedPlayerId = message.param(0);
            if (!senderId.equals(claimedPlayerId)) {
                // Prevent spoofing another player's data.
                return;
            }

            try {
                float x = Float.parseFloat(message.param(1));
                float y = Float.parseFloat(message.param(2));
                String state = message.param(3);
                playerStates.put(senderId, new PlayerSnapshot(senderId, x, y, state));
            } catch (NumberFormatException ignored) {
                // Ignore malformed numeric payloads.
            }
        }
    }

    private void disconnect(ClientHandler handler) {
        clients.remove(handler);
        playerStates.remove(handler.playerId);
        handler.close();
        System.out.println("Client disconnected: " + handler.playerId);
    }

    private class ClientHandler implements Runnable {
        private final String playerId;
        private final Socket socket;
        private final BufferedReader reader;
        private final PrintWriter writer;

        private volatile boolean connected = true;

        ClientHandler(String playerId, Socket socket) throws IOException {
            this.playerId = playerId;
            this.socket = socket;
            this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            this.writer = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8);
        }

        @Override
        public void run() {
            try {
                String line;
                while (connected && (line = reader.readLine()) != null) {
                    handleClientMessage(playerId, line);
                }
            } catch (IOException ignored) {
                // Socket closed or disconnected.
            } finally {
                disconnect(this);
            }
        }

        void send(String message) {
            if (!connected) {
                return;
            }
            writer.println(message);
            if (writer.checkError()) {
                disconnect(this);
            }
        }

        void close() {
            connected = false;
            try {
                socket.close();
            } catch (IOException ignored) {
                // Ignore errors during close.
            }
        }
    }

    /**
     * Optional standalone entry point for local testing.
     */
    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 7777;
        int tickRate = args.length > 1 ? Integer.parseInt(args[1]) : 20;

        Server server = new Server(port, tickRate);
        server.start();

        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
    }
}

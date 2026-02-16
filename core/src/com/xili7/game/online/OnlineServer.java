package com.xili7.game.online;

import com.xili7.game.online.MessageParser.ParsedMessage;
import com.xili7.game.online.MessageParser.PlayerState;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Dedicated multiplayer server for Flappy Bird Online.
 *
 * Architecture:
 * - Java ServerSocket
 * - one thread per client
 * - plain-text protocol (command|arg1|arg2)
 */
public class OnlineServer {
    private final int port;
    private final AtomicInteger idSequence = new AtomicInteger(1);
    private final Map<String, PlayerState> players = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<ClientHandler> clients = new CopyOnWriteArrayList<>();

    private volatile boolean running;
    private ServerSocket serverSocket;
    private Thread acceptThread;
    private ScheduledExecutorService snapshotScheduler;

    public OnlineServer(int port) {
        this.port = port;
    }

    public synchronized void start() throws IOException {
        if (running) {
            return;
        }

        running = true;
        serverSocket = new ServerSocket(port);

        snapshotScheduler = Executors.newSingleThreadScheduledExecutor();
        snapshotScheduler.scheduleAtFixedRate(this::broadcastSnapshotSafely, 0, 100, TimeUnit.MILLISECONDS);

        acceptThread = new Thread(this::acceptLoop, "online-server-accept");
        acceptThread.start();

        System.out.println("OnlineServer started on port " + port);
    }

    public synchronized void stop() {
        running = false;

        if (snapshotScheduler != null) {
            snapshotScheduler.shutdownNow();
        }

        for (ClientHandler client : clients) {
            client.close();
        }
        clients.clear();
        players.clear();

        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
                // ignored
            }
        }

        if (acceptThread != null) {
            acceptThread.interrupt();
        }
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                socket.setTcpNoDelay(true);

                String playerId = "P" + idSequence.getAndIncrement();
                ClientHandler clientHandler = new ClientHandler(playerId, socket);
                clients.add(clientHandler);
                players.put(playerId, new PlayerState(playerId, 0f, 0f, 0));

                clientHandler.send(MessageParser.welcome(playerId));
                broadcast(MessageParser.state(playerId, 0f, 0f, 0));

                Thread thread = new Thread(clientHandler, "client-" + playerId);
                thread.start();
            } catch (IOException e) {
                if (running) {
                    System.err.println("Accept loop error: " + e.getMessage());
                }
            }
        }
    }

    private void broadcastSnapshotSafely() {
        try {
            List<PlayerState> snapshot = new ArrayList<>(players.values());
            broadcast(MessageParser.bulkState(snapshot));
        } catch (Exception e) {
            System.err.println("Snapshot broadcast error: " + e.getMessage());
        }
    }

    private void broadcast(String message) {
        for (ClientHandler client : clients) {
            client.send(message);
        }
    }

    private void onStateUpdate(PlayerState state) {
        players.put(state.playerId(), state);
        broadcast(MessageParser.state(state.playerId(), state.x(), state.y(), state.score()));
    }

    private void disconnect(ClientHandler clientHandler) {
        clients.remove(clientHandler);
        clientHandler.close();

        players.remove(clientHandler.playerId);
        broadcast(MessageParser.left(clientHandler.playerId));
    }

    private final class ClientHandler implements Runnable {
        private final String playerId;
        private final Socket socket;
        private final BufferedReader reader;
        private final PrintWriter writer;

        private volatile boolean connected = true;

        private ClientHandler(String playerId, Socket socket) throws IOException {
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
                    ParsedMessage message = MessageParser.parse(line);
                    handleMessage(message);
                }
            } catch (Exception e) {
                if (connected) {
                    System.err.println("Client " + playerId + " error: " + e.getMessage());
                }
            } finally {
                disconnect(this);
            }
        }

        private void handleMessage(ParsedMessage message) {
            switch (message.command()) {
                case "JOIN" -> {
                    // JOIN acknowledged by WELCOME sent right after connect.
                }
                case "JUMP" -> broadcast(MessageParser.jump(playerId));
                case "STATE" -> {
                    PlayerState state = MessageParser.parseState(message);
                    onStateUpdate(new PlayerState(playerId, state.x(), state.y(), state.score()));
                }
                default -> {
                    // Ignore unknown commands for forward compatibility.
                }
            }
        }

        private void send(String message) {
            if (!connected) {
                return;
            }
            writer.println(message);
            if (writer.checkError()) {
                disconnect(this);
            }
        }

        private void close() {
            connected = false;
            try {
                socket.close();
            } catch (IOException ignored) {
                // ignored
            }
        }
    }

    public static void main(String[] args) throws Exception {
        int selectedPort = args.length > 0 ? Integer.parseInt(args[0]) : 7777;
        OnlineServer server = new OnlineServer(selectedPort);
        server.start();

        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
    }
}

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
    private final AtomicInteger roomSequence = new AtomicInteger(1);
    private final Map<String, PlayerState> players = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<ClientHandler> clients = new CopyOnWriteArrayList<>();
    private final Map<String, Room> rooms = new ConcurrentHashMap<>();

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
        rooms.clear();

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
            for (Room room : rooms.values()) {
                List<ClientHandler> members = room.membersSnapshot();
                if (members.isEmpty()) {
                    continue;
                }

                List<PlayerState> snapshot = new ArrayList<>(members.size());
                for (ClientHandler member : members) {
                    PlayerState state = players.get(member.playerId);
                    if (state != null) {
                        snapshot.add(state);
                    }
                }
                if (!snapshot.isEmpty()) {
                    String bulkMessage = MessageParser.bulkState(snapshot);
                    for (ClientHandler member : members) {
                        member.send(bulkMessage);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Snapshot broadcast error: " + e.getMessage());
        }
    }

    private void onStateUpdate(ClientHandler clientHandler, PlayerState state) {
        players.put(state.playerId(), state);
        String roomId = clientHandler.roomId;
        if (roomId != null) {
            broadcastToRoom(roomId, MessageParser.state(state.playerId(), state.x(), state.y(), state.score()));
        }
    }

    private void handleCreateRoom(ClientHandler clientHandler) {
        leaveCurrentRoom(clientHandler);

        String roomId = nextRoomId();
        Room room = new Room(roomId);
        room.add(clientHandler);
        rooms.put(roomId, room);
        clientHandler.roomId = roomId;

        clientHandler.send(MessageParser.roomCreated(roomId));
    }

    private void handleJoinRoom(ClientHandler clientHandler, ParsedMessage message) {
        if (message.size() < 1 || message.arg(0).isBlank()) {
            clientHandler.send(MessageParser.serialize("ERROR", "Room ID is required"));
            return;
        }

        String requestedRoomId = message.arg(0).trim().toUpperCase();
        Room room = rooms.get(requestedRoomId);
        if (room == null) {
            clientHandler.send(MessageParser.serialize("ERROR", "Room not found"));
            return;
        }

        leaveCurrentRoom(clientHandler);

        synchronized (room) {
            if (room.size() >= 2) {
                clientHandler.send(MessageParser.serialize("ERROR", "Room is full"));
                return;
            }
            room.add(clientHandler);
            clientHandler.roomId = requestedRoomId;
        }

        clientHandler.send(MessageParser.roomJoined(requestedRoomId));
        maybeStartRoom(room);
    }

    private void maybeStartRoom(Room room) {
        List<ClientHandler> members = room.membersSnapshot();
        if (members.size() == 2) {
            for (ClientHandler member : members) {
                member.send(MessageParser.start());
            }
        }
    }

    private void leaveCurrentRoom(ClientHandler clientHandler) {
        String currentRoomId = clientHandler.roomId;
        if (currentRoomId == null) {
            return;
        }

        Room room = rooms.get(currentRoomId);
        if (room != null) {
            synchronized (room) {
                room.remove(clientHandler);
                if (room.size() == 0) {
                    rooms.remove(currentRoomId);
                }
            }
        }
        clientHandler.roomId = null;
    }

    private void broadcastToRoom(String roomId, String message) {
        Room room = rooms.get(roomId);
        if (room == null) {
            return;
        }

        for (ClientHandler member : room.membersSnapshot()) {
            member.send(message);
        }
    }

    private void disconnect(ClientHandler clientHandler) {
        clients.remove(clientHandler);
        clientHandler.close();

        String roomId = clientHandler.roomId;
        leaveCurrentRoom(clientHandler);

        players.remove(clientHandler.playerId);
        if (roomId != null) {
            broadcastToRoom(roomId, MessageParser.left(clientHandler.playerId));
        }
    }

    private String nextRoomId() {
        return String.format("R%04d", roomSequence.getAndIncrement());
    }

    private static final class Room {
        private final String roomId;
        private final List<ClientHandler> members = new ArrayList<>(2);

        private Room(String roomId) {
            this.roomId = roomId;
        }

        private synchronized void add(ClientHandler clientHandler) {
            members.add(clientHandler);
        }

        private synchronized void remove(ClientHandler clientHandler) {
            members.remove(clientHandler);
        }

        private synchronized int size() {
            return members.size();
        }

        private synchronized List<ClientHandler> membersSnapshot() {
            return List.copyOf(members);
        }
    }

    private final class ClientHandler implements Runnable {
        private final String playerId;
        private final Socket socket;
        private final BufferedReader reader;
        private final PrintWriter writer;

        private volatile boolean connected = true;
        private volatile String roomId;

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
                case "CREATE_ROOM" -> handleCreateRoom(this);
                case "JOIN_ROOM" -> handleJoinRoom(this, message);
                case "JUMP" -> {
                    if (roomId != null) {
                        broadcastToRoom(roomId, MessageParser.jump(playerId));
                    }
                }
                case "STATE" -> {
                    if (roomId != null) {
                        PlayerState state = MessageParser.parseState(message);
                        onStateUpdate(this, new PlayerState(playerId, state.x(), state.y(), state.score()));
                    }
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

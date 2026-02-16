package com.xili7.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Scaling;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.xili7.game.online.OnlineClient;
import com.xili7.game.online.OnlineServer;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.util.Enumeration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class OnlineMenuScreen implements Screen {
    private static final String DEFAULT_HOST = "127.0.0.1";
    private static final int DEFAULT_PORT = 7777;
    private static final int CONNECT_TIMEOUT_MS = 1200;

    private static final Object SERVER_LOCK = new Object();
    private static OnlineServer localServer;

    private final MyGdxGame game;

    private Stage stage;
    private Skin skin;
    private Texture backgroundTexture;
    private OnlineClient onlineClient;

    private Label statusLabel;
    private TextField playerNameField;
    private TextField ipField;
    private TextField roomIdField;
    private TextButton createRoomButton;
    private TextButton joinRoomButton;
    private TextButton backButton;
    private ExecutorService networkExecutor;
    private String connectedHost;

    private interface OnlineAction {
        void run() throws IOException;
    }

    public OnlineMenuScreen(MyGdxGame game) {
        this.game = game;
    }

    @Override
    public void show() {
        stage = new Stage(new ScreenViewport(), game.getBatch());
        skin = UiSkinFactory.createDefaultSkin();
        networkExecutor = Executors.newSingleThreadExecutor();

        backgroundTexture = new Texture("png/stage_sky.png");
        Image background = new Image(backgroundTexture);
        background.setFillParent(true);
        background.setScaling(Scaling.fill);
        stage.addActor(background);

        Table root = new Table();
        root.setFillParent(true);
        root.defaults().pad(10f).width(260f);

        Label title = new Label("ONLINE", skin);
        statusLabel = new Label("Connect and create/join a room", skin);
        playerNameField = new TextField("", skin);
        playerNameField.setMessageText("Player name");
        ipField = new TextField(DEFAULT_HOST, skin);
        ipField.setMessageText("Host IP");
        roomIdField = new TextField("", skin);
        roomIdField.setMessageText("Room ID");

        createRoomButton = new TextButton("Create Room", skin);
        joinRoomButton = new TextButton("Join Room", skin);
        backButton = new TextButton("Back", skin);

        createRoomButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                runOnlineAction(new OnlineAction() {
                    @Override
                    public void run() throws IOException {
                        setStatus("Creating room...");
                        String lanIp = ensureLocalServerRunning();
                        setStatus("Server active at " + lanIp + ":" + DEFAULT_PORT + ". Creating room...");
                        ensureConnected(DEFAULT_HOST);
                        onlineClient.createRoom();
                    }
                });
            }
        });

        joinRoomButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                final String requestedRoomId = roomIdField.getText() == null ? "" : roomIdField.getText().trim();
                final String requestedName = playerNameField.getText() == null ? "" : playerNameField.getText().trim();
                final String hostIp = ipField.getText() == null ? "" : ipField.getText().trim();

                if (requestedName.isEmpty()) {
                    statusLabel.setText("Please enter your player name");
                    return;
                }
                if (hostIp.isEmpty()) {
                    statusLabel.setText("Please enter a host IP");
                    return;
                }
                if (requestedRoomId.isEmpty()) {
                    statusLabel.setText("Please enter a room ID");
                    return;
                }

                runOnlineAction(new OnlineAction() {
                    @Override
                    public void run() throws IOException {
                        if (!isServerReachable(hostIp, DEFAULT_PORT)) {
                            setStatus("Server is not active at " + hostIp + ":" + DEFAULT_PORT);
                            return;
                        }
                        setStatus("Joining as " + requestedName + " in room " + requestedRoomId.toUpperCase() + "...");
                        ensureConnected(hostIp);
                        onlineClient.joinRoom(requestedRoomId);
                    }
                });
            }
        });

        backButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                safeDisconnect();
                game.setScreen(new MainMenuScreen(game));
            }
        });

        root.add(title).row();
        root.add(playerNameField).row();
        root.add(ipField).row();
        root.add(roomIdField).row();
        root.add(createRoomButton).row();
        root.add(joinRoomButton).row();
        root.add(backButton).row();
        root.add(statusLabel).width(340f).row();
        stage.addActor(root);

        Gdx.input.setInputProcessor(stage);
    }

    private void runOnlineAction(OnlineAction action) {
        networkExecutor.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    action.run();
                } catch (IOException e) {
                    setStatus("Connection error: " + e.getMessage());
                } catch (Exception e) {
                    setStatus("Online error: " + e.getMessage());
                }
            }
        });
    }

    private void ensureConnected(String host) throws IOException {
        synchronized (this) {
            if (onlineClient != null && onlineClient.isConnected() && host.equals(connectedHost)) {
                return;
            }
            safeDisconnect();

            onlineClient = new OnlineClient(host, DEFAULT_PORT);
            connectedHost = host;
            onlineClient.setListener(new OnlineClient.Listener() {
                @Override
                public void onConnected(String playerId) {
                    setStatus("Connected as " + playerId + " on " + host);
                }

                @Override
                public void onRoomCreated(String roomId) {
                    moveToLobby(roomId);
                }

                @Override
                public void onRoomJoined(String roomId) {
                    moveToLobby(roomId);
                }

                @Override
                public void onServerError(String errorMessage) {
                    setStatus("Server error: " + errorMessage);
                }

                @Override
                public void onError(Exception exception) {
                    setStatus("Network error: " + exception.getMessage());
                }

                @Override
                public void onDisconnected() {
                    setStatus("Disconnected");
                }
            });
            onlineClient.connect();
        }
    }

    private String ensureLocalServerRunning() throws IOException {
        synchronized (SERVER_LOCK) {
            if (localServer == null) {
                localServer = new OnlineServer(DEFAULT_PORT);
                localServer.start();
            }
            return detectLanIp();
        }
    }

    private String detectLanIp() {
        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = networkInterfaces.nextElement();
                if (!networkInterface.isUp() || networkInterface.isLoopback()) {
                    continue;
                }
                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (address instanceof Inet4Address && !address.isLoopbackAddress()) {
                        return address.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {
            // Use fallback.
        }
        return DEFAULT_HOST;
    }

    private boolean isServerReachable(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private void setStatus(String status) {
        Gdx.app.postRunnable(new Runnable() {
            @Override
            public void run() {
                statusLabel.setText(status);
            }
        });
    }

    private void moveToLobby(String roomId) {
        Gdx.app.postRunnable(new Runnable() {
            @Override
            public void run() {
                game.setScreen(new LobbyScreen(game, onlineClient, roomId));
            }
        });
    }

    private void safeDisconnect() {
        if (onlineClient != null) {
            onlineClient.disconnect();
            onlineClient = null;
            connectedHost = null;
        }
    }

    @Override
    public void render(float delta) {
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            safeDisconnect();
            game.setScreen(new MainMenuScreen(game));
            return;
        }

        Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        stage.act(delta);
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void pause() {
    }

    @Override
    public void resume() {
    }

    @Override
    public void hide() {
        Gdx.input.setInputProcessor(null);
    }

    @Override
    public void dispose() {
        if (stage != null) {
            stage.dispose();
        }
        if (skin != null) {
            skin.dispose();
        }
        if (backgroundTexture != null) {
            backgroundTexture.dispose();
        }
        if (networkExecutor != null) {
            networkExecutor.shutdownNow();
        }
    }
}

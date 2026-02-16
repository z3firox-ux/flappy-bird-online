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

import java.io.IOException;

public class OnlineMenuScreen implements Screen {
    private static final String DEFAULT_HOST = "127.0.0.1";
    private static final int DEFAULT_PORT = 7777;

    private final MyGdxGame game;

    private Stage stage;
    private Skin skin;
    private Texture backgroundTexture;
    private OnlineClient onlineClient;

    private Label statusLabel;
    private TextField roomIdField;
    private TextButton createRoomButton;
    private TextButton joinRoomButton;
    private TextButton backButton;

    public OnlineMenuScreen(MyGdxGame game) {
        this.game = game;
    }

    @Override
    public void show() {
        stage = new Stage(new ScreenViewport(), game.getBatch());
        skin = UiSkinFactory.createDefaultSkin();

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
        roomIdField = new TextField("", skin);
        roomIdField.setMessageText("Room ID");

        createRoomButton = new TextButton("Create Room", skin);
        joinRoomButton = new TextButton("Join Room", skin);
        backButton = new TextButton("Back", skin);

        createRoomButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                runOnlineAction(new Runnable() {
                    @Override
                    public void run() {
                        statusLabel.setText("Creating room...");
                        onlineClient.createRoom();
                    }
                });
            }
        });

        joinRoomButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                final String requestedRoomId = roomIdField.getText() == null ? "" : roomIdField.getText().trim();
                if (requestedRoomId.isEmpty()) {
                    statusLabel.setText("Please enter a room ID");
                    return;
                }

                runOnlineAction(new Runnable() {
                    @Override
                    public void run() {
                        statusLabel.setText("Joining room " + requestedRoomId.toUpperCase() + "...");
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
        root.add(roomIdField).row();
        root.add(createRoomButton).row();
        root.add(joinRoomButton).row();
        root.add(backButton).row();
        root.add(statusLabel).width(340f).row();
        stage.addActor(root);

        Gdx.input.setInputProcessor(stage);
    }

    private void runOnlineAction(Runnable action) {
        try {
            ensureConnected();
            action.run();
        } catch (IOException e) {
            statusLabel.setText("Connection error: " + e.getMessage());
        }
    }

    private void ensureConnected() throws IOException {
        if (onlineClient != null && onlineClient.isConnected()) {
            return;
        }

        onlineClient = new OnlineClient(DEFAULT_HOST, DEFAULT_PORT);
        onlineClient.setListener(new OnlineClient.Listener() {
            @Override
            public void onConnected(String playerId) {
                Gdx.app.postRunnable(new Runnable() {
                    @Override
                    public void run() {
                        statusLabel.setText("Connected as " + playerId);
                    }
                });
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
                Gdx.app.postRunnable(new Runnable() {
                    @Override
                    public void run() {
                        statusLabel.setText("Server error: " + errorMessage);
                    }
                });
            }

            @Override
            public void onError(Exception exception) {
                Gdx.app.postRunnable(new Runnable() {
                    @Override
                    public void run() {
                        statusLabel.setText("Network error: " + exception.getMessage());
                    }
                });
            }

            @Override
            public void onDisconnected() {
                Gdx.app.postRunnable(new Runnable() {
                    @Override
                    public void run() {
                        statusLabel.setText("Disconnected");
                    }
                });
            }
        });
        onlineClient.connect();
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
    }
}

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
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Scaling;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.xili7.game.online.OnlineClient;

public class LobbyScreen implements Screen {
    private final MyGdxGame game;
    private final OnlineClient onlineClient;
    private final String roomId;
    private final String hostIp;

    private Stage stage;
    private Skin skin;
    private Texture backgroundTexture;
    private Label statusLabel;

    public LobbyScreen(MyGdxGame game, OnlineClient onlineClient, String roomId, String hostIp) {
        this.game = game;
        this.onlineClient = onlineClient;
        this.roomId = roomId;
        this.hostIp = hostIp;
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
        root.defaults().pad(10f);

        Label roomLabel = new Label("Room: " + roomId, skin);
        Label hostIpLabel = new Label(formatHostIp(hostIp), skin);
        statusLabel = new Label("Waiting for player...", skin);
        TextButton cancelButton = new TextButton("Cancel", skin);

        cancelButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                onlineClient.disconnect();
                game.setScreen(new OnlineMenuScreen(game));
            }
        });

        root.add(roomLabel).row();
        root.add(hostIpLabel).row();
        root.add(statusLabel).row();
        root.add(cancelButton).width(220f).row();
        stage.addActor(root);

        onlineClient.setListener(new OnlineClient.Listener() {
            @Override
            public void onStart() {
                Gdx.app.postRunnable(new Runnable() {
                    @Override
                    public void run() {
                        game.setScreen(new GameScreen(game, onlineClient));
                    }
                });
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
            public void onDisconnected() {
                Gdx.app.postRunnable(new Runnable() {
                    @Override
                    public void run() {
                        statusLabel.setText("Disconnected from server");
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
        });

        Gdx.input.setInputProcessor(stage);
    }

    @Override
    public void render(float delta) {
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            onlineClient.disconnect();
            game.setScreen(new OnlineMenuScreen(game));
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

    private String formatHostIp(String ip) {
        if (ip == null || ip.trim().isEmpty()) {
            return "Host IP: unavailable";
        }
        return "Host IP: " + ip;
    }
}

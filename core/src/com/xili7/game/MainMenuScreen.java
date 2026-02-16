package com.xili7.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Scaling;
import com.badlogic.gdx.utils.viewport.ScreenViewport;

public class MainMenuScreen implements Screen {
    private final MyGdxGame game;
    private Stage stage;
    private Skin skin;

    private Texture backgroundTexture;
    private Texture titleTexture;
    private Texture playTexture;
    private Texture onlineTexture;
    private Texture optionsTexture;

    public MainMenuScreen(MyGdxGame game) {
        this.game = game;
    }

    @Override
    public void show() {
        game.applyMusicState();

        stage = new Stage(new ScreenViewport(), game.getBatch());
        skin = UiSkinFactory.createDefaultSkin();

        backgroundTexture = new Texture("png/stage_sky.png");
        titleTexture = new Texture("png/title.png");
        playTexture = new Texture("png/playbuttom.png");
        onlineTexture = new Texture("png/online.png");
        optionsTexture = new Texture("png/options.png");

        Image background = new Image(backgroundTexture);
        background.setFillParent(true);
        background.setScaling(Scaling.fill);
        stage.addActor(background);

        Table root = new Table();
        root.setFillParent(true);
        root.center();
        root.defaults().pad(10f);

        Image title = new Image(titleTexture);
        title.setScaling(Scaling.fit);

        ImageButton playButton = createMenuButton(playTexture);
        ImageButton onlineButton = createMenuButton(onlineTexture);
        ImageButton optionsButton = createMenuButton(optionsTexture);
        Label onlineStatus = new Label("", skin);

        playButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                game.setScreen(new GameScreen(game));
            }
        });

        onlineButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                onlineStatus.setText("ONLINE (COMING SOON)");
            }
        });

        optionsButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                game.setScreen(new OptionsScreen(game, new Runnable() {
                    @Override
                    public void run() {
                        game.setScreen(new MainMenuScreen(game));
                    }
                }));
            }
        });

        root.add(title)
            .size(titleTexture.getWidth(), titleTexture.getHeight())
            .padBottom(26f)
            .row();
        root.add(playButton).size(playTexture.getWidth(), playTexture.getHeight()).row();
        root.add(onlineButton).size(onlineTexture.getWidth(), onlineTexture.getHeight()).row();
        root.add(optionsButton).size(optionsTexture.getWidth(), optionsTexture.getHeight()).row();
        root.add(onlineStatus).padTop(8f).row();

        stage.addActor(root);
        Gdx.input.setInputProcessor(stage);
    }

    private ImageButton createMenuButton(Texture texture) {
        TextureRegionDrawable drawable = new TextureRegionDrawable(new TextureRegion(texture));
        ImageButton.ImageButtonStyle style = new ImageButton.ImageButtonStyle();
        style.imageUp = drawable;
        style.imageDown = drawable;
        return new ImageButton(style);
    }

    @Override
    public void render(float delta) {
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            Gdx.app.exit();
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
        if (titleTexture != null) {
            titleTexture.dispose();
        }
        if (playTexture != null) {
            playTexture.dispose();
        }
        if (onlineTexture != null) {
            onlineTexture.dispose();
        }
        if (optionsTexture != null) {
            optionsTexture.dispose();
        }
    }
}

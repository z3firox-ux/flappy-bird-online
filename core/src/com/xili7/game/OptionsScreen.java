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
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Scaling;
import com.badlogic.gdx.utils.viewport.ScreenViewport;

public class OptionsScreen implements Screen {
    private final MyGdxGame game;
    private final Runnable onBack;

    private Stage stage;
    private Skin skin;

    private Texture backgroundTexture;
    private Texture backTexture;

    private Table table;
    private Slider volumeSlider;
    private Label titleLabel;
    private Label volumeLabel;
    private Label valueLabel;
    private TextButton muteButton;
    private ImageButton backButton;

    public OptionsScreen(MyGdxGame game, Runnable onBack) {
        this.game = game;
        this.onBack = onBack;
    }

    @Override
    public void show() {
        game.applyMusicState();

        stage = new Stage(new ScreenViewport(), game.getBatch());
        skin = UiSkinFactory.createDefaultSkin();

        backgroundTexture = new Texture("png/stage_sky.png");
        backTexture = new Texture("png/ok.png");

        Image background = new Image(backgroundTexture);
        background.setFillParent(true);
        background.setScaling(Scaling.fill);
        stage.addActor(background);

        table = new Table();
        table.setFillParent(true);
        table.center();

        titleLabel = new Label("OPTIONS", skin);
        volumeLabel = new Label("VOLUME", skin);
        valueLabel = new Label(String.format("%.2f", game.getMusicVolume()), skin);

        volumeSlider = new Slider(0f, 1f, 0.01f, false, skin);
        volumeSlider.setValue(game.getMusicVolume());

        muteButton = new TextButton(game.isMuted ? "UNMUTE" : "MUTE", skin);
        backButton = createImageButton(backTexture);

        volumeSlider.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                float value = volumeSlider.getValue();
                game.setMusicVolume(value);
                valueLabel.setText(String.format("%.2f", value));
            }
        });

        muteButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                game.toggleMute();
                muteButton.setText(game.isMuted ? "UNMUTE" : "MUTE");
            }
        });

        backButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                if (onBack != null) {
                    onBack.run();
                }
            }
        });

        updateLayout();
        stage.addActor(table);
        Gdx.input.setInputProcessor(stage);
    }

    private void updateLayout() {
        float viewportWidth = stage.getViewport().getWorldWidth();
        float viewportHeight = stage.getViewport().getWorldHeight();

        float[] backSize = fitSize(backTexture, viewportWidth * 0.36f, viewportHeight * 0.1f);
        float sliderWidth = Math.min(viewportWidth * 0.72f, 420f);
        float sliderHeight = Math.max(46f, viewportHeight * 0.08f);

        backButton.setSize(backSize[0], backSize[1]);

        table.clearChildren();
        table.defaults().center().pad(Math.max(8f, viewportHeight * 0.015f));

        table.add(titleLabel).colspan(2).padBottom(Math.max(16f, viewportHeight * 0.03f)).row();
        table.add(volumeLabel).left().padRight(24f);
        table.add(valueLabel).right().row();
        table.add(volumeSlider).colspan(2).size(sliderWidth, sliderHeight).padTop(4f).row();
        table.add(muteButton).colspan(2).width(Math.min(viewportWidth * 0.4f, 260f)).padTop(12f).row();
        table.add(backButton).colspan(2).size(backSize[0], backSize[1]).padTop(Math.max(14f, viewportHeight * 0.025f));
    }

    private float[] fitSize(Texture texture, float maxWidth, float maxHeight) {
        float scale = Math.min(maxWidth / texture.getWidth(), maxHeight / texture.getHeight());
        scale = Math.min(scale, 1f);
        return new float[]{texture.getWidth() * scale, texture.getHeight() * scale};
    }

    private ImageButton createImageButton(Texture texture) {
        TextureRegionDrawable drawable = new TextureRegionDrawable(new TextureRegion(texture));
        ImageButton.ImageButtonStyle style = new ImageButton.ImageButtonStyle();
        style.imageUp = drawable;
        style.imageDown = drawable;

        ImageButton button = new ImageButton(style);
        button.getImage().setScaling(Scaling.fit);
        return button;
    }

    @Override
    public void render(float delta) {
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            if (onBack != null) {
                onBack.run();
            }
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
        updateLayout();
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
        if (backTexture != null) {
            backTexture.dispose();
        }
    }
}

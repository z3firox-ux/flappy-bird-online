package com.xili7.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.viewport.ScreenViewport;

public class OptionsScreen implements Screen {
    private final MyGdxGame game;

    private Stage stage;
    private Skin skin;

    public OptionsScreen(MyGdxGame game) {
        this.game = game;
    }

    @Override
    public void show() {
        stage = new Stage(new ScreenViewport());
        skin = createSkin();

        Table table = new Table();
        table.setFillParent(true);
        table.defaults().pad(10f);

        Label title = new Label("OPCIONES", skin);

        final CheckBox musicEnabledCheckBox = new CheckBox(" Musica activada", skin);
        musicEnabledCheckBox.setChecked(game.isMusicEnabled());

        final Slider musicVolumeSlider = new Slider(0f, 1f, 0.01f, false, skin);
        musicVolumeSlider.setValue(game.getMusicVolume());

        final Label volumeValueLabel = new Label(String.format("Volumen: %.2f", game.getMusicVolume()), skin);
        final TextButton backButton = new TextButton("VOLVER", skin);

        musicEnabledCheckBox.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                game.setMusicEnabled(musicEnabledCheckBox.isChecked());
            }
        });

        musicVolumeSlider.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                float volume = musicVolumeSlider.getValue();
                game.setMusicVolume(volume);
                volumeValueLabel.setText(String.format("Volumen: %.2f", volume));
            }
        });

        backButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                game.setScreen(new MainScreen(game));
            }
        });

        table.add(title).colspan(2).center().row();
        table.add(musicEnabledCheckBox).colspan(2).left().row();
        table.add(new Label("Musica de fondo", skin)).left();
        table.add(musicVolumeSlider).width(250f).row();
        table.add(volumeValueLabel).colspan(2).left().row();
        table.add(backButton).colspan(2).width(180f).padTop(30f);

        stage.addActor(table);
        Gdx.input.setInputProcessor(stage);
    }

    private Skin createSkin() {
        Skin uiSkin = new Skin();

        BitmapFont font = new BitmapFont();
        uiSkin.add("default", font);

        Texture whiteTexture = createTexture(Color.WHITE);
        Texture grayTexture = createTexture(new Color(0.25f, 0.25f, 0.25f, 1f));
        Texture greenTexture = createTexture(new Color(0.2f, 0.65f, 0.3f, 1f));

        uiSkin.add("white", whiteTexture);
        uiSkin.add("gray", grayTexture);
        uiSkin.add("green", greenTexture);

        Label.LabelStyle labelStyle = new Label.LabelStyle();
        labelStyle.font = font;
        labelStyle.fontColor = Color.WHITE;
        uiSkin.add("default", labelStyle);

        TextButton.TextButtonStyle textButtonStyle = new TextButton.TextButtonStyle();
        textButtonStyle.font = font;
        textButtonStyle.fontColor = Color.WHITE;
        textButtonStyle.up = new TextureRegionDrawable(new TextureRegion(grayTexture));
        textButtonStyle.down = new TextureRegionDrawable(new TextureRegion(whiteTexture));
        uiSkin.add("default", textButtonStyle);

        CheckBox.CheckBoxStyle checkBoxStyle = new CheckBox.CheckBoxStyle();
        checkBoxStyle.checkboxOff = new TextureRegionDrawable(new TextureRegion(grayTexture));
        checkBoxStyle.checkboxOn = new TextureRegionDrawable(new TextureRegion(greenTexture));
        checkBoxStyle.font = font;
        checkBoxStyle.fontColor = Color.WHITE;
        uiSkin.add("default", checkBoxStyle);

        Slider.SliderStyle sliderStyle = new Slider.SliderStyle();
        sliderStyle.background = new TextureRegionDrawable(new TextureRegion(grayTexture));
        sliderStyle.knob = new TextureRegionDrawable(new TextureRegion(whiteTexture));
        uiSkin.add("default-horizontal", sliderStyle);

        return uiSkin;
    }

    private Texture createTexture(Color color) {
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(color);
        pixmap.fill();
        Texture texture = new Texture(pixmap);
        pixmap.dispose();
        return texture;
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0.08f, 0.08f, 0.08f, 1f);
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
    }
}

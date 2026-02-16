package com.xili7.game;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;


/**
 * Created by liray on 12/11/2015.
 */
public class MyGdxGame extends Game {
    private SpriteBatch batch;
    private BitmapFont font;
    private Music backgroundMusic;

    @Override
    public void create() {
        batch = new SpriteBatch();
        font = new BitmapFont();

        FileHandle preferredMusic = Gdx.files.internal("sound/Juego35.wav");
        FileHandle fallbackMusic = Gdx.files.internal("png/Juego 35.wav");
        FileHandle selectedMusic = preferredMusic.exists() ? preferredMusic : fallbackMusic;

        backgroundMusic = Gdx.audio.newMusic(selectedMusic);
        backgroundMusic.setLooping(true);
        backgroundMusic.setVolume(0.5f);
        backgroundMusic.play();

        setScreen(new MainScreen(this));
    }

    public SpriteBatch getBatch() {
        return batch;
    }

    public BitmapFont getFont() {
        return font;
    }

    @Override
    public void dispose() {
        super.dispose();
        if (backgroundMusic != null) {
            backgroundMusic.stop();
            backgroundMusic.dispose();
            backgroundMusic = null;
        }

        font.dispose();
        batch.dispose();
    }
}

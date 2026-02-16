package com.xili7.game;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

/**
 * Created by liray on 12/11/2015.
 */
public class MyGdxGame extends Game {
    private static final String PREFS_NAME = "flappy-bird-online";
    private static final String PREF_MUSIC_VOLUME = "music-volume";

    private SpriteBatch batch;
    private BitmapFont font;
    private Music backgroundMusic;
    private Preferences preferences;
    private float musicVolume;

    @Override
    public void create() {
        batch = new SpriteBatch();
        font = new BitmapFont();
        preferences = Gdx.app.getPreferences(PREFS_NAME);
        musicVolume = preferences.getFloat(PREF_MUSIC_VOLUME, 0.5f);

        FileHandle preferredMusic = Gdx.files.internal("sound/Juego35.wav");
        FileHandle fallbackMusic = Gdx.files.internal("png/Juego 35.wav");
        FileHandle selectedMusic = preferredMusic.exists() ? preferredMusic : fallbackMusic;

        backgroundMusic = Gdx.audio.newMusic(selectedMusic);
        backgroundMusic.setLooping(true);
        backgroundMusic.play();
        backgroundMusic.setVolume(musicVolume);

        setScreen(new MainMenuScreen(this));
    }

    public SpriteBatch getBatch() {
        return batch;
    }

    public BitmapFont getFont() {
        return font;
    }

    public void setMusicVolume(float volume) {
        musicVolume = Math.max(0f, Math.min(1f, volume));
        if (backgroundMusic != null) {
            backgroundMusic.setVolume(musicVolume);
        }
        if (preferences != null) {
            preferences.putFloat(PREF_MUSIC_VOLUME, musicVolume);
            preferences.flush();
        }
    }

    public float getMusicVolume() {
        return musicVolume;
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

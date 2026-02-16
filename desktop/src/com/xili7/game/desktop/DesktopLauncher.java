package com.xili7.game.desktop;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.xili7.game.MyGdxGame;

public class DesktopLauncher {
    public static void main(String[] arg) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Flappy Bird");
        config.setWindowedMode(480, 800);
        config.useVsync(true);
        config.setForegroundFPS(60);

        new Lwjgl3Application(new MyGdxGame(), config);
    }
}

package com.xili7.game.desktop;

import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.xili7.game.MyGdxGame;

public class DesktopLauncher {
    private static final int BASE_WIDTH = 800;
    private static final int BASE_HEIGHT = 480;

    public static void main(String[] arg) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Flappy Bird");
        config.setWindowedMode(BASE_WIDTH, BASE_HEIGHT);
        config.setResizable(true);
        config.useVsync(true);
        config.setForegroundFPS(60);

        if (arg != null && arg.length > 0 && "--fullscreen".equalsIgnoreCase(arg[0])) {
            Graphics.DisplayMode displayMode = Lwjgl3ApplicationConfiguration.getDisplayMode();
            config.setFullscreenMode(displayMode);
        }

        new Lwjgl3Application(new MyGdxGame(), config);
    }
}

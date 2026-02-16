package com.xili7.game;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;

import java.util.Random;

public class GameScreen implements Screen {
    private static final int WORLD_HEIGHT = 200;
    private static final int WORLD_WIDTH = 100;
    private static final String PREFS_NAME = "flappy-bird-online";
    private static final String PREF_BEST_SCORE = "best-score";

    private final float pipeSpaceWidth = 4f * WORLD_WIDTH / 6f;
    private final float pipeSpaceHeight = WORLD_HEIGHT / 3f;

    private OrthographicCamera camera;
    private Viewport viewport;
    private SpriteBatch batch;
    private BitmapFont font;
    private GlyphLayout glyphLayout;
    private Preferences preferences;

    private Texture skyTexture;
    private Texture groundTexture;
    private Texture birdTexture;
    private Texture pipeHeadTexture1;
    private Texture pipeHeadTexture2;
    private Texture pipeBodyTexture;

    private Animation<TextureRegion> birdAnimation;

    private float birdX;
    private float birdY;
    private float birdWidth;
    private float birdHeight;
    private float birdVelocity;
    private float birdRotation;

    private float groundOffset;
    private float pipeTimer;
    private float animationTime;

    private Vector2[] pipes;
    private boolean[] scoreCounted;
    private Random random;

    private boolean notReady;
    private boolean gameOver;
    private boolean newBest;

    private int currentScore;
    private int bestScore;

    @Override
    public void show() {
        camera = new OrthographicCamera();
        viewport = new FitViewport(WORLD_WIDTH, WORLD_HEIGHT, camera);
        viewport.apply();
        camera.position.set(WORLD_WIDTH / 2f, WORLD_HEIGHT / 2f, 0);
        camera.update();

        batch = new SpriteBatch();
        font = new BitmapFont();
        font.getData().setScale(0.7f);
        glyphLayout = new GlyphLayout();

        preferences = Gdx.app.getPreferences(PREFS_NAME);
        bestScore = preferences.getInteger(PREF_BEST_SCORE, 0);

        skyTexture = new Texture("png/stage_sky.png");
        groundTexture = new Texture("png/stage_ground.png");
        birdTexture = new Texture(Gdx.files.internal("png/bird.png"));
        pipeHeadTexture1 = new Texture("png/pipe_head_1.png");
        pipeHeadTexture2 = new Texture("png/pipe_head_2.png");
        pipeBodyTexture = new Texture("png/pipe_body.png");

        Array<TextureRegion> birdRegions = new Array<>();
        for (int i = 0; i < 3; i++) {
            birdRegions.add(new TextureRegion(birdTexture, 0, (59 + 11) * i, birdTexture.getWidth(), 59));
        }
        birdAnimation = new Animation<>(1 / 14f, birdRegions, Animation.PlayMode.LOOP_REVERSED);

        birdWidth = 0.15f * WORLD_WIDTH;
        birdHeight = WORLD_HEIGHT / 17f;
        birdX = 0.25f * WORLD_WIDTH;
        birdY = 0.5f * WORLD_HEIGHT;
        birdVelocity = 0;
        birdRotation = 0;

        pipes = new Vector2[4];
        pipes[0] = new Vector2(2f * WORLD_WIDTH, 0.5f * WORLD_HEIGHT);
        scoreCounted = new boolean[4];
        random = new Random();
        for (int i = 1; i < 4; i++) {
            pipes[i] = new Vector2(pipes[i - 1].x + pipeSpaceWidth, randomPipeY());
        }

        notReady = true;
        gameOver = false;
        newBest = false;
        currentScore = 0;
        groundOffset = 0;
        pipeTimer = 0;
        animationTime = 0;
    }

    private float randomPipeY() {
        return (random.nextFloat() * 0.4f + 0.2f) * WORLD_HEIGHT;
    }

    private boolean jumpPressed() {
        return Gdx.input.justTouched()
            || Gdx.input.isKeyJustPressed(Input.Keys.SPACE)
            || Gdx.input.isKeyJustPressed(Input.Keys.ENTER);
    }

    private void update(float delta) {
        animationTime += delta;

        if (gameOver) {
            if (jumpPressed()) {
                ((Game) Gdx.app.getApplicationListener()).setScreen(new GameScreen());
            }
            return;
        }

        if (jumpPressed()) {
            notReady = false;
            birdVelocity = 130;
            birdRotation = 0;
        }

        if (!notReady) {
            pipeTimer += delta;
            while (0.005f < pipeTimer) {
                pipeTimer -= 0.005f;
                for (int i = 0; i < 4; i++) {
                    pipes[i].x -= 0.0025f * WORLD_WIDTH;

                    if (pipes[i].x < -WORLD_WIDTH / 6f) {
                        pipes[i].x = pipes[(i + 3) % 4].x + pipeSpaceWidth;
                        pipes[i].y = randomPipeY();
                        scoreCounted[i] = false;
                    }

                    if (!scoreCounted[i] && pipes[i].x < (birdX + birdWidth / 2f)) {
                        currentScore++;
                        scoreCounted[i] = true;
                    }
                }
            }

            birdVelocity -= 400 * delta;
            birdY += birdVelocity * delta;
            if (birdVelocity < 0) {
                birdRotation = -45;
            }

            checkCollision();

            groundOffset -= WORLD_WIDTH / 20f;
            if (groundOffset <= -WORLD_WIDTH / 20f) {
                groundOffset = 0;
            }
        }
    }

    private void checkCollision() {
        if (birdY >= WORLD_HEIGHT - birdHeight) {
            birdY = WORLD_HEIGHT - birdHeight;
            birdVelocity = 0;
        }

        if (0.15f * WORLD_HEIGHT > birdY) {
            handleGameOver();
            return;
        }

        for (Vector2 pipe : pipes) {
            if (birdX + birdWidth >= pipe.x && birdX < pipe.x + WORLD_WIDTH / 6f) {
                if (birdY < (pipe.y + WORLD_HEIGHT / 30f) || birdY + birdHeight > pipe.y + pipeSpaceHeight) {
                    handleGameOver();
                    return;
                }
            }
        }
    }

    private void handleGameOver() {
        gameOver = true;
        birdRotation = -90;
        birdY = 0.15f * WORLD_HEIGHT;

        if (currentScore > bestScore) {
            bestScore = currentScore;
            newBest = true;
            preferences.putInteger(PREF_BEST_SCORE, bestScore);
            preferences.flush();
        }
    }

    private void drawCenteredText(String text, float y) {
        glyphLayout.setText(font, text);
        float x = (WORLD_WIDTH - glyphLayout.width) / 2f;
        font.draw(batch, glyphLayout, x, y);
    }

    @Override
    public void render(float delta) {
        update(delta);

        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        viewport.apply();
        camera.update();
        batch.setProjectionMatrix(camera.combined);

        batch.begin();

        batch.draw(skyTexture, 0, 0.15f * WORLD_HEIGHT, WORLD_WIDTH, 0.85f * WORLD_HEIGHT);

        for (Vector2 pipe : pipes) {
            batch.draw(pipeHeadTexture2, pipe.x, pipe.y, WORLD_WIDTH / 6f, WORLD_HEIGHT / 30f);
            batch.draw(pipeBodyTexture, pipe.x + (WORLD_WIDTH / 200f), 0.15f * WORLD_HEIGHT, (WORLD_WIDTH / 6f) - (WORLD_WIDTH / 100f), pipe.y - 0.15f * WORLD_HEIGHT);
            batch.draw(pipeBodyTexture, pipe.x + (WORLD_WIDTH / 200f), pipe.y + pipeSpaceHeight + (WORLD_WIDTH / 30f), (WORLD_WIDTH / 6f) - (WORLD_WIDTH / 100f), WORLD_HEIGHT / 2f);
            batch.draw(pipeHeadTexture1, pipe.x, pipe.y + pipeSpaceHeight, WORLD_WIDTH / 6f, WORLD_HEIGHT / 30f);
        }

        TextureRegion birdFrame = birdAnimation.getKeyFrame(animationTime, true);
        batch.draw(birdFrame, birdX, birdY, birdWidth / 2f, birdHeight / 2f, birdWidth, birdHeight, 1f, 1f, birdRotation);

        for (int i = 0; i < 25; i++) {
            batch.draw(groundTexture, groundOffset + i * WORLD_WIDTH / 20f, 0, WORLD_WIDTH / 20f, 0.15f * WORLD_HEIGHT);
        }

        if (gameOver) {
            font.getData().setScale(1.1f);
            drawCenteredText("GAME OVER", 0.65f * WORLD_HEIGHT);
            font.getData().setScale(0.9f);
            drawCenteredText("SCORE: " + currentScore, 0.55f * WORLD_HEIGHT);
            if (newBest) {
                drawCenteredText("NEW BEST!", 0.46f * WORLD_HEIGHT);
            }
            font.getData().setScale(0.7f);
            drawCenteredText("SPACE / ENTER para reiniciar", 0.32f * WORLD_HEIGHT);
        } else {
            drawCenteredText("Score: " + currentScore, 0.92f * WORLD_HEIGHT);
            if (notReady) {
                drawCenteredText("TAP / SPACE", 0.7f * WORLD_HEIGHT);
            }
        }

        batch.end();
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
    }

    @Override
    public void pause() {
    }

    @Override
    public void resume() {
    }

    @Override
    public void hide() {
        dispose();
    }

    @Override
    public void dispose() {
        skyTexture.dispose();
        groundTexture.dispose();
        birdTexture.dispose();
        pipeHeadTexture1.dispose();
        pipeHeadTexture2.dispose();
        pipeBodyTexture.dispose();
        font.dispose();
        batch.dispose();
    }
}

package model.animator;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import data.Constants;
import javafx.geometry.Rectangle2D;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

/**
 * Plays enemy sprite animations. The sheet is swapped per enemy type, frame size is read from the image.
 * 
 * one issue with this class is that its hard to handle assets with different number of sprites that's why in the game it looks like that the enemy is moving(Fixed : I just made all the sprites contains exact fframes) 
 */
public class EnemyAnimator implements Animates {

    private static final int ROWS = 4; // idle, attack, damaged, killed

    public enum EnemyAnimation {// not the ssame with the player animation because the robots has more sprites
        IDLE(0), ATTACKING(1), DAMAGED(2), KILLED(3);

        private final int row;

        EnemyAnimation(int row) {
            this.row = row;
        }
    }

    private final ImageView view;
    private final Map<String, Image> spritePath = new HashMap<>();

    // set per sheet in setSheet()
    private int columns = 7;
    private double frameWidth = 160;
    private double frameHeight = 104;

    private final double idleFrameDuration = 1.0 / 6.0;
    private double frameDuration = idleFrameDuration;

    private int currentFrame = 0;
    private double elapsedTime = 0;
    private EnemyAnimation currentAnimation = EnemyAnimation.IDLE;

    public EnemyAnimator(ImageView view) {
        this.view = view;
        view.setPreserveRatio(true);
        view.setSmooth(false);
        view.setFitHeight(256); // height only, keeps the aspect ratio
    }

    /** Loads the sheet for this enemy. Returns false if it's missing. */
    public boolean setSheet(String spriteKey) {
        if (spriteKey == null) {
            return false;
        }
        String key = spriteKey.toLowerCase(Locale.ROOT);

        String path = Constants.ENEMY_SPRITE_SHEETS.get(key);
        if (path == null) {
            return false;
        }

        Image image = spritePath.get(path);
        if (image == null) {
            var resource = getClass().getResource(path);
            if (resource == null) {
                return false;
            }
            image = new Image(resource.toExternalForm());
            spritePath.put(path, image);
        }

        columns = Constants.ENEMY_SPRITE_COLUMNS.getOrDefault(key, 7);
        frameWidth = image.getWidth() / columns;
        frameHeight = image.getHeight() / ROWS;

        view.setImage(image);
        reset();
        return true;
    }

    /** Plays an animation so it lasts durationSeconds. */
    public void playAnimation(EnemyAnimation animation, double durationSeconds) {
        // skip idle -> idle only, so other animations can replay
        if (animation == EnemyAnimation.IDLE && currentAnimation == EnemyAnimation.IDLE) {
            return;
        }

        currentAnimation = animation;
        currentFrame = 0;
        elapsedTime = 0;

        frameDuration = (animation == EnemyAnimation.IDLE || durationSeconds <= 0)
                ? idleFrameDuration
                : durationSeconds / columns;

        updateViewport();
    }

    @Override
    public void updateAnimation(double deltaSeconds) {
        if (view.getImage() == null) {
            return; // no sheet loaded
        }

        elapsedTime += deltaSeconds;

        while (elapsedTime >= frameDuration) {
            elapsedTime -= frameDuration;

            boolean lastFrame = currentFrame >= columns - 1;

            if (lastFrame && currentAnimation == EnemyAnimation.KILLED) {
                elapsedTime = 0; // stay on the last frame
                return;
            }

            if (lastFrame && currentAnimation != EnemyAnimation.IDLE) {
                playAnimation(EnemyAnimation.IDLE, 0); // back to idle
                return;
            }

            currentFrame = (currentFrame + 1) % columns;
            updateViewport();
        }
    }

    /* updates which part of the spritesheet can be seen */
    private void updateViewport() {
        if (view.getImage() == null) {
            return;
        }
        double x = currentFrame * frameWidth;
        double y = currentAnimation.row * frameHeight;
        view.setViewport(new Rectangle2D(x, y, frameWidth, frameHeight));
    }

    /* resets all parameters of the animations */
    public void reset() {
        currentAnimation = EnemyAnimation.IDLE;
        currentFrame = 0;
        elapsedTime = 0;
        frameDuration = idleFrameDuration;
        updateViewport();
    }
}
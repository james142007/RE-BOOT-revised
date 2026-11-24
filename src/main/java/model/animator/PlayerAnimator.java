package model.animator;

import javafx.geometry.Rectangle2D;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

/** Animation logic of the player, then passed to the battlecontroller. */
public class PlayerAnimator implements Animates {

    // states for what kind of animation to show
    public enum Animation {
        IDLE(0, 7),
        ATTACKING(1, 7),
        DAMAGED(2, 7),
        KILLED(3, 7);

        private final int row;
        private final int frameCount;

        Animation(int row, int frameCount) {
            this.row = row;
            this.frameCount = frameCount;
        }
    }

    private ImageView playerImageView;
    private Image spriteSheet;

    // Timers for the amimation

    private static final int FRAME_WIDTH = 160;
    private static final int CHARACTER_X = 0;
    private static final int CHARACTER_Y = 0;
    private static final int CHARACTER_WIDTH = 160;
    private static final int CHARACTER_HEIGHT = 104;
    private final double idleFrameDuration;
    private double frameDuration;

    private int currentFrame = 0;
    private double elapsedTime = 0;
    private Animation currentAnimation = Animation.IDLE;

    public PlayerAnimator(ImageView playerImageView, double framesPerSecond) {
        this.playerImageView = playerImageView;

        // load image, and if the image cannot be loaded then print the error
        String path = "/assets/player_assets/player_spritesheet.png";

        var resource = getClass().getResource(path);

        if (resource == null) {
            throw new IllegalStateException("Sprite sheet not found: " + path);
        }

        spriteSheet = new Image(resource.toExternalForm());
        this.idleFrameDuration = 1.0 / framesPerSecond;
        this.frameDuration = idleFrameDuration;

        // set the playerys sprite and the scale
        playerImageView.setImage(spriteSheet);
        playerImageView.setPreserveRatio(true);
        playerImageView.setSmooth(false);
        playerImageView.setFitWidth(320);
        playerImageView.setFitHeight(256);
        playerImageView.setViewport(new Rectangle2D(CHARACTER_X, CHARACTER_Y, CHARACTER_WIDTH, CHARACTER_HEIGHT));
    }

    public void animationHelper(Animation animation, double durationSeconds) {
        if (currentAnimation == animation) {
            return;
        }

        currentAnimation = animation;
        currentFrame = 0;
        elapsedTime = 0;
        frameDuration = (animation == Animation.IDLE || durationSeconds <= 0)
                ? idleFrameDuration
                : durationSeconds / animation.frameCount;

        updateViewport();
    }

    public void playerAnimation(Animation animation) {
        if (animation == currentAnimation && animation == Animation.IDLE) {
            return;
        }
        animationHelper(animation, 0);
    }

    @Override
    public void updateAnimation(double deltaSeconds) {
        elapsedTime += deltaSeconds;

        while (elapsedTime >= frameDuration) {
            elapsedTime -= frameDuration;

            boolean lastFrame = currentFrame >= currentAnimation.frameCount - 1;

            if (lastFrame && currentAnimation == Animation.KILLED) {
                elapsedTime = 0; // hold the final frame
                return;
            }
            if (lastFrame && currentAnimation != Animation.IDLE) {
                animationHelper(Animation.IDLE, 0);
                return;
            }
            currentFrame = (currentFrame + 1) % currentAnimation.frameCount;
            updateViewport();
        }
    }

    /* This method changes the sprite row based on the current state */
    private void updateViewport() {
        double x = currentFrame * FRAME_WIDTH + CHARACTER_X;
        double y = currentAnimation.row * 104 + CHARACTER_Y;

        playerImageView.setViewport(new Rectangle2D(x, y, CHARACTER_WIDTH, CHARACTER_HEIGHT));
    }

    public void reset() {
        currentAnimation = Animation.IDLE;
        currentFrame = 0;
        elapsedTime = 0;
        updateViewport();
    }
}

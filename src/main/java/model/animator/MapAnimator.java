package model.animator;


/** Animation logic for the map. */

import javafx.fxml.FXML;
import javafx.scene.image.ImageView;

public class MapAnimator implements Animates {

    @FXML
    private ImageView erasImageView;
    @FXML
    private ImageView particlesImageView;


    // Animation timers
    private double particleTime = 0;
    private double erasTime = 0;

    // Floating animation settings
    private static final double FLOAT_AMPLITUDE = 6.0;//how much the map position change
    private static final double FLOAT_SPEED = 1.2;//speed of floating

    // Particle opacity settings
    private static final double MIN_OPACITY = 0.35;
    private static final double MAX_OPACITY = 0.95;
    private static final double PARTICLE_SPEED = 1.5;

    // Original Y position
    private double originalErasY;
    private boolean initialized = false;



    public MapAnimator( ImageView eras, ImageView particle) {
        this.erasImageView = eras;
        this.particlesImageView = particle;
    }

    @Override
    public void updateAnimation(double deltaSeconds) {
        if (erasImageView == null || particlesImageView == null) {
            return;
        }

        if (!initialized) {
            originalErasY = erasImageView.getLayoutY();// the initializez bool ensures that we get the initial y pos of the map once
            initialized = true;
        }

        animateParticles(deltaSeconds);
        animateEras(deltaSeconds);

    }

    /**
     * Basically adjusts the opacity to create a dim light effect
     */
    private void animateParticles(double deltaSeconds) {

        particleTime += deltaSeconds;

        double opacity = MIN_OPACITY + (MAX_OPACITY - MIN_OPACITY) * (0.5 + 0.5 * Math.sin(particleTime * PARTICLE_SPEED));//sin is useful for the alternating light effect

        particlesImageView.setOpacity(opacity);
    }

    /**
     * Makes the eras slowly float up and down
     */
    private void animateEras(double deltaSeconds) {

        erasTime += deltaSeconds;

        double offset = Math.sin(erasTime * FLOAT_SPEED) * FLOAT_AMPLITUDE; // floating effect

        erasImageView.setLayoutY(originalErasY + offset);
    }
    
}

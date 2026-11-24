package controller;

import javafx.animation.AnimationTimer;
import javafx.stage.Stage;
import model.GameState;
import data.Constants;

/**
 * Main controller of the whole game, takes a stage and has the game loop.
 * @author Felix
 */

public class GameEngine {

    ///Controllers
    private final GameStateController gameStateController;
    private final SceneNavigator sceneNavigator;

    /// game loop
    private final AnimationTimer gameTimer;
    private long lastTime;
    private long frameCount;
    private long fpsTimer;

    /// update parameters
    // for splashscreen to MainMenu
    private double splashTimer;
    private double loadingTimer;

    // tracks if the pause menu is shown to avoid updating every frame that causes
    // the unclickable bug
    private boolean pauseShown;

    private GameState lastState;

    ///constructor
    public GameEngine(Stage stage) {
        // set up the screen
        stage.setTitle("Re:Boot");
        stage.show();

        // Game loop
        lastTime = -1;
        frameCount = 0;
        fpsTimer = 0;
        splashTimer = 0.0;
        loadingTimer = 0;
        pauseShown = false;

        gameStateController = new GameStateController();
        sceneNavigator = new SceneNavigator(stage, gameStateController);

        changeSceneByState();
        lastState = gameStateController.getState();

        gameTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (lastTime < 0) {
                    lastTime = now;
                    return; // skip first frame, gathers enough delta before updating
                }

                double deltaSeconds = (now - lastTime) / 1_000_000_000.0;
                lastTime = now;

                if (now - fpsTimer >= 1_000_000_000) {
                    // System.out.println("FPS: " + frameCount);
                    frameCount = 0;
                    fpsTimer = now;
                }

                frameCount++;
                if (!Constants.paused) {
                    if (pauseShown) {
                        sceneNavigator.hideOverlay();
                        pauseShown = false;
                    }
                    update(deltaSeconds);

                } else if (!pauseShown) {
                    pauseShown = true;
                    sceneNavigator.showPause();
                }

            }
        };

    }

    // runs the thread
    // starts the game
    // call when the game is opened
    /**
     * 
     */
    public void startGame() {
        gameTimer.start();
    }

    // Will stop the thread from running
    // stops the whole game
    // call when the exit confirm button is pressed
    public void stopGame() {
        gameTimer.stop();
    }

    // connector between sceneNavigator and GameStateController
    public void changeSceneByState() {
        sceneNavigator.setScene(gameStateController.getState());
    }

    ///UPDATE
    // NOTE: This is the main update method of the whole game
    public void update(double deltaSeconds) {
        gameStateController.update(deltaSeconds);
        switch (gameStateController.getState()) {
            case SPLASH_SCREEN:
                updateSplash(deltaSeconds);
                break;
            case LOADING_SCREEN:
                updateLoading(deltaSeconds);
                break;
            case PAUSED_STATE:
                break;
            default:
                break;
        }
        Constants.currentState = gameStateController.getState();
        if (Constants.currentState != lastState) {
            lastState = Constants.currentState;
            changeSceneByState();
        }

    }

    // updates individual states
    public void updateSplash(double deltaSeconds) {
        // if enough time elapsed, transition to main.java.Main menu
        splashTimer += deltaSeconds;
        if (splashTimer >= Constants.SPLASH_SCREEN_TIMER) {
            gameStateController.setState(GameState.MAIN_MENU);

            System.out.println("Updated");
        }

    }

    public void updateLoading(double deltaSeconds) {
        loadingTimer += deltaSeconds;
        if (loadingTimer >= 2.0) {
            gameStateController.setState(gameStateController.getNextState());
            loadingTimer = 0;
            System.out.println("Updated");
        }
    }
}

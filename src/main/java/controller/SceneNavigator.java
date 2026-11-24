package controller;

import data.AudioRegistry;
import data.GameSession;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.NumberBinding;
import javafx.event.EventHandler;
import javafx.fxml.FXMLLoader;
import javafx.scene.Group;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import model.GameState;

/**
 * Main scene controller of the game, lets the game navigate between scenes.
 * 
 * @author Felix
 */

public class SceneNavigator {
    private static final double DESIGN_WIDTH = 1920;
    private static final double DESIGN_HEIGHT = 1080;

    private final Stage stage;

    private final GameStateController stateController;
    private GameRootController gameRootController;

    // fxml controllers

    public SceneNavigator(Stage stage, GameStateController stateController) {
        this.stage = stage;
        this.stateController = stateController;
        stage.setWidth(1920);
        stage.setHeight(1080);

        initializeRoot();
    }

    // initialize the properties of the main game root
    public void initializeRoot() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/fxml/gameRoot.fxml"));

            Region root = loader.load();

            gameRootController = loader.getController();

            // lock the game root to the design size so every screen is laid out as designed
            root.setMinSize(DESIGN_WIDTH, DESIGN_HEIGHT);
            root.setPrefSize(DESIGN_WIDTH, DESIGN_HEIGHT);
            root.setMaxSize(DESIGN_WIDTH, DESIGN_HEIGHT);

            // Group + StackPane = the scaled game stays centered, black bars fill any
            // leftover space
            StackPane viewport = new StackPane(new Group(root));
            viewport.setStyle("-fx-background-color: black;");

            Scene scene = new Scene(viewport, DESIGN_WIDTH, DESIGN_HEIGHT);

            // shrink (or grow) the game to fit the window while keeping the 16:9 shape
            NumberBinding scale = Bindings.min(
                    scene.widthProperty().divide(DESIGN_WIDTH),
                    scene.heightProperty().divide(DESIGN_HEIGHT));
            root.scaleXProperty().bind(scale);
            root.scaleYProperty().bind(scale);

            stage.setScene(scene);
            stage.setMaximized(true);
            stage.setResizable(false);

        } catch (Exception e) {

            e.printStackTrace();

        }
    }

    /**
     * One place that decides which music / loading sound goes with which screen, so
     * music follows the scene and the old track never lingers.
     * Tracks are only requested here, AudioController crossfades and never restarts
     * one that's already playing.
     */
    private void updateAudioFor(GameState state) {
        AudioController audio = AudioController.getInstance();
        if (state != GameState.LOADING_SCREEN) {
            audio.stopSfx(AudioRegistry.SFX_LOADING); // never let the loading sound bleed into the next screen
        }
        switch (state) {
            case SPLASH_SCREEN:
                audio.stopMusic();
                break;
            case LOADING_SCREEN:
                if (AudioRegistry.STOP_MUSIC_ON_LOADING) {
                    audio.stopMusic();
                }
                audio.playSfx(AudioRegistry.SFX_LOADING);
                break;
            case MAIN_MENU:
                audio.playMusic(AudioRegistry.BGM_MAIN_MENU);
                break;
            case MAP_SATE:
                audio.playMusic(AudioRegistry.BGM_MAP);
                break;
            case PROLOGUE:
            case TUTORIAL:
                // the tutorial scene switches to the battle track itself when the battle starts
                audio.playMusic(AudioRegistry.BGM_TUTORIAL_DIALOGUE);
                break;
            case GAMEPLAY:
                GameSession session = GameSession.current();
                audio.playMusic(session == null ? null
                        : AudioRegistry.eraMusicKey(session.getPlayer().getCurrentEraNumber()));
                break;
            default:
                break; // PAUSED_STATE, EPILOGUE: leave the music as it is
        }
    }

    public void setScene(GameState currentState) {
        updateAudioFor(currentState);

        switch (currentState) {
            case SPLASH_SCREEN:
                navigateToSplash();
                break;
            case LOADING_SCREEN:
                loading();
                break;
            case MAIN_MENU:
                navigateToMainMenu();
                break;
            case PROLOGUE:
                navigateToPrologue();
                break;
            case TUTORIAL:
                navigateToTutorial();
                break;
            case MAP_SATE:
                navigateToMap();
                break;
            case GAMEPLAY:
                navigateToGameplay();
                break;
            case PAUSED_STATE:
                showPause();
                break;
            case EPILOGUE:
                navigateToPrologue();
                break;
            default:
                break;
        }
    }

    // shows a specific scene
    public void navigateToSplash() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/fxml/splashScreen.fxml"));
            Parent root = loader.load();

            gameRootController.setContent(root);
        } catch (Exception e) {
            e.printStackTrace();// define an exception, this should throw an error pane
        }
    }

    // paused
    public void showPause() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/fxml/pause.fxml"));
            PauseController pauseController = new PauseController(stateController);
            loader.setController(pauseController);

            Parent root = loader.load();
            AudioController.getInstance().bindAllButtons(root, false); // click sound on every pause menu button
            gameRootController.showOverlay(root, true);// true = dim the screen behind the pause menu
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void showHowToPlay() {
        try {

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/fxml/howtoplay.fxml"));
            HowToPlayController howToPlayController = new HowToPlayController(this);
            loader.setController(howToPlayController);

            Parent root = loader.load();
            AudioController.getInstance().bindAllButtons(root, false); // click sound on every pause menu button
            gameRootController.showOverlay(root, true);// true = dim the screen behind the pause menu
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Warning shown when a player makes a new game and there are previous sessions,
     * the save might get overwritten.
     */
    public void showNewGameWarning() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/fxml/newGameWarning.fxml"));
            NewGameController newGameController = new NewGameController(this, stateController);
            loader.setController(newGameController);

            Parent root = loader.load();
            gameRootController.showOverlay(root);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** Game won overlay */
    public void showGameWon() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/fxml/gameWon.fxml"));
            GameWonController gameWonController = new GameWonController(this, stateController);
            loader.setController(gameWonController);

            Parent root = loader.load();
            gameRootController.showOverlay(root);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** Game lost overlay */
    public void showGameLost() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/fxml/gameLost.fxml"));
            GameOverScreenController controller = new GameOverScreenController(this, stateController);
            loader.setController(controller);

            Parent root = loader.load();
            gameRootController.showOverlay(root);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Life lost overlay: shown when the player loses a life but still has lives
     * left. Background is dimmed (and blocked) like the pause menu.
     */
    public void showLifeLost() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/fxml/lifeLost.fxml"));
            LifeLostController lifeLostController = new LifeLostController(this, stateController);
            loader.setController(lifeLostController);

            Parent root = loader.load();
            gameRootController.showOverlay(root, true); // true = dim and block the battle screen behind it
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // loading screem
    public void loading() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/fxml/loading.fxml"));
            Parent root = loader.load();

            gameRootController.setContent(root);
        } catch (Exception e) {
            e.printStackTrace();// define an exception, this should throw an error pane
        }
    }

    public void navigateToMainMenu() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/fxml/mainMenu.fxml"));
            MainMenuController controller = new MainMenuController(stateController, this);

            loader.setController(controller);
            Parent root = loader.load();
            AudioController.getInstance().bindAllButtons(root, true); // click + hover on every main menu button
            gameRootController.setContent(root);

        } catch (Exception e) {
            e.printStackTrace();// define an exception, this should throw an error pane
        }
    }

    public void navigateToPrologue() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/fxml/visualNovel.fxml"));
            VisualNovelController controller = new VisualNovelController(stateController, this);
            stateController.setVisualNovelController(controller);
            loader.setController(controller);
            Parent root = loader.load();
            gameRootController.setContent(root);

            root.setFocusTraversable(true);
            root.setOnKeyPressed(new EventHandler<KeyEvent>() {
                @Override
                public void handle(KeyEvent e) {
                    if (e.getCode() == KeyCode.ESCAPE) {
                        controller.pause();
                    }
                }
            });

        } catch (Exception e) {
            e.printStackTrace();// define an exception, this should throw an error pane
        }
    }

    public void navigateToTutorial() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/fxml/tutorial.fxml"));
            TutorialController controller = new TutorialController(stateController);
            loader.setController(controller);
            stateController.setTutorialController(controller);
            Parent root = loader.load();
            gameRootController.setContent(root);

            root.setFocusTraversable(true);
            root.setOnKeyPressed(new EventHandler<KeyEvent>() {
                @Override
                public void handle(KeyEvent e) {
                    if (e.getCode() == KeyCode.ESCAPE) {
                        controller.pause();
                    }
                }
            });

        } catch (Exception e) {
            e.printStackTrace();// define an exception, this should throw an error pane(planned)
        }
    }

    // the main game: era -> encounters -> battles
    public void navigateToGameplay() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/fxml/gameplay.fxml"));
            GameplayController controller = new GameplayController(stateController, this);
            loader.setController(controller);
            stateController.setGameplayController(controller);
            Parent root = loader.load();
            gameRootController.setContent(root);

            root.setFocusTraversable(true);
            root.setOnKeyPressed(new EventHandler<KeyEvent>() {
                @Override
                public void handle(KeyEvent e) {
                    if (e.getCode() == KeyCode.ESCAPE) {
                        controller.pause();
                    }
                }
            });

        } catch (Exception e) {
            e.printStackTrace();// define an exception, this should throw an error pane
        }
    }

    public void navigateToMap() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/fxml/mainMap.fxml"));
            MapController controller = new MapController(stateController, this);
            loader.setController(controller);
            stateController.setMapController(controller);
            Parent root = loader.load();

            gameRootController.setContent(root);
            root.setFocusTraversable(true);
            root.setOnKeyPressed(new EventHandler<KeyEvent>() {
                @Override
                public void handle(KeyEvent e) {
                    if (e.getCode() == KeyCode.ESCAPE) {
                        // controller.pause();
                    }
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /* Hides the overlay that is imbedded to the root of the game */
    public void hideOverlay() {
        gameRootController.hideOverlay();
    }
}
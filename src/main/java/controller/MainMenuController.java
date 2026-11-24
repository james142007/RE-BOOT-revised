package controller;

import java.util.Optional;

import data.Constants;
import data.GameSession;
import data.SaveManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import model.GameState;

/**
 * Main menu controller, handles switching screens like going to the main game.
 * @author Felix
 */
public class MainMenuController {
    private Button exitButton;
    private Button tutorialButton;
    @FXML
    private Button continueButton;
    @FXML
    private Button newGameButton;
    @FXML
    private Slider musicSlider;
    @FXML
    private Label musicPercentLabel;
    private String newGameNormalText;
    private final GameStateController stateController;
    private  SceneNavigator navigator;

 

    public MainMenuController(GameStateController stateController, SceneNavigator navigator) {
        this.stateController = stateController;
        this.navigator = navigator;//should have acces to make the overlays appear
    
    }


    // Runs after the FXML is loaded: only enable Continue when a usable save exists
    @FXML
    public void initialize() {
        newGameNormalText = newGameButton.getText();
        refreshButtons();
        MusicVolumeSlider.bind(musicSlider, musicPercentLabel); // same shared music volume as the pause menu
    }

    // Called every time the menu is built (the menu is re-created each time you come back to it)
    private void refreshButtons() {
        continueButton.setDisable(!SaveManager.getInstance().hasSave());

        boolean unlocked = SaveManager.getInstance().isTutorialCompleted();
        newGameButton.setDisable(!unlocked);
        if (!unlocked && Constants.NEW_GAME_LOCKED_TEXT != null) {
            newGameButton.setText(Constants.NEW_GAME_LOCKED_TEXT);
        } else {
            newGameButton.setText(newGameNormalText);
        }
    }

    // Restores the saved playthrough and goes to the map
    @FXML
    public void continuePressed() {
        Optional<GameSession> saved = SaveManager.getInstance().load();
        if (saved.isEmpty()) {
            continueButton.setDisable(true); // save vanished or is corrupt
            return;
        }
        GameSession.setCurrent(saved.get());
        stateController.setState(GameState.LOADING_SCREEN);
        stateController.setNextState(GameState.MAP_SATE);
    }

    // If a session is saved last time it should show a warning
    @FXML
    public void newGamePressed() {
        if (!SaveManager.getInstance().isTutorialCompleted()) {
            return; // safety net: the button is disabled, but never start a game while locked
        }
        if (SaveManager.getInstance().hasSave()) {
            navigator.showNewGameWarning();            // confirming it runs NewGameController.createNewGame()
        } else {
            GameSession.startNewGame(Constants.defaultPlayerName).checkpoint();
            stateController.setState(GameState.LOADING_SCREEN);
            stateController.setNextState(GameState.MAP_SATE);
        }
        // navigator.showGameWon(); debug
    }

    @FXML
    public void exit() {
        Platform.exit();
    }

    // When the tutorial button is pressed, it will automatically go to the prologue
    @FXML
    public void tutorialPressed() {
        // loading first
        Constants.sceneIndex = 0;
        stateController.setState(GameState.LOADING_SCREEN);
        stateController.setNextState(GameState.PROLOGUE);
    }

    public Button getExitButton() {
        return exitButton;
    }

    public Button getTutorialButton() {
        return tutorialButton;
    }

    public GameStateController getStateController() {
        return stateController;
    }

    /* For how to play */
    @FXML 
    public void showHowToPlay(){
        System.out.println("pressed");
        navigator.showHowToPlay();
    }
}
package controller;

import data.Constants;
import data.GameSession;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import model.GameState;

/** Popup controller for when a player makes a new game. */
public class NewGameController {
    private SceneNavigator navigator;
    GameStateController controller;
    @FXML 
    private Button backButton;
    @FXML 
    private Button newGameButton;

    public NewGameController(SceneNavigator navigator, GameStateController controller) {
        this.navigator = navigator;
        this.controller = controller;
    }

    /* If back is pressed, it will just hide the overlay */
    public void backPressed() {
        navigator.hideOverlay();
        Constants.NEW_GAME_WARNING_SHOWN = false;
    }

    /* Create a new game Session */
    public void createNewGame() {
    navigator.hideOverlay();
    GameSession.startNewGame(Constants.defaultPlayerName).checkpoint(); // fresh game + save so Continue works
    controller.setState(GameState.LOADING_SCREEN);
    controller.setNextState(GameState.MAP_SATE);
    Constants.NEW_GAME_WARNING_SHOWN = false;
}
}

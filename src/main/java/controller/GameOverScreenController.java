package controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import model.GameState;

public class GameOverScreenController {
     @FXML private Button returnButton; // its an imageview so that it would not stand out 

    private GameStateController stateController;
    private SceneNavigator sceneNavigator;

    public  GameOverScreenController(SceneNavigator sceneNavigator, GameStateController stateController){
        this.stateController = stateController;
        this.sceneNavigator = sceneNavigator;

    }
    // The save is already gone: GameplayController wipes it when this overlay opens, so nothing is deleted here
    // (this overlay is also reachable from the map debug hook, which must never wipe a real save).
    public void backToMenu(){
        // go back to the main menu
        sceneNavigator.hideOverlay();
        stateController.setState(GameState.LOADING_SCREEN);
        stateController.setNextState(GameState.MAIN_MENU);
    }
}

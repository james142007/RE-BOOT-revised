package controller;
/** Game won overlay, shows over the screen once the player finishes the game. */

import javafx.fxml.FXML;
import javafx.scene.image.ImageView;
import model.GameState;

public class GameWonController {
    /**
     * This ovelay will pop up after the apilogue // its unclickable anywhere except the ckick here part of the image where the imageview sits
     */
    @FXML private ImageView returnButton; // its an imageview so that it would not stand out 

    private GameStateController stateController;
    private SceneNavigator sceneNavigator;

    public  GameWonController(SceneNavigator sceneNavigator, GameStateController stateController){
        this.stateController = stateController;
        this.sceneNavigator = sceneNavigator;

    }

    public void backToMenu(){
        // go back to the main menu
        sceneNavigator.hideOverlay();
        stateController.setState(GameState.LOADING_SCREEN);
        stateController.setNextState(GameState.MAIN_MENU);
    }
}

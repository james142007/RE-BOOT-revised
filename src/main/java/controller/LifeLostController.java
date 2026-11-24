package controller;

import data.AudioRegistry;
import data.GameSession;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import model.GameState;
import model.character.Player;

/** The "You lost a life" overlay on the battle screen. One button: click sound, close the overlay, back to the map. */
public class LifeLostController {

    @FXML
    private Label livesLabel;
    @FXML
    private Button returnButton;

    private final SceneNavigator sceneNavigator;
    private final GameStateController stateController;
    private boolean leaving; // guards against a double click

    public LifeLostController(SceneNavigator sceneNavigator, GameStateController stateController) {
        this.sceneNavigator = sceneNavigator;
        this.stateController = stateController;
    }

    @FXML
    public void initialize() {
        AudioController.getInstance().bindClick(returnButton, AudioRegistry.SFX_UI_CLICK);

        GameSession session = GameSession.current();
        if (session != null) {
            livesLabel.setText("Lives remaining: " + session.getPlayer().getLives() + " / " + Player.MAX_LIVES);
        }
    }

    /** Back to the map, same flow as the "Return to Map" banner after a cleared era. */
    @FXML
    public void returnToMap() {
        if (leaving) {
            return;
        }
        leaving = true;
        sceneNavigator.hideOverlay();
        stateController.setState(GameState.LOADING_SCREEN);
        stateController.setNextState(GameState.MAP_SATE);
    }
}

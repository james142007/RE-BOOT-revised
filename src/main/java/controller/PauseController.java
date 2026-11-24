package controller;
/**
 * Pause state of the game. Shows the pause overlay and stops the whole game.
 * @author Felix
 */
import data.Constants;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import model.GameState;

public class PauseController {
    
    private GameStateController controller;

    @FXML
    private Slider musicSlider;
    @FXML
    private Label musicPercentLabel;
    
    public PauseController(GameStateController controller){
        this.controller = controller;
    }

    // Runs after the FXML is loaded (the controller is set from code, so this still gets called)
    @FXML
    public void initialize(){
        MusicVolumeSlider.bind(musicSlider, musicPercentLabel); // same shared music volume as the main menu
    }

    @FXML 
    public void resume(){
        Constants.paused = false;
    }
    @FXML 
    public void howToPlay(){

    }
    @FXML 
    public void exitToMenu(){
        saveRunIfInEra();
        Constants.paused = false;
        Constants.sceneIndex = 0;
        controller.setState(GameState.LOADING_SCREEN);
        controller.setNextState(GameState.MAIN_MENU);
    }
    @FXML 
    public void exitToDekstop(){
        saveRunIfInEra();
        Platform.exit();
    }

    // Mid-era save. The pause menu is also used by the prologue and the tutorial, which must never create a run, so only the era screen saves.
    private void saveRunIfInEra(){
        if (controller.getState() != GameState.GAMEPLAY) {
            return;
        }
        GameplayController gameplay = controller.getGameplayController();
        if (gameplay != null) {
            gameplay.saveRun();
        }
    }
}

package controller;

import data.Constants;
import model.GameState;

/**
 * Keeps track of what state the game is in (splash screen, main menu, etc).
 * @author Felix
 */

public class GameStateController {

    private GameState currentState;
    private GameState nextState;
    private GameState previousState;

    private VisualNovelController visualNovelController;
    private TutorialController tutorialController;
    private GameplayController gameplayController;
    private MapController mapController;

    // private PauseController pauseController;
    ///constructor
    public GameStateController() {
        // starting state
        currentState = Constants.currentState;
        nextState = null;
        previousState = null;
    }

    public void setState(GameState state) {
        previousState = currentState;
        currentState = state;

    }

    public GameState getState() {

        return currentState;

    }
    /**
     * 
     * @return
     */
    public GameState getPreviousState() {
        return previousState;
    }

    // experimental, this should add the loading
    public void setNextState(GameState nextState) {
        this.nextState = nextState;
    }

    public GameState getNextState() {
        return nextState;
    }
    /**
     * 
     * @param deltaSeconds
     */
    public void update(double deltaSeconds) {
        switch (currentState) {
            case PROLOGUE:
                if(visualNovelController != null ){
                    visualNovelController.update(deltaSeconds);
                }
                break;
            case TUTORIAL:
                if(tutorialController != null){
                    tutorialController.update(deltaSeconds);
                }
                break;
            case MAP_SATE:
                if(mapController != null){
                    mapController.update(deltaSeconds);
                }
                break;
            case GAMEPLAY:
                if (gameplayController != null) {
                    gameplayController.update(deltaSeconds);
                }
                break;
            default:
                break;
        }
    }
    /**
     * 
     * @param visualNovelController
     */
    public void setVisualNovelController(VisualNovelController visualNovelController) {
        this.visualNovelController = visualNovelController;
    }
    /**
     * 
     * @param tutorialController
     */
    public void setTutorialController(TutorialController tutorialController) {
        this.tutorialController = tutorialController;
    }
    /**
     * 
     * @param gameplayController
     */
    public void setGameplayController(GameplayController gameplayController) {
        this.gameplayController = gameplayController;
    }
    /** The controller of the era screen that was built last, or null. Only meaningful while the state is GAMEPLAY. */
    public GameplayController getGameplayController() {
        return gameplayController;
    }
    public void setMapController(MapController mapController){
        this.mapController = mapController;
    }

}

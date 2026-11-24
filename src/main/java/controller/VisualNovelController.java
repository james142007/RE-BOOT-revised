package controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import model.GameState;
import model.novel.DialogueLine;
import model.novel.DialogueLoader;
import model.novel.VisualNovel;
import model.novel.VisualNovelScene;

import java.util.Optional;

import data.Constants;
import data.GameSession;
import data.SaveManager;

enum NovelState {
    DIALOG,
    FINISH
}

public class VisualNovelController {

    @FXML
    private Label speaker;
    @FXML
    private Label dialogue;

    @FXML
    private Pane completedPane;
    @FXML
    private Button returnButton;

    @FXML
    private Button skipButton;
    @FXML
    private ImageView backgroundImage;

    private Boolean completedShown;

    private VisualNovel visualNovel;
    private int dialogueIndex;

    private NovelState currentState;

    private int charIndex;
    private double typeWriterTimer;
    private final double delayPerChar;

    private GameStateController stateController;

    private SceneNavigator navigator;

    private boolean backgroundChanged = false; // for each scene the background should only be pasted once

    public VisualNovelController(GameStateController stateController, SceneNavigator navigator) {
        this.stateController = stateController;
        dialogueIndex = 0;
        charIndex = 0;
        typeWriterTimer = 0;
        delayPerChar = 0.05;

        visualNovel = DialogueLoader.load("/dialogues/DialogData.json");
        completedShown = false;
        currentState = NovelState.DIALOG;
        this.navigator = navigator;

    }

    @FXML
    public void initialize() {
        try {
            setBackground("/assets/Prologue/" + visualNovel.getScenes().get(Constants.sceneIndex).getDialogues()
                    .get(dialogueIndex).getBackground() + ".png");
        } catch (Exception e) {
            e.printStackTrace();
        }
        completedPane.setVisible(false);
        if (Constants.sceneIndex >= 2) {
            skipButton.setVisible(false);
        }
        backgroundChanged = true;
    }

    @FXML
    private void next() {

        if (currentState == NovelState.DIALOG) {

            VisualNovelScene currentScene = visualNovel.getScenes().get(Constants.sceneIndex);

            var dialogues = currentScene.getDialogues();

            // Prevent accessing a nonexistent dialogue
            if (dialogueIndex >= dialogues.size()) {
                return;
            }

            DialogueLine line = dialogues.get(dialogueIndex);

            // Finish displaying the current dialogue first
            if (charIndex < line.getDialogue().length()) {
                charIndex = line.getDialogue().length();
                dialogue.setText(line.getDialogue());
                return;
            }

            backgroundChanged = false;

            // Move to the next dialogue
            dialogueIndex++;
            charIndex = 0;
            typeWriterTimer = 0;
            dialogue.setText("");
        }
    }

    /*
     * Sets the bg of the visual novel depending on {@code currentSceneBackground}
     * 
     */
    public void setBackground(String path) throws Exception {
        var resource = getClass().getResource(path);

        if (resource == null) {// if the path or the image is not found
            System.err.println("Background image not found: " + path);
            return;
        }

        Image image = new Image(resource.toExternalForm());// convert the resource to image

        if (image.isError()) {
            System.err.println("Failed to load background: " + path);
            return;
        }

        backgroundImage.setImage(image);// load as bg
    }

    @FXML
    private void skip() {
        if (Constants.sceneIndex != 0)
            return; // only the prologue can be skipped

        resetDialogueState();
        Constants.sceneIndex = 1;
        stateController.setState(GameState.LOADING_SCREEN);
        stateController.setNextState(GameState.TUTORIAL);
    }

    @FXML
    public void pause() {
        System.out.println("Pressed");
        if (!Constants.paused) {
            Constants.paused = true;
            System.out.println("Pressed");
            return;
        } else {
            Constants.paused = false;
            System.out.println("Pressed");
        }
    }

    public void update(double now) {

        switch (currentState) {
            case DIALOG:
                updateDialog(now);
                break;
            case FINISH:
                showComplete();
                break;
            default:
                break;
        }

    }

    public void updateDialog(double now) {
        int index = Constants.sceneIndex;

        // Scene 1 is the tutorial battle, so the visual novel has nothing to do
        if (index == 1 || index < 0 || index >= visualNovel.getScenes().size()) {
            return;
        }

        VisualNovelScene currentScene = visualNovel.getScenes().get(index);
        var dialogues = currentScene.getDialogues();

        // Current scene is exhausted
        if (dialogueIndex >= dialogues.size()) {
            onSceneExhausted();
            return;
        }
        if (dialogueIndex >= dialogues.size()) {
            if (currentScene.getScene().equals("prologue")) {
                // Prologue finished: transition to tutorial
                Constants.sceneIndex = 1;
                dialogueIndex = 0;
                charIndex = 0;
                typeWriterTimer = 0;
                dialogue.setText("");
                speaker.setText("");
                stateController.setState(GameState.LOADING_SCREEN);
                stateController.setNextState(GameState.TUTORIAL);
                return;
            }
            if (currentScene.getScene().equals("prologue2")) {
                // Prologue 2 finished: show completion pane
                currentState = NovelState.FINISH;
                return;
            }

            return;
        }
        // change the background based on the current index
        if (!backgroundChanged) {
            try {
                DialogueLine line = dialogues.get(dialogueIndex);
                String background = line.getBackground();

                if (background != null && !background.isBlank()) {
                    setBackground(
                            "/assets/Prologue/" + background + ".png");
                }

                backgroundChanged = true;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        DialogueLine line = currentScene.getDialogues().get(dialogueIndex);

        speaker.setText(line.getSpeaker());
        if (charIndex < line.getDialogue().length()) {
            typeWriterTimer += now;
            if (typeWriterTimer >= delayPerChar) {
                charIndex++;
                dialogue.setText(line.getDialogue().substring(0, charIndex));
                typeWriterTimer = 0;
            }
        }
    }

    private void onSceneExhausted() {
        switch (Constants.sceneIndex) {
            case 0 -> {
                // Prologue done, so go to the tutorial battle
                resetDialogueState();
                Constants.sceneIndex = 1;
                stateController.setState(GameState.LOADING_SCREEN);
                stateController.setNextState(GameState.TUTORIAL);
            }
            case 2 -> {
                // Post-tutorial dialogue done, so continue to scene 3
                resetDialogueState();
                Constants.sceneIndex = 0;
                gotoMap();
                backgroundChanged = false; // so scene 3's first background gets loaded
            }
            case 3 -> {
                // Last scene exhausted, so show the game won banner

                if (!completedShown) {
                    completedShown = true;
                    navigator.showGameWon();
                }
            }
            default -> {
            }
        }
    }

    /* after the prologue, will create new game and go to prologues */
    public void gotoMap() {
        Optional<GameSession> saved = SaveManager.getInstance().load();
        if (saved.isPresent()) {
            // load the save to avoid overwriting
            GameSession.setCurrent(saved.get());
            stateController.setState(GameState.MAP_SATE);
        } else {
            GameSession.startNewGame(Constants.defaultPlayerName).checkpoint();
            stateController.setState(GameState.LOADING_SCREEN);
            stateController.setNextState(GameState.MAP_SATE);
        }
    }

    private void resetDialogueState() {
        dialogueIndex = 0;
        charIndex = 0;
        typeWriterTimer = 0;
        dialogue.setText("");
        speaker.setText("");
    }

    public void setDialogueIndex(int dialogueIndex) {
        this.dialogueIndex = dialogueIndex;
    }

    // show complete pane
    public void showComplete() {
        if (!completedShown) {
            completedPane.setVisible(true);
            completedShown = true;
        }
    }

    public void returnMenu() {

        // loading then go to main menu
        completedShown = false;
        stateController.setState(GameState.LOADING_SCREEN);
        stateController.setNextState(GameState.MAIN_MENU);
    }

}

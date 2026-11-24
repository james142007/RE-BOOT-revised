package controller;

import data.AudioRegistry;
import data.Constants;
import data.SaveManager;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import model.GameState;
import model.animator.EnemyAnimator;
import model.animator.PlayerAnimator;
import model.novel.DialogueLine;
import model.novel.DialogueLoader;
import model.novel.VisualNovel;
import model.novel.VisualNovelScene;

enum TutorialStages {
    DIALOGUE_STATE,
    TOOLBOX_SHOW,
    BATTLE_STATE,
    TOOLS_SHOW,
    TUTORIAL_DONE
}

/**
 * 
 * TutorialController {@author @james142007}
 * controls the flow of the entire toturial
 * Hardcoded sometimes :) and my code is so messy
 */
public class TutorialController {
    private GameStateController gameStateController;

    // player components FXML
    @FXML
    private Pane playerDialoguePane;
    @FXML
    private Label playerDialogueLabel;
    @FXML
    private Pane playerStatPane;
    @FXML
    private ImageView playerImageView;
    private PlayerAnimator playerAnimator; // animates the player

    // ai FXML components
    @FXML
    private ScrollPane aiDialogueScrollPane;
    @FXML
    private Label aiDialogueLabel;
    @FXML
    private Pane aiDialoguePane;

    @FXML
    private Pane toolBoxPane;

    // choices
    @FXML
    private ScrollPane choicesScrollPane;
    @FXML
    private Pane choicesPane;
    @FXML
    private Label choicesLabel;
    @FXML
    private Button nextButton;
    @FXML
    private Button prevButton;
    @FXML
    private Button selectButton;

    // enemy FXML components
    @FXML
    private Pane enemyDialoguePane;
    @FXML
    private Pane enemyStatPane;
    @FXML
    private Label enemyDialogueLabel;
    @FXML
    private ImageView enemyImageView;
    private EnemyAnimator enemyAnimator;

    // Panel for completing the tutorial and exit
    // @FXML
    // private Pane completedPane;
    // @FXML
    // private Button returnButton;

    private VisualNovel visualNovel;
    private int dialogueIndex;

    // typewriter var
    private double typeWriterTimer;
    private final double delayPerChar;
    private int charIndex;

    private double dialogueDelay;
    private double dialogueTimer;

    private TutorialStages stages;
    private VisualNovelScene currentScene;

    private boolean toolBoxShown;// flag for toolbox timing
    private double toolBoxTimer;
    private double toolBoxDelay; // total time before transitioning to dialog

    // var for the battle state
    private boolean inBattle;
    private TutorialBattleController battleController;
    private boolean pasted;
    private int choiceIndex;
    private boolean choiceSelected;
    private boolean flag1;// after learnig dealing damage
    private static int POST_BATTLE_DIALOGUE_INDEX = 12;
    private boolean battleEndHandled = false;

    ///Constructor
    public TutorialController(GameStateController stateController) {
        this.gameStateController = stateController;
        this.dialogueIndex = 0;
        typeWriterTimer = 0;
        delayPerChar = 0.05;
        charIndex = 0;
        dialogueDelay = 1.5;
        dialogueTimer = 0;
        stages = TutorialStages.DIALOGUE_STATE;
        visualNovel = DialogueLoader.load("/dialogues/DialogData.json");

        currentScene = visualNovel.getScenes().get(Constants.sceneIndex);

        toolBoxShown = false;
        toolBoxDelay = 2.0;
        toolBoxTimer = 0;

        flag1 = false;

        inBattle = false;
        battleController = new TutorialBattleController(this);
        pasted = false;
        choiceIndex = 0; // always show letter A always at the start of the battle stages
        choiceSelected = false; // tracks if the player had selected an answer,
        // this will then be passed to the battle controller to trigger some events
        // based on the asnwer of the player

    }

    // initializes the pane to be invisible to create a more immersive scene
    @FXML
    public void initialize() {
        AudioController audio = AudioController.getInstance();
        audio.bindClick(prevButton, AudioRegistry.SFX_BATTLE_NAV);
        audio.bindClick(nextButton, AudioRegistry.SFX_BATTLE_NAV);
        audio.bindClick(selectButton, AudioRegistry.SFX_BATTLE_SELECT);
        aiDialoguePane.setVisible(false);
        toolBoxPane.setVisible(false);
        playerStatPane.setVisible(false);
        enemyStatPane.setVisible(false);
        enemyDialoguePane.setVisible(false);
        // completedPane.setVisible(false);

        // animators
        playerAnimator = new PlayerAnimator(playerImageView, 6);
        enemyAnimator = new EnemyAnimator(enemyImageView);
        enemyAnimator.setSheet("easy");
    }

    // should show the text with a typewriter effect

    private void setDialogue(
            double deltaSeconds,
            DialogueLine line,
            Label dialogueLabel,
            Pane dialoguePane) {

        dialoguePane.setVisible(true);// when a speaker speaks the pane should be visible

        // typewriter effect
        if (charIndex < line.getDialogue().length()) {
            typeWriterTimer += deltaSeconds;
            if (typeWriterTimer >= delayPerChar) {
                charIndex++;
                dialogueLabel.setText(line.getDialogue().substring(0, charIndex));
                typeWriterTimer = 0;

            }

        } else if (charIndex >= line.getDialogue().length()) {
            dialogueTimer += deltaSeconds;
            if (dialogueTimer >= dialogueDelay) {
                charIndex = 0;
                dialogueIndex++;
                dialogueTimer = 0;
                dialogueLabel.setText(" ");
                dialoguePane.setVisible(false);
            }
        }
    }

    // shows the toolbox when triggered
    public void showToolBox(double deltaSeconds) {
        if (!toolBoxShown) {
            toolBoxPane.setVisible(true);
            toolBoxShown = true;
        }
        toolBoxTimer += deltaSeconds;

        if (toolBoxTimer >= toolBoxDelay) {
            stages = TutorialStages.DIALOGUE_STATE;
        }
    }

    public void transition() {
        // The tutorial dialogue + battle are finished: unlock New Game (saved to disk)
        SaveManager.getInstance().markTutorialCompleted();
        Constants.sceneIndex = 2;
        gameStateController.setState(GameState.PROLOGUE);
    }

    // Battle logic

    // ==========================================================================================================

    // resposible for the battle updates
    public void updateBattle(
            double deltaSeconds,
            Pane questionsPane,
            Label questionLabel,
            Label choicesLabel) {
        if (!pasted) {

            pasteQuestions(deltaSeconds, questionsPane, questionLabel, choicesLabel);

        }
        // select choice -> determine if correct -> trigger outocome -> repeat

        if (pasted) {
            battleController.setBattleState(deltaSeconds); // trigger the sequence of events in the battle controller
                                                           // class
        }

    }

    public void pasteQuestions(double deltaSeconds, Pane questionsPane, Label questionLabel, Label choicesLabel) {
        if (!pasted) {
            questionLabel.setText(" ");
            questionsPane.setVisible(true);
            questionLabel.setText(battleController.getQuestion());
            loadChoices(choicesLabel);
            pasted = true;
        }
    }

    public void loadChoices(Label choicesLabel) {
        choicesLabel.setText(
                battleController.getLetters().get(choiceIndex) + ". " + battleController.getChoices().get(choiceIndex));
    }

    // choices navigators
    @FXML
    public void nextChoice() {
        if ((choiceIndex < 3) && stages == TutorialStages.BATTLE_STATE) {
            choiceIndex++;
            loadChoices(choicesLabel);
        }
    }

    // these two methods can only be triggered inside battle state
    @FXML
    public void prevChoice() {
        if ((choiceIndex > 0) && stages == TutorialStages.BATTLE_STATE) {
            choiceIndex--;
            loadChoices(choicesLabel);
        }
    }

    // returns the letter if the current choices that the player selected
    @FXML
    public String choiceSelected() {
        choiceSelected = true;
        return battleController.getLetters().get(choiceIndex);
    }

    // pass to battle controller
    public boolean hasSelected() {
        return choiceSelected;
    }

    public void setChoiceSelected(boolean choiceSelected) {
        this.choiceSelected = choiceSelected;
    }

    public void setPasted(boolean pasted) {
        this.pasted = pasted;
    }

    public void setChoiceSelected() {
        choiceSelected = false;
    }

    public void reset() {
        choiceIndex = 0;
        choiceSelected = false;

        if (!flag1 && Integer.parseInt(battleController.getCurrentNumber()) == 3) {
            flag1 = true;
        }

    }

    public void enemyDead() {
        if (battleEndHandled || !battleController.botDead()) {
            return;
        }
        // Prevent the transition from running again.
        battleEndHandled = true;
        AudioController.getInstance().playSfx(AudioRegistry.SFX_BATTLE_ENEMY_DEFEATED);
        AudioController.getInstance().playMusic(AudioRegistry.BGM_TUTORIAL_DIALOGUE); // battle over, back to the
                                                                                      // dialogue track

        // Resume the dialogue at the first post-battle line.
        dialogueIndex = POST_BATTLE_DIALOGUE_INDEX;
        stages = TutorialStages.DIALOGUE_STATE;

        // Reset the typewriter effect ONCE.
        charIndex = 0;
        typeWriterTimer = 0;
        dialogueTimer = 0;

        // Clear any text left over from the battle.
        aiDialogueLabel.setText("");
        enemyDialogueLabel.setText("");
        playerDialogueLabel.setText("");
        choicesLabel.setText("");
    }

    // ===========================================================================================================================================================

    public void update(double deltaSeconds) {
        playerAnimator.updateAnimation(deltaSeconds);
        enemyAnimator.updateAnimation(deltaSeconds);
        var dialogues = currentScene.getDialogues();
        if (dialogueIndex >= dialogues.size()) {
            transition();
            return;
        }
        if (dialogueIndex < 0 || dialogueIndex >= dialogues.size()) {
            return;
        }
        DialogueLine line = dialogues.get(dialogueIndex);

        if ((stages != TutorialStages.TOOLBOX_SHOW) && dialogueIndex == 5 && !toolBoxShown) {// exact timing in the
                                                                                             // dialog
            stages = TutorialStages.TOOLBOX_SHOW;
        }
        if ((stages != TutorialStages.BATTLE_STATE) && dialogueIndex == 11 && !inBattle) { // trigger for the battle
                                                                                           // stage
            stages = TutorialStages.BATTLE_STATE;
            inBattle = true;
            enemyAnimator.setSheet("easy");
            playerAnimator.reset();
            ;
            enemyAnimator.reset();
            AudioController.getInstance().playMusic(AudioRegistry.BGM_TUTORIAL_BATTLE);
        }

        switch (stages) {
            // Only shows the panel depending on the current speaker
            case DIALOGUE_STATE:
                switch (line.getSpeaker()) {// should track who the current speaker is
                    case "Character":
                        setDialogue(deltaSeconds, line, playerDialogueLabel, playerDialoguePane);
                        break;
                    case "Mimi":
                        setDialogue(deltaSeconds, line, aiDialogueLabel, aiDialoguePane);
                        break;
                    case "Enemy":
                        setDialogue(deltaSeconds, line, enemyDialogueLabel, enemyDialoguePane);
                        break;

                    default:
                        break;
                }
                break;
            case TOOLBOX_SHOW:
                showToolBox(deltaSeconds);
                break;
            case BATTLE_STATE:
                updateBattle(deltaSeconds, aiDialoguePane, aiDialogueLabel, choicesLabel);
                break;
            case TUTORIAL_DONE:
                transition();// cause to go back to the visual novel part
                break;
            default:
                break;
        }
    }

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

    // {@note} the main battle and tutorial battle have different logic due to its
    // served purpose
    // and adding the fact that it is created by different people

    /* -------Animation Triggers------- */
   
    public void playPlayerAttack(double s) {
        playerAnimator.animationHelper(PlayerAnimator.Animation.ATTACKING, s);
    }

    public void playPlayerDamaged(double s) {
        playerAnimator.animationHelper(PlayerAnimator.Animation.DAMAGED, s);
    }

    public void playPlayerKilled(double s) {
        playerAnimator.animationHelper(PlayerAnimator.Animation.KILLED, s);
    }

    public void playEnemyAttack(double s) {
        enemyAnimator.playAnimation(EnemyAnimator.EnemyAnimation.ATTACKING, s);
    }

    public void playEnemyDamaged(double s) {
        enemyAnimator.playAnimation(EnemyAnimator.EnemyAnimation.DAMAGED, s);
    }

    public void playEnemyKilled(double s) {
        enemyAnimator.playAnimation(EnemyAnimator.EnemyAnimation.KILLED, s);
    }
}
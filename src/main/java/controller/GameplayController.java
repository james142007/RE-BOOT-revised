package controller;

import java.io.IOException;
import java.net.URL;
import model.map.Era;
import java.util.Locale;

import controller.EraController.Phase;
import data.AudioRegistry;
import data.Constants;
import data.EraContent;
import data.EraLoader;
import data.GameSession;
import data.SaveData;
import data.SaveManager;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import model.GameState;
import model.animator.EnemyAnimator;
import model.animator.EnemyAnimator.EnemyAnimation;
import model.animator.PlayerAnimator;
import model.animator.PlayerAnimator.Animation;
import model.character.Component;
import model.character.Player;
import model.enemy.Boss;
import model.enemy.Enemy;
import model.enemy.Medium;
import model.question.Question;
import model.toolbox.ToolBox;

/**
 * Main gameplay screen controller, sets things up from the save file (if any)
 * and the era content.
 */
public class GameplayController implements EraController.Listener, BattleController.Listener {

    private static final String[] LETTERS = { "A", "B", "C", "D" };
    // style classes from gameplay.css that colour the shown choice after the
    // question is resolved
    private static final String CHOICE_CORRECT_CLASS = "choice-correct";
    private static final String CHOICE_WRONG_CLASS = "choice-wrong";
    // style class from gameplay.css that turns the countdown red in the last
    // seconds
    private static final String TIMER_WARNING_CLASS = "timer-warning";

    private final GameStateController stateController;
    private final SceneNavigator sceneNavigator;
    private final GameSession session;
    private final EraController eraController;
    private final BattleController battle;

    // top bar
    @FXML
    private Label eraLabel;
    @FXML
    private Label encounterLabel;

    // battle
    @FXML
    private Pane battlePane;
    @FXML
    private Label playerStatsLabel;
    @FXML
    private Label enemyStatsLabel;
    @FXML
    private Label questionHeaderLabel;
    @FXML
    private ScrollPane questionScrollPane;
    @FXML
    private Label questionLabel;
    @FXML
    private Label messageLabel;
    @FXML
    private Label timerLabel;
    @FXML
    private Button pauseButton;
    @FXML
    private Button debugButton;
    @FXML
    private Button databaseButton;
    @FXML
    private Button consultButton;
    @FXML
    private Button saveButton;
    // the choices: one box showing a single choice, Previous / Next move between
    // them (like the tutorial)
    @FXML
    private ScrollPane choicesScrollPane;
    @FXML
    private Label choicesLabel;
    @FXML
    private Button prevButton;
    @FXML
    private Button nextButton;
    @FXML
    private Button selectButton;

    // banner
    @FXML
    private Pane bannerPane;
    @FXML
    private Label bannerTitleLabel;
    @FXML
    private Label bannerBodyLabel;
    @FXML
    private Button bannerButton;
    // playerImageView
    @FXML
    private ImageView playerImageView;
    private PlayerAnimator playerAnimator;
    // enemy ImageView and animator
    @FXML
    private ImageView enemyImageView;
    private EnemyAnimator enemyAnimator;

    // era bg

    @FXML
    private ImageView eraBackground;

    // for game over
    private boolean gameOverPending;
    private double gameOverTimer;
    private boolean saveDiscarded; // the save is wiped once per game over, however often the panel is triggered

    // what the choice box is showing
    private Question currentQuestion;
    private int choiceIndex; // index of the choice currently shown
    private int submittedIndex = -1; // the choice the player locked in, -1 until they answer
    private boolean resolved; // true once the answer has been applied (colours the shown choice)

    // life lost: set when the answer that cost a life is resolved; the panel opens
    // once the
    // damage animation has played (i.e. when the battle moves on to its next
    // question)
    private boolean lifeLostPending;
    // true while the "life lost" panel is open: the battle is frozen and takes no
    // input
    private boolean inputLocked;

    public GameplayController(GameStateController stateController, SceneNavigator sceneNavigator) {
        this.stateController = stateController;
        this.sceneNavigator = sceneNavigator;
        this.session = GameSession.current();
        if (session == null) {
            throw new IllegalStateException("No active game session. Start a New Game or Continue first.");
        }
        this.eraController = buildEraController(session, session.getPlayer().getCurrentEraNumber());
        this.battle = eraController.getBattle();
        this.battle.addListener(this);
        this.eraController.addListener(this);
    }

    /**
     * Resumes the era in progress if the save has one for this era, otherwise starts the era fresh at its intro.
     * A snapshot that doesn't fit the era content (e.g. a question id that no longer exists) is dropped and the era starts fresh,
     * from newly loaded content because the failed attempt may have changed the old one.
     */
    private static EraController buildEraController(GameSession session, int eraNumber) {
        SaveData.RunData run = session.getPendingRun();
        if (run != null && run.eraNumber() == eraNumber) {
            try {
                return new EraController(session, EraLoader.load(eraNumber), run);
            } catch (RuntimeException e) {
                System.err.println("[Gameplay] Saved run can't be resumed, starting era " + eraNumber
                        + " fresh: " + e.getMessage());
                session.abandonRun(); // drops the snapshot and refills the toolbox like a normal era start
            }
        }
        EraContent content = EraLoader.load(eraNumber);
        return new EraController(session, content);
    }

    /** Mid-era save, called by the pause menu's exit buttons. Does nothing when there is no stable moment to save. */
    public void saveRun() {
        eraController.saveRun();
    }

    @FXML
    public void initialize() {
        setupAudio();
        setBackground();
        playerAnimator = new PlayerAnimator(playerImageView, 6.0);
        enemyAnimator = new EnemyAnimator(enemyImageView);
        choicesLabel.setText("");
        battlePane.setVisible(false);
        messageLabel.setText("");
        onPhaseChanged(eraController.getPhase());
    }

    /** Click / hover sounds for the battle screen buttons. */
    private void setupAudio() {
        AudioController audio = AudioController.getInstance();
        audio.bindClick(pauseButton, AudioRegistry.SFX_UI_CLICK);
        audio.bindClick(prevButton, AudioRegistry.SFX_BATTLE_NAV);
        audio.bindClick(nextButton, AudioRegistry.SFX_BATTLE_NAV);
        audio.bindClick(selectButton, AudioRegistry.SFX_BATTLE_SELECT);
        for (Button lifeline : new Button[] { debugButton, databaseButton, consultButton, saveButton }) {
            audio.bindClick(lifeline, AudioRegistry.SFX_LIFELINE_USE);
            audio.bindHover(lifeline);
        }
    }

    /** One stinger per enemy type when an encounter starts. */
    private void playEncounterSound(Enemy enemy) {
        String key;
        if (enemy instanceof Boss) {
            key = AudioRegistry.SFX_ENCOUNTER_BOSS;
        } else if (enemy instanceof Medium) {
            key = AudioRegistry.SFX_ENCOUNTER_MEDIUM;
        } else {
            key = AudioRegistry.SFX_ENCOUNTER_EASY;
        }
        AudioController.getInstance().playSfx(key);
    }

    /**
     * Update
     */
    public void update(double deltaSeconds) {
        if (inputLocked) {
            return; // "life lost" panel is open: no battle timer, no play time, nothing moves
        }
        if (gameOverPending) {
            gameOverTimer += deltaSeconds;
            if (gameOverTimer >= Constants.damageTime + 0.3) {
                gameOverPending = false;
                showGameOverPanel();
            }
        }
        eraController.update(deltaSeconds);
        updateTimerLabel();
        playerAnimator.updateAnimation(deltaSeconds);
        enemyAnimator.updateAnimation(deltaSeconds);
    }

    /**
     * Shows the question time left as m:ss, red once it hits the warning threshold.
     */
    private void updateTimerLabel() {
        boolean show = battlePane.isVisible() && currentQuestion != null && battle.hasTimeLimit();
        timerLabel.setVisible(show);
        if (!show) {
            return;
        }
        int seconds = (int) Math.ceil(battle.getQuestionTimeRemaining());
        timerLabel.setText(String.format("Time: %d:%02d", seconds / 60, seconds % 60));
        boolean warning = seconds <= Constants.TIMER_WARNING_SECONDS;
        if (warning && !timerLabel.getStyleClass().contains(TIMER_WARNING_CLASS)) {
            timerLabel.getStyleClass().add(TIMER_WARNING_CLASS);
        } else if (!warning) {
            timerLabel.getStyleClass().remove(TIMER_WARNING_CLASS);
        }
    }

    /*
     * Gets the current state of the player or enemy, and sets the animation based
     * on thier current state
     */
    @Override
    public void onPlayerDefeated() {
        playerAnimator.animationHelper(Animation.KILLED, Constants.damageTime);
    }

    // ---- era events ----

    @Override
    public void onPhaseChanged(EraController.Phase phase) {
        eraLabel.setText("Era " + eraController.getEra().getEraNumber() + ": " + eraController.getEra().getName());
        encounterLabel
                .setText("Encounter " + eraController.getEncounterNumber() + " / " + eraController.getEncounterCount());
        /// Comments, make the case syntax, shoulld match the lower version of java(for
        /// installation purposes)
        Enemy enemy = eraController.getCurrentEnemy();
        switch (phase) {
            case ERA_INTRO -> {
                showBanner(eraLabel.getText(), eraController.getEra().getNarrative(), "Begin");
                setBackground();
            }
            case ENCOUNTER_INTRO -> {
                showBanner(
                        eraController.isBossEncounter() ? "BOSS" : "Encounter " + eraController.getEncounterNumber(),
                        enemy.getDifficultyLabel() + ": " + enemy.getName(), null);
                playEncounterSound(enemy);
                boolean ok = enemyAnimator.setSheet(enemy.getSpriteKey());
                enemyImageView.setVisible(ok);

            }
            case BATTLE -> {
                bannerPane.setVisible(false);
                battlePane.setVisible(true);
                refreshHud();
            }
            case ENCOUNTER_CLEARED -> showBanner("Enemy defeated!", enemy.getName() + " has been shut down.", null);
            case ERA_CLEARED -> {
                 showBanner("Era cleared!", rewardText(), isGameCompleted() ? "Continue" : "Return to Map");
            } // will go
              // to the
              // map
              // it exists
            case GAME_OVER -> {
                battlePane.setDisable(true);
                pauseButton.setDisable(true);
                timerLabel.setVisible(false);
                gameOverPending = true;
                gameOverTimer = 0;
            } // if all 7 lives are lost, pop up the loss overlay (showGameOverPanel() wipes the save)
            case GAME_WON -> {

                Constants.sceneIndex = 3;
                stateController.setState(GameState.LOADING_SCREEN);
                stateController.setNextState(GameState.EPILOGUE);
            }
        }
    }

    private String rewardText() {
        Component reward = eraController.getEra().getRewardComponent();
        String text = "You defeated the boss.";
        if (reward != null) {
            text += "\n\nComponent recovered: " + reward.getName() + "\n" + reward.getDescription();
        }
        return text + "\n\nProgress saved.";
    }

    private void showBanner(String title, String body, String buttonText) {
        battlePane.setVisible(false);
        currentQuestion = null;
        bannerTitleLabel.setText(title);
        bannerBodyLabel.setText(body == null ? "" : body);
        bannerButton.setVisible(buttonText != null);
        if (buttonText != null) {
            bannerButton.setText(buttonText);
        }
        bannerPane.setVisible(true);
    }

    @FXML
    public void bannerButtonPressed() {
        switch (eraController.getPhase()) {
            case ERA_INTRO -> eraController.beginEra();
            case ERA_CLEARED -> {
                if (isGameCompleted()) {
                    Constants.sceneIndex = 3;
                    stateController.setState(GameState.LOADING_SCREEN);
                    stateController.setNextState(GameState.EPILOGUE);
                } else {
                    exitToMap();
                }
            }
            default -> {
            }
        }
    }

    private void exitToMap() {
        stateController.setState(GameState.LOADING_SCREEN);
        stateController.setNextState(GameState.MAP_SATE);
    }

    // ---- battle events ----

    @Override
    public void onQuestionAsked(Question question, Enemy enemy) {
        if (lifeLostPending) { // the hit has landed and HP is refilled: stop here instead of showing the next
                               // question
            lifeLostPending = false;
            showLifeLostPanel();
            return;
        }
        playerAnimator.playerAnimation(Animation.IDLE);
        enemyAnimator.playAnimation(EnemyAnimation.IDLE, 0);

        questionHeaderLabel.setText(QuestionFormatter.header(question));
        questionLabel.setText(QuestionFormatter.body(question));
        questionScrollPane.setVvalue(0);
        choicesScrollPane.setVvalue(0);
        String recap = lifelineRecap(question);
        messageLabel.setText(recap.isEmpty() ? "Choose your answer." : "Choose your answer.\n" + recap);

        currentQuestion = question;
        choiceIndex = 0;
        submittedIndex = -1;
        resolved = false;
        showChoice();
        refreshHud();
    }

    /**
     * What the lifelines already used on this question told the player. Only has text after a resume: the uses themselves are saved
     * but the message was only ever on screen. Empty for a freshly asked question.
     */
    private String lifelineRecap(Question question) {
        StringBuilder recap = new StringBuilder();
        if (battle.getDatabaseSuggestion() >= 0) {
            recap.append("Database suggests: ").append(LETTERS[battle.getDatabaseSuggestion()]);
        }
        if (battle.isConsultUsed() && question.getHint() != null) {
            if (recap.length() > 0) {
                recap.append("\n");
            }
            recap.append(eraController.getEra().getProgrammer()).append(": \"").append(question.getHint()).append("\"");
        }
        return recap.toString();
    }

    @Override
    public void onAnswerSubmitted(Question question, int selectedIndex, boolean correct) {
        AudioController.getInstance()
                .playSfx(correct ? AudioRegistry.SFX_BATTLE_CORRECT : AudioRegistry.SFX_BATTLE_WRONG);
        if (correct) {
            playerAnimator.animationHelper(Animation.ATTACKING, Constants.attackingTime);
        } else {
            enemyAnimator.playAnimation(EnemyAnimation.ATTACKING, Constants.attackingTime);
        }
        if (selectedIndex < 0) { // -1: the countdown ran out, handled like a wrong answer
            messageLabel.setText("Time's up! The enemy is attacking...");
        } else {
            messageLabel.setText(correct ? "Correct! Attacking..." : "Wrong! The enemy is attacking...");
        }
        submittedIndex = selectedIndex;
        refreshAvailability();
    }

    @Override
    public void onAnswerResolved(Question question, int selectedIndex, Enemy.AnswerResult result) {
        if (result.correct()) {
            enemyAnimator.playAnimation(
                    eraController.getCurrentEnemy().isDefeated() ? EnemyAnimation.KILLED : EnemyAnimation.DAMAGED,
                    Constants.damageTime);
        } else if (result.playerOutcome() != Player.DamageOutcome.BLOCKED) {
            playerAnimator.animationHelper(
                    result.playerOutcome() == Player.DamageOutcome.GAME_OVER ? Animation.KILLED : Animation.DAMAGED,
                    Constants.damageTime);
        }

        StringBuilder message = new StringBuilder();
        if (result.correct()) {
            message.append("Correct! The enemy lost ").append(result.damageToEnemy()).append(" HP.");
        } else {
            message.append(battle.isTimedOut() ? "Time's up! " : "Wrong! ");
            switch (result.playerOutcome()) {
                case BLOCKED ->
                    message.append("Your shield blocked ").append(result.damageToPlayer()).append(" damage.");
                case DAMAGED -> message.append("You took ").append(result.damageToPlayer()).append(" damage.");
                case LIFE_LOST -> {
                    message.append("You took ").append(result.damageToPlayer()).append(" damage and lost a life!");
                    lifeLostPending = true;
                }
                case GAME_OVER -> message.append("That was your last life.");
                case NONE -> {
                }
            }
        }
        String explanation = question.getExplanation();
        if (result.correct() && explanation != null) {
            message.append("\n").append(explanation);
        }
        messageLabel.setText(message.toString());

        // show the right answer in green; Previous / Next still lets the player look at
        // the choice they picked (red)
        resolved = true;
        submittedIndex = selectedIndex;
        if (selectedIndex >= 0) { // on a timeout there is no picked choice, keep the one being browsed
            choiceIndex = selectedIndex;
        }
        showChoice();
        refreshHud();
    }

    @Override
    public void onEnemyDefeated(Enemy enemy) {
        AudioController.getInstance().playSfx(AudioRegistry.SFX_BATTLE_ENEMY_DEFEATED);
    }

    // ---- life lost panel ----

    /**
     * Freezes the battle and opens the "You lost a life" overlay. Its button
     * (LifeLostController) goes back to the map.
     */
    private void showLifeLostPanel() {
        inputLocked = true;
        battlePane.setDisable(true); // also stops keyboard focus / Space / Enter reaching the battle buttons
        pauseButton.setDisable(true);
        timerLabel.setVisible(false);
        refreshAvailability();
        sceneNavigator.showLifeLost();
    }

    // ---- the choice box ----

    /** Draws the choice at choiceIndex like "A. statement". */
    private void showChoice() {
        choicesLabel.getStyleClass().removeAll(CHOICE_CORRECT_CLASS, CHOICE_WRONG_CLASS);
        if (currentQuestion == null) {
            choicesLabel.setText("");
            refreshAvailability();
            return;
        }
        String text = LETTERS[choiceIndex] + ". " + currentQuestion.getChoices().get(choiceIndex);
        if (battle.isEliminated(choiceIndex)) {
            text += "\n(removed by Debug)";
        }
        choicesLabel.setText(text);
        choicesScrollPane.setVvalue(0);

        if (resolved) {
            if (choiceIndex == submittedIndex) {
                choicesLabel.getStyleClass().add(
                        currentQuestion.isAnswerCorrect(submittedIndex) ? CHOICE_CORRECT_CLASS : CHOICE_WRONG_CLASS);
            }
        }
        refreshAvailability();
    }

    // ---- player input ----

    @FXML
    public void nextChoice() {
        if (!inputLocked && currentQuestion != null && choiceIndex < currentQuestion.getChoices().size() - 1) {
            choiceIndex++;
            showChoice();
        }
    }

    @FXML
    public void prevChoice() {
        if (!inputLocked && currentQuestion != null && choiceIndex > 0) {
            choiceIndex--;
            showChoice();
        }
    }

    /** Locks in the choice that is currently shown. */
    @FXML
    public void selectChoice() {
        if (!inputLocked && currentQuestion != null && battle.submitAnswer(choiceIndex)) {
            submittedIndex = choiceIndex;
            refreshAvailability(); // lock the button right away so a double-click can't answer twice
        }
    }

    @FXML
    public void useDebug() {
        if (inputLocked) {
            return;
        }
        BattleController.LifelineResult result = battle.useDebug();
        messageLabel.setText(result.used()
                ? "Debug removed choice " + LETTERS[result.choiceIndex()] + "."
                : "Debug can't be used on this question.");
        showChoice();
        refreshHud();
    }

    @FXML
    public void useDatabase() {
        if (inputLocked) {
            return;
        }
        BattleController.LifelineResult result = battle.useDatabase();
        messageLabel.setText(result.used()
                ? "Database suggests: " + LETTERS[result.choiceIndex()]
                : "You can't use the database right now.");
        refreshHud();
    }

    @FXML
    public void useConsult() {
        if (inputLocked) {
            return;
        }
        BattleController.LifelineResult result = battle.useConsultProgrammer();

        messageLabel.setText(result.used()
                ? eraController.getEra().getProgrammer() + ": \"" + result.hint() + "\""
                : "The programmer has no hint for this question.");
        refreshHud();
    }

    @FXML
    public void useSave() {
        if (inputLocked) {
            return;
        }
        BattleController.LifelineResult result = battle.useSave();
        messageLabel.setText(result.used()
                ? "Shield armed: your next wrong answer deals no damage."
                : "You can't use Save right now.");
        refreshHud();
    }

    @FXML
    public void pause() {
        if (inputLocked || session.getPlayer().isGameOver()) {
            return; // a panel is up, or the last life is gone and the game over panel is on its way (the save is wiped when it opens): Esc must not open the pause menu
        }
        Constants.paused = !Constants.paused;
    }

    // ---- drawing the numbers ----

    private void refreshHud() {
        Player player = session.getPlayer();
        playerStatsLabel.setText(player.getName()
                + "\nHP: " + player.getCurrentHealth() + " / " + player.getMaxHealth()
                + "\nLives: " + player.getLives() + " / " + Player.MAX_LIVES
                + (player.getToolBox().isSaveShieldActive() ? "\nShield: ON" : ""));

        Enemy enemy = eraController.getCurrentEnemy();
        enemyStatsLabel.setText(enemy.getName() + " (" + enemy.getDifficultyLabel() + ")"
                + "\nHP: " + enemy.getCurrentHealth() + " / " + enemy.getMaxHealth()
                + "\nTheory: " + enemy.getTheoreticalAnswersGiven() + " / " + enemy.getRequiredTheoreticalAnswers()
                + "   Code: " + enemy.getProgrammingAnswersGiven() + " / " + enemy.getRequiredProgrammingAnswers());

        ToolBox toolBox = player.getToolBox();
        debugButton.setText("Debug (" + toolBox.getRemainingUses(ToolBox.Lifeline.DEBUG) + ")");
        databaseButton.setText("Database (" + toolBox.getRemainingUses(ToolBox.Lifeline.DATABASE) + ")");
        consultButton.setText("Consult (" + toolBox.getRemainingUses(ToolBox.Lifeline.CONSULT_PROGRAMMER) + ")");
        saveButton.setText("Save (" + toolBox.getRemainingUses(ToolBox.Lifeline.SAVE) + ")");

        refreshAvailability();
    }

    /**
     * Choices and lifelines only work while a question is waiting for an answer.
     */
    private void refreshAvailability() {
        boolean canAnswer = !inputLocked && battle.isAcceptingAnswers() && currentQuestion != null;
        ToolBox toolBox = session.getPlayer().getToolBox();
        boolean browsing = currentQuestion != null;
        prevButton.setDisable(!browsing || choiceIndex <= 0);
        nextButton.setDisable(!browsing || choiceIndex >= currentQuestion.getChoices().size() - 1);
        selectButton.setDisable(!canAnswer || battle.isEliminated(choiceIndex));
        debugButton.setDisable(!canAnswer || !toolBox.isAvailable(ToolBox.Lifeline.DEBUG));
        databaseButton.setDisable(!canAnswer || !toolBox.isAvailable(ToolBox.Lifeline.DATABASE));
        consultButton.setDisable(!canAnswer || !toolBox.isAvailable(ToolBox.Lifeline.CONSULT_PROGRAMMER));
        saveButton.setDisable(!canAnswer || !toolBox.canUseSave());
    }

    // sets the background image based on the era that we're currently in
    public void setBackground() {
        String path = "/assets/bg/era" + eraController.getEraNumber() + "background.png";

        URL resource = getClass().getResource(path);
        if (resource == null) {
            System.err.println("Image from " + path + " cannot be found");
            return;
        }

        eraBackground.setImage(new Image(resource.toExternalForm()));
        eraBackground.setPreserveRatio(false);
        eraBackground.setFitWidth(1920);
        eraBackground.setFitHeight(564);
        eraBackground.setSmooth(false);
    }

    /* Sees if the player collected all of the components and win the game */
    private boolean isGameCompleted() {
        return session.getPlayer().getCollectedComponents().size() >= Era.TOTAL_ERAS;
    }

    private void showGameOverPanel() {
        discardSaveAfterGameOver(); // before the overlay, so closing the window on it can't keep the save
        inputLocked = true;
        battlePane.setDisable(true);
        pauseButton.setDisable(true);
        timerLabel.setVisible(false);
        refreshAvailability();
        sceneNavigator.showGameLost(); // your overlay's real method name
    }

    /**
     * Game over wipes the save (save.json, its backup and temp file) so Continue can't go back to the last checkpoint, and drops the
     * session from memory. Runs once. profile.json (the tutorial flag) is not touched. Never throws: if the files can't be deleted
     * it is logged and the game goes on (the player may then still see Continue).
     */
    private void discardSaveAfterGameOver() {
        if (saveDiscarded) {
            return;
        }
        saveDiscarded = true;
        SaveManager saves = SaveManager.getInstance();
        try {
            if (saves.isTutorialCompleted()) {
                saves.markTutorialCompleted(); // an old player may only have the flag implied by the save that is about to go
            }
            saves.delete();
        } catch (IOException | RuntimeException e) {
            System.err.println("[Gameplay] Could not delete the save after game over: " + e);
        }
        if (GameSession.current() == session) {
            GameSession.setCurrent(null); // nothing may keep playing (or re-saving) the dead run
        }
    }
}

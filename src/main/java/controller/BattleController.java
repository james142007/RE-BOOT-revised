package controller;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;

import data.Constants;
import data.SaveData;
import model.character.Player;
import model.enemy.Enemy;
import model.question.Question;
import model.toolbox.ToolBox;

/**
 * One fight: the player vs one enemy. Same loop as the tutorial battle but works with any enemy/question source, no JavaFX in it.
 * The answer is only applied at the end of the attack animation, so the health bars match what the player sees.
 */
public class BattleController implements Battle {

    public enum Phase {
        IDLE,               // no enemy yet
        ASKING,             // a question is on screen, waiting for the player
        PLAYER_ATTACKING,   // correct answer: the player's attack plays (Constants.attackingTime)
        ENEMY_DAMAGED,      // the hit lands on the enemy (Constants.damageTime)
        ENEMY_ATTACKING,    // wrong answer: the enemy's attack plays
        PLAYER_DAMAGED,     // the hit lands on the player
        ENEMY_DEFEATED,     // terminal: enemy is down
        PLAYER_DEFEATED     // terminal: the player is out of lives
    }

    /** Everything the screen / era controller might want to know. All methods are optional. */
    public interface Listener {
        /** A new question is on screen. */
        default void onQuestionAsked(Question question, Enemy enemy) {}

        /** Player locked in an answer, attack animation starts now. selectedIndex is -1 if time ran out. */
        default void onAnswerSubmitted(Question question, int selectedIndex, boolean correct) {}

        /** Answer has been applied. Check result.playerOutcome() before showing "-HP" (it may be BLOCKED). */
        default void onAnswerResolved(Question question, int selectedIndex, Enemy.AnswerResult result) {}

        default void onEnemyDefeated(Enemy enemy) {}

        default void onPlayerDefeated() {}

        /** A lifeline was actually used (not called when it was refused). The question state already reflects it. */
        default void onLifelineUsed(LifelineResult result) {}
    }
    public record LifelineResult(ToolBox.Lifeline lifeline, boolean used, int choiceIndex, String hint,
            String programmerName) {
        static LifelineResult notUsed(ToolBox.Lifeline lifeline) {
            return new LifelineResult(lifeline, false, -1, null, null);// set the programmer name based on the era/ get in json : to be done
        }
    }

    /** Used when no era gives a programmer (like in tests). */
    private static final String DEFAULT_PROGRAMMER_NAME = "A programmer";

    private final Player player;
    private final QuestionController questions;
    private final Random random;
    private final String programmerName;
    private final List<Listener> listeners = new ArrayList<>();

    private Enemy enemy;
    private Phase phase = Phase.IDLE;
    private Question question;
    private int pendingChoice = -1;     // set by submitAnswer, consumed by playerIsAnswering
    private int selectedIndex = -1;
    private final Set<Integer> eliminated = new HashSet<>();   // choices removed by Debug
    private Enemy.AnswerResult lastResult;
    private double timer;
    private double timeLimit;           // seconds allowed for the current question; 0 = no limit
    private double timeRemaining;       // countdown for the current question, only ticks in ASKING
    private boolean timedOut;           // true if the current question was failed by running out of time
    private int databaseSuggestion = -1;    // choice Database pointed at on the current question, -1 = not used (kept so a resume can show it again)
    private boolean consultUsed;            // Consult a Programmer was used on the current question

    /** The era's programmer gives the hint for Consult a Programmer. Blank or null name falls back to a generic one. */
    public BattleController(Player player, QuestionController questions, Random random, String programmerName) {
        this.player = Objects.requireNonNull(player, "player must not be null");
        this.questions = Objects.requireNonNull(questions, "questions must not be null");
        this.random = Objects.requireNonNull(random, "random must not be null");
        this.programmerName = (programmerName == null || programmerName.isBlank())
                ? DEFAULT_PROGRAMMER_NAME : programmerName;
    }

    public BattleController(Player player, QuestionController questions, Random random) {
        this(player, questions, random, null);
    }

    public BattleController(Player player, QuestionController questions) {
        this(player, questions, new Random());
    }

    public void addListener(Listener listener) {
        listeners.add(Objects.requireNonNull(listener));
    }

    // ---- spawning / clearing an enemy ----

    /** Spawns the enemy and asks the first question. */
    public void startBattle(Enemy enemy) {
        this.enemy = Objects.requireNonNull(enemy, "enemy must not be null");
        if (enemy.isDefeated()) {
            throw new IllegalArgumentException(enemy.getName() + " is already defeated");
        }
        askNextQuestion();
    }

    /** Clears the current enemy and goes back to IDLE. */
    public void endBattle() {
        enemy = null;
        question = null;
        pendingChoice = -1;
        selectedIndex = -1;
        eliminated.clear();
        lastResult = null;
        timer = 0;
        timeLimit = 0;
        timeRemaining = 0;
        timedOut = false;
        databaseSuggestion = -1;
        consultUsed = false;
        phase = Phase.IDLE;
    }

    private void askNextQuestion() {
        question = questions.nextQuestionFor(enemy);
        shuffleChoices(question);
        pendingChoice = -1;
        selectedIndex = -1;
        eliminated.clear();
        lastResult = null;
        timer = 0;
        timeLimit = enemy.getTimeLimitSeconds(question.getQuestionType());
        timeRemaining = timeLimit;
        timedOut = false;
        databaseSuggestion = -1;
        consultUsed = false;
        phase = Phase.ASKING;
        announceQuestion();
    }

    private void announceQuestion() {
        fire(new Consumer<Listener>() {
            @Override
            public void accept(Listener l) {
                l.onQuestionAsked(question, enemy);
            }
        });
    }

    private void shuffleChoices(Question q) {
        List<String> old = q.getChoices();
        int size = old.size();
        if (size < 2) {
            return;
        }
        List<Integer> order = new ArrayList<>(size);   // order.get(newPosition) = oldPosition
        for (int i = 0; i < size; i++) {
            order.add(i);
        }
        Collections.shuffle(order, random);
        boolean unchanged = true;
        for (int i = 0; i < size; i++) {
            if (order.get(i) != i) {
                unchanged = false;
                break;
            }
        }
        if (unchanged) {
            Collections.rotate(order, 1);   // guarantees a different order
        }

        int oldCorrect = q.getCorrectChoiceIndex();
        int newCorrect = oldCorrect;
        List<String> shuffled = new ArrayList<>(size);
        for (int pos = 0; pos < size; pos++) {
            int from = order.get(pos);
            shuffled.add(old.get(from));
            if (from == oldCorrect) {
                newCorrect = pos;
            }
        }
        q.setChoices(shuffled, newCorrect);
    }

    // ---- the player's turn ----

    /** True if a question is waiting for an answer and none is picked yet. */
    public boolean isAcceptingAnswers() {
        return phase == Phase.ASKING && pendingChoice < 0;
    }

    /**
     * Locks in the player's answer, picked up on the next update().
     * Returns false if refused (no question open, already answered, bad or removed choice).
     */
    public boolean submitAnswer(int choiceIndex) {
        if (!isAcceptingAnswers()) {
            return false;
        }
        if (choiceIndex < 0 || choiceIndex >= question.getChoices().size() || eliminated.contains(choiceIndex)) {
            return false;
        }
        pendingChoice = choiceIndex;
        return true;
    }

    // ---- lifelines (only while a question is waiting for an answer) ----

    /** Debug: removes one wrong choice. Needs at least two wrong choices left so it's still a question. */
    public LifelineResult useDebug() {
        ToolBox toolBox = player.getToolBox();
        if (!isAcceptingAnswers() || !toolBox.isAvailable(ToolBox.Lifeline.DEBUG)) {
            return LifelineResult.notUsed(ToolBox.Lifeline.DEBUG);
        }
        List<Integer> candidates = wrongChoicesStillShown();
        if (candidates.size() < 2) {
            return LifelineResult.notUsed(ToolBox.Lifeline.DEBUG);
        }
        toolBox.useDebug();
        int removed = candidates.get(random.nextInt(candidates.size()));
        eliminated.add(removed);
        LifelineResult result = new LifelineResult(ToolBox.Lifeline.DEBUG, true, removed, null, null);
        fireLifelineUsed(result);
        return result;
    }

    /**
     * Database: suggests one choice, correct with the chance set by the toolbox's consult accuracy, otherwise a wrong one.
     * Never suggests a choice Debug already removed.
     */
    public LifelineResult useDatabase() {
        ToolBox toolBox = player.getToolBox();
        if (!isAcceptingAnswers() || !toolBox.isAvailable(ToolBox.Lifeline.DATABASE)) {
            return LifelineResult.notUsed(ToolBox.Lifeline.DATABASE);
        }
        toolBox.useDatabase();
        int suggestion = question.getCorrectChoiceIndex();
        List<Integer> wrong = wrongChoicesStillShown();
        if (!wrong.isEmpty() && random.nextDouble() >= toolBox.getConsultProgrammerAccuracy()) {
            suggestion = wrong.get(random.nextInt(wrong.size()));
        }
        databaseSuggestion = suggestion;
        LifelineResult result = new LifelineResult(ToolBox.Lifeline.DATABASE, true, suggestion, null, null);
        fireLifelineUsed(result);
        return result;
    }

    /** Consult a Programmer: the era's programmer gives the question's hint (result has the hint and name). No hint = nothing spent. */
    public LifelineResult useConsultProgrammer() {
        ToolBox toolBox = player.getToolBox();
        if (!isAcceptingAnswers() || !toolBox.isAvailable(ToolBox.Lifeline.CONSULT_PROGRAMMER)) {
            return LifelineResult.notUsed(ToolBox.Lifeline.CONSULT_PROGRAMMER);
        }
        String hint = question.getHint();
        if (hint == null || hint.isBlank()) {
            return LifelineResult.notUsed(ToolBox.Lifeline.CONSULT_PROGRAMMER);
        }
        toolBox.useConsultProgrammer();
        consultUsed = true;
        LifelineResult result = new LifelineResult(ToolBox.Lifeline.CONSULT_PROGRAMMER, true, -1, hint, programmerName);
        fireLifelineUsed(result);
        return result;
    }

    /** Save: the next wrong answer deals no damage. */
    public LifelineResult useSave() {
        if (!isAcceptingAnswers()) {
            return LifelineResult.notUsed(ToolBox.Lifeline.SAVE);
        }
        boolean armed = player.getToolBox().useSave();
        LifelineResult result = new LifelineResult(ToolBox.Lifeline.SAVE, armed, -1, null, null);
        if (armed) {
            fireLifelineUsed(result);
        }
        return result;
    }

    private void fireLifelineUsed(final LifelineResult result) {
        fire(new Consumer<Listener>() {
            @Override
            public void accept(Listener l) {
                l.onLifelineUsed(result);
            }
        });
    }

    private List<Integer> wrongChoicesStillShown() {
        List<Integer> wrong = new ArrayList<>();
        for (int i = 0; i < question.getChoices().size(); i++) {
            if (i != question.getCorrectChoiceIndex() && !eliminated.contains(i)) {
                wrong.add(i);
            }
        }
        return wrong;
    }

    // ---- saving / resuming the question on screen ----

    /**
     * Snapshot of the question on screen for the mid-era save. Returns null when there is nothing to restore: no question is open,
     * or the answer was already applied (then the next question is simply drawn after the resume).
     * An answer that is locked in but not applied yet is saved as submitted and replayed, so it is applied exactly once and can't be changed.
     */
    public SaveData.QuestionData captureQuestion() {
        Integer submitted;
        switch (phase) {
            case ASKING -> {
                if (pendingChoice >= 0) {
                    submitted = pendingChoice; // locked in, picked up on the next update()
                } else {
                    submitted = null;
                }
            }
            case PLAYER_ATTACKING, ENEMY_ATTACKING -> {
                if (timedOut) {
                    submitted = -1;
                } else {
                    submitted = selectedIndex;
                }
            }
            default -> {
                return null;
            }
        }
        List<Integer> removed = new ArrayList<>(eliminated);
        Collections.sort(removed);
        return new SaveData.QuestionData(question.getQuestionId(), new ArrayList<>(question.getChoices()),
                question.getCorrectChoiceIndex(), removed, timeLimit > 0 ? timeRemaining : 0, submitted,
                databaseSuggestion >= 0 ? databaseSuggestion : null, consultUsed);
    }

    /**
     * Resumes a saved question instead of drawing a new one: same choices, same removed choices, same time left.
     * The caller has already put the question's choices in the saved order and checked the data (EraController does).
     * A locked-in answer is picked up again on the next update(), a timeout fires again immediately.
     * Fires onQuestionAsked like a normal start, so the screen draws it.
     */
    public void resumeBattle(Enemy enemy, Question question, SaveData.QuestionData saved) {
        this.enemy = Objects.requireNonNull(enemy, "enemy must not be null");
        if (enemy.isDefeated()) {
            throw new IllegalArgumentException(enemy.getName() + " is already defeated");
        }
        this.question = Objects.requireNonNull(question, "question must not be null");
        pendingChoice = -1;
        selectedIndex = -1;
        lastResult = null;
        timer = 0;
        timedOut = false;
        eliminated.clear();
        eliminated.addAll(saved.eliminated());
        timeLimit = enemy.getTimeLimitSeconds(question.getQuestionType());
        timeRemaining = timeLimit > 0 ? Math.max(0, Math.min(timeLimit, saved.timeRemainingSeconds())) : 0;
        databaseSuggestion = saved.databaseSuggestion() == null ? -1 : saved.databaseSuggestion();
        consultUsed = saved.consultUsed();

        Integer submitted = saved.submittedChoice();
        if (submitted != null) {
            if (submitted >= 0) {
                pendingChoice = submitted;
            } else if (timeLimit > 0) {
                timeRemaining = 0; // the countdown had already run out: it fires again on the next update()
            }
        }
        phase = Phase.ASKING;
        announceQuestion();
    }

    // ---- the frame update (same shape as the tutorial's setBattleState) ----

    /** Call once per frame while this battle is active. */
    public void update(double deltaSeconds) {
        setBattleState(deltaSeconds);
    }

    @Override
    public void setBattleState(double deltaSeconds) {
        switch (phase) {
            case ASKING -> {
                playerIsAnswering();                 // an answer already locked in wins over a timeout in the same frame
                if (phase == Phase.ASKING) {
                    tickQuestionTimer(deltaSeconds);
                }
            }
            case PLAYER_ATTACKING -> playerAttack(deltaSeconds);
            case ENEMY_ATTACKING -> enemyAttack(deltaSeconds);
            case ENEMY_DAMAGED -> enemyDamaged(deltaSeconds);
            case PLAYER_DAMAGED -> playerDamaged(deltaSeconds);
            default -> { }   // IDLE, ENEMY_DEFEATED, PLAYER_DEFEATED: nothing to do
        }
    }

    /** ASKING: picks up the answer from submitAnswer(), if there is one. */
    @Override
    public void playerIsAnswering() {
        if (pendingChoice < 0) {
            return;
        }
        selectedIndex = pendingChoice;
        pendingChoice = -1;
        boolean correct = question.isAnswerCorrect(selectedIndex);
        timer = 0;
        phase = correct ? Phase.PLAYER_ATTACKING : Phase.ENEMY_ATTACKING;
        fire(new Consumer<Listener>() {
            @Override
            public void accept(Listener l) {
                l.onAnswerSubmitted(question, selectedIndex, correct);
            }
        });
    }

    /**
     * ASKING: counts the question timer down. Running out of time works like a wrong answer (no choice selected),
     * so the enemy attacks and the normal wrong-answer flow follows.
     */
    private void tickQuestionTimer(double deltaSeconds) {
        if (timeLimit <= 0 || pendingChoice >= 0) {
            return;
        }
        timeRemaining = Math.max(0, timeRemaining - deltaSeconds);
        if (timeRemaining > 0) {
            return;
        }
        selectedIndex = -1;     // no choice: Question.isAnswerCorrect(-1) is always false
        timedOut = true;
        timer = 0;
        phase = Phase.ENEMY_ATTACKING;
        fire(new Consumer<Listener>() {
            @Override
            public void accept(Listener l) {
                l.onAnswerSubmitted(question, -1, false);
            }
        });
    }

    /** PLAYER_ATTACKING: plays the attack, then the hit lands. */
    @Override
    public void playerAttack(double deltaSeconds) {
        timer += deltaSeconds;
        if (timer < Constants.attackingTime) {
            return;
        }
        timer = 0;
        phase = Phase.ENEMY_DAMAGED;
        resolveAnswer();
    }

    /** ENEMY_ATTACKING: plays the enemy's attack, then the hit lands. */
    @Override
    public void enemyAttack(double deltaSeconds) {
        timer += deltaSeconds;
        if (timer < Constants.attackingTime) {
            return;
        }
        timer = 0;
        phase = Phase.PLAYER_DAMAGED;
        resolveAnswer();
    }

    private void enemyDamaged(double deltaSeconds) {
        timer += deltaSeconds;
        if (timer < Constants.damageTime) {
            return;
        }
        timer = 0;
        if (enemy.isDefeated()) {
            phase = Phase.ENEMY_DEFEATED;
            fire(new Consumer<Listener>() {
                @Override
                public void accept(Listener l) {
                    l.onEnemyDefeated(enemy);
                }
            });
        } else {
            askNextQuestion();
        }
    }

    private void playerDamaged(double deltaSeconds) {
        timer += deltaSeconds;
        if (timer < Constants.damageTime) {
            return;
        }
        timer = 0;
        if (player.isGameOver()) {
            phase = Phase.PLAYER_DEFEATED;
            fire(new Consumer<Listener>() {
                @Override
                public void accept(Listener l) {
                    l.onPlayerDefeated();
                }
            });
        } else {
            askNextQuestion();   // a lost life refills HP; the enemy keeps its progress
        }
    }

    /** Applies the chosen answer using the Enemy.resolveAnswer rules. */
    private void resolveAnswer() {
        lastResult = enemy.resolveAnswer(question, selectedIndex, player);
        questions.recordResult(question, lastResult.correct());
        fire(new Consumer<Listener>() {
            @Override
            public void accept(Listener l) {
                l.onAnswerResolved(question, selectedIndex, lastResult);
            }
        });
    }

    private void fire(Consumer<Listener> event) {
        for (Listener listener : new ArrayList<>(listeners)) {   // copy: a listener may react by changing state
            event.accept(listener);
        }
    }

    // ---- state for the screen ----

    public Phase getPhase() {
        return phase;
    }

    public Enemy getEnemy() {
        return enemy;
    }

    public Player getPlayer() {
        return player;
    }

    public Question getQuestion() {
        return question;
    }

    public int getSelectedIndex() {
        return selectedIndex;
    }

    public Enemy.AnswerResult getLastResult() {
        return lastResult;
    }

    //if the current question has a countdown. 
    public boolean hasTimeLimit() {
        return timeLimit > 0;
    }

    //the seconds the current question started with (0 = no limit). 
    public double getQuestionTimeLimit() {
        return timeLimit;
    }

    //the seconds left on the current question; it stops changing once an answer is locked in. 
    public double getQuestionTimeRemaining() {
        return timeRemaining;
    }

    //if the current question was failed because time ran out (its selected index is -1). 
    public boolean isTimedOut() {
        return timedOut;
    }

    //if Debug removed this choice from the current question. 
    public boolean isEliminated(int choiceIndex) {
        return eliminated.contains(choiceIndex);
    }

    //the choice Database pointed at on the current question, -1 if it wasn't used on it. 
    public int getDatabaseSuggestion() {
        return databaseSuggestion;
    }

    //if Consult a Programmer was used on the current question. 
    public boolean isConsultUsed() {
        return consultUsed;
    }
}

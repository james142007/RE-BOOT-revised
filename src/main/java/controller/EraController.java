package controller;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;

import data.Constants;
import data.EraContent;
import data.GameSession;
import data.SaveData;
import model.character.Player;
import model.enemy.Boss;
import model.enemy.Enemy;
import model.map.Era;
import model.question.Question;

public class EraController implements BattleController.Listener {

    public enum Phase {
        ERA_INTRO, // era title + story; waits for beginEra()
        ENCOUNTER_INTRO, // "Encounter 3/7: Scout Bot 3", short timer
        BATTLE, // the BattleController is running
        ENCOUNTER_CLEARED, // enemy down, short timer, then the next encounter
        ERA_CLEARED, // boss down: component collected, game saved; waits for the screen to leave
        GAME_WON, // all seven components are collected, transition to epilogue
        GAME_OVER // out of lives; waits for the screen to leave
    }

    public interface Listener {
        void onPhaseChanged(Phase phase);
    }

    private final GameSession session;
    private final Era era;
    private final BattleController battle;
    private final QuestionController questions;
    private final List<Listener> listeners = new ArrayList<>();

    private Phase phase = Phase.ERA_INTRO;
    private int encounterIndex = 0;
    private double timer = 0;

    // set only by a resume: the question that was open when the player left, asked again once the encounter banner is done
    private Question resumeQuestion;
    private SaveData.QuestionData resumeQuestionData;

    /** Starts the era fresh at its intro. */
    public EraController(GameSession session, EraContent content, Random random) {
        this(session, content, random, null);
    }

    /**
     * Starts the era fresh (run == null) or resumes a saved run. A resume skips the era intro and re-enters the saved encounter through its
     * banner. Throws IllegalArgumentException if the snapshot doesn't match the era content (the caller then starts the era fresh from
     * newly loaded content, because this attempt may have changed the content's objects). The player and stats are only touched when
     * everything else has checked out.
     */
    public EraController(GameSession session, EraContent content, Random random, SaveData.RunData run) {
        this.session = Objects.requireNonNull(session, "session must not be null");
        this.era = Objects.requireNonNull(content, "content must not be null").era();

        List<Enemy> enemies = era.getEnemies();
        if (enemies.isEmpty() || !(enemies.get(enemies.size() - 1) instanceof Boss)) {
            throw new IllegalArgumentException("Era " + era.getEraNumber() + " must end with its boss");
        }
        Player player = session.getPlayer();
        if (player.getCurrentEraNumber() != era.getEraNumber()) {
            throw new IllegalArgumentException("The player is in era " + player.getCurrentEraNumber()
                    + " but era " + era.getEraNumber() + " was loaded (call session.enterEra(n) first)");
        }

        this.questions = new QuestionController(content.questions(), random);
        this.battle = new BattleController(player, questions, random);
        this.battle.addListener(this); // registered first, so the era reacts before the screen does

        if (run != null) {
            restore(run);
        }
    }

    public EraController(GameSession session, EraContent content) {
        this(session, content, new Random());
    }

    /** Resumes a saved run, see the constructor above. */
    public EraController(GameSession session, EraContent content, SaveData.RunData run) {
        this(session, content, new Random(), run);
    }

    public void addListener(Listener listener) {
        listeners.add(Objects.requireNonNull(listener));
    }

    // ---- driving the loop ----

    /** Player pressed "Begin" on the era intro. Does nothing in any other phase. */
    public void beginEra() {
        if (phase != Phase.ERA_INTRO) {
            return;
        }
        encounterIndex = 0;
        enterEncounterIntro();
    }

    /** Call once per frame. */
    public void update(double deltaSeconds) {
        switch (phase) {
            case ENCOUNTER_INTRO -> {
                session.addPlayTime(deltaSeconds);
                timer += deltaSeconds;
                if (timer >= Constants.encounterIntroTime) {
                    startBattle();
                }
            }
            case BATTLE -> {
                session.addPlayTime(deltaSeconds);
                battle.update(deltaSeconds);
            }
            case ENCOUNTER_CLEARED -> {
                session.addPlayTime(deltaSeconds);
                timer += deltaSeconds;
                if (timer >= Constants.encounterClearedTime) {
                    encounterIndex++;
                    enterEncounterIntro();
                }
            }
            default -> {
            } // ERA_INTRO, ERA_CLEARED, GAME_OVER: waiting for the player
        }
    }

    private void enterEncounterIntro() {
        battle.endBattle();
        timer = 0;
        setPhase(Phase.ENCOUNTER_INTRO);
    }

    private void startBattle() {
        Enemy enemy = getCurrentEnemy();
        if (enemy.isDefeated()) {
            // resumed from a save made right after the killing answer: the kill itself wasn't counted yet, do it now (exactly once)
            onEnemyDefeated(enemy);
            return;
        }
        setPhase(Phase.BATTLE);
        if (resumeQuestion != null) {
            Question question = resumeQuestion;
            SaveData.QuestionData saved = resumeQuestionData;
            resumeQuestion = null;
            resumeQuestionData = null;
            battle.resumeBattle(enemy, question, saved);
        } else {
            battle.startBattle(enemy);
        }
    }
    public void gameWon(){
        setPhase(Phase.GAME_WON);
    }
    public void GameLost(){
        setPhase(Phase.GAME_OVER);
    }

    private void setPhase(Phase newPhase) {
        phase = newPhase;
        for (Listener listener : new ArrayList<>(listeners)) {
            listener.onPhaseChanged(newPhase);
        }
    }

    // ---- reacting to the battle ----

    @Override
    public void onAnswerResolved(Question question, int selectedIndex, Enemy.AnswerResult result) {
        session.recordAnswer(result.correct());
        Player.DamageOutcome outcome = result.playerOutcome();
        if (outcome == Player.DamageOutcome.LIFE_LOST) {
            // The lost life must reach disk now: a checkpoint drops the era in progress (the era restarts, see LifeLostController)
            // and stores the new life count, so Continue after leaving via the main menu doesn't hand the life back.
            if (!session.checkpoint()) {
                session.clearRun(); // save failed: at least don't leave a stale snapshot that would hand the life back
            }
        } else if (outcome == Player.DamageOutcome.GAME_OVER) {
            session.clearRun(); // the checkpoint is deliberately left alone: Continue still loads the last one
        } else {
            saveRun(); // autosave: the answer and its damage are applied, the decks are up to date
        }
    }

    @Override
    public void onLifelineUsed(BattleController.LifelineResult result) {
        saveRun(); // autosave, so quitting can't hand a used lifeline (and what it revealed) back
    }

    @Override
    public void onEnemyDefeated(Enemy enemy) {
        timer = 0;
        if (enemy instanceof Boss boss) {
            session.completeCurrentEra(boss); // collects the component, marks the era done, unlocks the next
            era.markCompleted();
            session.checkpoint(); // also drops the era in progress
            setPhase(Phase.ERA_CLEARED);
        } else {
            session.recordEnemyDefeated();
            setPhase(Phase.ENCOUNTER_CLEARED);
            saveRun(); // after setPhase: saved as "the next encounter", the kill is already counted
        }
    }

    @Override
    public void onPlayerDefeated() {
        // The checkpoint is deliberately not touched: Continue still loads the last one. Only the era in progress is dropped.
        session.clearRun();
        setPhase(Phase.GAME_OVER);
    }

    // ---- saving / resuming the era in progress ----

    /**
     * Mid-era save. Called by the autosave hooks above and by the pause menu's exit buttons (through GameplayController).
     * Does nothing when there is no stable moment to save (era intro, era over). Returns true if it reached disk.
     */
    public boolean saveRun() {
        SaveData.RunData run = captureRun();
        return run != null && session.saveRun(run);
    }

    /**
     * The era as a snapshot, normalized to a moment that can be resumed without losing or repeating anything:
     * a question that is open (or answered but not applied yet) is saved as it is, an answer that is already applied
     * resumes at the next question, and a cleared encounter resumes at the next one. Null = nothing to save.
     */
    private SaveData.RunData captureRun() {
        int index = encounterIndex;
        Phase savedPhase;
        SaveData.QuestionData question = null;
        switch (phase) {
            case ENCOUNTER_INTRO -> {
                // right after a resume the saved question is still waiting for the banner to finish: keep it
                question = resumeQuestionData;
                savedPhase = question != null ? Phase.BATTLE : Phase.ENCOUNTER_INTRO;
            }
            case ENCOUNTER_CLEARED -> {
                savedPhase = Phase.ENCOUNTER_INTRO;
                index = encounterIndex + 1;
            }
            case BATTLE -> {
                Enemy.AnswerResult last = battle.getLastResult();
                if (last != null && (last.playerOutcome() == Player.DamageOutcome.LIFE_LOST
                        || last.playerOutcome() == Player.DamageOutcome.GAME_OVER)) {
                    return null; // that answer already cleared the run, the era restarts
                }
                savedPhase = Phase.BATTLE;
                question = battle.captureQuestion();
            }
            default -> {
                return null; // ERA_INTRO: nothing has happened yet. ERA_CLEARED / GAME_WON / GAME_OVER: the run is over
            }
        }
        List<Enemy> enemies = era.getEnemies();
        if (index >= enemies.size()) {
            return null;
        }

        List<SaveData.EnemyProgressData> progress = new ArrayList<>();
        for (Enemy enemy : enemies) {
            progress.add(new SaveData.EnemyProgressData(enemy.getTheoreticalAnswersGiven(),
                    enemy.getProgrammingAnswersGiven()));
        }
        return new SaveData.RunData(era.getEraNumber(), savedPhase.name(), index, progress,
                session.capturePlayerState(), session.captureStats(), questions.captureDecks(), question);
    }

    /**
     * Puts this (freshly built) era back to a saved run. Always resumes through ENCOUNTER_INTRO: the banner shows where the player is,
     * then the saved question (or the next one) is asked. Everything is checked before the live player is touched.
     */
    private void restore(SaveData.RunData run) {
        if (run.eraNumber() != era.getEraNumber()) {
            throw new IllegalArgumentException("snapshot is for era " + run.eraNumber() + ", not era " + era.getEraNumber());
        }
        Phase savedPhase;
        try {
            savedPhase = Phase.valueOf(run.phase());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("unknown phase \"" + run.phase() + "\"");
        }
        if (savedPhase != Phase.ENCOUNTER_INTRO && savedPhase != Phase.BATTLE) {
            throw new IllegalArgumentException("a run can't be saved in phase " + savedPhase);
        }

        List<Enemy> enemies = era.getEnemies();
        int index = run.encounterIndex();
        if (index < 0 || index >= enemies.size()) {
            throw new IllegalArgumentException("encounter " + index + " doesn't exist in era " + era.getEraNumber());
        }
        if (run.enemies().size() != enemies.size()) {
            throw new IllegalArgumentException("snapshot has " + run.enemies().size() + " enemies, the era has " + enemies.size());
        }
        for (int i = 0; i < enemies.size(); i++) {
            SaveData.EnemyProgressData saved = run.enemies().get(i);
            if (saved == null) {
                throw new IllegalArgumentException("missing progress for enemy " + i);
            }
            enemies.get(i).restoreProgress(saved.theoreticalAnswersGiven(), saved.programmingAnswersGiven());
        }

        if (run.deck() != null) {
            questions.restoreDecks(run.deck()); // no deck saved = the decks stay freshly shuffled
        }
        if (run.question() != null) {
            Enemy current = enemies.get(index);
            if (current.isDefeated()) {
                throw new IllegalArgumentException("a defeated enemy can't have an open question");
            }
            resumeQuestion = rebuildQuestion(run.question(), current);
            resumeQuestionData = run.question();
        }

        session.applyRunState(run); // last: the only step that changes the live player and stats
        encounterIndex = index;
        phase = Phase.ENCOUNTER_INTRO;
        timer = 0;
    }

    /**
     * Finds the saved question in the bank and puts its choices back in the saved order, after checking that it is still the same
     * question (same id, same choice texts, correct answer still the same one) and that it fits the enemy.
     */
    private Question rebuildQuestion(SaveData.QuestionData saved, Enemy enemy) {
        Question question = questions.find(saved.id());
        if (question == null) {
            throw new IllegalArgumentException("question " + saved.id() + " no longer exists");
        }
        if (question.getQuestionType() != questions.nextTypeFor(enemy)) {
            throw new IllegalArgumentException("question " + saved.id() + " doesn't fit " + enemy.getName() + "'s progress");
        }

        List<String> original = new ArrayList<>(question.getChoices());
        List<String> choices = saved.choices();
        int size = original.size();
        if (choices.size() != size) {
            throw new IllegalArgumentException("question " + saved.id() + " has a different number of choices now");
        }
        List<String> sortedOriginal = new ArrayList<>(original);
        List<String> sortedSaved = new ArrayList<>(choices);
        Collections.sort(sortedOriginal);
        Collections.sort(sortedSaved);
        if (!sortedOriginal.equals(sortedSaved)) {
            throw new IllegalArgumentException("question " + saved.id() + " has different choices now");
        }
        int correct = saved.correctChoiceIndex();
        if (correct < 0 || correct >= size
                || !choices.get(correct).equals(original.get(question.getCorrectChoiceIndex()))) {
            throw new IllegalArgumentException("question " + saved.id() + " has a different correct answer now");
        }

        for (Integer removed : saved.eliminated()) {
            if (removed == null || removed < 0 || removed >= size || removed == correct) {
                throw new IllegalArgumentException("bad removed choice " + removed + " for question " + saved.id());
            }
        }
        Integer submitted = saved.submittedChoice();
        if (submitted != null && (submitted < -1 || submitted >= size || saved.eliminated().contains(submitted))) {
            throw new IllegalArgumentException("bad submitted choice " + submitted + " for question " + saved.id());
        }
        Integer suggestion = saved.databaseSuggestion();
        if (suggestion != null && (suggestion < 0 || suggestion >= size)) {
            throw new IllegalArgumentException("bad Database suggestion " + suggestion + " for question " + saved.id());
        }
        double time = saved.timeRemainingSeconds();
        if (Double.isNaN(time) || Double.isInfinite(time) || time < 0) {
            throw new IllegalArgumentException("bad time left " + time + " for question " + saved.id());
        }

        question.setChoices(choices, correct); // only now, after every check passed
        return question;
    }

    // ---- state for the screen ----

    public Phase getPhase() {
        return phase;
    }

    public Era getEra() {
        return era;
    }

    public BattleController getBattle() {
        return battle;
    }

    public GameSession getSession() {
        return session;
    }

    public Enemy getCurrentEnemy() {
        List<Enemy> enemies = era.getEnemies();
        return enemies.get(Math.min(encounterIndex, enemies.size() - 1));
    }

    /** Number of the current fight, starting at 1. */
    public int getEncounterNumber() {
        return Math.min(encounterIndex, era.getEnemies().size() - 1) + 1;
    }

    public int getEncounterCount() {
        return era.getEnemies().size();
    }

    public boolean isBossEncounter() {
        return getCurrentEnemy() instanceof Boss;
    }
    public int getEraNumber(){
        return era.getEraNumber();
    }


}

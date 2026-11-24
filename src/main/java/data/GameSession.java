package data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;

import model.character.Component;
import model.character.Player;
import model.enemy.Boss;
import model.map.Era;
import model.toolbox.ToolBox;

/**
 * Live progress of the current playthrough: the player plus what must survive a restart (unlocked/completed eras, tutorial flag, stats).
 * Controllers read and update it, SaveManager turns it into a file and back. Only one session at a time.
 */
public final class GameSession {

    /** Lifetime counters shown in a stats screen. */
    public static final class Stats {
        private int questionsAnswered;
        private int correctAnswers;
        private int enemiesDefeated;
        private int bossesDefeated;
        private double playTimeSeconds;

        public int getQuestionsAnswered() {
            return questionsAnswered;
        }

        public int getCorrectAnswers() {
            return correctAnswers;
        }

        public int getEnemiesDefeated() {
            return enemiesDefeated;
        }

        public int getBossesDefeated() {
            return bossesDefeated;
        }

        public long getPlayTimeSeconds() {
            return (long) playTimeSeconds;
        }

        /** correct / answered, or 0 if nothing is answered yet. */
        public double getAccuracy() {
            return questionsAnswered == 0 ? 0.0 : (double) correctAnswers / questionsAnswered;
        }
    }

    private static GameSession current;

    private final Player player;
    private final Stats stats = new Stats();
    private final SortedSet<Integer> completedEras = new TreeSet<>();
    private int highestUnlockedEra = 1;
    private boolean tutorialCompleted;
    private SaveData.RunData pendingRun; // era in progress read from the save, waiting for the player to enter that era again

    public GameSession(Player player) {
        this.player = Objects.requireNonNull(player, "player must not be null");
    }

    // ---- the one active session ----

    /** The active session, or null before New Game / Continue. */
    public static GameSession current() {
        return current;
    }

    public static void setCurrent(GameSession session) {
        current = session;
    }

    /** Creates a fresh playthrough and makes it the active session. */
    public static GameSession startNewGame(String playerName) {
        GameSession session = new GameSession(new Player(playerName));
        if (SaveManager.getInstance().isTutorialCompleted()) {
            session.markTutorialCompleted(); // keep the per-save flag in step with the profile
        }
        current = session;
        return session;
    }

    // ---- gameplay hooks (call these from your controllers) ----

    /** Call after every answered question (works for normal enemies and bosses). */
    public void recordAnswer(boolean correct) {
        stats.questionsAnswered++;
        if (correct)
            stats.correctAnswers++;
    }

    /** Call when a non-boss enemy is defeated. */
    public void recordEnemyDefeated() {
        stats.enemiesDefeated++;
    }

    /** Call from the game loop with the frame delta while the player is playing. */
    public void addPlayTime(double deltaSeconds) {
        if (deltaSeconds > 0)
            stats.playTimeSeconds += deltaSeconds;
    }

    public void markTutorialCompleted() {
        tutorialCompleted = true;
    }

    /**
     * Rewards for beating the current era's boss: collects its component, marks the era done, unlocks the next.
     * Doesn't write to disk, call checkpoint() after.
     */
    public void completeCurrentEra(Boss boss) {
        Objects.requireNonNull(boss, "boss must not be null");
        Component reward = boss.claimReward();
        if (reward != null) {
            player.collectComponent(reward);
        }
        int era = player.getCurrentEraNumber();
        completedEras.add(era);
        stats.bossesDefeated++;
        if (era < Era.TOTAL_ERAS) {
            highestUnlockedEra = Math.max(highestUnlockedEra, era + 1);
        }
    }

    /**
     * Enters an unlocked era from the map. If the save holds an era in progress for this era it is kept for GameplayController to resume
     * (nothing is refilled, the snapshot brings the toolbox with it); otherwise any old snapshot is dropped and the era starts fresh
     * (refills the toolbox via Player.startEra). Throws IllegalArgumentException if it's still locked.
     */
    public void enterEra(int eraNumber) {
        if (eraNumber < 1 || eraNumber > highestUnlockedEra) {
            throw new IllegalArgumentException("Era " + eraNumber + " is locked");//problem is that instead of an exception, the level should just be unclickable
        }
        if (pendingRun != null && pendingRun.eraNumber() == eraNumber) {
            player.setCurrentEraNumber(eraNumber); // not startEra(): that would refill the toolbox
            return;
        }
        clearRun(); // the player picked another era (or there was nothing to resume)
        player.startEra(eraNumber);
    }

    /** Saves this session as a new checkpoint. Never throws (a failed save mustn't crash the game). Returns true if it reached disk. */
    public boolean checkpoint() {
        if (player.isGameOver()) {
            return false; // out of lives: that run is over and its save is deleted, never write it again
        }
        try {
            SaveManager.getInstance().save(this);
            pendingRun = null; // a checkpoint on disk never carries an era in progress
            return true;
        } catch (Exception e) {
            System.err.println("[SaveManager] Save failed: " + e);
            return false;
        }
    }

    // ---- era in progress (mid-era save) ----

    /** The saved era in progress that enterEra() decided to resume, or null for a fresh start. */
    public SaveData.RunData getPendingRun() {
        return pendingRun;
    }

    /**
     * Writes the era in progress to disk next to the last checkpoint, which stays as it is (so dying still goes back to it).
     * Never throws. Returns true if it reached disk.
     */
    public boolean saveRun(SaveData.RunData run) {
        if (player.isGameOver()) {
            return false; // same as checkpoint(): a finished run must not come back on disk
        }
        try {
            SaveManager.getInstance().saveRun(run);
            return true;
        } catch (Exception e) {
            System.err.println("[SaveManager] Run save failed: " + e);
            return false;
        }
    }

    /** Drops the era in progress, in memory and on disk. The checkpoint is left alone. Never throws. */
    public void clearRun() {
        pendingRun = null;
        try {
            SaveManager.getInstance().clearRun();
        } catch (Exception e) {
            System.err.println("[SaveManager] Could not clear the run: " + e);
        }
    }

    /** The saved run can't be used: drops it and starts the current era the way enterEra() does for an era without a snapshot. */
    public void abandonRun() {
        clearRun();
        player.startEra(player.getCurrentEraNumber());
    }

    /** The player's live state, for the era in progress. */
    public SaveData.RunPlayerData capturePlayerState() {
        ToolBox tb = player.getToolBox();
        return new SaveData.RunPlayerData(player.getCurrentHealth(), player.getLives(),
                tb.getConsultProgrammerAccuracy(), tb.isSaveShieldActive(), lifelineUses(tb));
    }

    /** The stats counters as they are right now. */
    public SaveData.StatsData captureStats() {
        return new SaveData.StatsData(stats.questionsAnswered, stats.correctAnswers,
                stats.enemiesDefeated, stats.bossesDefeated, stats.getPlayTimeSeconds());
    }

    /**
     * Puts the live player state and the stats back to what the snapshot says. Everything is checked before anything is changed,
     * so a bad snapshot throws IllegalArgumentException and leaves the session as it was. EraController calls this last, once the
     * rest of the snapshot has checked out.
     */
    public void applyRunState(SaveData.RunData run) {
        SaveData.RunPlayerData p = run.player();
        SaveData.StatsData s = run.stats();
        if (p == null || s == null) {
            throw new IllegalArgumentException("Run snapshot is missing the player or stats section");
        }
        ToolBox tb = player.getToolBox();
        if (p.currentHealth() < 1 || p.currentHealth() > player.getMaxHealth()) {
            throw new IllegalArgumentException("Run health out of range: " + p.currentHealth());
        }
        if (p.lives() < 1 || p.lives() > Player.MAX_LIVES) { // 0 lives would be game over, which is never saved
            throw new IllegalArgumentException("Run lives out of range: " + p.lives());
        }
        if (Double.isNaN(p.consultAccuracy()) || p.consultAccuracy() < 0.0 || p.consultAccuracy() > 1.0) {
            throw new IllegalArgumentException("Run accuracy out of range: " + p.consultAccuracy());
        }
        for (ToolBox.Lifeline l : ToolBox.Lifeline.values()) {
            Integer left = p.lifelineUses().get(l.name());
            if (left != null && (left < 0 || left > tb.getUsesPerLifeline())) {
                throw new IllegalArgumentException("Run uses of " + l + " out of range: " + left);
            }
        }

        player.setCurrentHealth(p.currentHealth());
        player.setLives(p.lives());
        tb.setConsultProgrammerAccuracy(p.consultAccuracy());
        tb.setSaveShieldActive(p.saveShieldActive());
        for (ToolBox.Lifeline l : ToolBox.Lifeline.values()) {
            Integer left = p.lifelineUses().get(l.name());
            if (left != null)
                tb.setRemainingUses(l, left);
        }
        stats.questionsAnswered = Math.max(0, s.questionsAnswered());
        stats.correctAnswers = Math.max(0, s.correctAnswers());
        stats.enemiesDefeated = Math.max(0, s.enemiesDefeated());
        stats.bossesDefeated = Math.max(0, s.bossesDefeated());
        stats.playTimeSeconds = Math.max(0, s.playTimeSeconds());
        pendingRun = null; // consumed; the copy on disk stays until the next autosave or clear replaces it
    }

    // ---- accessors ----

    public Player getPlayer() {
        return player;
    }

    public Stats getStats() {
        return stats;
    }

    public boolean isTutorialCompleted() {
        return tutorialCompleted;
    }

    public int getHighestUnlockedEra() {
        return highestUnlockedEra;
    }

    public boolean isEraCompleted(int era) {
        return completedEras.contains(era);
    }

    public SortedSet<Integer> getCompletedEras() {
        return Collections.unmodifiableSortedSet(completedEras);
    }

    // ---- conversion to / from the file format ----

    /** The session as a checkpoint: live state, no era in progress. */
    SaveData toSaveData() {
        ToolBox tb = player.getToolBox();

        var components = new ArrayList<SaveData.ComponentData>();
        for (Component c : player.getCollectedComponents()) {
            components.add(new SaveData.ComponentData(c.getComponentId(), c.getName(),
                    c.getDescription(), c.getIconAssetPath(), c.getOriginEraNumber()));
        }

        var playerData = new SaveData.PlayerData(player.getName(), player.getMaxHealth(),
                player.getCurrentHealth(), player.getLives(), player.getCurrentEraNumber(),
                tb.getConsultProgrammerAccuracy(), tb.isSaveShieldActive(), lifelineUses(tb), components);

        var progress = new SaveData.ProgressData(tutorialCompleted, highestUnlockedEra,
                new ArrayList<>(completedEras));

        return new SaveData(SaveData.CURRENT_VERSION, Instant.now().toString(),
                playerData, progress, captureStats(), null);
    }

    private static Map<String, Integer> lifelineUses(ToolBox tb) {
        Map<String, Integer> uses = new java.util.LinkedHashMap<>();
        for (ToolBox.Lifeline l : ToolBox.Lifeline.values()) {
            uses.put(l.name(), tb.getRemainingUses(l));
        }
        return uses;
    }

    /**
     * Rebuilds a session from save data. Throws IllegalArgumentException if the data is missing or out of range
     * (the model setters validate too, so bad values can't sneak in).
     */
    static GameSession fromSaveData(SaveData data) {
        if (data == null || data.player() == null || data.progress() == null) {
            throw new IllegalArgumentException("Save file is missing required sections");
        }
        SaveData.PlayerData p = data.player();

        Player player = new Player(p.name() == null ? "Player" : p.name(),
                p.maxHealth(), p.lives());
        player.setCurrentHealth(p.currentHealth()); // after maxHealth is set
        player.setCurrentEraNumber(p.currentEra()); // not startEra(): that would reset the toolbox

        ToolBox tb = player.getToolBox();
        tb.setConsultProgrammerAccuracy(p.consultAccuracy());
        tb.setSaveShieldActive(p.saveShieldActive());
        for (ToolBox.Lifeline l : ToolBox.Lifeline.values()) {
            Integer left = p.lifelineUses().get(l.name());
            if (left != null)
                tb.setRemainingUses(l, left);
        }

        for (SaveData.ComponentData c : p.components()) {
            player.collectComponent(new Component(c.id(), c.name(), c.description(),
                    c.iconAssetPath(), c.originEra()));
        }

        GameSession session = new GameSession(player);
        session.tutorialCompleted = data.progress().tutorialCompleted();

        int unlocked = data.progress().highestUnlockedEra();
        if (unlocked < 1) {
            throw new IllegalArgumentException("highestUnlockedEra out of range: " + unlocked);
        }
        session.highestUnlockedEra = Math.min(Era.TOTAL_ERAS, Math.max(unlocked, p.currentEra()));
        session.completedEras.addAll(data.progress().completedEras());

        SaveData.StatsData s = data.stats();
        if (s != null) {
            session.stats.questionsAnswered = Math.max(0, s.questionsAnswered());
            session.stats.correctAnswers = Math.max(0, s.correctAnswers());
            session.stats.enemiesDefeated = Math.max(0, s.enemiesDefeated());
            session.stats.bossesDefeated = Math.max(0, s.bossesDefeated());
            session.stats.playTimeSeconds = Math.max(0, s.playTimeSeconds());
        }
        session.pendingRun = usableRun(data.run());
        return session;
    }

    /**
     * Cheap shape check of the era in progress. A snapshot that fails it is dropped (the player starts the era fresh) instead of failing the
     * whole load; the checks that need the era content (question ids, enemy counts) happen when EraController resumes it.
     */
    private static SaveData.RunData usableRun(SaveData.RunData run) {
        if (run == null) {
            return null;
        }
        boolean ok = run.eraNumber() >= 1 && run.eraNumber() <= Era.TOTAL_ERAS
                && run.phase() != null && run.encounterIndex() >= 0
                && !run.enemies().isEmpty() && run.player() != null && run.stats() != null;
        if (!ok) {
            System.err.println("[SaveManager] Ignoring malformed run snapshot");
            return null;
        }
        return run;
    }
}

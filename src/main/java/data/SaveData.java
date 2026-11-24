package data;

import java.util.List;
import java.util.Map;

/**
 * On-disk shape of a save file. Plain immutable records, kept separate from the model so the format stays stable while the model changes.
 * Bump CURRENT_VERSION when the format changes and add a migration step in SaveManager.
 * player / progress / stats are always the last CHECKPOINT (what Continue falls back to when the player dies).
 * The optional run section is the era in progress; it is saved and cleared on its own, so it never overwrites the checkpoint.
 */
public record SaveData(int version,
                       String savedAt,
                       PlayerData player,
                       ProgressData progress,
                       StatsData stats,
                       RunData run) {   // null = no era in progress (v1 files never have one)

    public static final int CURRENT_VERSION = 2;

    /** Everything needed to rebuild a Player. */
    public record PlayerData(String name,
                             int maxHealth,
                             int currentHealth,
                             int lives,
                             int currentEra,
                             double consultAccuracy,
                             boolean saveShieldActive,
                             Map<String, Integer> lifelineUses,   // keyed by ToolBox.Lifeline name
                             List<ComponentData> components) {
        public PlayerData {
            lifelineUses = lifelineUses == null ? Map.of() : Map.copyOf(lifelineUses);
            components = components == null ? List.of() : List.copyOf(components);
        }
    }

    /** Snapshot of a collected Component. */
    public record ComponentData(String id,
                                String name,
                                String description,
                                String iconAssetPath,
                                int originEra) {}

    /** Story / map progression. */
    public record ProgressData(boolean tutorialCompleted,
                               int highestUnlockedEra,
                               List<Integer> completedEras) {
        public ProgressData {
            completedEras = completedEras == null ? List.of() : List.copyOf(completedEras);
        }
    }

    /** Lifetime counters. */
    public record StatsData(int questionsAnswered,
                            int correctAnswers,
                            int enemiesDefeated,
                            int bossesDefeated,
                            long playTimeSeconds) {}

    // ---- v2: the era in progress (mid-era save) ----

    /**
     * Everything needed to put the player back into an era they left: where they are in the era, how the fight stands,
     * the live player state and the question on screen. Every list may be missing in a hand-edited file, so none of them can be null.
     */
    public record RunData(int eraNumber,
                          String phase,                       // EraController.Phase name: ENCOUNTER_INTRO (fight not started) or BATTLE
                          int encounterIndex,                 // 0-based position of the enemy being fought
                          List<EnemyProgressData> enemies,    // every enemy of the era in fight order, so defeated ones are the full ones
                          RunPlayerData player,
                          StatsData stats,
                          DeckData deck,                      // null = reshuffle the question decks
                          QuestionData question) {            // null = nothing to restore, the next question is drawn
        public RunData {
            enemies = enemies == null ? List.of() : List.copyOf(enemies);
        }
    }

    /** Combat progress of one enemy; its health is worked out from these (see Enemy.getCurrentHealth). */
    public record EnemyProgressData(int theoreticalAnswersGiven,
                                    int programmingAnswersGiven) {}

    /** The part of the player that changes during an era. Name, max health and components stay in the checkpoint. */
    public record RunPlayerData(int currentHealth,
                                int lives,
                                double consultAccuracy,
                                boolean saveShieldActive,
                                Map<String, Integer> lifelineUses) {   // keyed by ToolBox.Lifeline name
        public RunPlayerData {
            lifelineUses = lifelineUses == null ? Map.of() : Map.copyOf(lifelineUses);
        }
    }

    /** What is left in each question draw pile (next draw first), by question id, so resumed runs don't repeat or re-roll questions. */
    public record DeckData(List<String> theoreticalPile,
                           String theoreticalLastDrawn,
                           List<String> programmingPile,
                           String programmingLastDrawn) {
        public DeckData {
            theoreticalPile = theoreticalPile == null ? List.of() : List.copyOf(theoreticalPile);
            programmingPile = programmingPile == null ? List.of() : List.copyOf(programmingPile);
        }
    }

    /**
     * The question on screen. The bank's question is shuffled in place during play, so the order is saved as the choice texts
     * in the order shown (not as a permutation of the file order) together with where the correct one ended up.
     */
    public record QuestionData(String id,                         // Question.getQuestionId(), e.g. "E3-T04"
                               List<String> choices,              // statements in the order they were shown
                               int correctChoiceIndex,            // index into choices
                               List<Integer> eliminated,          // choices removed by Debug
                               double timeRemainingSeconds,       // frozen countdown (ignored if the question has no time limit)
                               Integer submittedChoice,           // null = not answered; -1 = time ran out; else the locked-in choice, not applied yet
                               Integer databaseSuggestion,        // choice the Database lifeline pointed at, null = not used on this question
                               boolean consultUsed) {             // Consult a Programmer was used on this question (the hint comes from the question)
        public QuestionData {
            choices = choices == null ? List.of() : List.copyOf(choices);
            eliminated = eliminated == null ? List.of() : List.copyOf(eliminated);
        }
    }
}
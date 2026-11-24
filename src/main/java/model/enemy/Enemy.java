package model.enemy;

import java.util.Objects;

import model.Paradigm;
import model.character.Player;
import model.question.Question;

public abstract class Enemy {

    /** Outcome of the player answering one question against this enemy. */
    public record AnswerResult(boolean correct,
                               int damageToEnemy,
                               int damageToPlayer,
                               Player.DamageOutcome playerOutcome,
                               boolean enemyDefeated) {
        // damageToPlayer is the damage the enemy *attempted*; if playerOutcome is
        // BLOCKED the Save shield absorbed it and no HP was actually lost.
    }

    private String name;
    private Paradigm paradigm;
    private String spriteAssetPath;
    private final int requiredTheoreticalAnswers;
    private final int requiredProgrammingAnswers;
    private int theoreticalAnswersGiven;
    private int programmingAnswersGiven;
    private int maxHealth;
    private int attackDamage;

    protected Enemy(String name,
                    Paradigm paradigm,
                    int requiredTheoreticalAnswers,
                    int requiredProgrammingAnswers,
                    int maxHealth,
                    int attackDamage) {
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.paradigm = paradigm;
        if (requiredTheoreticalAnswers < 0 || requiredProgrammingAnswers < 0) {
            throw new IllegalArgumentException("required answer counts cannot be negative");
        }
        if (maxHealth <= 0) {
            throw new IllegalArgumentException("maxHealth must be positive");
        }
        if (attackDamage < 0) {
            throw new IllegalArgumentException("attackDamage cannot be negative");
        }
        this.requiredTheoreticalAnswers = requiredTheoreticalAnswers;
        this.requiredProgrammingAnswers = requiredProgrammingAnswers;
        this.theoreticalAnswersGiven = 0;
        this.programmingAnswersGiven = 0;
        this.maxHealth = maxHealth;
        this.attackDamage = attackDamage;
    }

    /** Short label for display, e.g. "Easy", "Medium", "Boss". */
    public abstract String getDifficultyLabel();

    /** Seconds the player gets to answer a question of this type against this enemy. 0 (default) means no time limit. */
    public int getTimeLimitSeconds(Question.QuestionType type) {
        return 0;
    }

    // combat section:

    /**
     * Resolves the player's answer: a correct one damages this enemy, a wrong one makes it attack the player.
     * Throws IllegalStateException if this enemy is already defeated.
     */
    public AnswerResult resolveAnswer(Question question, int selectedChoiceIndex, Player player) {
        Objects.requireNonNull(question, "question must not be null");
        Objects.requireNonNull(player, "player must not be null");
        if (isDefeated()) {
            throw new IllegalStateException(name + " is already defeated");
        }

        if (question.isAnswerCorrect(selectedChoiceIndex)) {
            int dealt = registerCorrectAnswer(question.getQuestionType());
            return new AnswerResult(true, dealt, 0, Player.DamageOutcome.NONE, isDefeated());
        }

        int attempted = calculateAttackDamage(question);
        Player.DamageOutcome outcome = player.takeDamage(attempted);
        return new AnswerResult(false, 0, attempted, outcome, false);
    }

    /** Makes this enemy attack the player after the question was answered wrong. Returns what happened to the player. */
    public Player.DamageOutcome attack(Player player, Question question) {
        Objects.requireNonNull(player, "player must not be null");
        return player.takeDamage(calculateAttackDamage(question));
    }

    /**
     * Attack damage x question multiplier, rounded. A non-zero attack never rounds down to 0; a zero attack or 0.0 multiplier (e.g. harmless tutorial question) deals nothing.
     * A null question means a plain, unscaled attack.
     */
    public int calculateAttackDamage(Question question) {
        double multiplier = (question == null) ? 1.0 : question.getDamageMultiplier();
        if (attackDamage == 0 || multiplier == 0.0) {
            return 0;
        }
        return Math.max(1, (int) Math.round(attackDamage * multiplier));
    }

    /**
     * Records one correct answer of that type towards defeating this enemy, capped at the quota.
     * Returns the HP the enemy lost (0 if that quota was already full).
     */
    public int registerCorrectAnswer(Question.QuestionType type) {
        Objects.requireNonNull(type, "type must not be null");
        int healthBefore = getCurrentHealth();
        if (type == Question.QuestionType.THEORETICAL) {
            theoreticalAnswersGiven = Math.min(requiredTheoreticalAnswers, theoreticalAnswersGiven + 1);
        } else {
            programmingAnswersGiven = Math.min(requiredProgrammingAnswers, programmingAnswersGiven + 1);
        }
        return healthBefore - getCurrentHealth();
    }

    /** True once both answer quotas are met. */
    public boolean isDefeated() {
        return theoreticalAnswersGiven >= requiredTheoreticalAnswers
                && programmingAnswersGiven >= requiredProgrammingAnswers;
    }

    /** Resets combat progress (and therefore health), e.g. when the player retreats or restarts. */
    public void resetProgress() {
        theoreticalAnswersGiven = 0;
        programmingAnswersGiven = 0;
    }

    /**
     * Sets the combat progress (and therefore health) straight from a saved run. Each count must be between 0 and its quota,
     * otherwise IllegalArgumentException and nothing changes.
     */
    public void restoreProgress(int theoreticalAnswersGiven, int programmingAnswersGiven) {
        if (theoreticalAnswersGiven < 0 || theoreticalAnswersGiven > requiredTheoreticalAnswers
                || programmingAnswersGiven < 0 || programmingAnswersGiven > requiredProgrammingAnswers) {
            throw new IllegalArgumentException("saved progress " + theoreticalAnswersGiven + "/" + programmingAnswersGiven
                    + " doesn't fit " + name + " (needs " + requiredTheoreticalAnswers + "/" + requiredProgrammingAnswers + ")");
        }
        this.theoreticalAnswersGiven = theoreticalAnswersGiven;
        this.programmingAnswersGiven = programmingAnswersGiven;
    }

    // health section:

    /** Remaining HP, from 0 to maxHealth. Worked out from quota progress: exactly 0 when defeated, at least 1 until then. */
    public int getCurrentHealth() {
        int totalRequired = requiredTheoreticalAnswers + requiredProgrammingAnswers;
        if (totalRequired == 0) {
            return 0; // nothing to answer: already defeated
        }
        int answered = theoreticalAnswersGiven + programmingAnswersGiven;
        int lost = (int) ((long) maxHealth * answered / totalRequired);
        return maxHealth - lost;
    }

    public int getMaxHealth() {
        return maxHealth;
    }

    public void setMaxHealth(int maxHealth) {
        if (maxHealth <= 0) {
            throw new IllegalArgumentException("maxHealth must be positive");
        }
        this.maxHealth = maxHealth;
    }

    //base damage this enemy deals to the player on a wrong answer.
    public int getAttackDamage() {
        return attackDamage;
    }

    public void setAttackDamage(int attackDamage) {
        if (attackDamage < 0) {
            throw new IllegalArgumentException("attackDamage cannot be negative");
        }
        this.attackDamage = attackDamage;
    }

    // plain properties section: 

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = Objects.requireNonNull(name, "name must not be null");
    }

    public Paradigm getParadigm() {
        return paradigm;
    }

    public void setParadigm(Paradigm paradigm) {
        this.paradigm = paradigm;
    }

    public String getSpriteAssetPath() {
        return spriteAssetPath;
    }

    public void setSpriteAssetPath(String spriteAssetPath) {
        this.spriteAssetPath = spriteAssetPath;
    }

    public int getRequiredTheoreticalAnswers() {
        return requiredTheoreticalAnswers;
    }

    public int getRequiredProgrammingAnswers() {
        return requiredProgrammingAnswers;
    }

    public int getTheoreticalAnswersGiven() {
        return theoreticalAnswersGiven;
    }

    public int getProgrammingAnswersGiven() {
        return programmingAnswersGiven;
    }
    public String getSpriteKey() {
        return getDifficultyLabel();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{name='" + name + "', paradigm=" + paradigm
                + ", hp=" + getCurrentHealth() + "/" + maxHealth + ", attack=" + attackDamage
                + ", progress=" + theoreticalAnswersGiven + "/" + requiredTheoreticalAnswers
                + " theoretical, " + programmingAnswersGiven + "/" + requiredProgrammingAnswers + " programming}";
    }
}
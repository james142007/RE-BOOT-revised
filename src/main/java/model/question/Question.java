package model.question;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import model.Paradigm;

public abstract class Question {

    /** Which combat requirement a question meets when answered correctly, matched against the Enemy's theoretical / programming answer quotas. */
    public enum QuestionType {
        THEORETICAL,
        PROGRAMMING
    }

    /** Default scaling of the attacking enemy's damage when this question is answered wrong. */
    public static final double DEFAULT_DAMAGE_MULTIPLIER = 1.0;

    private String questionId;
    private String promptText;
    private List<String> choices;
    private int correctChoiceIndex;
    private Paradigm paradigm;
    private String hint;
    private String explanation;
    private int pointValue;
    private double damageMultiplier;

    protected Question(String questionId,
                        String promptText,
                        List<String> choices,
                        int correctChoiceIndex,
                        Paradigm paradigm) {
        this.questionId = Objects.requireNonNull(questionId, "questionId must not be null");
        this.promptText = Objects.requireNonNull(promptText, "promptText must not be null");
        this.choices = new ArrayList<>(Objects.requireNonNull(choices, "choices must not be null"));
        if (this.choices.isEmpty()) {
            throw new IllegalArgumentException("a question must have at least one choice");
        }
        if (correctChoiceIndex < 0 || correctChoiceIndex >= this.choices.size()) {
            throw new IllegalArgumentException("correctChoiceIndex is out of bounds for the supplied choices");
        }
        this.correctChoiceIndex = correctChoiceIndex;
        this.paradigm = paradigm;
        this.pointValue = 1;
        this.damageMultiplier = DEFAULT_DAMAGE_MULTIPLIER;
    }

    /** Which QuestionType this question counts towards when answered correctly. Each subclass implements it. */
    public abstract QuestionType getQuestionType();

    /** Checks the player's selected choice index against the correct choice. */
    public boolean isAnswerCorrect(int selectedChoiceIndex) {
        return selectedChoiceIndex == correctChoiceIndex;
    }

    public String getQuestionId() {
        return questionId;
    }

    public void setQuestionId(String questionId) {
        this.questionId = Objects.requireNonNull(questionId, "questionId must not be null");
    }

    public String getPromptText() {
        return promptText;
    }

    public void setPromptText(String promptText) {
        this.promptText = Objects.requireNonNull(promptText, "promptText must not be null");
    }

    /** Unmodifiable view of the answer choices. */
    public List<String> getChoices() {
        return Collections.unmodifiableList(choices);
    }

    /**
     * Replaces the choices but keeps the current correct index. Throws if the new list is empty or the index would go out of bounds;
     * use setChoices(List, int) to change both.
     */
    public void setChoices(List<String> choices) {
        setChoices(choices, correctChoiceIndex);
    }

    /** Atomically replaces the choices and the index of the correct one. */
    public void setChoices(List<String> choices, int correctChoiceIndex) {
        List<String> copy = new ArrayList<>(Objects.requireNonNull(choices, "choices must not be null"));
        if (copy.isEmpty()) {
            throw new IllegalArgumentException("a question must have at least one choice");
        }
        if (correctChoiceIndex < 0 || correctChoiceIndex >= copy.size()) {
            throw new IllegalArgumentException("correctChoiceIndex is out of bounds for the supplied choices");
        }
        this.choices = copy;
        this.correctChoiceIndex = correctChoiceIndex;
    }

    public int getCorrectChoiceIndex() {
        return correctChoiceIndex;
    }

    public void setCorrectChoiceIndex(int correctChoiceIndex) {
        if (correctChoiceIndex < 0 || correctChoiceIndex >= choices.size()) {
            throw new IllegalArgumentException("correctChoiceIndex is out of bounds for the current choices");
        }
        this.correctChoiceIndex = correctChoiceIndex;
    }

    public Paradigm getParadigm() {
        return paradigm;
    }

    public void setParadigm(Paradigm paradigm) {
        this.paradigm = paradigm;
    }

    /** Hint text the Consult a Programmer lifeline gives, if any. */
    public String getHint() {
        return hint;
    }

    public void setHint(String hint) {
        this.hint = hint;
    }

    /** Optional explanation shown after answering. */
    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public int getPointValue() {
        return pointValue;
    }

    public void setPointValue(int pointValue) {
        if (pointValue < 0) {
            throw new IllegalArgumentException("pointValue cannot be negative");
        }
        this.pointValue = pointValue;
    }

    /**
     * How hard the enemy hits on a wrong answer: attackDamage x multiplier. 1.0 = normal, above 1.0 = harder questions hurt more,
     * 0.0 = no damage (e.g. tutorial questions).
     */
    public double getDamageMultiplier() {
        return damageMultiplier;
    }

    public void setDamageMultiplier(double damageMultiplier) {
        if (Double.isNaN(damageMultiplier) || Double.isInfinite(damageMultiplier) || damageMultiplier < 0.0) {
            throw new IllegalArgumentException("damageMultiplier must be a finite value >= 0");
        }
        this.damageMultiplier = damageMultiplier;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Question)) return false;
        Question question = (Question) o;
        return questionId.equals(question.questionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(questionId);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{questionId='" + questionId + "', paradigm=" + paradigm + '}';
    }
}
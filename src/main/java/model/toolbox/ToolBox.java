package model.toolbox;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;

/** The player's lifelines. Each one has a limited number of uses per era. */
public class ToolBox {

    /** The four lifelines available to the player. */
    public enum Lifeline {
        DEBUG,
        DATABASE,
        CONSULT_PROGRAMMER,
        SAVE
    }

    private static final int DEFAULT_USES_PER_LIFELINE = 1;
    private static final double DEFAULT_CONSULT_ACCURACY = 0.75;

    /** Per-era allowance set at construction, resetForNewEra() restores to this. */
    private final int usesPerLifeline;
    private final Map<Lifeline, Integer> remainingUses;
    /** Accuracy of the Database suggestion. The name is kept for save compatibility (see getConsultProgrammerAccuracy()). */
    private double consultProgrammerAccuracy;
    private boolean saveShieldActive;

    public ToolBox() {
        this(DEFAULT_USES_PER_LIFELINE);
    }

    public ToolBox(int usesPerLifeline) {
        if (usesPerLifeline < 0) {
            throw new IllegalArgumentException("usesPerLifeline cannot be negative");
        }
        this.usesPerLifeline = usesPerLifeline;
        this.remainingUses = new EnumMap<>(Lifeline.class);
        this.consultProgrammerAccuracy = DEFAULT_CONSULT_ACCURACY;
        this.saveShieldActive = false;
        refillAllUses();
    }

    /** True if the lifeline still has at least one charge. */
    public boolean isAvailable(Lifeline lifeline) {
        return remainingUses.getOrDefault(lifeline, 0) > 0;
    }

    /** True if Save can be used right now: it has a charge and no shield is armed. Handy for enabling / disabling the Save button. */
    public boolean canUseSave() {
        return isAvailable(Lifeline.SAVE) && !saveShieldActive;
    }

    private boolean consume(Lifeline lifeline) {
        if (!isAvailable(lifeline)) {
            return false;
        }
        remainingUses.merge(lifeline, -1, new BiFunction<Integer, Integer, Integer>() {
            @Override
            public Integer apply(Integer a, Integer b) {
                return a + b;
            }
        });
        return true;
    }

    /** Activates Debug. Returns true if a use was available and consumed. */
    public boolean useDebug() {
        return consume(Lifeline.DEBUG);
    }

    /** Activates Database. Returns true if a use was available and consumed. */
    public boolean useDatabase() {
        return consume(Lifeline.DATABASE);
    }

    /** Activates Consult a Programmer (the era programmer gives the question's hint). Returns true if a use was available and consumed. */
    public boolean useConsultProgrammer() {
        return consume(Lifeline.CONSULT_PROGRAMMER);
    }

    /**
     * Activates Save and arms the shield. Does nothing if a shield is already armed.
     * Returns true only if a charge was consumed and a new shield was armed.
     */
    public boolean useSave() {
        if (saveShieldActive) {
            return false;
        }
        if (!consume(Lifeline.SAVE)) {
            return false;
        }
        saveShieldActive = true;
        return true;
    }

    /**
     * Consumes the Save shield if it's armed, blocking one hit. Player.takeDamage calls this automatically.
     * Returns true if a shield was active and is now consumed.
     */
    public boolean consumeSaveShieldIfActive() {
        if (saveShieldActive) {
            saveShieldActive = false;
            return true;
        }
        return false;
    }

    /** Gets the toolbox ready for a new era: lifelines go back to the allowance from construction and temporary effects (the Save shield for now) are cleared so nothing carries over. */
    public void resetForNewEra() {
        refillAllUses();
        clearActiveEffects();
    }

    private void refillAllUses() {
        for (Lifeline lifeline : Lifeline.values()) {
            remainingUses.put(lifeline, usesPerLifeline);
        }
    }

    /** Clears every armed/temporary effect. Add any future "active" flags here. */
    private void clearActiveEffects() {
        saveShieldActive = false;
    }

    /** Uses each lifeline is restored to on a new era. */
    public int getUsesPerLifeline() {
        return usesPerLifeline;
    }

    public int getRemainingUses(Lifeline lifeline) {
        return remainingUses.getOrDefault(lifeline, 0);
    }

    public void setRemainingUses(Lifeline lifeline, int uses) {
        Objects.requireNonNull(lifeline, "lifeline must not be null");
        if (uses < 0) {
            throw new IllegalArgumentException("uses cannot be negative");
        }
        remainingUses.put(lifeline, uses);
    }

    /**
     * Despite the name (kept so old saves still load), this is the accuracy of the Database lifeline's suggestion, since Consult a Programmer now gives a hint.
     * Returns the chance (0.0-1.0) that the Database suggestion is correct.
     */
    public double getConsultProgrammerAccuracy() {
        return consultProgrammerAccuracy;
    }

    public void setConsultProgrammerAccuracy(double consultProgrammerAccuracy) {
        if (consultProgrammerAccuracy < 0.0 || consultProgrammerAccuracy > 1.0) {
            throw new IllegalArgumentException("accuracy must be between 0.0 and 1.0");
        }
        this.consultProgrammerAccuracy = consultProgrammerAccuracy;
    }

    public boolean isSaveShieldActive() {
        return saveShieldActive;
    }

    /** For loading saved games; gameplay should go through useSave(). */
    public void setSaveShieldActive(boolean saveShieldActive) {
        this.saveShieldActive = saveShieldActive;
    }
}
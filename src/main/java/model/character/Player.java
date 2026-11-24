package model.character;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


import model.map.Era;
import model.toolbox.ToolBox;

public class Player {

    /** Max number of lives the player can ever hold. */
    public static final int MAX_LIVES = 7;

    /** Default size of the health pool. */
    public static final int DEFAULT_MAX_HEALTH = 100;

    /** What happened when the player was hit; lets the UI pick the right feedback. */
    public enum DamageOutcome {
        NONE,
        BLOCKED,
        DAMAGED,
        LIFE_LOST,
        GAME_OVER
    }

    private String name;
    private int maxHealth;
    private int currentHealth;
    private int lives;
    private int currentEraNumber;
    private final List<Component> collectedComponents;
    private final ToolBox toolBox;

    public Player(String name) {
        this(name, DEFAULT_MAX_HEALTH, MAX_LIVES);
    }

    public Player(String name, int maxHealth, int startingLives) {
        this.name = name;
        if (maxHealth <= 0) {
            throw new IllegalArgumentException("maxHealth must be positive");
        }
        this.maxHealth = maxHealth;
        this.currentHealth = maxHealth;
        this.lives = clampLives(startingLives);
        this.currentEraNumber = 1;
        this.collectedComponents = new ArrayList<>();
        this.toolBox = new ToolBox();
    }

    private static int clampLives(int lives) {
        return Math.max(0, Math.min(lives, MAX_LIVES));
    }

    /** Applies damage to the player. */
    
    public DamageOutcome takeDamage(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("amount cannot be negative");
        }
        if (isGameOver()) {
            return DamageOutcome.GAME_OVER;
        }
        if (amount == 0) {
            return DamageOutcome.NONE;
        }
        if (toolBox.consumeSaveShieldIfActive()) {
            return DamageOutcome.BLOCKED;
        }

        currentHealth = Math.max(0, currentHealth - amount);
        if (currentHealth == 0) {
            loseLife();
            return isGameOver() ? DamageOutcome.GAME_OVER : DamageOutcome.LIFE_LOST;
        }
        return DamageOutcome.DAMAGED;
    }

    /** Restores health, never above max health. The amount shouldn't be negative. */
    public void heal(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("amount cannot be negative");
        }
        currentHealth = Math.min(maxHealth, currentHealth + amount);
    }

    /** Deducts one life (floored at zero) and restores health to full. */
    public void loseLife() {
        if (lives > 0) {
            lives--;
        }
        currentHealth = maxHealth;
    }

    /** True once the player has no lives left. */
    public boolean isGameOver() {
        return lives <= 0;
    }

    /**
     * Moves the player into the era and prepares the toolbox (lifelines refilled, Save shield cleared).
     * Use this instead of setCurrentEraNumber() in normal progression so the toolbox reset isn't forgotten.
     */
    public void startEra(int eraNumber) {
        setCurrentEraNumber(eraNumber);
        toolBox.resetForNewEra();
    }

    /** Adds a defeated boss's reward to the inventory and marks it collected. Does nothing if it's already collected. */
    public void collectComponent(Component component) {
        if (component != null && !collectedComponents.contains(component)) {
            component.collect();
            collectedComponents.add(component);
        }
    }

    /** True once one component is recovered from every era. */
    public boolean hasCollectedAllComponents() {
        return collectedComponents.size() >= Era.TOTAL_ERAS;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getMaxHealth() {
        return maxHealth;
    }

    public void setMaxHealth(int maxHealth) {
        if (maxHealth <= 0) {
            throw new IllegalArgumentException("maxHealth must be positive");
        }
        this.maxHealth = maxHealth;
        this.currentHealth = Math.min(currentHealth, maxHealth);
    }

    public int getCurrentHealth() {
        return currentHealth;
    }

    public void setCurrentHealth(int currentHealth) {
        this.currentHealth = Math.max(0, Math.min(currentHealth, maxHealth));
    }

    public int getLives() {
        return lives;
    }

    public void setLives(int lives) {
        this.lives = clampLives(lives);
    }

    public int getCurrentEraNumber() {
        return currentEraNumber;
    }

    public void setCurrentEraNumber(int currentEraNumber) {
        if (currentEraNumber < 1 || currentEraNumber > Era.TOTAL_ERAS) {
            throw new IllegalArgumentException("currentEraNumber must be between 1 and " + Era.TOTAL_ERAS);
        }
        this.currentEraNumber = currentEraNumber;
    }

    /** Unmodifiable view of the components recovered so far. */
    public List<Component> getCollectedComponents() {
        return Collections.unmodifiableList(collectedComponents);
    }

    public ToolBox getToolBox() {
        return toolBox;
    }

    @Override
    public String toString() {
        return "Player{name='" + name + "', health=" + currentHealth + "/" + maxHealth
                + ", lives=" + lives + ", era=" + currentEraNumber
                + ", components=" + collectedComponents.size() + "/" + Era.TOTAL_ERAS + '}';
    }

}
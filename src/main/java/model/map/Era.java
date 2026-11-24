package model.map;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import model.Paradigm;
import model.character.Component;
import model.enemy.Boss;
import model.enemy.Enemy;

/*
Models the behavior of each era.
 */
public class Era {

    /** Re: Boot's story is fixed at exactly seven eras. */
    public static final int TOTAL_ERAS = 7;

    private int eraNumber;
    private String name;
    private String narrative;
    private Paradigm focusParadigm;
    private String programmer;
    private String backgroundAssetPath;
    private String musicTrackPath;
    private final List<Enemy> enemies;
    private boolean unlocked;
    private boolean completed;

    public Era(int eraNumber, String name, String narrative, Paradigm focusParadigm) {
        this(eraNumber, name, narrative, focusParadigm, null);
    }

    public Era(int eraNumber, String name, String narrative, Paradigm focusParadigm, String programmer) {
        if (eraNumber < 1 || eraNumber > TOTAL_ERAS) {
            throw new IllegalArgumentException("eraNumber must be between 1 and " + TOTAL_ERAS);
        }
        this.eraNumber = eraNumber;
        this.name = name;
        this.narrative = narrative;
        this.focusParadigm = focusParadigm;
        this.programmer = programmer;
        this.enemies = new ArrayList<>();
        this.unlocked = (eraNumber == 1);
        this.completed = false;
    }

    /** Adds an enemy (usually several Easy/Medium and one Boss) to this era. */
    public void addEnemy(Enemy enemy) {
        if (enemy != null) {
            enemies.add(enemy);
        }
    }

    /** Boss of this era, or null if none is set yet. */
    public Boss getBoss() {
        for (Enemy enemy : enemies) {
            if (enemy instanceof Boss) {
                return (Boss) enemy;
            }
        }
        return null;
    }

    /** Component dropped by this era's boss, or null if there's no boss yet. */
    public Component getRewardComponent() {
        Boss boss = getBoss();
        return boss != null ? boss.getRewardComponent() : null;
    }

    /** Marks this era as completed (the controller layer unlocks the next one). */
    public void markCompleted() {
        this.completed = true;
    }

    public int getEraNumber() {
        return eraNumber;
    }

    public void setEraNumber(int eraNumber) {
        if (eraNumber < 1 || eraNumber > TOTAL_ERAS) {
            throw new IllegalArgumentException("eraNumber must be between 1 and " + TOTAL_ERAS);
        }
        this.eraNumber = eraNumber;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNarrative() {
        return narrative;
    }

    public void setNarrative(String narrative) {
        this.narrative = narrative;
    }

    public Paradigm getFocusParadigm() {
        return focusParadigm;
    }

    public void setFocusParadigm(Paradigm focusParadigm) {
        this.focusParadigm = focusParadigm;
    }

    /** Name of the historical programmer who helps in this era (Consult a Programmer), or null if none is set. */
    public String getProgrammer() {
        return programmer;
    }

    public void setProgrammer(String programmer) {
        this.programmer = programmer;
    }

    public String getBackgroundAssetPath() {
        return backgroundAssetPath;
    }

    public void setBackgroundAssetPath(String backgroundAssetPath) {
        this.backgroundAssetPath = backgroundAssetPath;
    }

    public String getMusicTrackPath() {
        return musicTrackPath;
    }

    public void setMusicTrackPath(String musicTrackPath) {
        this.musicTrackPath = musicTrackPath;
    }

    /** Unmodifiable view of every enemy in this era. */
    public List<Enemy> getEnemies() {
        return Collections.unmodifiableList(enemies);
    }

    public void setEnemies(List<Enemy> enemies) {
        this.enemies.clear();
        if (enemies != null) {
            this.enemies.addAll(enemies);
        }
    }

    public boolean isUnlocked() {
        return unlocked;
    }

    public void setUnlocked(boolean unlocked) {
        this.unlocked = unlocked;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Era)) return false;
        Era era = (Era) o;
        return eraNumber == era.eraNumber;
    }

    @Override
    public int hashCode() {
        return Objects.hash(eraNumber);
    }

    @Override
    public String toString() {
        return "Era{eraNumber=" + eraNumber + ", name='" + name + "', focusParadigm=" + focusParadigm + '}';
    }
}

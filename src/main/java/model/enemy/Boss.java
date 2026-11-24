package model.enemy;

import data.Constants;
import model.Paradigm;
import model.character.Component;
import model.question.Question;

public class Boss extends Enemy {

    private static final int REQUIRED_THEORETICAL_ANSWERS = 2;
    private static final int REQUIRED_PROGRAMMING_ANSWERS = 6;

    /** Tuning values: 4 wrong answers cost the player a life. */
    private static final int BASE_HEALTH = 200;
    private static final int BASE_ATTACK_DAMAGE = 25;

    private Component rewardComponent;

    public Boss(String name, Paradigm paradigm, String spriteAssetPath, Component rewardComponent) {
        super(name, paradigm, REQUIRED_THEORETICAL_ANSWERS, REQUIRED_PROGRAMMING_ANSWERS,
                BASE_HEALTH, BASE_ATTACK_DAMAGE);
        setSpriteAssetPath(spriteAssetPath);
        this.rewardComponent = rewardComponent;
    }

    @Override
    public String getDifficultyLabel() {
        return "Boss";
    }

    @Override
    public int getTimeLimitSeconds(Question.QuestionType type) {
        return type == Question.QuestionType.THEORETICAL
                ? Constants.BOSS_THEORETICAL_TIME : Constants.BOSS_PROGRAMMING_TIME;
    }

    public Component claimReward() {
        if (isDefeated() && rewardComponent != null && !rewardComponent.isCollected()) {
            return rewardComponent;
        }
        return null;
    }

    public Component getRewardComponent() {
        return rewardComponent;
    }

    public void setRewardComponent(Component rewardComponent) {
        this.rewardComponent = rewardComponent;
    }
}
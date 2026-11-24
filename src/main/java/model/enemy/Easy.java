package model.enemy;

import data.Constants;
import model.Paradigm;
import model.question.Question;

public class Easy extends Enemy {

    private static final int REQUIRED_THEORETICAL_ANSWERS = 1;
    private static final int REQUIRED_PROGRAMMING_ANSWERS = 0;

    /** Tuning values: player has 100 HP, so an Easy enemy takes 10 wrong answers to cost a life. */
    private static final int BASE_HEALTH = 30;
    private static final int BASE_ATTACK_DAMAGE = 10;

    public Easy(String name, Paradigm paradigm, String spriteAssetPath) {
        super(name, paradigm, REQUIRED_THEORETICAL_ANSWERS, REQUIRED_PROGRAMMING_ANSWERS,
                BASE_HEALTH, BASE_ATTACK_DAMAGE);
        setSpriteAssetPath(spriteAssetPath);
    }

    @Override
    public String getDifficultyLabel() {
        return "Easy";
    }

    @Override
    public int getTimeLimitSeconds(Question.QuestionType type) {
        return type == Question.QuestionType.THEORETICAL
                ? Constants.EASY_THEORETICAL_TIME : Constants.EASY_PROGRAMMING_TIME;
    }
}

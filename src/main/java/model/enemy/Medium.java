package model.enemy;
 
import data.Constants;
import model.Paradigm;
import model.question.Question;
 
public class Medium extends Enemy {
 
    private static final int REQUIRED_THEORETICAL_ANSWERS = 2;
    private static final int REQUIRED_PROGRAMMING_ANSWERS = 2;
 
    /** Tuning values: 7 wrong answers cost the player a life. */
    private static final int BASE_HEALTH = 80;
    private static final int BASE_ATTACK_DAMAGE = 15;
 
    public Medium(String name, Paradigm paradigm, String spriteAssetPath) {
        super(name, paradigm, REQUIRED_THEORETICAL_ANSWERS, REQUIRED_PROGRAMMING_ANSWERS,
                BASE_HEALTH, BASE_ATTACK_DAMAGE);
        setSpriteAssetPath(spriteAssetPath);
    }
 
    @Override
    public String getDifficultyLabel() {
        return "Medium";
    }

    @Override
    public int getTimeLimitSeconds(Question.QuestionType type) {
        return type == Question.QuestionType.THEORETICAL
                ? Constants.MEDIUM_THEORETICAL_TIME : Constants.MEDIUM_PROGRAMMING_TIME;
    }
}

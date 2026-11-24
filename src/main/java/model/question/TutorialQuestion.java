package model.question;

import java.util.ArrayList;

public class TutorialQuestion {
    private String number;
    private String question;
    private ArrayList<TutorialChoices> choices;
    private String answer;

    public String getAnswer() {
        return answer;
    }
    public ArrayList<TutorialChoices> getChoices() {
        return choices;
    }
    public String getNumber() {
        return number;
    }
    public String getQuestion() {
        return question;
    }

    

}

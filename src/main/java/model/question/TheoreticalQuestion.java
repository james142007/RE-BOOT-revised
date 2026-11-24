package model.question;

import java.util.List;

import model.Paradigm;

public class TheoreticalQuestion extends Question {

    public TheoreticalQuestion(String questionId,
                                String promptText,
                                List<String> choices,
                                int correctChoiceIndex,
                                Paradigm paradigm) {
        super(questionId, promptText, choices, correctChoiceIndex, paradigm);
    }

    @Override
    public QuestionType getQuestionType() {
        return QuestionType.THEORETICAL;
    }
}

package model.question;

import java.util.List;

import model.Paradigm;

/**
 * A code question. Code / expected output is in the prompt text (see era1Questions.json),
 * this class just marks it as counting towards an enemy's programming-answer quota.
 */
public class ProgrammingQuestion extends Question {

    public ProgrammingQuestion(String questionId,
                                String promptText,
                                List<String> choices,
                                int correctChoiceIndex,
                                Paradigm paradigm) {
        super(questionId, promptText, choices, correctChoiceIndex, paradigm);
    }

    @Override
    public QuestionType getQuestionType() {
        return QuestionType.PROGRAMMING;
    }
}

package controller;

import model.question.Question;

/** Turns a Question into the text the battle screen shows. Code and expected output are already in the question text in the JSON. */
final class QuestionFormatter {

    private QuestionFormatter() {}

    /** Short label: "Theoretical" or "Programming". */
    static String header(Question question) {
        return question.getQuestionType() == Question.QuestionType.PROGRAMMING ? "Programming" : "Theoretical";
    }

    /** The full question text. */
    static String body(Question question) {
        return question.getPromptText();
    }
}

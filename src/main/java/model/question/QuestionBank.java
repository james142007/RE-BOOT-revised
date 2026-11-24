package model.question;

import java.util.List;

/**
 * Every question of one era, split by kind. Theoretical ones come from the module's multiple-choice set,
 * programming ones are the Type A/B/C code questions.
 */
public record QuestionBank(int eraNumber,
                           List<TheoreticalQuestion> theoretical,
                           List<ProgrammingQuestion> programming) {
    public QuestionBank {
        theoretical = List.copyOf(theoretical);
        programming = List.copyOf(programming);
    }
}

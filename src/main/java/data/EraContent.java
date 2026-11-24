package data;

import model.map.Era;
import model.question.QuestionBank;

/** Everything needed to play one era: the era itself (with its enemies) and its questions. */
public record EraContent(Era era, QuestionBank questions) {
}

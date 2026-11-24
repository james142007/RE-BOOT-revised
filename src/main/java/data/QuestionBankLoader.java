package data;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;

import model.Paradigm;
import model.question.ProgrammingQuestion;
import model.question.QuestionBank;
import model.question.TheoreticalQuestion;

/**
 * Loads an era's question bank from a JSON in resources/questions and validates it, so a typo fails at load time (with the question number) instead of mid-battle.
 * Same shape as tutorialQuestions.json plus an optional hint (Consult a Programmer shows it).
 * "number" is a unique id whose last part starts with T (theoretical, E1-T01) or P (programming, E1-P03); programming code/output goes in the question text.
 */
public final class QuestionBankLoader {

    // ---- on-disk shape (Jackson fills these in) ----

    public record BankFile(List<Entry> questions) {
    }

    public record Entry(String number, String question, List<ChoiceEntry> choices,
                        String answer, String hint) {
    }

    public record ChoiceEntry(String choice, String statement) {
    }

    private static final ObjectMapper mapper = new ObjectMapper();

    private QuestionBankLoader() {}

    /**
     * Loads the bank at resourcePath (like "/questions/era1Questions.json"), stamping the paradigm on every question.
     * Throws IllegalArgumentException if the file is missing or a question is invalid.
     */
    public static QuestionBank load(String resourcePath, Paradigm paradigm, int eraNumber) {
        BankFile file = read(resourcePath);
        if (file.questions() == null || file.questions().isEmpty()) {
            throw new IllegalArgumentException(resourcePath + ": needs a \"questions\" list");
        }

        Set<String> seenIds = new HashSet<>();
        List<TheoreticalQuestion> theoretical = new ArrayList<>();
        List<ProgrammingQuestion> programming = new ArrayList<>();
        for (Entry e : file.questions()) {
            String id = checkId(e.number(), resourcePath, seenIds);
            String prompt = requireText(e.question(), resourcePath, id, "question");
            List<String> choices = checkChoices(e.choices(), resourcePath, id);
            int answer = answerIndex(e.answer(), choices.size(), resourcePath, id);

            if (isProgramming(id, resourcePath)) {
                ProgrammingQuestion q = new ProgrammingQuestion(id, prompt, choices, answer, paradigm);
                q.setHint(blankToNull(e.hint()));
                programming.add(q);
            } else {
                TheoreticalQuestion q = new TheoreticalQuestion(id, prompt, choices, answer, paradigm);
                q.setHint(blankToNull(e.hint()));
                theoretical.add(q);
            }
        }
        return new QuestionBank(eraNumber, theoretical, programming);
    }

    private static BankFile read(String resourcePath) {
        try (InputStream input = QuestionBankLoader.class.getResourceAsStream(resourcePath)) {
            if (input == null) {
                throw new IllegalArgumentException("Resource not found: " + resourcePath);
            }
            return mapper.readValue(input, BankFile.class);
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read question bank " + resourcePath + ": " + e.getMessage(), e);
        }
    }

    // ---- validation ----

    private static String checkId(String id, String path, Set<String> seenIds) {
        if (id == null || id.isBlank()) {
            throw fail(path, "(no number)", "every question needs a \"number\"");
        }
        if (!seenIds.add(id)) {
            throw fail(path, id, "duplicate number");
        }
        return id;
    }

    /** The last part of the number (after the final '-') starts with P for programming, T for theoretical. */
    private static boolean isProgramming(String id, String path) {
        String kind = id.substring(id.lastIndexOf('-') + 1).trim().toUpperCase();
        if (kind.startsWith("P")) {
            return true;
        }
        if (kind.startsWith("T")) {
            return false;
        }
        throw fail(path, id, "the number must look like \"E1-T01\" (theoretical) or \"E1-P01\" (programming)");
    }

    /** Choices are {"choice": "A", "statement": "..."}; they must be lettered A, B, C... in order. */
    private static List<String> checkChoices(List<ChoiceEntry> entries, String path, String id) {
        if (entries == null || entries.size() < 2 || entries.size() > Constants.maxChoices) {
            throw fail(path, id, "needs between 2 and " + Constants.maxChoices + " choices");
        }
        List<String> statements = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            ChoiceEntry c = entries.get(i);
            if (c == null || c.statement() == null || c.statement().isBlank()) {
                throw fail(path, id, "a choice has no statement");
            }
            String expected = String.valueOf((char) ('A' + i));
            if (c.choice() == null || !c.choice().trim().equalsIgnoreCase(expected)) {
                throw fail(path, id, "choice " + (i + 1) + " must be lettered \"" + expected + "\"");
            }
            statements.add(c.statement());
        }
        return statements;
    }

    private static int answerIndex(String answer, int choiceCount, String path, String id) {
        if (answer == null || answer.trim().length() != 1) {
            throw fail(path, id, "\"answer\" must be a single letter like \"B\"");
        }
        int index = Character.toUpperCase(answer.trim().charAt(0)) - 'A';
        if (index < 0 || index >= choiceCount) {
            throw fail(path, id, "answer \"" + answer + "\" does not match any of the " + choiceCount + " choices");
        }
        return index;
    }

    private static String requireText(String value, String path, String id, String field) {
        if (value == null || value.isBlank()) {
            throw fail(path, id, "missing " + field);
        }
        return value;
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    private static IllegalArgumentException fail(String path, String id, String problem) {
        return new IllegalArgumentException(path + ": question " + id + ": " + problem);
    }
}

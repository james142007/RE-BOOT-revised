package data;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.function.ToIntFunction;

import com.fasterxml.jackson.databind.ObjectMapper;

import model.Paradigm;
import model.character.Component;
import model.enemy.Boss;
import model.enemy.Easy;
import model.enemy.Enemy;
import model.enemy.Medium;
import model.map.Era;
import model.question.QuestionBank;

/**
 * Builds a playable era from resources/eras/eraN.json. To add an era, copy era1.json and write its question file (no code changes).
 * Enemies are fought in file order (easy, medium, then the boss). "programmer" is the one shown by Consult a Programmer.
 */
public final class EraLoader {

    // ---- on-disk shape (Jackson fills these in) ----

    public record EraFile(int eraNumber, String name, String narrative, String focusParadigm,
            String programmer, String questionBank, EnemiesFile enemies) {
    }

    public record EnemiesFile(List<String> easy, List<String> medium, BossFile boss) {
    }

    public record BossFile(String name, ComponentFile component) {
    }

    public record ComponentFile(String id, String name, String description, String iconAssetPath) {
    }

    private static final ObjectMapper mapper = new ObjectMapper();

    private EraLoader() {
    }

    //this loads the questions for an era
    public static String resourcePathFor(int eraNumber) {
        return "/eras/era" + eraNumber + ".json";
    }

    /** True if an era file exists for this number (eras not written yet return false). */
    public static boolean isAvailable(int eraNumber) {
        return EraLoader.class.getResource(resourcePathFor(eraNumber)) != null;
    }

    /** Throws IllegalArgumentException if the era file is missing or invalid. */
    public static EraContent load(int eraNumber) {
        String path = resourcePathFor(eraNumber);
        EraFile file = read(path);

        if (file.eraNumber() != eraNumber) {
            throw new IllegalArgumentException(
                    path + ": eraNumber is " + file.eraNumber() + " but the file is for era " + eraNumber);
        }
        Paradigm paradigm = parseParadigm(file.focusParadigm(), path);
        if (file.programmer() == null || file.programmer().isBlank()) {
            throw new IllegalArgumentException(path + ": needs a \"programmer\" (the name of the era's helper)");
        }
        EnemiesFile enemies = file.enemies();
        if (enemies == null || enemies.easy() == null || enemies.medium() == null
                || enemies.boss() == null || enemies.boss().component() == null) {
            throw new IllegalArgumentException(
                    path + ": needs \"enemies\" with \"easy\", \"medium\" and a \"boss\" that has a \"component\"");
        }
        if (enemies.easy().size() != Constants.easyEnemiesPerEra
                || enemies.medium().size() != Constants.mediumEnemiesPerEra) {
            throw new IllegalArgumentException(path + ": an era needs exactly " + Constants.easyEnemiesPerEra
                    + " easy and " + Constants.mediumEnemiesPerEra + " medium enemies (found "
                    + enemies.easy().size() + " and " + enemies.medium().size() + ")");
        }

        Era era = new Era(file.eraNumber(), file.name(), file.narrative(), paradigm, file.programmer().trim());
        for (String name : enemies.easy()) {
            era.addEnemy(new Easy(name, paradigm, null)); // null sprite = placeholder art for now
        }
        for (String name : enemies.medium()) {
            era.addEnemy(new Medium(name, paradigm, null));
        }
        ComponentFile c = enemies.boss().component();
        Component reward = new Component(c.id(), c.name(), c.description(), c.iconAssetPath(), eraNumber);
        era.addEnemy(new Boss(enemies.boss().name(), paradigm, null, reward));

        QuestionBank bank = QuestionBankLoader.load(file.questionBank(), paradigm, eraNumber);
        checkBank(era, bank, path);
        return new EraContent(era, bank);
    }

    /** The bank needs at least one question of each kind the era's enemies ask for. */
    private static void checkBank(Era era, QuestionBank bank, String path) {
        int theoryNeeded = era.getEnemies().stream().mapToInt(new ToIntFunction<Enemy>() {
            @Override
            public int applyAsInt(Enemy e) {
                return e.getRequiredTheoreticalAnswers();
            }
        }).sum();
        int codeNeeded = era.getEnemies().stream().mapToInt(new ToIntFunction<Enemy>() {
            @Override
            public int applyAsInt(Enemy e) {
                return e.getRequiredProgrammingAnswers();
            }
        }).sum();
        if (theoryNeeded > 0 && bank.theoretical().isEmpty()) {
            throw new IllegalArgumentException(path + ": the question bank has no theoretical questions");
        }
        if (codeNeeded > 0 && bank.programming().isEmpty()) {
            throw new IllegalArgumentException(path + ": the question bank has no programming questions");
        }
        if (bank.theoretical().size() < theoryNeeded || bank.programming().size() < codeNeeded) {
            // not fatal: the deck reshuffles, but the player will see repeats
            System.out.println("[EraLoader] Era " + era.getEraNumber() + " needs " + theoryNeeded + " theoretical / "
                    + codeNeeded + " programming correct answers but the bank has " + bank.theoretical().size()
                    + " / " + bank.programming().size() + " questions, so some will repeat.");
        }
    }

    private static Paradigm parseParadigm(String name, String path) {
        try {
            return Paradigm.valueOf(name == null ? "" : name.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    path + ": unknown focusParadigm \"" + name + "\" (use a name from the Paradigm enum)");
        }
    }

    private static EraFile read(String path) {
        try (InputStream input = EraLoader.class.getResourceAsStream(path)) {
            if (input == null) {
                throw new IllegalArgumentException("Resource not found: " + path);
            }
            return mapper.readValue(input, EraFile.class);
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read era file " + path + ": " + e.getMessage(), e);
        }
    }
}

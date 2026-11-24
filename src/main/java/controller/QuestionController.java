package controller;

import java.util.Random;

import data.SaveData;
import model.enemy.Enemy;
import model.question.Question;
import model.question.QuestionBank;
import model.question.QuestionDeck;

/**
 * Gives the questions for one era's battles. Picks which kind (theory or programming) to ask next and hands out the next one.
 * Knows nothing about the battle itself.
 */
public class QuestionController {

    private final QuestionDeck theoreticalDeck;
    private final QuestionDeck programmingDeck;

    public QuestionController(QuestionBank bank, Random random) {
        this.theoreticalDeck = new QuestionDeck(bank.theoretical(), random);
        this.programmingDeck = new QuestionDeck(bank.programming(), random);
    }

    public QuestionController(QuestionBank bank) {
        this(bank, new Random());
    }

    /**
     * Policy: theoretical questions first, then programming. Change this one
     * method to interleave them or to ask programming first.
     */
    public Question.QuestionType nextTypeFor(Enemy enemy) {
        boolean theoryLeft = enemy.getTheoreticalAnswersGiven() < enemy.getRequiredTheoreticalAnswers();
        return theoryLeft ? Question.QuestionType.THEORETICAL : Question.QuestionType.PROGRAMMING;
    }

    /** The next question to ask while fighting the enemy. */
    public Question nextQuestionFor(Enemy enemy) {
        return deckFor(nextTypeFor(enemy)).draw();
    }

    /** Call after every answer: a wrong question goes back in its deck so the player can retry it later. */
    public void recordResult(Question question, boolean correct) {
        if (!correct) {
            deckFor(question.getQuestionType()).putBack(question);
        }
    }

    // ---- saving / resuming ----

    /** The question with this id from either deck, or null if there is none (the question files changed since the save). */
    public Question find(String questionId) {
        Question q = theoreticalDeck.find(questionId);
        return q != null ? q : programmingDeck.find(questionId);
    }

    /** What is left in both draw piles, so a resumed run asks the same questions in the same order. */
    public SaveData.DeckData captureDecks() {
        return new SaveData.DeckData(theoreticalDeck.pileIds(), theoreticalDeck.lastDrawnId(),
                programmingDeck.pileIds(), programmingDeck.lastDrawnId());
    }

    /** Puts both piles back as saved. Throws IllegalArgumentException if a saved id isn't in the bank any more. */
    public void restoreDecks(SaveData.DeckData saved) {
        theoreticalDeck.restore(saved.theoreticalPile(), saved.theoreticalLastDrawn());
        programmingDeck.restore(saved.programmingPile(), saved.programmingLastDrawn());
    }

    private QuestionDeck deckFor(Question.QuestionType type) {
        return type == Question.QuestionType.THEORETICAL ? theoreticalDeck : programmingDeck;
    }
}

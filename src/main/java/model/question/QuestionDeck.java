package model.question;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Shuffled draw pile of questions of one kind (theoretical or programming). Drawn one by one so nothing repeats until the pile runs out.
 * A wrongly answered question can be put back for another try later. An empty pile refills with the whole bank,
 * so a battle never runs out of questions even if the bank is smaller than the answers an enemy needs.
 */
public class QuestionDeck {

    private final List<Question> all;
    private final List<Question> pile = new ArrayList<>();
    private final Random random;
    private Question lastDrawn;

    public QuestionDeck(List<? extends Question> questions, Random random) {
        this.all = new ArrayList<>(Objects.requireNonNull(questions, "questions must not be null"));
        this.random = Objects.requireNonNull(random, "random must not be null");
    }

    public boolean isEmpty() {
        return all.isEmpty();
    }

    /** Next question, refilling and reshuffling first if the pile is empty. */
    public Question draw() {
        if (all.isEmpty()) {
            throw new IllegalStateException("This deck has no questions");
        }
        if (pile.isEmpty()) {
            refill();
        }
        lastDrawn = pile.remove(0);
        return lastDrawn;
    }

    /**
     * Puts a wrong question back at a random spot that's never the very next draw.
     * If the pile is empty it comes back with the next refill (which never starts with the same question).
     */
    public void putBack(Question question) {
        if (pile.isEmpty()) {
            return;
        }
        pile.add(1 + random.nextInt(pile.size()), question);
    }

    // ---- saving / resuming (see SaveData.DeckData) ----

    /** Ids of the questions still in the pile, next draw first. */
    public List<String> pileIds() {
        List<String> ids = new ArrayList<>(pile.size());
        for (Question q : pile) {
            ids.add(q.getQuestionId());
        }
        return ids;
    }

    /** Id of the question drawn last, or null if nothing was drawn yet. */
    public String lastDrawnId() {
        return lastDrawn == null ? null : lastDrawn.getQuestionId();
    }

    /** The question with this id from this deck's bank, or null if it isn't there. */
    public Question find(String questionId) {
        for (Question q : all) {
            if (q.getQuestionId().equals(questionId)) {
                return q;
            }
        }
        return null;
    }

    /**
     * Puts the pile back as saved. Throws IllegalArgumentException, and changes nothing,
     * if an id is not in this deck's bank any more (the question files changed since the save).
     */
    public void restore(List<String> pileIds, String lastDrawnId) {
        List<Question> restored = new ArrayList<>(pileIds.size());
        for (String id : pileIds) {
            Question q = find(id);
            if (q == null) {
                throw new IllegalArgumentException("question " + id + " is not in this deck any more");
            }
            restored.add(q);
        }
        Question last = null;
        if (lastDrawnId != null) {
            last = find(lastDrawnId);
            if (last == null) {
                throw new IllegalArgumentException("question " + lastDrawnId + " is not in this deck any more");
            }
        }
        pile.clear();
        pile.addAll(restored);
        lastDrawn = last;
    }

    private void refill() {
        List<Question> shuffled = new ArrayList<>(all);
        Collections.shuffle(shuffled, random);
        // don't ask the same question twice in a row across a refill
        if (shuffled.size() > 1 && shuffled.get(0).equals(lastDrawn)) {
            Collections.swap(shuffled, 0, 1 + random.nextInt(shuffled.size() - 1));
        }
        pile.addAll(shuffled);
    }
}

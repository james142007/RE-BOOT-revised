package controller;

import java.util.ArrayList;

import data.AudioRegistry;
import data.Constants;
import model.character.TutorialPlayer;
import model.enemy.TutorialBot;
import model.question.TutorialChoices;
import model.question.TutorialQuestion;
import model.question.TutorialQuestionLoader;
import model.question.TutorialQuestionData;

/**
 * Experimental class for the main game battle, used for the tutorial.
 * The battle controller should load all the questions to decide the outcome of the battle.
 */
enum BattleState {
    ASK_QUESTION,
    PLAYER_ATTACK,
    BOT_ATTACK,
    BOT_DEAD;

}

enum AttackingPhase {
    START,
    ATTACKING,
    DAMAGING,
    FINISH
}

public class TutorialBattleController implements Battle {

    // what to do

    /// based on the timeline and the script of the tutorial this class should do
    /*
     * ===================
     * - player dialogue
     * -> enemy dialogue
     * ==================
     * after last player dialogue the ui should show for the toolbox
     * 
     * @trigger
     * -> ui should show
     * -> dialogues
     * 
     * @trigger
     * -> question will show
     * //the question should be easy enough that the player always gets it correct
     * 
     */
    private TutorialPlayer player;
    private TutorialBot enemy;
    private BattleState currState;
    private AttackingPhase attackingPhase;
    // Questions
    private TutorialQuestionData questionData;
    private TutorialQuestion currentQuestion;
    private String currentNumber;
    private int currentIndex;
    private String question;
    private ArrayList<String> choices;
    private ArrayList<String> letters;
    private String answer;

    // battle time delays
    private double attackTimer;
    private double damagedTimer;
    private double attackTracker;
    private double damagedTracker;

    // battle flags
    private boolean playerAttacked;
    private boolean enemyAttacked;

    // TutorialController
    private TutorialController tutorialController;

    ///Constructor
    public TutorialBattleController(TutorialController tutorialController) {
        this.tutorialController = tutorialController;
        player = new TutorialPlayer();
        enemy = new TutorialBot();
        currState = BattleState.ASK_QUESTION;
        attackingPhase = AttackingPhase.FINISH;

        attackTimer = Constants.attackingTime;
        damagedTimer = Constants.damageTime;
        attackTracker = 0.0;
        damagedTracker = 0.0;

        playerAttacked = false;
        enemyAttacked = false;

        // load and initialize the questions
        currentIndex = 0;
        questionData = TutorialQuestionLoader.load("/questions/tutorialQuestions.json");
        choices = new ArrayList<>();
        letters = new ArrayList<>();
        loadQuestion();

    }

    // this method will load all the current questions, choices and its
    // corresponding answers
    public void loadQuestion() {
        // add bounds to avoid errors

        choices.clear();
        letters.clear();

        currentQuestion = questionData.getQuestions().get(currentIndex);
        currentNumber = currentQuestion.getNumber();
        question = currentQuestion.getQuestion();

        for (TutorialChoices choice : currentQuestion.getChoices()) {
            choices.add(choice.getStatement()); // add to the list the choices
            letters.add(choice.getChoice());// ABCD
        }
        answer = currentQuestion.getAnswer();
    }

    // battle model

    // update based on the state of the controller
    public void setBattleState(double deltaSeconds) {
        switch (currState) {
            case ASK_QUESTION:
                playerIsAnswering();
                break;
            case PLAYER_ATTACK:
                switch (attackingPhase) {
                    case START:
                        // none placeholder to mark that the game has not reached the attacking stage
                        // yet
                        break;
                    case ATTACKING:
                        playerAttack(deltaSeconds);
                        break;
                    case DAMAGING:
                        enemyDamaged(deltaSeconds);
                        break;
                    case FINISH:
                        resetAttack();
                        break;
                    default:
                        break;
                }
                break;
            case BOT_ATTACK:
                switch (attackingPhase) {
                    case START:
                        break;
                    case ATTACKING:
                        enemyAttack(deltaSeconds);
                        break;
                    case DAMAGING:
                        playerDamaged(deltaSeconds);
                        break;
                    case FINISH:
                        resetAttack();
                        break;
                    default:
                        break;
                }
                break;
            case BOT_DEAD:
                // after the bot is dead proceed to the final dialogue and finish the tutorial
                break;
            default:
                break;
        }
    }

    // Checks if the player is correct or wrong
    public void playerIsAnswering() {
        // if the answer is equal to the answer key of that number, then correct trigger
        // attack state, else trigger damaged state
        if (tutorialController.hasSelected()) {
            if (tutorialController.choiceSelected().equals(answer)) {
                currState = BattleState.PLAYER_ATTACK;
                attackingPhase = AttackingPhase.ATTACKING;
                playerAttacked = true;
                AudioController.getInstance().playSfx(AudioRegistry.SFX_BATTLE_CORRECT);
                System.out.println("Correct");
                tutorialController.playPlayerAttack(attackTimer);
                tutorialController.playEnemyDamaged(damagedTimer);
            } else {
                tutorialController.playEnemyAttack(attackTimer);
                tutorialController.playPlayerDamaged(damagedTimer);
                currState = BattleState.BOT_ATTACK;
                attackingPhase = AttackingPhase.ATTACKING;
                enemyAttacked = true;
                AudioController.getInstance().playSfx(AudioRegistry.SFX_BATTLE_WRONG);
                System.out.println("wrong");
            }
            tutorialController.setChoiceSelected(false);
        }
    }

    // Attack <-> Damage Logic
    // ===================================================================================================

    @Override
    public void playerAttack(double deltaSeconds) {
        if (playerAttacked) {

            if (!player.isAttacking()) {
                player.attacking();// player deals damage
                System.out.println("player attacked");
            }

            attackTracker += deltaSeconds;
            if (attackTracker >= attackTimer) {

                attackingPhase = AttackingPhase.DAMAGING;
                player.attackDone();
                attackTracker = 0;

            }
        }

    }

    @Override
    public void enemyAttack(double deltaSeconds) {
        if (enemyAttacked) {
            if (!enemy.isAttacking()) {
                enemy.attacking();
            }
            attackTracker += deltaSeconds;
            if (attackTracker >= attackTimer) {
                attackingPhase = AttackingPhase.DAMAGING;
                enemy.attackDone();
                attackTracker = 0;
            }
        }

    }

    public void playerDamaged(double deltaSeconds) {
        if (!player.isDamaged()) {
            player.takeDamage(enemy.getAttackDamage());
        }

        damagedTracker += deltaSeconds;
        if (damagedTracker >= damagedTimer) {
            attackingPhase = AttackingPhase.FINISH;
            damagedTracker = 0;
        }
    }

    public void enemyDamaged(double deltaSeconds) {

        if (!enemy.isDamaged()) {
            enemy.takeDamage(player.getPalyerDamage());
        }

        damagedTracker += deltaSeconds;

        if (damagedTracker >= damagedTimer) {
            attackingPhase = AttackingPhase.FINISH;
            damagedTracker = 0;
            if (enemy.isDead()) {
                currState = BattleState.BOT_DEAD;
                tutorialController.playEnemyKilled(deltaSeconds);

                // Notify the tutorial controller once, immediately after death.
                tutorialController.enemyDead();
                return;
            }
            enemy.damageDone();
        }
    }

    public boolean botDead() {
        return enemy.isDead() ? true : false;
    }

    // re initializes all the parameters for the attacking logic
    public void resetAttack() {
        enemyAttacked = false;
        playerAttacked = false;

        if (currentIndex < questionData.getQuestions().size() - 1) {// whats the correct bounds
            currentIndex++;
            loadQuestion();
        } else {
            tutorialController.playEnemyKilled(attackTimer);
            currState = BattleState.BOT_DEAD;
            return;
        }

        tutorialController.reset();
        tutorialController.setPasted(false);

        attackingPhase = AttackingPhase.START;
        currState = BattleState.ASK_QUESTION;
    }

    // ======================================================================================================================================
    // getters
    public BattleState getCurrState() {
        return currState;
    }

    public int getCurrentIndex() {
        return currentIndex;
    }

    public String getCurrentNumber() {
        return currentNumber;
    }

    public TutorialQuestion getCurrentQuestion() {
        return currentQuestion;
    }

    public String getQuestion() {
        return question;
    }

    public TutorialQuestionData getQuestionData() {
        return questionData;
    }

    public ArrayList<String> getChoices() {
        return choices;
    }

    public ArrayList<String> getLetters() {
        return letters;
    }

    public String getAnswer() {
        return answer;
    }

}

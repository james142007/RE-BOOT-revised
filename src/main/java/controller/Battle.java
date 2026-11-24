package controller;
/**
 * Every battle controller must implement these methods so they all follow the same structure.
 * @author Felix
 */
public interface Battle {

    void playerAttack(double var);
    void enemyAttack(double var);
    void playerIsAnswering();
    void setBattleState(double var);

}

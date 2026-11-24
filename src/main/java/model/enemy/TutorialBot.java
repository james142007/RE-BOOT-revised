package model.enemy;

import data.Constants;

/**
 * Enemy used in the tutorial, the first one the player meets. Fewer params than the original Enemy class.
 * @author Felix
 */

//enum for enemy states
// tutorial enemy differs in code from the main one to suit the tutorial flow
enum EnemyState{
    IDLE,
    TALKING,
    ATTACKING,
    DAMAGED,
    KILLED,
}


public class TutorialBot {
    private int maxHealth;
    private int attackDamage;
    private int currentHealth;
    private EnemyState currentState;
    public TutorialBot(){
        maxHealth = Constants.tutorialEnemyHealth;
        currentHealth = maxHealth;
        attackDamage = Constants.tutorialEnemyDamage;
        currentState = EnemyState.IDLE;
    }

    //Take damage
    public void takeDamage(int damage) {
        if (currentState == EnemyState.KILLED || damage <= 0) {
            return;
        }

        currentHealth = Math.max(0, currentHealth - damage);

        if (currentHealth == 0) {
            currentState = EnemyState.KILLED;
        } else {
            currentState = EnemyState.DAMAGED;
        }
    }

    //should show the attaking phase
    public void attacking(){
        currentState = EnemyState.ATTACKING;
    }
    public boolean isAttacking(){
        return currentState == EnemyState.ATTACKING; 
    }
    public void attackDone(){
        if(isAttacking()){
            currentState = EnemyState.IDLE;
        }
    }
    public boolean isDamaged(){
        return currentState == EnemyState.DAMAGED;
    }
    public void damaged(){
        if(isDamaged()){
            if (currentHealth <= 0){
                currentState = EnemyState.KILLED;
            }else{
                currentState = EnemyState.DAMAGED;
            }
        }
    }
    public void damageDone() {
        if (currentState == EnemyState.DAMAGED) {
            currentState = EnemyState.IDLE;
        }
    }
    public boolean isDead(){
        return currentState == EnemyState.KILLED;
    }
    public int getAttackDamage() {
        return attackDamage;
    }
    public int getCurrentHealth() {
        return currentHealth;
    }
    public EnemyState getCurrentState() {
        return currentState;
    }
    public int getMaxHealth() {
        return maxHealth;
    }
    public void setCurrentState(EnemyState currentState) {
        this.currentState = currentState;
    }
    


}

package model.character;

import data.Constants;

/**
 * Player model used in the tutorial. Practically the same as Player but with fewer params.
 * @author Felix
 */
//this controls the current animation or satate of the player
enum TutorialPlayerState{
    IDLE, 
    TALKING,
    DAMAGED,
    ATTACKING,
    KILLED

}
public class TutorialPlayer{
    private int maxHealth;//   
    private int currentHealth;
    private TutorialPlayerState playerState;
    private int palyerDamage;

    public TutorialPlayer(){
        this.maxHealth = Constants.startingPlayerHealth + 999;//ensure that the player doesn't dies no matter what
        this.currentHealth = maxHealth;
        this.palyerDamage =  Constants.basePlayerDamage;
        this.playerState = TutorialPlayerState.IDLE;
    }

    //add methods

    //take damage
    public void  takeDamage(int damage){
        currentHealth -= damage;
        playerState = TutorialPlayerState.DAMAGED;
    }
    public void attacking(){
        playerState = TutorialPlayerState.ATTACKING;
    }
    /** For the battle classes: tracks if the player is still in the attacking state (delays the timer so the animations stay in sync with the background). */
    public boolean isAttacking(){
        return playerState == TutorialPlayerState.ATTACKING; 
    }
    //after the animation for the attacking is done reset the player state
    public void attackDone(){
        if(isAttacking()){
            playerState = TutorialPlayerState.IDLE;
        }
    }
    public boolean isDamaged(){
        return playerState == TutorialPlayerState.DAMAGED;
    }
    public void damaged(){
        if(isDamaged()){
            if (currentHealth <= 0){
                playerState = TutorialPlayerState.KILLED;
            }else{
                playerState = TutorialPlayerState.DAMAGED;
            }
        }
    }
    public void damageDone() {
        if (playerState== TutorialPlayerState.DAMAGED) {
            playerState = TutorialPlayerState.IDLE;
        }
    }
    
    public int getCurrentHealth() {
        return currentHealth;
    }
    public TutorialPlayerState getPlayerState() {
        return playerState;
    }
    public int getMaxHealth() {
        return maxHealth;
    }
    public int getPalyerDamage() {
        return palyerDamage;
    }
    public void setPlayerState(TutorialPlayerState playerState) {
        this.playerState = playerState;
    }
}

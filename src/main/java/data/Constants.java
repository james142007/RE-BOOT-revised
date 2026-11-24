package data;

import java.util.Map;

import javafx.scene.effect.ColorAdjust;
import model.GameState;

/** All the global variables needed to track the game. */

public class Constants {
        private Constants() {
        }

        public static int sceneIndex = 0;// tracks the current scene the game is in

        public static GameState currentState = GameState.SPLASH_SCREEN; // The sate of the whole game

        public static boolean paused = false;// pause state
        /* Splash screen Loading time */
        public static final double SPLASH_SCREEN_TIMER = 2.0;

        public static boolean resumed = false;
        public static boolean NEW_GAME_WARNING_SHOWN = false;

        // Player stats should have the same value here
        public static final int startingPlayerHealth = 10;
        public static final int basePlayerDamage = 1;

        // enemy param
        public static final int tutorialEnemyHealth = 3;
        public static final int tutorialEnemyDamage = 1;

        // Battle timers
        public static final double attackingTime = 1.0;
        public static final double damageTime = 0.8;

        // Per-question countdown (seconds). 0 = no time limit. Tweak these to
        // rebalance.
        public static final int EASY_THEORETICAL_TIME = 90;
        public static final int EASY_PROGRAMMING_TIME = 0; // Easy enemies never ask programming questions
        public static final int MEDIUM_THEORETICAL_TIME = 105; // 1:45
        public static final int MEDIUM_PROGRAMMING_TIME = 135; // 2:15
        public static final int BOSS_THEORETICAL_TIME = 120;
        public static final int BOSS_PROGRAMMING_TIME = 150; // 1:30
        public static final int TIMER_WARNING_SECONDS = 10; // the countdown label turns red at or below this

        // Main gameplay loop
        public static final String defaultPlayerName = "Player"; // placeholder until there is a name entry screen
        public static final int maxChoices = 4; // the battle screen has 4 answer buttons
        public static final int easyEnemiesPerEra = 4;
        public static final int mediumEnemiesPerEra = 2; // plus 1 boss, so 7 encounters per era
        public static final double encounterIntroTime = 1.5; // seconds the "Encounter 3/7" banner stays up
        public static final double encounterClearedTime = 1.5; // seconds the "Enemy defeated" banner stays up

        // Flag
        public static final int transitionToPrologueFromBattle = 7;

        // New Game lock
        public static final boolean REQUIRE_TUTORIAL_FOR_NEW_GAME = true; // false = New Game is never locked
        public static final boolean TUTORIAL_DONE_IF_PROFILE_MISSING = false; // first launch / no profile file yet
        public static final boolean TUTORIAL_DONE_IF_PROFILE_CORRUPT = false; // profile file exists but can't be read
        public static final boolean TUTORIAL_DONE_IF_OLD_SAVE_EXISTS = true; // player already has a save from before
                                                                             // this
                                                                             // feature
        public static final String NEW_GAME_LOCKED_TEXT = "New Game - Finish Tutorial"; // null = keep normal label

        /*
         * Since there are sveral types of enemy, it should load diff spritesheets
         * depending on what type of enemy we're dealing with
         */
        public static final Map<String, String> ENEMY_SPRITE_SHEETS = Map.of(
                        "easy", "/assets/robot/easy_robot_spritesheet.png",
                        "medium", "/assets/robot/medium_robot_spritesheet.png",
                        "boss", "/assets/robot/hard_robot_spritesheet.png");// uses the same as the medium one since I
                                                                              // cant find assets yet
        // frame columns per sheet (every sheet has 4 rows)
        public static final Map<String, Integer> ENEMY_SPRITE_COLUMNS = Map.of(
                        "easy", 7,
                        "medium", 7,
                        "boss", 7);// kindof useless now cuz i decided to make all frames max at 7
        /* For the imageview, sets the color black when not unlocked */
        private static final ColorAdjust BLACKOUT = new ColorAdjust();
        static {
                BLACKOUT.setBrightness(-1.0);
        }

        /* For debugging */
        public static final boolean DEBUG_TOOLS = false;
}

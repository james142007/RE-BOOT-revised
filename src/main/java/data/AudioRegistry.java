package data;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import model.map.Era;

public final class AudioRegistry {

    /** One registered sound. gain (0..1) trims how loud the file is (e.g. 0.6 = 60%). */
    public record Sound(String path, double gain) {
    }

    private AudioRegistry() {
    }

    // -------------- Folders --------------
    private static final String SFX_DIR = "/assets/audio/sfx/";
    private static final String MUSIC_DIR = "/assets/audio/music/";

    // -------------- Tuning --------------
    public static final double DEFAULT_MASTER_VOLUME = 1.0;
    public static final double DEFAULT_MUSIC_VOLUME = 0.7;
    public static final double DEFAULT_SFX_VOLUME = 1.0;

    /** Crossfade / fade length when music changes or stops (milliseconds). */
    public static final int MUSIC_FADE_MS = 800;
    /** Minimum gap between two hover sounds (milliseconds). Raise it if hover feels spammy. */
    public static final int HOVER_THROTTLE_MS = 90;
    /** true = music fades out while the loading screen is up; false = it keeps playing until the next screen's track takes over. */
    public static final boolean STOP_MUSIC_ON_LOADING = true;

    // -------------- SFX keys --------------
    public static final String SFX_UI_CLICK = "sfx.ui.click";
    public static final String SFX_UI_HOVER = "sfx.ui.hover";
    public static final String SFX_LIFELINE_USE = "sfx.lifeline.use";
    public static final String SFX_BATTLE_NAV = "sfx.battle.nav"; // previous / next choice
    public static final String SFX_BATTLE_SELECT = "sfx.battle.select"; // "Select Choice"
    public static final String SFX_BATTLE_CORRECT = "sfx.battle.correct";
    public static final String SFX_BATTLE_WRONG = "sfx.battle.wrong";
    public static final String SFX_BATTLE_ENEMY_DEFEATED = "sfx.battle.enemy_defeated";
    public static final String SFX_ENCOUNTER_EASY = "sfx.encounter.easy";
    public static final String SFX_ENCOUNTER_MEDIUM = "sfx.encounter.medium";
    public static final String SFX_ENCOUNTER_BOSS = "sfx.encounter.boss";
    public static final String SFX_LOADING = "sfx.loading";

    // -------------- Music keys --------------
    public static final String BGM_MAIN_MENU = "bgm.main_menu";
    public static final String BGM_MAP = "bgm.map";
    public static final String BGM_TUTORIAL_DIALOGUE = "bgm.tutorial_dialogue";
    public static final String BGM_TUTORIAL_BATTLE = "bgm.tutorial_battle";

    /** Key of the track for the era (1..TOTAL_ERAS), e.g. "bgm.era3". */
    public static String eraMusicKey(int eraNumber) {
        return "bgm.era" + eraNumber;
    }

    /** Music that is loaded at startup so the first screens start instantly. */
    public static final String[] PRELOAD_MUSIC = { BGM_MAIN_MENU, BGM_MAP };

    // -------------- key -> file   (EDIT FILE NAMES HERE) --------------
    private static final Map<String, Sound> SFX = new LinkedHashMap<>();
    private static final Map<String, Sound> MUSIC = new LinkedHashMap<>();

    static {
        // ---- SFX ----
        sfx(SFX_UI_CLICK, "ui/click.wav", 1.0);
        sfx(SFX_UI_HOVER, "ui/hover.wav", 0.6);
        sfx(SFX_LIFELINE_USE, "battle/lifeline_use.wav", 1.0);
        sfx(SFX_BATTLE_NAV, "battle/choice_nav.wav", 1.0);
        sfx(SFX_BATTLE_SELECT, "battle/select.wav", 1.0);
        sfx(SFX_BATTLE_CORRECT, "battle/correct.wav", 1.0);
        sfx(SFX_BATTLE_WRONG, "battle/wrong.wav", 1.0);
        sfx(SFX_BATTLE_ENEMY_DEFEATED, "battle/enemy_defeated.wav", 1.0);
        sfx(SFX_ENCOUNTER_EASY, "encounter/easy.wav", 1.0);
        sfx(SFX_ENCOUNTER_MEDIUM, "encounter/medium.wav", 1.0);
        sfx(SFX_ENCOUNTER_BOSS, "encounter/boss.wav", 1.0);
        sfx(SFX_LOADING, "system/loading.wav", 1.0);

        // ---- Music ----
        music(BGM_MAIN_MENU, "main_menu.mp3", 1.0);
        music(BGM_MAP, "map.mp3", 1.0);
        music(BGM_TUTORIAL_DIALOGUE, "tutorial_dialogue.mp3", 1.0);
        music(BGM_TUTORIAL_BATTLE, "tutorial_battle.mp3", 1.0);
        // one track per era; follows Era.TOTAL_ERAS automatically (currently 7)
        for (int era = 1; era <= Era.TOTAL_ERAS; era++) {
            music(eraMusicKey(era), "eras/era" + era + ".mp3", 1.0);
        }
    }

    private static void sfx(String key, String file, double gain) {
        SFX.put(key, new Sound(SFX_DIR + file, gain));
    }

    private static void music(String key, String file, double gain) {
        MUSIC.put(key, new Sound(MUSIC_DIR + file, gain));
    }

    // -------------- lookups (used by AudioController) --------------
    /** return the registered SFX, or null if the key is unknown. */
    public static Sound sfx(String key) {
        return key == null ? null : SFX.get(key);
    }

    /** return the registered music track, or null if the key is unknown. */
    public static Sound music(String key) {
        return key == null ? null : MUSIC.get(key);
    }

    public static Map<String, Sound> allSfx() {
        return Collections.unmodifiableMap(SFX);
    }

    public static Map<String, Sound> allMusic() {
        return Collections.unmodifiableMap(MUSIC);
    }
}

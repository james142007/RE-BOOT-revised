package data;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import model.character.Player;

/** SaveManager.delete(): everything of the save goes, the tutorial profile stays, and nothing brings the save back. */
class SaveManagerDeleteTest {

    @TempDir
    Path dir;

    @Test
    void deleteRemovesSaveBackupAndTempButKeepsProfile() throws IOException {
        SaveManager manager = new SaveManager(dir);
        GameSession session = new GameSession(new Player("Tester"));
        manager.save(session);
        manager.save(session); // the second write leaves save.json.bak behind
        Files.writeString(dir.resolve(SaveManager.TEMP_FILE), "half written");
        manager.markTutorialCompleted();

        assertTrue(manager.hasSave());
        assertTrue(Files.exists(dir.resolve(SaveManager.BACKUP_FILE)));

        manager.delete();

        assertFalse(Files.exists(dir.resolve(SaveManager.SAVE_FILE)));
        assertFalse(Files.exists(dir.resolve(SaveManager.BACKUP_FILE)));
        assertFalse(Files.exists(dir.resolve(SaveManager.TEMP_FILE)));
        assertFalse(manager.hasSave());
        assertTrue(manager.load().isEmpty());
        assertTrue(Files.exists(dir.resolve(SaveManager.PROFILE_FILE)), "profile.json must survive");
        assertTrue(new SaveManager(dir).isTutorialCompleted(), "tutorial flag must survive a restart");
    }

    @Test
    void deleteWithNothingToDeleteDoesNotThrow() {
        SaveManager manager = new SaveManager(dir);
        assertDoesNotThrow(manager::delete);
        assertFalse(manager.hasSave());
    }

    @Test
    void clearRunAfterDeleteDoesNotRecreateTheSave() throws IOException {
        SaveManager manager = new SaveManager(dir);
        manager.save(new GameSession(new Player("Tester")));
        manager.delete();

        manager.clearRun(); // what EraController does on a defeat

        assertFalse(Files.exists(dir.resolve(SaveManager.SAVE_FILE)));
        assertFalse(manager.hasSave());
    }

    @Test
    void sessionOutOfLivesIsNeverCheckpointed() {
        Player player = new Player("Tester");
        for (int i = 0; i < Player.MAX_LIVES; i++) {
            player.loseLife();
        }
        assertTrue(player.isGameOver());
        assertFalse(new GameSession(player).checkpoint(), "a finished run must not be written");
    }
}

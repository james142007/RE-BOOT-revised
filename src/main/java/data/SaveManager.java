package data;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Optional;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Reads and writes the save file, the only class that touches the disk for saves.
 * Writes are crash-safe: JSON is built in memory, written to a temp file, the old save is kept as save.json.bak, then the temp file is moved in.
 * load() falls back to the backup if the main file is unreadable.
 * save() writes a checkpoint (player, progress, stats, and no era in progress); saveRun() / clearRun() only change the optional in-era snapshot
 * and keep the checkpoint that is already on disk, so a mid-era save can never overwrite what dying falls back to.
 */
public final class SaveManager {

    static final String SAVE_FILE   = "save.json";
    static final String BACKUP_FILE = "save.json.bak";
    static final String TEMP_FILE   = "save.json.tmp";
    static final String PROFILE_FILE = "profile.json"; // survives New Game / delete(); holds the tutorial flag

    /** Tiny on-disk record for things that belong to the player, not to one playthrough. */
    record ProfileData(boolean tutorialCompleted) {}

    private Boolean tutorialCompletedCache; // null = not read from disk yet

    private static SaveManager instance;

    private final Path directory;
    private final ObjectMapper mapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES); // tolerate newer/older files

    /** Uses the per-user data directory from SaveLocation. */
    public static synchronized SaveManager getInstance() {
        if (instance == null) {
            instance = new SaveManager(SaveLocation.resolveDataDirectory());
        }
        return instance;
    }

    /** Package-visible so tests can point the manager at a temp folder. */
    SaveManager(Path directory) {
        this.directory = directory;
    }

    public Path getSaveFilePath() {
        return directory.resolve(SAVE_FILE);
    }

    /** True if there's a save that can actually be loaded. */
    public synchronized boolean hasSave() {
        return readSaveData().isPresent();
    }

    /**
     * Writes the session as a new CHECKPOINT, replacing any old save (and dropping any in-era snapshot, which a checkpoint makes obsolete).
     * Throws IOException if the folder or file can't be written.
     */
    public synchronized void save(GameSession session) throws IOException {
        write(session.toSaveData());
    }

    /**
     * Stores the era in progress next to the checkpoint that is already on disk, which is left exactly as it is.
     * Throws IOException if there is no readable checkpoint to attach it to or the file can't be written.
     */
    public synchronized void saveRun(SaveData.RunData run) throws IOException {
        Optional<SaveData> checkpoint = readSaveData();
        if (checkpoint.isEmpty()) {
            throw new IOException("No readable checkpoint to attach the run to");
        }
        write(withRun(checkpoint.get(), run));
    }

    /** Removes the in-era snapshot (if any) and keeps the checkpoint. Writes nothing when there is no snapshot. */
    public synchronized void clearRun() throws IOException {
        Optional<SaveData> checkpoint = readSaveData();
        if (checkpoint.isEmpty() || checkpoint.get().run() == null) {
            return;
        }
        write(withRun(checkpoint.get(), null));
    }

    private static SaveData withRun(SaveData data, SaveData.RunData run) {
        return new SaveData(SaveData.CURRENT_VERSION, Instant.now().toString(),
                data.player(), data.progress(), data.stats(), run);
    }

    /** The crash-safe write shared by every kind of save. */
    private void write(SaveData data) throws IOException {
        byte[] json = mapper.writeValueAsBytes(data); // fail here before touching any file

        Files.createDirectories(directory);
        Path target = directory.resolve(SAVE_FILE);
        Path temp   = directory.resolve(TEMP_FILE);
        Path backup = directory.resolve(BACKUP_FILE);

        Files.write(temp, json);
        if (Files.exists(target)) {
            Files.copy(target, backup, StandardCopyOption.REPLACE_EXISTING);
        }
        try {
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Loads the saved session (doesn't make it current, the caller decides). Empty if there's no save or neither file is readable. */
    public synchronized Optional<GameSession> load() {
        Optional<SaveData> data = readSaveData();
        if (data.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(GameSession.fromSaveData(data.get()));
        } catch (RuntimeException e) {
            System.err.println("[SaveManager] Save is invalid: " + e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Whether the player finished the tutorial. Lives in its own file (not save.json) because the tutorial is played before any save exists
     * and New Game replaces the save. Defaults come from Constants.
     */
    public synchronized boolean isTutorialCompleted() {
        if (!Constants.REQUIRE_TUTORIAL_FOR_NEW_GAME) {
            return true; // feature switched off
        }
        if (tutorialCompletedCache != null) {
            return tutorialCompletedCache;
        }
        Path file = directory.resolve(PROFILE_FILE);
        boolean value;
        if (!Files.isRegularFile(file)) {
            // No profile yet: old players who already have a save keep access
            value = Constants.TUTORIAL_DONE_IF_PROFILE_MISSING
                    || (Constants.TUTORIAL_DONE_IF_OLD_SAVE_EXISTS && hasSave());
        } else {
            try {
                value = mapper.readValue(Files.readAllBytes(file), ProfileData.class).tutorialCompleted();
            } catch (IOException | RuntimeException e) {
                System.err.println("[SaveManager] Could not read " + PROFILE_FILE + ": " + e.getMessage());
                value = Constants.TUTORIAL_DONE_IF_PROFILE_CORRUPT;
            }
        }
        tutorialCompletedCache = value;
        return value;
    }

    /** Records that the tutorial was finished and writes it to disk. Never throws. */
    public synchronized void markTutorialCompleted() {
        tutorialCompletedCache = true; // unlock this run even if the write below fails
        try {
            Files.createDirectories(directory);
            byte[] json = mapper.writeValueAsBytes(new ProfileData(true));
            Path temp = directory.resolve(PROFILE_FILE + ".tmp");
            Files.write(temp, json);
            try {
                Files.move(temp, directory.resolve(PROFILE_FILE),
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, directory.resolve(PROFILE_FILE), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException e) {
            System.err.println("[SaveManager] Could not save tutorial flag: " + e);
        }
    }

    /**
     * Deletes the save, its backup and the temp file (e.g. on game over). profile.json is left alone.
     * Best effort: every file is tried even if an earlier one fails, and a file that can't be deleted is emptied instead so it can't be loaded.
     * Throws the first IOException (others are added as suppressed) if some file could neither be deleted nor emptied.
     */
    public synchronized void delete() throws IOException {
        IOException failure = null;
        for (String name : new String[] { SAVE_FILE, BACKUP_FILE, TEMP_FILE }) {
            Path file = directory.resolve(name);
            try {
                Files.deleteIfExists(file);
            } catch (IOException e) {
                try {
                    Files.write(file, new byte[0]); // an empty file is unreadable, so hasSave() / load() skip it
                } catch (IOException ignored) {
                    if (failure == null) {
                        failure = e;
                    } else {
                        failure.addSuppressed(e);
                    }
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    // ---- internals ----

    /** Tries the main file, then the backup. Each candidate must parse AND rebuild into a session. */
    private Optional<SaveData> readSaveData() {
        for (String name : new String[] { SAVE_FILE, BACKUP_FILE }) {
            Path file = directory.resolve(name);
            if (!Files.isRegularFile(file)) continue;
            try {
                SaveData data = migrate(parse(Files.readAllBytes(file)));
                GameSession.fromSaveData(data); // validate fully; throws if out of range
                return Optional.of(data);
            } catch (IOException | RuntimeException e) {
                System.err.println("[SaveManager] Could not read " + name + ": " + e.getMessage());
            }
        }
        return Optional.empty();
    }

    /**
     * Reads the file, with the era-in-progress section parsed on its own: if that section is damaged it is dropped (the player just starts
     * the era fresh) instead of making the whole save, checkpoint included, unreadable.
     */
    private SaveData parse(byte[] bytes) throws IOException {
        JsonNode root = mapper.readTree(bytes);
        if (root == null || !root.isObject()) {
            throw new IOException("Save file is not a JSON object");
        }
        JsonNode runNode = ((ObjectNode) root).remove("run");
        SaveData data = mapper.treeToValue(root, SaveData.class);
        return new SaveData(data.version(), data.savedAt(), data.player(), data.progress(), data.stats(), readRun(runNode));
    }

    private SaveData.RunData readRun(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        try {
            return mapper.treeToValue(node, SaveData.RunData.class);
        } catch (IOException | RuntimeException e) {
            System.err.println("[SaveManager] Ignoring unreadable run snapshot: " + e.getMessage());
            return null;
        }
    }

    /** Upgrade older formats here as the game evolves. */
    private SaveData migrate(SaveData data) {
        if (data.version() > SaveData.CURRENT_VERSION) {
            throw new IllegalArgumentException("Save was made by a newer version of the game (v"
                    + data.version() + ")");
        }
        if (data.version() < 2) {
            data = upgradeV1toV2(data);
        }
        // if (data.version() < 3) { data = upgradeV2toV3(data); }
        return data;
    }

    /** v2 added the optional run section. A v1 save has no era in progress, so it just gets none. */
    private SaveData upgradeV1toV2(SaveData data) {
        return new SaveData(2, data.savedAt(), data.player(), data.progress(), data.stats(), null);
    }
}

// --- New Game button: create the session and save it so Continue works right away
// GameSession.startNewGame("Player").checkpoint();

// --- Boss defeated (in future BattleController)
// GameSession s = GameSession.current();
// s.completeCurrentEra(boss);   // collects the component, marks the era done, unlocks the next
// s.checkpoint();               // writes to disk, never throws

// --- After each answer / enemy kill
// s.recordAnswer(correct);
// s.recordEnemyDefeated();

// --- GameEngine.update(), while actually playing
// if (GameSession.current() != null) GameSession.current().addPlayTime(deltaSeconds);

// --- Map screen, when the player picks an era
// s.enterEra(n);                // throws if the era is still locked; resumes the saved run if it is for this era

// --- Mid-era save (EraController does these, the pause menu calls GameplayController.saveRun())
// s.saveRun(run);               // era in progress, keeps the checkpoint on disk untouched
// s.clearRun();                 // era cleared / life lost / player defeated: drops only the era in progress
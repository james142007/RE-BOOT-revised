package data;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Works out where The Game keeps its per-user data on Windows, outside the
 * 
 *
 * 
 */
public final class SaveLocation {

    public static final String APP_FOLDER = "Re-Boot";
    public static final String OVERRIDE_PROPERTY = "reboot.saveDir";

    private SaveLocation() {}

    /** The data directory path. Doesn't create it. */
    public static Path resolveDataDirectory() {
        String override = System.getProperty(OVERRIDE_PROPERTY);
        if (override != null && !override.isBlank()) {
            return Paths.get(override);
        }

        String appData = System.getenv("APPDATA");
        Path base = (appData != null && !appData.isBlank())
                ? Paths.get(appData)
                : Paths.get(System.getProperty("user.home"), "AppData", "Roaming");
        return base.resolve(APP_FOLDER);
    }

    /** Resolves the data directory and creates it (and any parents) if missing. */
    public static Path ensureDataDirectory() throws IOException {
        return Files.createDirectories(resolveDataDirectory());
    }
}


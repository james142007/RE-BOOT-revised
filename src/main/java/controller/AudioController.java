package controller;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

import data.AudioRegistry;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.DoubleBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ButtonBase;
import javafx.scene.input.MouseEvent;
import javafx.scene.media.AudioClip;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.util.Duration;

public final class AudioController {

    private static final AudioController INSTANCE = new AudioController();

    public static AudioController getInstance() {
        return INSTANCE;
    }

    /** A music track that is playing, fading in or fading out. */
    private static final class MusicTrack {
        final String key;
        final MediaPlayer player;
        final DoubleProperty fade = new SimpleDoubleProperty(0); // 0..1, animated by the crossfade
        Timeline timeline;

        MusicTrack(String key, MediaPlayer player) {
            this.key = key;
            this.player = player;
        }
    }

    // ---- volumes ----
    private final DoubleProperty masterVolume = new SimpleDoubleProperty(AudioRegistry.DEFAULT_MASTER_VOLUME);
    private final DoubleProperty musicVolume = new SimpleDoubleProperty(AudioRegistry.DEFAULT_MUSIC_VOLUME);
    private final DoubleProperty sfxVolume = new SimpleDoubleProperty(AudioRegistry.DEFAULT_SFX_VOLUME);
    private final BooleanProperty muted = new SimpleBooleanProperty(false);
    private final DoubleBinding effectiveMusicVolume;

    // ---- sfx ----
    private final Map<String, AudioClip> clips = new HashMap<>();
    private final Set<String> unavailable = new HashSet<>(); // keys that failed to load: don't retry every click
    private final Map<String, Long> lastPlayedNanos = new HashMap<>();

    // ---- music ----
    private final Map<String, Media> mediaCache = new HashMap<>();
    private final List<MusicTrack> fadingOut = new ArrayList<>();
    private MusicTrack current;

    private final Set<String> warned = new HashSet<>();

    private static final String CLICK_TAG = "audio.click.bound";
    private static final String HOVER_TAG = "audio.hover.bound";

    private AudioController() {
        effectiveMusicVolume = Bindings.createDoubleBinding(new Callable<Double>() {
            @Override
            public Double call() {
                return muted.get() ? 0.0 : clamp(masterVolume.get()) * clamp(musicVolume.get());
            }
        }, masterVolume, musicVolume, muted);
    }

    // -------------- Volume / mute --------------

    public DoubleProperty masterVolumeProperty() {
        return masterVolume;
    }

    public DoubleProperty musicVolumeProperty() {
        return musicVolume;
    }

    public DoubleProperty sfxVolumeProperty() {
        return sfxVolume;
    }

    public BooleanProperty mutedProperty() {
        return muted;
    }

    public void setMasterVolume(double volume) {
        masterVolume.set(clamp(volume));
    }

    public void setMusicVolume(double volume) {
        musicVolume.set(clamp(volume));
    }

    public void setSfxVolume(double volume) {
        sfxVolume.set(clamp(volume));
    }

    public void setMuted(boolean value) {
        muted.set(value);
    }

    public void toggleMute() {
        muted.set(!muted.get());
    }

    public double getMasterVolume() {
        return masterVolume.get();
    }

    public double getMusicVolume() {
        return musicVolume.get();
    }

    public double getSfxVolume() {
        return sfxVolume.get();
    }

    public boolean isMuted() {
        return muted.get();
    }

    // -------------- Lifecycle --------------

    /**
     * Loads every sfx that has a file, caches the start-up music, and prints ONE summary of the sounds with no file yet.
     * Call once at launch.
     */
    public void preload() {
        List<String> missing = new ArrayList<>();
        int found = 0;
        for (Map.Entry<String, AudioRegistry.Sound> entry : AudioRegistry.allSfx().entrySet()) {
            if (loadClip(entry.getKey(), false) != null) {
                found++;
            } else {
                missing.add(entry.getKey() + "  ->  " + entry.getValue().path());
            }
        }
        for (Map.Entry<String, AudioRegistry.Sound> entry : AudioRegistry.allMusic().entrySet()) {
            if (resource(entry.getValue().path()) != null) {
                found++;
            } else {
                missing.add(entry.getKey() + "  ->  " + entry.getValue().path());
            }
        }
        for (String key : AudioRegistry.PRELOAD_MUSIC) {
            loadMedia(key, false);
        }
        int total = AudioRegistry.allSfx().size() + AudioRegistry.allMusic().size();
        System.out.println("[Audio] " + found + " / " + total + " sound files found.");
        if (!missing.isEmpty()) {
            System.err.println("[Audio] No audio set (not harmful):");
            for (String line : missing) {
                System.err.println("[Audio]   " + line);
            }
        }
    }

    /** Stops everything and frees the players. Called when the app closes. */
    public void shutdown() {
        onFx(new Runnable() {
            @Override
            public void run() {
                if (current != null) {
                    disposeTrack(current);
                    current = null;
                }
                for (MusicTrack track : new ArrayList<>(fadingOut)) {
                    disposeTrack(track);
                }
                fadingOut.clear();
                for (AudioClip clip : clips.values()) {
                    clip.stop();
                }
            }
        });
    }

    // -------------- SFX --------------

    /** Plays a sound effect. Effects overlap freely. Unknown or missing key just prints a warning. */
    public void playSfx(String key) {
        if (muted.get()) {
            return;
        }
        AudioClip clip = loadClip(key, true);
        if (clip == null) {
            return;
        }
        double volume = clamp(masterVolume.get()) * clamp(sfxVolume.get()) * AudioRegistry.sfx(key).gain();
        if (volume <= 0) {
            return;
        }
        try {
            clip.play(clamp(volume));
        } catch (RuntimeException e) {
            warnOnce("play:" + key, "Could not play SFX '" + key + "': " + e.getMessage());
        }
    }

    /** Same as playSfx but ignored if the same key played less than minIntervalMs ago. */
    public void playSfxThrottled(String key, int minIntervalMs) {
        long now = System.nanoTime();
        Long last = lastPlayedNanos.get(key);
        if (last != null && (now - last) < minIntervalMs * 1_000_000L) {
            return;
        }
        lastPlayedNanos.put(key, now);
        playSfx(key);
    }

    /** Stops every playing copy of this effect (used to cut the loading sound). */
    public void stopSfx(String key) {
        AudioClip clip = clips.get(key);
        if (clip != null) {
            clip.stop();
        }
    }

    private AudioClip loadClip(String key, boolean warn) {
        AudioClip clip = clips.get(key);
        if (clip != null) {
            return clip;
        }
        if (unavailable.contains(key)) {
            return null;
        }
        AudioRegistry.Sound def = AudioRegistry.sfx(key);
        if (def == null) {
            unavailable.add(key);
            if (warn) {
                warnOnce("key:" + key, "Unknown SFX key '" + key + "' (not in AudioRegistry).");
            }
            return null;
        }
        URL url = resource(def.path());
        if (url == null) {
            unavailable.add(key);
            if (warn) {
                warnOnce("file:" + key, "SFX file missing for '" + key + "': " + def.path());
            }
            return null;
        }
        try {
            clip = new AudioClip(url.toExternalForm());
        } catch (RuntimeException e) {
            unavailable.add(key);
            if (warn) {
                warnOnce("file:" + key, "Could not load SFX '" + key + "' (" + def.path() + "): " + e.getMessage()
                        + " (supported: wav, mp3, aiff, m4a)");
            }
            return null;
        }
        clips.put(key, clip);
        return clip;
    }

    // -------------- Music --------------

    /**
     * Starts the track for the key, crossfading from whatever is playing. Does nothing if it's already the current one (no restart).
     * If the key/file is missing, the old music still fades out (silence) so the last screen's music never leaks into the new one.
     */
    public void playMusic(String key) {
        onFx(new Runnable() {
            @Override
            public void run() {
                startMusic(key);
            }
        });
    }

    /** Fades the current music out and leaves silence. */
    public void stopMusic() {
        stopMusic(true);
    }

    public void stopMusic(boolean fade) {
        onFx(new Runnable() {
            @Override
            public void run() {
                if (fade) {
                    fadeOutCurrent();
                } else {
                    if (current != null) {
                        disposeTrack(current);
                        current = null;
                    }
                    for (MusicTrack track : new ArrayList<>(fadingOut)) {
                        disposeTrack(track);
                    }
                }
            }
        });
    }

    /** Key of the track that is playing (or fading in), or null. */
    public String getCurrentMusicKey() {
        return current == null ? null : current.key;
    }

    /** Reads the given tracks ahead of time so they start without a hiccup. */
    public void preloadMusic(String... keys) {
        onFx(new Runnable() {
            @Override
            public void run() {
                for (String key : keys) {
                    loadMedia(key, false);
                }
            }
        });
    }

    private void startMusic(String key) {
        if (key == null) {
            fadeOutCurrent();
            return;
        }
        if (current != null && current.key.equals(key)) {
            return; // already playing: don't restart
        }
        Media media = loadMedia(key, true);
        if (media == null) {
            fadeOutCurrent(); // missing file = silence, not the old screen's music
            return;
        }
        MediaPlayer player;
        try {
            player = new MediaPlayer(media);
        } catch (RuntimeException e) {
            warnOnce("play:" + key, "Could not play music '" + key + "': " + e.getMessage());
            fadeOutCurrent();
            return;
        }
        MusicTrack next = new MusicTrack(key, player);
        double gain = AudioRegistry.music(key).gain();
        player.setCycleCount(MediaPlayer.INDEFINITE);
        player.volumeProperty().bind(next.fade.multiply(effectiveMusicVolume).multiply(gain));
        player.setOnError(new Runnable() {
            @Override
            public void run() {
                warnOnce("play:" + key, "Music '" + key + "' failed: " + player.getError());
                if (current == next) {
                    current = null;
                }
                disposeTrack(next);
            }
        });

        fadeOutCurrent();
        current = next;
        player.play();
        fade(next, 1.0, AudioRegistry.MUSIC_FADE_MS, null);
    }

    private void fadeOutCurrent() {
        if (current == null) {
            return;
        }
        MusicTrack old = current;
        current = null;
        fadingOut.add(old);
        fade(old, 0.0, AudioRegistry.MUSIC_FADE_MS, new Runnable() {
            @Override
            public void run() {
                disposeTrack(old);
            }
        });
    }

    private void fade(MusicTrack track, double target, double ms, Runnable whenDone) {
        if (track.timeline != null) {
            track.timeline.stop();
        }
        if (ms <= 0) {
            track.fade.set(target);
            if (whenDone != null) {
                whenDone.run();
            }
            return;
        }
        Timeline timeline = new Timeline(new KeyFrame(Duration.millis(ms), new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent e) {
                if (whenDone != null) {
                    whenDone.run();
                }
            }
        }, new KeyValue(track.fade, target)));
        track.timeline = timeline;
        timeline.play();
    }

    private void disposeTrack(MusicTrack track) {
        fadingOut.remove(track);
        if (track.timeline != null) {
            track.timeline.stop();
        }
        try {
            track.player.volumeProperty().unbind();
            track.player.stop();
            track.player.dispose();
        } catch (RuntimeException e) {
            // already disposed / never started: nothing to clean up
        }
    }

    private Media loadMedia(String key, boolean warn) {
        Media media = mediaCache.get(key);
        if (media != null) {
            return media;
        }
        AudioRegistry.Sound def = AudioRegistry.music(key);
        if (def == null) {
            if (warn) {
                warnOnce("key:" + key, "Unknown music key '" + key + "' (not in AudioRegistry).");
            }
            return null;
        }
        URL url = resource(def.path());
        if (url == null) {
            if (warn) {
                warnOnce("file:" + key, "Music file missing for '" + key + "': " + def.path());
            }
            return null;
        }
        try {
            media = new Media(url.toExternalForm());
        } catch (RuntimeException e) {
            if (warn) {
                warnOnce("file:" + key, "Could not load music '" + key + "' (" + def.path() + "): " + e.getMessage()
                        + " (supported: mp3, wav, m4a/aac; NOT ogg)");
            }
            return null;
        }
        mediaCache.put(key, media);
        return media;
    }

    // -------------- Reusable button helpers --------------

    /** Plays sfxKey whenever the button is activated (mouse or keyboard). Safe to call twice on one button. */
    public void bindClick(ButtonBase button, String sfxKey) {
        if (button == null || button.getProperties().containsKey(CLICK_TAG)) {
            return;
        }
        button.getProperties().put(CLICK_TAG, Boolean.TRUE);
        // addEventHandler = in addition to the button's onAction, never replaces it
        button.addEventHandler(ActionEvent.ACTION, new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent e) {
                playSfx(sfxKey);
            }
        });
    }

    /** Standard UI click. */
    public void bindClick(ButtonBase button) {
        bindClick(button, AudioRegistry.SFX_UI_CLICK);
    }

    /** Plays the (throttled) hover sound when the mouse enters the node. Disabled nodes get no mouse events, so no sound. */
    public void bindHover(Node node) {
        if (node == null || node.getProperties().containsKey(HOVER_TAG)) {
            return;
        }
        node.getProperties().put(HOVER_TAG, Boolean.TRUE);
        node.addEventHandler(MouseEvent.MOUSE_ENTERED, new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent e) {
                playSfxThrottled(AudioRegistry.SFX_UI_HOVER, AudioRegistry.HOVER_THROTTLE_MS);
            }
        });
    }

    /** Click (+ optional hover) sounds on EVERY button found inside */
    public void bindAllButtons(Parent root, boolean withHover) {
        List<ButtonBase> buttons = new ArrayList<>();
        collectButtons(root, buttons);
        for (ButtonBase button : buttons) {
            bindClick(button);
            if (withHover) {
                bindHover(button);
            }
        }
    }

    private static void collectButtons(Node node, List<ButtonBase> out) {
        if (node instanceof ButtonBase button) {
            out.add(button);
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                collectButtons(child, out);
            }
        }
    }


    // -------------- helpers --------------

    private static URL resource(String path) {
        return AudioController.class.getResource(path);
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static void onFx(Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            Platform.runLater(action);
        }
    }

    private void warnOnce(String id, String message) {
        if (warned.add(id)) {
            System.err.println("[Audio] " + message);
        }
    }
}

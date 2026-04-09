package lv.lenc;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javafx.application.Platform;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ComboBoxBase;
import javafx.scene.control.Slider;
import javafx.event.ActionEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.media.AudioClip;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineEvent;

public final class UiSoundManager {
    public static final String KEY_UI_SOUNDS_ENABLED = "audio.ui.enabled";
    public static final String KEY_NOVA_HOVER = "audio.nova.hover";
    public static final String KEY_NOVA_CLICK = "audio.nova.click";
    public static final String KEY_BNET_OPEN = "audio.bnet.open";
    public static final String KEY_BNET_CLOSE = "audio.bnet.close";
    public static final String KEY_BNET_SELECT = "audio.bnet.select";
    public static final String KEY_ERROR = "audio.error";
    public static final String KEY_UI_VOLUME = "audio.ui.volume";
    public static final String KEY_MUSIC_ENABLED = "audio.music.enabled";
    public static final String KEY_MUSIC_VOLUME = "audio.music.volume";
    public static final double DEFAULT_UI_VOLUME = 0.10;
    public static final double DEFAULT_MUSIC_VOLUME = 0.32;

    private static final String[] NOVA_HOVER_OPTIONS = {
            "UI_Nova_MouseOver1.wav",
            "UI_Nova_MouseOver2.wav",
            "UI_Nova_MouseOver3.wav",
            "UI_Nova_MouseOver4.wav"
    };
    private static final String[] NOVA_CLICK_OPTIONS = {
            "UI_NovaUT_ButtonClick1.wav",
            "UI_NovaUT_ButtonClick2.wav",
            "UI_NovaUT_ButtonClick3.wav",
            "UI_NovaUT_ButtonClick4.wav",
            "UI_Nova_TechClick1.wav",
            "UI_Nova_TechClick2.wav",
            "UI_Nova_TechClick3.wav",
            "UI_Nova_TechClick4.wav"
    };
    private static final String[] BNET_OPEN_OPTIONS = {"UI_BnetDropDownOpen_1.wav"};
    private static final String[] BNET_CLOSE_OPTIONS = {"UI_BnetDropDownClose_1.wav"};
    private static final String[] BNET_SELECT_OPTIONS = {"UI_BnetSelect01_1.wav"};
    private static final String[] ERROR_OPTIONS = {"UI_BnetError_2.wav"};
    private static final String[] ALL_OPTIONS = {
            "UI_Nova_MouseOver1.wav",
            "UI_Nova_MouseOver2.wav",
            "UI_Nova_MouseOver3.wav",
            "UI_Nova_MouseOver4.wav",
            "UI_NovaUT_ButtonClick1.wav",
            "UI_NovaUT_ButtonClick2.wav",
            "UI_NovaUT_ButtonClick3.wav",
            "UI_NovaUT_ButtonClick4.wav",
            "UI_Nova_TechClick1.wav",
            "UI_Nova_TechClick2.wav",
            "UI_Nova_TechClick3.wav",
            "UI_Nova_TechClick4.wav",
            "UI_BnetDropDownOpen_1.wav",
            "UI_BnetDropDownClose_1.wav",
            "UI_BnetSelect01_1.wav",
            "UI_BnetError_2.wav"
    };

    private static final String SOUNDS_BASE = "/Assets/Sounds/";
    private static final String MUSIC_BASE = "/Assets/Music/";
    private static final String MUSIC_TRACK = "nova_cue2.wav";
    private static final String NODE_BIND_KEY = "lv.lenc.uiSound.bound";
    private static final double LOUDNESS_BOOST = 1.35;
    private static final double MAX_BOOSTED_VOLUME = 2.0;

    private static final Map<String, AudioClip> CLIP_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, JavaSoundBuffer> JAVA_SOUND_CACHE = new ConcurrentHashMap<>();
    private static final Set<String> AUDIOCLIP_UNSUPPORTED = ConcurrentHashMap.newKeySet();
    private static final Set<String> FALLBACK_LOGGED = ConcurrentHashMap.newKeySet();

    private static volatile boolean enabled;
    private static volatile String novaHover;
    private static volatile String novaClick;
    private static volatile String bnetOpen;
    private static volatile String bnetClose;
    private static volatile String bnetSelect;
    private static volatile String error;
    private static volatile double volume;
    private static volatile boolean musicEnabled;
    private static volatile double musicVolume;
    private static volatile MediaPlayer backgroundMusicPlayer;

    static {
        reloadFromSettings();
    }

    private UiSoundManager() {}

    public static void reloadFromSettings() {
        enabled = Boolean.parseBoolean(SettingsManager.loadProperty(KEY_UI_SOUNDS_ENABLED, "true"));
        novaHover = sanitizeOption(
                SettingsManager.loadProperty(KEY_NOVA_HOVER, NOVA_HOVER_OPTIONS[0]),
                ALL_OPTIONS,
                NOVA_HOVER_OPTIONS[0]
        );
        novaClick = sanitizeOption(
                SettingsManager.loadProperty(KEY_NOVA_CLICK, "UI_NovaUT_ButtonClick1.wav"),
                ALL_OPTIONS,
                "UI_NovaUT_ButtonClick1.wav"
        );
        bnetOpen = sanitizeOption(
                SettingsManager.loadProperty(KEY_BNET_OPEN, BNET_OPEN_OPTIONS[0]),
                ALL_OPTIONS,
                BNET_OPEN_OPTIONS[0]
        );
        bnetClose = sanitizeOption(
                SettingsManager.loadProperty(KEY_BNET_CLOSE, BNET_CLOSE_OPTIONS[0]),
                ALL_OPTIONS,
                BNET_CLOSE_OPTIONS[0]
        );
        bnetSelect = sanitizeOption(
                SettingsManager.loadProperty(KEY_BNET_SELECT, BNET_SELECT_OPTIONS[0]),
                ALL_OPTIONS,
                BNET_SELECT_OPTIONS[0]
        );
        error = sanitizeOption(
                SettingsManager.loadProperty(KEY_ERROR, ERROR_OPTIONS[0]),
                ALL_OPTIONS,
                ERROR_OPTIONS[0]
        );
        volume = sanitizeVolume(
                SettingsManager.loadProperty(KEY_UI_VOLUME, Double.toString(DEFAULT_UI_VOLUME)),
                DEFAULT_UI_VOLUME
        );
        musicEnabled = Boolean.parseBoolean(SettingsManager.loadProperty(KEY_MUSIC_ENABLED, "true"));
        musicVolume = sanitizeVolume(
                SettingsManager.loadProperty(KEY_MUSIC_VOLUME, Double.toString(DEFAULT_MUSIC_VOLUME)),
                DEFAULT_MUSIC_VOLUME
        );
    }

    public static void saveAndApply(
            boolean enabledValue,
            String novaHoverValue,
            String novaClickValue,
            String bnetOpenValue,
            String bnetCloseValue,
            String bnetSelectValue,
            String errorValue
    ) {
        SettingsManager.saveProperty(KEY_UI_SOUNDS_ENABLED, Boolean.toString(enabledValue));
        SettingsManager.saveProperty(KEY_NOVA_HOVER, sanitizeOption(novaHoverValue, ALL_OPTIONS, NOVA_HOVER_OPTIONS[0]));
        SettingsManager.saveProperty(KEY_NOVA_CLICK, sanitizeOption(novaClickValue, ALL_OPTIONS, NOVA_CLICK_OPTIONS[0]));
        SettingsManager.saveProperty(KEY_BNET_OPEN, sanitizeOption(bnetOpenValue, ALL_OPTIONS, BNET_OPEN_OPTIONS[0]));
        SettingsManager.saveProperty(KEY_BNET_CLOSE, sanitizeOption(bnetCloseValue, ALL_OPTIONS, BNET_CLOSE_OPTIONS[0]));
        SettingsManager.saveProperty(KEY_BNET_SELECT, sanitizeOption(bnetSelectValue, ALL_OPTIONS, BNET_SELECT_OPTIONS[0]));
        SettingsManager.saveProperty(KEY_ERROR, sanitizeOption(errorValue, ALL_OPTIONS, ERROR_OPTIONS[0]));
        reloadFromSettings();
    }

    public static void saveVolumeAndEnabled(boolean enabledValue, double volumeValue) {
        SettingsManager.saveProperty(KEY_UI_SOUNDS_ENABLED, Boolean.toString(enabledValue));
        SettingsManager.saveProperty(KEY_UI_VOLUME, Double.toString(clampVolume(volumeValue)));
        reloadFromSettings();
    }

    public static void saveMusicSettings(boolean enabledValue, double volumeValue) {
        SettingsManager.saveProperty(KEY_MUSIC_ENABLED, Boolean.toString(enabledValue));
        SettingsManager.saveProperty(KEY_MUSIC_VOLUME, Double.toString(clampVolume(volumeValue)));
        reloadFromSettings();
        applyBackgroundMusicState();
    }

    public static void resetToDefaults() {
        SettingsManager.saveProperty(KEY_UI_SOUNDS_ENABLED, Boolean.toString(true));
        SettingsManager.saveProperty(KEY_UI_VOLUME, Double.toString(DEFAULT_UI_VOLUME));
        SettingsManager.saveProperty(KEY_MUSIC_ENABLED, Boolean.toString(true));
        SettingsManager.saveProperty(KEY_MUSIC_VOLUME, Double.toString(DEFAULT_MUSIC_VOLUME));
        SettingsManager.saveProperty(KEY_NOVA_HOVER, NOVA_HOVER_OPTIONS[0]);
        SettingsManager.saveProperty(KEY_NOVA_CLICK, "UI_NovaUT_ButtonClick1.wav");
        SettingsManager.saveProperty(KEY_BNET_OPEN, BNET_OPEN_OPTIONS[0]);
        SettingsManager.saveProperty(KEY_BNET_CLOSE, BNET_CLOSE_OPTIONS[0]);
        SettingsManager.saveProperty(KEY_BNET_SELECT, BNET_SELECT_OPTIONS[0]);
        SettingsManager.saveProperty(KEY_ERROR, ERROR_OPTIONS[0]);
        reloadFromSettings();
        applyBackgroundMusicState();
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static String currentNovaHover() {
        return novaHover;
    }

    public static String currentNovaClick() {
        return novaClick;
    }

    public static String currentBnetOpen() {
        return bnetOpen;
    }

    public static String currentBnetClose() {
        return bnetClose;
    }

    public static String currentBnetSelect() {
        return bnetSelect;
    }

    public static String currentError() {
        return error;
    }

    public static double currentVolume() {
        return volume;
    }

    public static boolean isMusicEnabled() {
        return musicEnabled;
    }

    public static double currentMusicVolume() {
        return musicVolume;
    }

    public static void ensureBackgroundMusicPlayback() {
        applyBackgroundMusicState();
    }

    public static String[] novaHoverOptions() {
        return NOVA_HOVER_OPTIONS.clone();
    }

    public static String[] novaClickOptions() {
        return NOVA_CLICK_OPTIONS.clone();
    }

    public static String[] bnetOpenOptions() {
        return BNET_OPEN_OPTIONS.clone();
    }

    public static String[] bnetCloseOptions() {
        return BNET_CLOSE_OPTIONS.clone();
    }

    public static String[] bnetSelectOptions() {
        return BNET_SELECT_OPTIONS.clone();
    }

    public static String[] errorOptions() {
        return ERROR_OPTIONS.clone();
    }

    public static String[] combinedSoundOptions() {
        Set<String> all = new LinkedHashSet<>();
        addAll(all, ALL_OPTIONS);
        return all.toArray(new String[0]);
    }

    public static void bindNovaButton(ButtonBase button) {
        if (button == null || markBound(button, "nova")) {
            return;
        }

        button.addEventHandler(MouseEvent.MOUSE_ENTERED, e -> {
            if (!button.isDisabled()) {
                playNovaHover();
            }
        });
        button.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
            if (!button.isDisabled() && e.getButton() == MouseButton.PRIMARY) {
                playNovaClick();
            }
        });
    }

    public static void bindBnetButton(ButtonBase button) {
        if (button == null || markBound(button, "bnet")) {
            return;
        }

        button.addEventHandler(MouseEvent.MOUSE_ENTERED, e -> {
            if (!button.isDisabled()) {
                // Keep one consistent button profile across the app.
                playNovaHover();
            }
        });
        button.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
            if (!button.isDisabled() && e.getButton() == MouseButton.PRIMARY) {
                playNovaClick();
            }
        });
    }

    public static void bindBnetDropdown(ComboBoxBase<?> comboBox) {
        if (comboBox == null || markBound(comboBox, "dropdown")) {
            return;
        }

        comboBox.showingProperty().addListener((obs, oldValue, showing) -> {
            if (showing) {
                playBnetOpen();
            } else {
                playBnetClose();
            }
        });
        comboBox.addEventHandler(ActionEvent.ACTION, e -> playBnetSelect());
    }

    public static void bindToggle(ButtonBase toggle) {
        if (toggle == null || markBound(toggle, "toggle")) {
            return;
        }
        toggle.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
            if (!toggle.isDisabled() && e.getButton() == MouseButton.PRIMARY) {
                playNovaClick();
            }
        });
    }

    public static void bindSlider(Slider slider) {
        if (slider == null || markBound(slider, "slider")) {
            return;
        }
        slider.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
            if (!slider.isDisabled() && e.getButton() == MouseButton.PRIMARY) {
                playNovaClick();
            }
        });
    }

    public static void playNovaHover() {
        playFile(novaHover, 0.64);
    }

    public static void playNovaClick() {
        playFile(novaClick, 0.75);
    }

    public static void playBnetOpen() {
        playFile(bnetOpen, 0.72);
    }

    public static void playBnetClose() {
        playFile(bnetClose, 0.72);
    }

    public static void playBnetSelect() {
        playFile(bnetSelect, 0.8);
    }

    public static void playError() {
        playFile(error, 0.9);
    }

    private static boolean markBound(javafx.scene.Node node, String profile) {
        String key = NODE_BIND_KEY + "." + profile;
        Object existing = node.getProperties().putIfAbsent(key, Boolean.TRUE);
        return existing != null;
    }

    private static void playFile(String fileName, double volume) {
        if (!enabled || fileName == null || fileName.isBlank()) {
            return;
        }
        double effective = boostedVolume(volume * UiSoundManager.volume);
        if (!AUDIOCLIP_UNSUPPORTED.contains(fileName)) {
            AudioClip clip = CLIP_CACHE.computeIfAbsent(fileName, UiSoundManager::loadClip);
            if (clip != null) {
                clip.play(clampVolume(effective));
                return;
            }
            AUDIOCLIP_UNSUPPORTED.add(fileName);
        }
        playWithJavaSound(fileName, effective);
    }

    private static AudioClip loadClip(String fileName) {
        URL url = UiSoundManager.class.getResource(SOUNDS_BASE + fileName);
        if (url == null) {
            AppLog.warn("[AUDIO] UI sound not found: " + fileName);
            return null;
        }
        try {
            return new AudioClip(url.toExternalForm());
        } catch (Exception ex) {
            if (FALLBACK_LOGGED.add(fileName)) {
                AppLog.warn("[AUDIO] AudioClip unsupported for " + fileName + ", switching to JavaSound fallback.");
            }
            return null;
        }
    }

    private static void playWithJavaSound(String fileName, double linearVolume) {
        JavaSoundBuffer buffer = JAVA_SOUND_CACHE.computeIfAbsent(fileName, UiSoundManager::loadJavaSoundBuffer);
        if (buffer == null) {
            return;
        }
        try {
            Clip clip = AudioSystem.getClip();
            clip.open(buffer.format(), buffer.data(), 0, buffer.data().length);
            applyClipGain(clip, linearVolume);
            clip.addLineListener(event -> {
                if (event.getType() == LineEvent.Type.STOP || event.getType() == LineEvent.Type.CLOSE) {
                    clip.close();
                }
            });
            clip.start();
        } catch (Exception ex) {
            AppLog.exception(ex);
        }
    }

    private static JavaSoundBuffer loadJavaSoundBuffer(String fileName) {
        URL url = UiSoundManager.class.getResource(SOUNDS_BASE + fileName);
        if (url == null) {
            AppLog.warn("[AUDIO] UI sound not found for fallback: " + fileName);
            return null;
        }

        try (InputStream is = url.openStream();
             BufferedInputStream bis = new BufferedInputStream(is);
             AudioInputStream source = AudioSystem.getAudioInputStream(bis)) {

            AudioFormat sourceFormat = source.getFormat();
            AudioFormat pcmFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    sourceFormat.getSampleRate(),
                    16,
                    sourceFormat.getChannels(),
                    sourceFormat.getChannels() * 2,
                    sourceFormat.getSampleRate(),
                    false
            );

            try (AudioInputStream pcm = AudioSystem.getAudioInputStream(pcmFormat, source);
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] chunk = new byte[8192];
                int read;
                while ((read = pcm.read(chunk)) != -1) {
                    out.write(chunk, 0, read);
                }
                return new JavaSoundBuffer(pcmFormat, out.toByteArray());
            }
        } catch (Exception ex) {
            AppLog.warn("[AUDIO] Fallback load failed for " + fileName + ": " + ex.getMessage());
            return null;
        }
    }

    private static void applyClipGain(Clip clip, double linearVolume) {
        if (clip == null) {
            return;
        }
        try {
            if (!clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                return;
            }
            FloatControl gain = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
            double clamped = Math.max(0.0, Math.min(MAX_BOOSTED_VOLUME, linearVolume));
            float targetDb;
            if (clamped <= 0.0001) {
                targetDb = gain.getMinimum();
            } else {
                targetDb = (float) (20.0 * Math.log10(clamped));
            }
            targetDb = Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), targetDb));
            gain.setValue(targetDb);
        } catch (Exception ignored) {
            // No-op: if gain control is unavailable, playback still works with default loudness.
        }
    }

    private static void applyBackgroundMusicState() {
        runOnFxThread(() -> {
            if (!musicEnabled) {
                stopBackgroundMusicOnFxThread();
                return;
            }
            MediaPlayer player = ensureBackgroundMusicPlayerOnFxThread();
            if (player == null) {
                return;
            }
            player.setVolume(clampVolume(musicVolume));
            if (player.getStatus() != MediaPlayer.Status.PLAYING) {
                player.play();
            }
        });
    }

    private static MediaPlayer ensureBackgroundMusicPlayerOnFxThread() {
        MediaPlayer existing = backgroundMusicPlayer;
        if (existing != null) {
            return existing;
        }

        URL url = UiSoundManager.class.getResource(MUSIC_BASE + MUSIC_TRACK);
        if (url == null) {
            AppLog.warn("[AUDIO] Background music not found: " + MUSIC_TRACK);
            return null;
        }

        try {
            Media media = new Media(url.toExternalForm());
            MediaPlayer player = new MediaPlayer(media);
            player.setCycleCount(MediaPlayer.INDEFINITE);
            player.setAutoPlay(false);
            player.setOnError(() -> {
                if (player.getError() != null) {
                    AppLog.warn("[AUDIO] Background music player error: " + player.getError().getMessage());
                }
            });
            backgroundMusicPlayer = player;
            return player;
        } catch (Exception ex) {
            AppLog.warn("[AUDIO] Failed to initialize background music: " + ex.getMessage());
            return null;
        }
    }

    private static void stopBackgroundMusicOnFxThread() {
        MediaPlayer player = backgroundMusicPlayer;
        if (player == null) {
            return;
        }
        try {
            player.stop();
        } catch (Exception ignored) {
            // ignore stop failures
        }
    }

    private static void runOnFxThread(Runnable action) {
        if (action == null) {
            return;
        }
        if (Platform.isFxApplicationThread()) {
            action.run();
            return;
        }
        try {
            Platform.runLater(action);
        } catch (IllegalStateException ignored) {
            // JavaFX runtime not ready yet
        }
    }

    private static String sanitizeOption(String value, String[] options, String fallback) {
        if (value != null) {
            for (String option : options) {
                if (option.equalsIgnoreCase(value.trim())) {
                    return option;
                }
            }
        }
        return fallback;
    }

    private static double sanitizeVolume(String value, double fallback) {
        if (value == null || value.isBlank()) {
            return clampVolume(fallback);
        }
        try {
            return clampVolume(Double.parseDouble(value.trim()));
        } catch (NumberFormatException ignored) {
            return clampVolume(fallback);
        }
    }

    private static double clampVolume(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static double boostedVolume(double value) {
        return Math.max(0.0, value * LOUDNESS_BOOST);
    }

    private static void addAll(Set<String> target, String[] values) {
        if (target == null || values == null) {
            return;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                target.add(value);
            }
        }
    }

    private record JavaSoundBuffer(AudioFormat format, byte[] data) {}
}

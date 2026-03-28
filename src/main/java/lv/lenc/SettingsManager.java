package lv.lenc;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;


public class SettingsManager {
    private static final String SETTINGS_FILE = "settings.properties";
    private static final String LANGUAGE_KEY = "language";
    private static final String GRID_ALPHA_KEY = "gridAlpha";
    private static final String POINT_ALPHA_KEY = "pointAlpha";
    private static final String FLASH_ALPHA_KEY = "flashAlpha";
    public static final String TABLE_LIGHTING_KEY = "ui.tableLighting";
    public static final String SHIMMERS_KEY = "ui.shimmers";
    public static final String BLUR_KEY = "ui.backgroundBlur";
    public static final double DEFAULT_GRID_ALPHA = 0.035;
    public static final double DEFAULT_POINT_ALPHA = 0.25;
    public static final double DEFAULT_FLASH_ALPHA = 0.25;

    public static final boolean DEFAULT_TABLE_LIGHTING = true;
    public static final boolean DEFAULT_SHIMMERS = true;
    public static final boolean DEFAULT_BACKGROUND_BLUR = true;

    public static final String TRANSLATION_CACHE_PERSIST_KEY = "translation.cache.persistent";
    public static final boolean DEFAULT_TRANSLATION_CACHE_PERSIST = false;

    public static final String USE_GPU_DOCKER_KEY = "use.gpu.docker";
    public static final boolean DEFAULT_USE_GPU_DOCKER = false;

    public static void saveLanguage(String langCode) {
        saveProperty(LANGUAGE_KEY, langCode);
    }

    public static String loadLanguage() {
        Properties props = new Properties();
        try (FileInputStream in = new FileInputStream(SETTINGS_FILE)) {
            props.load(in);
            return props.getProperty(LANGUAGE_KEY, "en"); // default EN
        } catch (IOException e) {
            return "en"; // if file does not exist — use EN
        }
    }
    public static void saveAlphaValues(double grid, double point, double flash) {
        Properties props = new Properties();
        props.setProperty(LANGUAGE_KEY, loadLanguage()); // also save current language
        props.setProperty(GRID_ALPHA_KEY, String.valueOf(grid));
        props.setProperty(POINT_ALPHA_KEY, String.valueOf(point));
        props.setProperty(FLASH_ALPHA_KEY, String.valueOf(flash));

        try (FileOutputStream out = new FileOutputStream(SETTINGS_FILE)) {
            props.store(out, null);
            AppLog.info("[Settings] Alpha settings saved.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public static double loadAlpha(String key, double defaultValue) {
        Properties props = new Properties();
        try (FileInputStream in = new FileInputStream(SETTINGS_FILE)) {
            props.load(in);
            return Double.parseDouble(props.getProperty(key, String.valueOf(defaultValue)));
        } catch (IOException | NumberFormatException e) {
            return defaultValue;
        }
    }
    // === Checkbox handling ===

    public static boolean loadCheckboxState(String key, boolean defaultValue) {
        Properties props = loadAllProperties();
        return Boolean.parseBoolean(props.getProperty(key, Boolean.toString(defaultValue)));
    }

    public static boolean loadTranslationCachePersistence() {
        return loadCheckboxState(TRANSLATION_CACHE_PERSIST_KEY, DEFAULT_TRANSLATION_CACHE_PERSIST);
    }

    public static void saveTranslationCachePersistence(boolean enabled) {
        saveProperty(TRANSLATION_CACHE_PERSIST_KEY, Boolean.toString(enabled));
    }

    public static boolean loadUseGpuDocker() {
        return loadCheckboxState(USE_GPU_DOCKER_KEY, DEFAULT_USE_GPU_DOCKER);
    }

    public static void saveUseGpuDocker(boolean enabled) {
        saveProperty(USE_GPU_DOCKER_KEY, Boolean.toString(enabled));
    }

    private static Properties loadAllProperties() {
        Properties props = new Properties();
        try (FileInputStream in = new FileInputStream(SETTINGS_FILE)) {
            props.load(in);
        } catch (IOException ignored) {} // if file does not exist — it will be created later
        return props;
    }

    public static void saveAllSettings(

            double gridAlpha,
            double pointAlpha,
            double flashAlpha,
            boolean tableLighting,
            boolean shimmers,
            boolean backgroundBlur
    ) {
        Properties props = loadAllProperties();

        props.setProperty(GRID_ALPHA_KEY, String.valueOf(gridAlpha));
        props.setProperty(POINT_ALPHA_KEY, String.valueOf(pointAlpha));
        props.setProperty(FLASH_ALPHA_KEY, String.valueOf(flashAlpha));
        props.setProperty(TABLE_LIGHTING_KEY, Boolean.toString(tableLighting));
        props.setProperty(SHIMMERS_KEY, Boolean.toString(shimmers));
        props.setProperty(BLUR_KEY, Boolean.toString(backgroundBlur));

        try (FileOutputStream out = new FileOutputStream(SETTINGS_FILE)) {
            props.store(out, null);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    // 1. Method to load all UI settings (Object[])
    public static Object[] loadUiSettings() {
        double gridAlpha = loadAlpha("gridAlpha", DEFAULT_GRID_ALPHA);
        double pointAlpha = loadAlpha("pointAlpha", DEFAULT_POINT_ALPHA);
        double flashAlpha = loadAlpha("flashAlpha", DEFAULT_FLASH_ALPHA);
        boolean tableLighting = loadCheckboxState("ui.tableLighting", DEFAULT_TABLE_LIGHTING);
        boolean shimmers = loadCheckboxState("ui.shimmers", DEFAULT_SHIMMERS);
        boolean backgroundBlur = loadCheckboxState("ui.backgroundBlur", DEFAULT_BACKGROUND_BLUR);

        return new Object[]{ gridAlpha, pointAlpha, flashAlpha, tableLighting, shimmers, backgroundBlur };
    }

    // 2. Method to apply settings (receives references to your objects)
    public static void applyUiSettings(Object[] ui,
                                       BackgroundGridLayer backgroundLayer,
                                       CustomBorder borderTable) {
        backgroundLayer.setGridAlpha((double) ui[0]);
        backgroundLayer.setPointAlpha((double) ui[1]);
        backgroundLayer.setFlashAlpha((double) ui[2]);
        borderTable.setTableLightingVisible((boolean) ui[3]);
        backgroundLayer.shimmerContainer.setVisible((boolean) ui[4]);
        backgroundLayer.blurredLights.setVisible((boolean) ui[5]);
    }
    public static void saveProperty(String key, String value) {
        Properties props = loadAllProperties();
        props.setProperty(key, value);
        try (FileOutputStream out = new FileOutputStream(SETTINGS_FILE)) {
            props.store(out, null);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


}

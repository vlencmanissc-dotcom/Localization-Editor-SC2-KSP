package lv.lenc;
import java.io.*;
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

    public static void saveLanguage(String langCode) {
        saveProperty(LANGUAGE_KEY, langCode);
    }

    public static String loadLanguage() {
        Properties props = new Properties();
        try (FileInputStream in = new FileInputStream(SETTINGS_FILE)) {
            props.load(in);
            return props.getProperty(LANGUAGE_KEY, "en"); // по умолчанию EN
        } catch (IOException e) {
            return "en"; // если файла нет — тоже EN
        }
    }
    public static void saveAlphaValues(double grid, double point, double flash) {
        Properties props = new Properties();
        props.setProperty(LANGUAGE_KEY, loadLanguage()); // сохранить текущий язык тоже
        props.setProperty(GRID_ALPHA_KEY, String.valueOf(grid));
        props.setProperty(POINT_ALPHA_KEY, String.valueOf(point));
        props.setProperty(FLASH_ALPHA_KEY, String.valueOf(flash));

        try (FileOutputStream out = new FileOutputStream(SETTINGS_FILE)) {
            props.store(out, null);
            System.out.println("Alpha settings saved.");
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
     //=== Checkbox ===


    public static boolean loadCheckboxState(String key, boolean defaultValue) {
        Properties props = loadAllProperties();
        return Boolean.parseBoolean(props.getProperty(key, Boolean.toString(defaultValue)));
    }
    private static Properties loadAllProperties() {
        Properties props = new Properties();
        try (FileInputStream in = new FileInputStream(SETTINGS_FILE)) {
            props.load(in);
        } catch (IOException ignored) {} // если файла нет — создастся позже
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
    // 1. Метод для загрузки всех значений (Object[])
    public static Object[] loadUiSettings() {
        double gridAlpha = loadAlpha("gridAlpha", DEFAULT_GRID_ALPHA);
        double pointAlpha = loadAlpha("pointAlpha", DEFAULT_POINT_ALPHA);
        double flashAlpha = loadAlpha("flashAlpha", DEFAULT_FLASH_ALPHA);
        boolean tableLighting = loadCheckboxState("ui.tableLighting", DEFAULT_TABLE_LIGHTING);
        boolean shimmers = loadCheckboxState("ui.shimmers", DEFAULT_SHIMMERS);
        boolean backgroundBlur = loadCheckboxState("ui.backgroundBlur", DEFAULT_BACKGROUND_BLUR);

        return new Object[]{ gridAlpha, pointAlpha, flashAlpha, tableLighting, shimmers, backgroundBlur };
    }

    // 2. Метод для присвоения (принимает ссылки на твои объекты)
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

package lv.lenc;

import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

public class LocalizationManager {
    private ResourceBundle bundle;
    private String currentLanguageCode;

    public LocalizationManager(String languageCode) {
        this.currentLanguageCode = normalizeLanguageCode(languageCode);
        bundle = loadBundle(this.currentLanguageCode);
    }

    public String getCurrentLanguage() {
        return currentLanguageCode;
    }
    public String get(String key) {
        try {
            return bundle.getString(key);
        } catch (MissingResourceException ex) {
            try {
                return ResourceBundle.getBundle("messages", Locale.ENGLISH).getString(key);
            } catch (MissingResourceException ignored) {
                return key;
            }
        }
    }

    public void changeLanguage(String languageCode) {
        this.currentLanguageCode = normalizeLanguageCode(languageCode);
        bundle = loadBundle(this.currentLanguageCode);
    }

    private static String normalizeLanguageCode(String languageCode) {
        if (languageCode == null || languageCode.isBlank()) return "en";
        return languageCode.trim();
    }

    private static ResourceBundle loadBundle(String languageCode) {
        Locale locale = Locale.forLanguageTag(languageCode.replace('_', '-'));
        try {
            return ResourceBundle.getBundle("messages", locale);
        } catch (MissingResourceException ex) {
            String languageOnly = locale.getLanguage();
            if (languageOnly != null && !languageOnly.isBlank()) {
                try {
                    return ResourceBundle.getBundle("messages", new Locale(languageOnly));
                } catch (MissingResourceException ignored) {
                    // fallback below
                }
            }
            return ResourceBundle.getBundle("messages", Locale.ENGLISH);
        }
    }

}

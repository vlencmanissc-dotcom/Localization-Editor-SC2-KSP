package lv.lenc;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;

public class LocalizationManager {
    private static final ResourceBundle.Control UTF8_CONTROL = new ResourceBundle.Control() {
        @Override
        public ResourceBundle newBundle(String baseName,
                                        Locale locale,
                                        String format,
                                        ClassLoader loader,
                                        boolean reload) throws IllegalAccessException, InstantiationException, IOException {
            if (!"java.properties".equals(format)) {
                return super.newBundle(baseName, locale, format, loader, reload);
            }
            String bundleName = toBundleName(baseName, locale);
            String resourceName = toResourceName(bundleName, "properties");
            InputStream stream;
            if (reload) {
                URL url = loader.getResource(resourceName);
                if (url == null) return null;
                URLConnection connection = url.openConnection();
                if (connection == null) return null;
                connection.setUseCaches(false);
                stream = connection.getInputStream();
            } else {
                stream = loader.getResourceAsStream(resourceName);
            }
            if (stream == null) {
                return null;
            }
            try (InputStream in = stream; Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return new PropertyResourceBundle(reader);
            }
        }
    };

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
                return ResourceBundle.getBundle("messages", Locale.ENGLISH, UTF8_CONTROL).getString(key);
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
            return ResourceBundle.getBundle("messages", locale, UTF8_CONTROL);
        } catch (MissingResourceException ex) {
            String languageOnly = locale.getLanguage();
            if (languageOnly != null && !languageOnly.isBlank()) {
                try {
                    return ResourceBundle.getBundle("messages", new Locale(languageOnly), UTF8_CONTROL);
                } catch (MissingResourceException ignored) {
                    // fallback below
                }
            }
            return ResourceBundle.getBundle("messages", Locale.ENGLISH, UTF8_CONTROL);
        }
    }

}

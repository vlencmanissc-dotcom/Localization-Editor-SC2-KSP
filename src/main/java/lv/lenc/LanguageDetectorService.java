package lv.lenc;

import com.cybozu.labs.langdetect.Detector;
import com.cybozu.labs.langdetect.DetectorFactory;
import com.cybozu.labs.langdetect.LangDetectException;

import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class LanguageDetectorService {

    private static final Object LOCK = new Object();
    private static volatile boolean LOADED = false;

    private final Map<String, String> supportedLanguages = new HashMap<>();

    public LanguageDetectorService() throws LangDetectException {
        ensureProfilesLoaded();

        supportedLanguages.put("ru", "ruRU");
        supportedLanguages.put("de", "deDE");
        supportedLanguages.put("en", "enUS");
        supportedLanguages.put("es", "esES");
        supportedLanguages.put("fr", "frFR");
        supportedLanguages.put("it", "itIT");
        supportedLanguages.put("pl", "plPL");
        supportedLanguages.put("pt", "ptBR");
        supportedLanguages.put("ko", "koKR");
        supportedLanguages.put("zh-cn", "zhCN");
        supportedLanguages.put("zh-tw", "zhTW");
    }

    private static void ensureProfilesLoaded() throws LangDetectException {
        if (LOADED) return;
        synchronized (LOCK) {
            if (LOADED) return;
            DetectorFactory.loadProfile(Paths.get("src", "main", "resources", "profiles").toString());
            LOADED = true;
        }
    }

    public String detectLanguage(String text) throws LangDetectException {
        Detector detector = DetectorFactory.create();
        detector.append(text);
        String lang = detector.detect();
        return supportedLanguages.getOrDefault(lang, "unknown");
    }
}

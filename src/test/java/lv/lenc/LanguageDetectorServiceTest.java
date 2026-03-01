package lv.lenc;

import org.junit.jupiter.api.Test;

public class LanguageDetectorServiceTest {
    @Test
    public void testDetectLanguage_EN() throws Exception {
        LanguageDetectorService languageDetectorService = new LanguageDetectorService();
        String lang = languageDetectorService.detectLanguage("Hello world!");
        System.out.println(lang);
    }

    @Test
    public void testDetectLanguage_RU() throws Exception {
        LanguageDetectorService languageDetectorService = new LanguageDetectorService();
        String lang = languageDetectorService.detectLanguage("Привет Мир! Это Русский");
        System.out.println(lang);
    }
}

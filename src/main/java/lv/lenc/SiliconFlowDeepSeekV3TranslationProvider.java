package lv.lenc;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import okhttp3.OkHttpClient;

final class SiliconFlowDeepSeekV3TranslationProvider {
    static final String MODEL_ID = "deepseek-ai/DeepSeek-V3.2";

    private SiliconFlowDeepSeekV3TranslationProvider() {
    }

    static boolean isConfigured() {
        return SiliconFlowTranslationProvider.isConfigured();
    }

    static String checkAvailability(OkHttpClient http) {
        SiliconFlowTranslationProvider.setRuntimeModelOverride(MODEL_ID, true);
        return SiliconFlowTranslationProvider.checkAvailability(http);
    }

    static List<String> translatePreparedTexts(
            List<String> uncachedInputs,
            String source,
            String target,
            OkHttpClient http
    ) throws IOException {
        SiliconFlowTranslationProvider.setRuntimeModelOverride(MODEL_ID, true);
        return SiliconFlowTranslationProvider.translatePreparedTexts(uncachedInputs, source, target, http);
    }

    static List<String> inflectGlossaryPlaceholders(
            List<String> textsWithTokens,
            List<Map<String, String>> tokenMaps,
            String targetLang,
            OkHttpClient http
    ) throws IOException {
        SiliconFlowTranslationProvider.setRuntimeModelOverride(MODEL_ID, true);
        return SiliconFlowTranslationProvider.inflectGlossaryPlaceholders(textsWithTokens, tokenMaps, targetLang, http);
    }
}

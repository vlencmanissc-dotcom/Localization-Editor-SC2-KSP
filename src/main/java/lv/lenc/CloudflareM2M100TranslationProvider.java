package lv.lenc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

final class CloudflareM2M100TranslationProvider {
    static final String MODEL_ID = "@cf/meta/m2m100-1.2b";
    private static final String ENDPOINT_TEMPLATE =
            "https://api.cloudflare.com/client/v4/accounts/%s/ai/run/" + MODEL_ID;
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private CloudflareM2M100TranslationProvider() {
    }

    static boolean isConfigured() {
        return !resolveAccountId().isBlank() && !resolveApiToken().isBlank();
    }

    static String activeEndpointForLogs() {
        String accountId = resolveAccountId();
        if (accountId.isBlank()) {
            return "https://api.cloudflare.com/client/v4/accounts/<account-id>/ai/run/" + MODEL_ID;
        }
        return String.format(Locale.ROOT, ENDPOINT_TEMPLATE, accountId);
    }

    static String checkAvailability(OkHttpClient http) {
        if (!isConfigured()) {
            return "Cloudflare Worker AI (M2M100) requires CLOUDFLARE_ACCOUNT_ID and CLOUDFLARE_API_TOKEN "
                    + "or settings.properties cloudflare.account.id/cloudflare.api.token.";
        }
        try {
            translateSingle("ping", "en", "en", http);
            return "";
        } catch (IOException ex) {
            String message = ex.getMessage();
            return (message == null || message.isBlank())
                    ? "Cloudflare Worker AI availability check failed."
                    : message;
        }
    }

    static List<String> translatePreparedTexts(
            List<String> uncachedInputs,
            String source,
            String target,
            OkHttpClient http
    ) throws IOException {
        if (uncachedInputs == null || uncachedInputs.isEmpty()) {
            return List.of();
        }
        if (!isConfigured()) {
            throw new IOException("Cloudflare Worker AI (M2M100) is not configured");
        }

        String sourceLang = normalizeSourceLang(source);
        String targetLang = normalizeTargetLang(target);
        List<String> translated = new ArrayList<>(uncachedInputs.size());
        for (String text : uncachedInputs) {
            translated.add(translateSingle(text == null ? "" : text, sourceLang, targetLang, http));
        }
        return translated;
    }

    private static String translateSingle(
            String text,
            String sourceLang,
            String targetLang,
            OkHttpClient http
    ) throws IOException {
        String accountId = resolveAccountId();
        String token = resolveApiToken();
        if (accountId.isBlank() || token.isBlank()) {
            throw new IOException("Cloudflare Worker AI credentials are missing");
        }

        String endpoint = String.format(Locale.ROOT, ENDPOINT_TEMPLATE, accountId);
        JsonObject payload = new JsonObject();
        payload.addProperty("text", text);
        if (sourceLang != null && !sourceLang.isBlank() && !"auto".equalsIgnoreCase(sourceLang)) {
            payload.addProperty("source_lang", sourceLang);
        }
        payload.addProperty("target_lang", targetLang);

        Request request = new Request.Builder()
                .url(endpoint)
                .post(RequestBody.create(payload.toString().getBytes(StandardCharsets.UTF_8), JSON))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/json")
                .build();

        try (Response response = http.newCall(request).execute()) {
            String responseText = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                throw new IOException("[Cloudflare M2M100] HTTP " + response.code() + ": " + shortenDiagnostic(responseText));
            }
            if (responseText.isBlank()) {
                throw new IOException("[Cloudflare M2M100] Empty response");
            }

            JsonElement parsed = JsonParser.parseString(responseText);
            String translated = extractTranslatedText(parsed);
            if (translated == null) {
                throw new IOException("[Cloudflare M2M100] Missing translated_text in response");
            }
            return translated;
        }
    }

    private static String extractTranslatedText(JsonElement parsed) {
        if (parsed == null || !parsed.isJsonObject()) {
            return null;
        }
        JsonObject root = parsed.getAsJsonObject();
        if (root.has("result") && root.get("result").isJsonObject()) {
            JsonObject result = root.getAsJsonObject("result");
            if (result.has("translated_text") && !result.get("translated_text").isJsonNull()) {
                return result.get("translated_text").getAsString();
            }
        }
        if (root.has("translated_text") && !root.get("translated_text").isJsonNull()) {
            return root.get("translated_text").getAsString();
        }
        return null;
    }

    private static String resolveAccountId() {
        String accountId = SettingsManager.loadCloudflareAccountId();
        return accountId == null ? "" : accountId.trim();
    }

    private static String resolveApiToken() {
        String token = SettingsManager.loadCloudflareApiToken();
        return token == null ? "" : token.trim();
    }

    private static String normalizeSourceLang(String value) {
        String normalized = normalizeLang(value);
        return normalized.isBlank() ? "en" : normalized;
    }

    private static String normalizeTargetLang(String value) {
        String normalized = normalizeLang(value);
        return normalized.isBlank() ? "en" : normalized;
    }

    private static String normalizeLang(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "ru", "ruru" -> "ru";
            case "de", "dede" -> "de";
            case "en", "enus" -> "en";
            case "es", "eses", "esmx" -> "es";
            case "fr", "frfr" -> "fr";
            case "it", "itit" -> "it";
            case "pl", "plpl" -> "pl";
            case "pt", "ptbr" -> "pt";
            case "ko", "kokr" -> "ko";
            case "zh", "zhcn", "zhtw" -> "zh";
            case "auto" -> "auto";
            default -> normalized.length() > 2 ? normalized.substring(0, 2) : normalized;
        };
    }

    private static String shortenDiagnostic(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        final int maxLen = 320;
        if (normalized.length() <= maxLen) {
            return normalized;
        }
        return normalized.substring(0, maxLen - 3) + "...";
    }
}

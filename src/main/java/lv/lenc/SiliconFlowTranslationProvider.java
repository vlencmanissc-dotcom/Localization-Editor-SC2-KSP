package lv.lenc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

final class SiliconFlowTranslationProvider {
    static final String DEFAULT_MODEL_ID = "Qwen/Qwen2.5-7B-Instruct";
    private static final List<String> FALLBACK_MODELS = List.of(
            DEFAULT_MODEL_ID,
            "Qwen/Qwen3-8B"
    );
    private static final String PRIMARY_CHAT_ENDPOINT = "https://api.siliconflow.cn/v1/chat/completions";
    private static final String SECONDARY_CHAT_ENDPOINT = "https://api.siliconflow.com/v1/chat/completions";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static volatile String activeEndpoint = PRIMARY_CHAT_ENDPOINT;
    private static volatile String activeModel = DEFAULT_MODEL_ID;
    private static volatile String runtimeModelOverride = "";
    private static volatile boolean runtimeModelStrict = false;

    private SiliconFlowTranslationProvider() {
    }

    static boolean isConfigured() {
        return !resolveApiKey().isBlank();
    }

    static String checkAvailability(OkHttpClient http) {
        String apiKey = resolveApiKey();
        if (apiKey.isBlank()) {
            AppLog.info("[SILICONFLOW] key source=missing");
            return "SiliconFlow API requires SILICONFLOW_API_KEY or settings.properties siliconflow.api.key.";
        }
        AppLog.info("[SILICONFLOW] key source=" + resolveApiKeySource() + ", key=" + maskKey(apiKey));
        OkHttpClient probeHttp = availabilityHttp(http);

        String lastFailure = "";
        for (String modelId : modelOrder()) {
            for (String endpoint : endpointOrder()) {
                Request request = buildProbeRequest(endpoint, apiKey, modelId);
                try (Response response = probeHttp.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        activeEndpoint = endpoint;
                        activeModel = modelId;
                        return "";
                    }
                    String body = response.body() == null ? "" : response.body().string();
                    if (response.code() == 429) {
                        lastFailure = "SiliconFlow rate limit exceeded (HTTP 429). Free models have fixed limits.";
                        continue;
                    }
                    if (isAuthError(response.code())) {
                        return "SiliconFlow access denied (HTTP " + response.code()
                                + "). Check API key, account verification, and regenerate key if needed.";
                    }
                    if (response.code() == 402) {
                        return "SiliconFlow insufficient balance/permission (HTTP 402). Choose a free model or top up.";
                    }
                    if (response.code() == 404) {
                        lastFailure = "SiliconFlow model '" + modelId + "' is unavailable for this account.";
                        continue;
                    }
                    lastFailure = "SiliconFlow is unavailable (HTTP " + response.code() + "): " + shortenDiagnostic(body);
                } catch (IOException ex) {
                    lastFailure = "SiliconFlow check failed via " + endpoint + ": " + ex.getMessage();
                }
            }
        }
        return lastFailure.isBlank() ? "SiliconFlow availability check failed." : lastFailure;
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

        String apiKey = resolveApiKey();
        if (apiKey.isBlank()) {
            throw new IOException("SiliconFlow API key is not configured");
        }
        OkHttpClient requestHttp = translationHttp(http);

        IOException ioFailure = null;
        String httpFailure = "";
        for (String modelId : modelOrder()) {
            JsonObject payload = buildTranslationPayload(uncachedInputs, source, target, modelId);
            for (String endpoint : endpointOrder()) {
                Request request = new Request.Builder()
                        .url(endpoint)
                        .post(RequestBody.create(payload.toString().getBytes(StandardCharsets.UTF_8), JSON))
                        .header("Authorization", "Bearer " + apiKey)
                        .header("Accept", "application/json")
                        .build();
                try (Response response = requestHttp.newCall(request).execute()) {
                    String responseText = response.body() == null ? "" : response.body().string();
                    if (!response.isSuccessful()) {
                        httpFailure = formatHttpFailure(response.code(), responseText, modelId);
                        if (isAuthError(response.code())) {
                            throw new IOException("[NON_RETRYABLE] " + httpFailure);
                        }
                        if (response.code() == 404 || response.code() == 429 || response.code() >= 500) {
                            continue;
                        }
                        throw new IOException(httpFailure);
                    }
                    List<String> translated = parseTranslationArray(responseText, uncachedInputs.size());
                    if (translated.size() != uncachedInputs.size()) {
                        throw new IOException("[SiliconFlow] MISMATCH: in=" + uncachedInputs.size() + " out=" + translated.size());
                    }
                    activeEndpoint = endpoint;
                    activeModel = modelId;
                    return translated;
                } catch (IOException ex) {
                    ioFailure = ex;
                    if (ex.getMessage() != null && ex.getMessage().toLowerCase().contains("[non_retryable]")) {
                        throw ex;
                    }
                }
            }
        }

        if (ioFailure != null) {
            throw ioFailure;
        }
        throw new IOException(httpFailure.isBlank() ? "[SiliconFlow] translation request failed" : httpFailure);
    }

    static List<String> inflectGlossaryPlaceholders(
            List<String> textsWithTokens,
            List<Map<String, String>> tokenMaps,
            String targetLang,
            OkHttpClient http
    ) throws IOException {
        if (textsWithTokens == null || textsWithTokens.isEmpty()) {
            return List.of();
        }
        if (tokenMaps == null || tokenMaps.size() != textsWithTokens.size()) {
            throw new IOException("[SiliconFlow] invalid placeholder payload");
        }
        String apiKey = resolveApiKey();
        if (apiKey.isBlank()) {
            throw new IOException("SiliconFlow API key is not configured");
        }
        OkHttpClient requestHttp = translationHttp(http);

        IOException ioFailure = null;
        String httpFailure = "";
        for (String modelId : modelOrder()) {
            JsonObject payload = buildInflectionPayload(textsWithTokens, tokenMaps, targetLang, modelId);
            for (String endpoint : endpointOrder()) {
                Request request = new Request.Builder()
                        .url(endpoint)
                        .post(RequestBody.create(payload.toString().getBytes(StandardCharsets.UTF_8), JSON))
                        .header("Authorization", "Bearer " + apiKey)
                        .header("Accept", "application/json")
                        .build();
                try (Response response = requestHttp.newCall(request).execute()) {
                    String responseText = response.body() == null ? "" : response.body().string();
                    if (!response.isSuccessful()) {
                        httpFailure = formatHttpFailure(response.code(), responseText, modelId);
                        if (isAuthError(response.code())) {
                            throw new IOException("[NON_RETRYABLE] " + httpFailure);
                        }
                        if (response.code() == 404 || response.code() == 429 || response.code() >= 500) {
                            continue;
                        }
                        throw new IOException(httpFailure);
                    }
                    List<String> resolved = parseTranslationArray(responseText, textsWithTokens.size());
                    if (resolved.size() != textsWithTokens.size()) {
                        throw new IOException("[SiliconFlow] inflection MISMATCH: in=" + textsWithTokens.size() + " out=" + resolved.size());
                    }
                    activeEndpoint = endpoint;
                    activeModel = modelId;
                    return resolved;
                } catch (IOException ex) {
                    ioFailure = ex;
                    if (ex.getMessage() != null && ex.getMessage().toLowerCase().contains("[non_retryable]")) {
                        throw ex;
                    }
                }
            }
        }
        if (ioFailure != null) {
            throw ioFailure;
        }
        throw new IOException(httpFailure.isBlank() ? "[SiliconFlow] inflection request failed" : httpFailure);
    }

    static String activeEndpointForLogs() {
        return activeEndpoint;
    }

    static String activeModelForLogs() {
        return activeModel;
    }

    static void setRuntimeModelOverride(String modelId, boolean strictMode) {
        String normalized = modelId == null ? "" : modelId.trim();
        runtimeModelOverride = normalized;
        runtimeModelStrict = strictMode && !normalized.isBlank();
    }

    private static List<String> endpointOrder() {
        LinkedHashSet<String> endpoints = new LinkedHashSet<>();
        if (activeEndpoint != null && !activeEndpoint.isBlank()) {
            endpoints.add(activeEndpoint);
        }
        endpoints.add(PRIMARY_CHAT_ENDPOINT);
        endpoints.add(SECONDARY_CHAT_ENDPOINT);
        return new ArrayList<>(endpoints);
    }

    private static List<String> modelOrder() {
        String runtimeModel = runtimeModelOverride == null ? "" : runtimeModelOverride.trim();
        if (!runtimeModel.isBlank() && runtimeModelStrict) {
            return List.of(runtimeModel);
        }

        LinkedHashSet<String> models = new LinkedHashSet<>();
        if (!runtimeModel.isBlank()) {
            models.add(runtimeModel);
        }
        String preferred = resolvePreferredModel();
        if (!preferred.isBlank()) {
            models.add(preferred);
        }
        if (activeModel != null && !activeModel.isBlank()) {
            models.add(activeModel);
        }
        models.addAll(FALLBACK_MODELS);
        return new ArrayList<>(models);
    }

    private static List<String> parseTranslationArray(String responseText, int expectedSize) throws IOException {
        if (responseText == null || responseText.isBlank()) {
            throw new IOException("[SiliconFlow] Empty response");
        }
        JsonElement root = JsonParser.parseString(responseText);
        JsonArray choices = root.getAsJsonObject().getAsJsonArray("choices");
        if (choices == null || choices.isEmpty()) {
            throw new IOException("[SiliconFlow] Missing choices");
        }
        JsonObject message = choices.get(0).getAsJsonObject().getAsJsonObject("message");
        if (message == null || !message.has("content")) {
            throw new IOException("[SiliconFlow] Missing message.content");
        }
        String content = message.get("content").isJsonNull() ? "" : message.get("content").getAsString();
        String normalized = stripMarkdownJsonFence(content);
        JsonElement parsed = JsonParser.parseString(normalized);

        List<String> out = new ArrayList<>();
        if (parsed.isJsonArray()) {
            JsonArray arr = parsed.getAsJsonArray();
            for (JsonElement item : arr) {
                out.add(item == null || item.isJsonNull() ? "" : item.getAsString());
            }
        } else if (parsed.isJsonObject() && parsed.getAsJsonObject().has("translations")) {
            JsonArray arr = parsed.getAsJsonObject().getAsJsonArray("translations");
            for (JsonElement item : arr) {
                out.add(item == null || item.isJsonNull() ? "" : item.getAsString());
            }
        } else {
            throw new IOException("[SiliconFlow] Expected JSON array response");
        }

        if (out.size() != expectedSize) {
            throw new IOException("[SiliconFlow] Expected " + expectedSize + " items, got " + out.size());
        }
        return out;
    }

    private static Request buildProbeRequest(String endpoint, String apiKey, String modelId) {
        JsonObject payload = new JsonObject();
        payload.addProperty("model", modelId);
        payload.addProperty("stream", false);
        payload.addProperty("temperature", 0.0);
        payload.addProperty("max_tokens", 1);
        JsonArray messages = new JsonArray();
        JsonObject userMessage = new JsonObject();
        userMessage.addProperty("role", "user");
        userMessage.addProperty("content", "ping");
        messages.add(userMessage);
        payload.add("messages", messages);

        return new Request.Builder()
                .url(endpoint)
                .post(RequestBody.create(payload.toString().getBytes(StandardCharsets.UTF_8), JSON))
                .header("Authorization", "Bearer " + apiKey)
                .header("Accept", "application/json")
                .build();
    }

    private static JsonObject buildTranslationPayload(
            List<String> uncachedInputs,
            String source,
            String target,
            String modelId
    ) {
        JsonObject payload = new JsonObject();
        payload.addProperty("model", modelId);
        payload.addProperty("stream", false);
        payload.addProperty("temperature", 0.0);

        JsonArray messages = new JsonArray();
        JsonObject systemMessage = new JsonObject();
        systemMessage.addProperty("role", "system");
        systemMessage.addProperty(
                "content",
                "You are an expert StarCraft II localization translator. Return only strict JSON with no markdown."
        );
        messages.add(systemMessage);

        JsonObject userMessage = new JsonObject();
        userMessage.addProperty("role", "user");
        userMessage.addProperty(
                "content",
                buildTranslationPrompt(
                        uncachedInputs,
                        source,
                        target,
                        TranslationService.currentRuntimeGlossaryHints()
                )
        );
        messages.add(userMessage);
        payload.add("messages", messages);
        return payload;
    }

    private static JsonObject buildInflectionPayload(
            List<String> textsWithTokens,
            List<Map<String, String>> tokenMaps,
            String targetLang,
            String modelId
    ) {
        JsonObject payload = new JsonObject();
        payload.addProperty("model", modelId);
        payload.addProperty("stream", false);
        payload.addProperty("temperature", 0.0);

        JsonArray messages = new JsonArray();
        JsonObject systemMessage = new JsonObject();
        systemMessage.addProperty("role", "system");
        systemMessage.addProperty(
                "content",
                "You are a StarCraft II glossary morphology post-processor. Return only strict JSON array."
        );
        messages.add(systemMessage);

        JsonObject userMessage = new JsonObject();
        userMessage.addProperty("role", "user");
        userMessage.addProperty("content", buildInflectionPrompt(textsWithTokens, tokenMaps, targetLang));
        messages.add(userMessage);
        payload.add("messages", messages);
        return payload;
    }

    private static String buildTranslationPrompt(List<String> uncachedInputs,
                                                 String source,
                                                 String target,
                                                 Map<String, String> glossaryHints) {
        JsonArray inputArray = new JsonArray();
        for (String text : uncachedInputs) {
            inputArray.add(text == null ? "" : text);
        }
        String sourceValue = (source == null || source.isBlank()) ? "auto" : source;
        Map<String, String> relevantGlossary = filterGlossaryHintsForInputs(uncachedInputs, glossaryHints, 24);
        String glossarySection = buildGlossaryHintSection(relevantGlossary);

        return "You are an expert StarCraft II localization translator.\n"
                + "Context: these strings are from SC2 UI (units, abilities, buttons, tooltips).\n"
                + "Translate each string from source language '" + sourceValue + "' to target language '" + target + "'.\n"
                + "Rules:\n"
                + "1) Preserve placeholders/tokens exactly.\n"
                + "2) Keep input order and number of items unchanged.\n"
                + "3) Return ONLY a JSON array of strings, no markdown, no comments.\n"
                + "4) Use provided SC2 glossary mappings as preferred terminology.\n"
                + "5) Adapt endings/case/gender only when grammar requires it.\n"
                + glossarySection
                + "Input JSON array:\n"
                + inputArray;
    }

    private static String buildInflectionPrompt(List<String> textsWithTokens,
                                                List<Map<String, String>> tokenMaps,
                                                String targetLang) {
        JsonArray items = new JsonArray();
        for (int i = 0; i < textsWithTokens.size(); i++) {
            JsonObject obj = new JsonObject();
            obj.addProperty("text", textsWithTokens.get(i) == null ? "" : textsWithTokens.get(i));
            JsonObject terms = new JsonObject();
            Map<String, String> map = tokenMaps.get(i);
            if (map != null) {
                for (Map.Entry<String, String> e : map.entrySet()) {
                    if (e.getKey() == null || e.getKey().isBlank() || e.getValue() == null || e.getValue().isBlank()) {
                        continue;
                    }
                    terms.addProperty(e.getKey(), e.getValue());
                }
            }
            obj.add("terms", terms);
            items.add(obj);
        }
        String target = (targetLang == null || targetLang.isBlank()) ? "auto" : targetLang;
        return "For each item, replace placeholders __SC2_TERM_n__ in text using provided base terms.\n"
                + "Language of text: '" + target + "'.\n"
                + "Rules:\n"
                + "1) Replace placeholders only.\n"
                + "2) Keep all other text unchanged as much as possible.\n"
                + "3) You may inflect base terms for grammar.\n"
                + "4) If unsure, keep base term unchanged.\n"
                + "5) Return ONLY JSON array of final strings in same order.\n"
                + "Input JSON array:\n"
                + items;
    }

    private static String buildGlossaryHintSection(Map<String, String> glossaryHints) {
        if (glossaryHints == null || glossaryHints.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("SC2 glossary for this batch:\n");
        int count = 0;
        for (Map.Entry<String, String> e : glossaryHints.entrySet()) {
            if (count >= 24) {
                break;
            }
            String sourceTerm = e.getKey();
            String targetTerm = e.getValue();
            if (sourceTerm == null || sourceTerm.isBlank() || targetTerm == null || targetTerm.isBlank()) {
                continue;
            }
            sb.append("- ").append(sourceTerm).append(" -> ").append(targetTerm).append("\n");
            count++;
        }
        if (count == 0) {
            return "";
        }
        return sb.toString();
    }

    private static Map<String, String> filterGlossaryHintsForInputs(List<String> inputs,
                                                                    Map<String, String> glossaryHints,
                                                                    int limit) {
        if (inputs == null || inputs.isEmpty() || glossaryHints == null || glossaryHints.isEmpty() || limit <= 0) {
            return Map.of();
        }

        List<String> normalizedInputs = new ArrayList<>(inputs.size());
        for (String input : inputs) {
            normalizedInputs.add((input == null ? "" : input).toLowerCase(Locale.ROOT));
        }

        Map<String, String> filtered = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : glossaryHints.entrySet()) {
            if (filtered.size() >= limit) {
                break;
            }
            String sourceTerm = entry.getKey();
            String targetTerm = entry.getValue();
            if (sourceTerm == null || sourceTerm.isBlank() || targetTerm == null || targetTerm.isBlank()) {
                continue;
            }
            String needle = sourceTerm.toLowerCase(Locale.ROOT);
            for (String input : normalizedInputs) {
                if (input.contains(needle)) {
                    filtered.putIfAbsent(sourceTerm, targetTerm);
                    break;
                }
            }
        }
        return filtered;
    }

    private static String stripMarkdownJsonFence(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline >= 0) {
                trimmed = trimmed.substring(firstNewline + 1);
            }
            int lastFence = trimmed.lastIndexOf("```");
            if (lastFence >= 0) {
                trimmed = trimmed.substring(0, lastFence);
            }
        }
        return trimmed.trim();
    }

    private static String resolveApiKey() {
        String apiKey = SettingsManager.loadSiliconFlowApiKey();
        if (apiKey == null) {
            return "";
        }
        String normalized = apiKey.replace("\uFEFF", "").replaceAll("\\s+", "");
        return normalized.trim();
    }

    private static String resolvePreferredModel() {
        String model = SettingsManager.loadSiliconFlowModel();
        if (model == null) {
            return "";
        }
        return model.trim();
    }

    private static String resolveApiKeySource() {
        String envValue = System.getenv("SILICONFLOW_API_KEY");
        if (envValue != null && !envValue.trim().isEmpty()) {
            return "env:SILICONFLOW_API_KEY";
        }
        return "settings:siliconflow.api.key";
    }

    private static String maskKey(String key) {
        if (key == null) return "empty";
        String trimmed = key.trim();
        if (trimmed.isEmpty()) return "empty";
        if (trimmed.length() <= 8) return "***";
        return trimmed.substring(0, 4) + "..." + trimmed.substring(trimmed.length() - 4);
    }

    private static String shortenDiagnostic(String value) {
        if (value == null || value.isBlank()) return "";
        String normalized = value.replaceAll("\\s+", " ").trim();
        final int maxLen = 320;
        if (normalized.length() <= maxLen) {
            return normalized;
        }
        return normalized.substring(0, maxLen - 3) + "...";
    }

    private static OkHttpClient availabilityHttp(OkHttpClient base) {
        return base.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(12))
                .writeTimeout(Duration.ofSeconds(12))
                .callTimeout(Duration.ofSeconds(15))
                .build();
    }

    private static OkHttpClient translationHttp(OkHttpClient base) {
        return base.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .readTimeout(Duration.ofSeconds(25))
                .writeTimeout(Duration.ofSeconds(25))
                .callTimeout(Duration.ofSeconds(35))
                .build();
    }

    private static boolean isAuthError(int code) {
        return code == 401 || code == 403;
    }

    private static String formatHttpFailure(int code, String body, String modelId) {
        String diag = shortenDiagnostic(body);
        if (isAuthError(code)) {
            return "[SiliconFlow] HTTP " + code + ": API key is invalid or account is not verified."
                    + " model=" + modelId + ". " + diag;
        }
        return "[SiliconFlow] HTTP " + code + ": model=" + modelId + ". " + diag;
    }
}

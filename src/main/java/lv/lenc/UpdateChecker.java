package lv.lenc;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

final class UpdateChecker {
    private static final String LATEST_RELEASE_API =
            "https://api.github.com/repos/VoVanRusLvSC2/Localization-Editor-SC2-KSP/releases/latest";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(8);

    private UpdateChecker() {
    }

    static Optional<UpdateInfo> checkLatest() {
        try {
            HttpClient http = HttpClient.newBuilder()
                    .connectTimeout(DEFAULT_TIMEOUT)
                    .build();
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(LATEST_RELEASE_API))
                    .timeout(DEFAULT_TIMEOUT)
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "LocalizationEditorSC2KSP/UpdateChecker")
                    .GET()
                    .build();

            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (res.statusCode() < 200 || res.statusCode() >= 300) {
                return Optional.empty();
            }

            JsonElement parsed = JsonParser.parseString(res.body());
            if (!parsed.isJsonObject()) {
                return Optional.empty();
            }
            JsonObject root = parsed.getAsJsonObject();
            String latestTag = readAsText(root, "tag_name");
            String htmlUrl = readAsText(root, "html_url");
            if (latestTag == null || latestTag.isBlank() || htmlUrl == null || htmlUrl.isBlank()) {
                return Optional.empty();
            }

            String current = currentVersion();
            boolean hasUpdate = compareVersionTokens(normalizeVersion(latestTag), normalizeVersion(current)) > 0;
            return Optional.of(new UpdateInfo(current, latestTag, htmlUrl, hasUpdate));
        } catch (Exception ex) {
            AppLog.warn("[Update] check failed: " + ex.getMessage());
            return Optional.empty();
        }
    }

    static String currentVersion() {
        try {
            String impl = trimToNull(UpdateChecker.class.getPackage().getImplementationVersion());
            if (impl != null) {
                return impl;
            }
        } catch (Exception ignored) {
        }

        // Dev mode fallback: read version from pom.xml in workspace.
        try {
            Path pom = Path.of("pom.xml");
            if (Files.isRegularFile(pom)) {
                for (String line : Files.readAllLines(pom, StandardCharsets.UTF_8)) {
                    String trimmed = line.trim();
                    if (trimmed.startsWith("<version>") && trimmed.endsWith("</version>")) {
                        String v = trimmed.replace("<version>", "").replace("</version>", "").trim();
                        if (!v.isBlank()) {
                            return v;
                        }
                    }
                }
            }
        } catch (IOException ignored) {
        }

        return "0.0.0";
    }

    private static String normalizeVersion(String raw) {
        String s = trimToNull(raw);
        if (s == null) {
            return "0.0.0";
        }
        s = s.toLowerCase(Locale.ROOT);
        if (s.startsWith("v")) {
            s = s.substring(1);
        }
        return s;
    }

    private static int compareVersionTokens(String left, String right) {
        String[] a = left.split("[^0-9]+");
        String[] b = right.split("[^0-9]+");
        int max = Math.max(a.length, b.length);
        for (int i = 0; i < max; i++) {
            int ai = (i < a.length && !a[i].isBlank()) ? parseSafe(a[i]) : 0;
            int bi = (i < b.length && !b[i].isBlank()) ? parseSafe(b[i]) : 0;
            int cmp = Integer.compare(ai, bi);
            if (cmp != 0) {
                return cmp;
            }
        }
        return 0;
    }

    private static int parseSafe(String n) {
        try {
            return Integer.parseInt(n);
        } catch (Exception ex) {
            return 0;
        }
    }

    private static String readAsText(JsonObject obj, String key) {
        if (obj == null || key == null || !obj.has(key)) {
            return null;
        }
        try {
            JsonElement el = obj.get(key);
            if (el == null || el.isJsonNull()) {
                return null;
            }
            return trimToNull(el.getAsString());
        } catch (Exception ex) {
            return null;
        }
    }

    private static String trimToNull(String v) {
        if (v == null) {
            return null;
        }
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }

    static final class UpdateInfo {
        final String currentVersion;
        final String latestVersion;
        final String releaseUrl;
        final boolean hasUpdate;

        UpdateInfo(String currentVersion, String latestVersion, String releaseUrl, boolean hasUpdate) {
            this.currentVersion = currentVersion;
            this.latestVersion = latestVersion;
            this.releaseUrl = releaseUrl;
            this.hasUpdate = hasUpdate;
        }
    }
}


// TranslationService.java
package lv.lenc;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import org.jsoup.parser.Parser;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import okhttp3.Dispatcher;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public final class TranslationService {

    public enum TranslationBackend {
        LIBRE_TRANSLATE,
        GOOGLE_CLOUD,
        CLOUDFLARE_M2M100,
        GOOGLE_WEB_FREE,
        GEMINI,
        SILICONFLOW,
        SILICONFLOW_DEEPSEEK_V3,
        SILICONFLOW_M2M100,
        DEEPL_FREE
    }


    public static final String DEFAULT_BASE_URL = "http://127.0.0.1:5000/";
    public static final String GPU_FALLBACK_BASE_URL = "http://127.0.0.1:5001/";
    public static volatile String BASE_URL = DEFAULT_BASE_URL;
    public static final String SEP = "\n";
    private static final double PERFORMANCE_LOAD_FACTOR = 1.0;
    // CPU profile: smaller batches to avoid LT request timeouts on weak machines.
    private static final int BATCH_MAX_ITEMS = 90;
    private static final int BATCH_MAX_CHARS = 7_500;
    private static final int GPU_BATCH_MAX_ITEMS = 220;
    private static final int GPU_BATCH_MAX_CHARS = 18_000;
    // Initial model download can take several minutes on first run.
    private static final int LT_STARTUP_FAST_ATTEMPTS = 60;
    private static final int LT_STARTUP_SLOW_ATTEMPTS = 240;
    private static final long LT_STARTUP_SLEEP_MS = 1_000L;
    private static final long LT_DOCKER_STARTUP_SLEEP_MS = 1_500L;
    private static final int TRANSLATION_CACHE_LIMIT = 20_000;
    // We no longer send tags. Additionally protect "geometric" symbols (progress bars).
    private static final Pattern GEOM_RUN = Pattern.compile("[\\u2580-\\u259F\\u25A0-\\u25FF]+");
    private static final AtomicReference<Call<?>> inFlight = new AtomicReference<>();
    private static final AtomicInteger PERFORMANCE_MODE_LEASES = new AtomicInteger();
    private static final AtomicLong RUN_PREPARED_CHARS = new AtomicLong();
    private static final AtomicLong RUN_SENT_CHARS = new AtomicLong();
    private static final AtomicLong RUN_CACHE_HIT_CHARS = new AtomicLong();
    private static final AtomicLong RUN_RESULT_CHARS = new AtomicLong();
    private static final AtomicLong RUN_BACKEND_REQUESTS = new AtomicLong();
    private static final AtomicLong RUN_BACKEND_IN_CHARS = new AtomicLong();
    private static final AtomicLong RUN_BACKEND_OUT_CHARS = new AtomicLong();
    private static final AtomicReference<Map<String, String>> ACTIVE_GLOSSARY_HINTS =
            new AtomicReference<>(Collections.emptyMap());
    private static final OkHttpClient HTTP = buildTranslationClient();
    private static final OkHttpClient HEALTH_HTTP = new OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(2))
            .readTimeout(Duration.ofSeconds(4))
            .writeTimeout(Duration.ofSeconds(4))
            .callTimeout(Duration.ofSeconds(5))
            .retryOnConnectionFailure(true)
            .build();

    public static volatile LibreTranslateApi api;
    private static volatile Process managedLtProcess;
    private static volatile boolean managedLtGpu;
    private static volatile boolean activeEndpointGpu;
    private static volatile String cachedPythonExecutable;
    private static final long CAPABILITY_CACHE_TTL_MS = 180_000L;
    private static volatile long capabilityCacheTs;
    private static volatile boolean cachedNvidiaGpu;
    private static volatile boolean cachedDockerUsable;
    private static volatile boolean cachedLocalPythonCuda;
    private static volatile boolean shutdownRequested;
    private static volatile boolean onnxRepairAttempted;
    private static volatile String lastStartupFailureHint = "";
    private static final Object CACHE_IO_LOCK = new Object();
    private static volatile boolean persistentCacheLoaded;
    private static volatile boolean shutdownHookInstalled;
    private static volatile boolean persistentCacheEnabled = false; // default: do not persist on disk (as requested)
    private static volatile boolean useGpuDocker = false; // default: avoid docker-induced deadlock by default
    private static volatile boolean gpuDockerStartupFailed;
    private static final long LT_PIP_REPAIR_TIMEOUT_MS = 300_000L;
    private static final long PYTHON_PROBE_TIMEOUT_MS = 4_000L;
    private static final Map<String, String> TRANSLATION_CACHE =
            Collections.synchronizedMap(new LinkedHashMap<>(256, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                    return size() > TRANSLATION_CACHE_LIMIT;
                }
            });
    private static volatile Path lastLtStartupLogPath;
    private static volatile String cachedPythonProbeSummary = "";
    private static volatile PythonProbeResult cachedPythonProbeResult;
    private static volatile TranslationBackend selectedBackend = TranslationBackend.GOOGLE_WEB_FREE;
    public static final String SILICONFLOW_DEEPSEEK_MODEL_ID = SiliconFlowDeepSeekV3TranslationProvider.MODEL_ID;
    public static final String SILICONFLOW_M2M100_MODEL_ID = SiliconFlowM2M100TranslationProvider.MODEL_ID;

    static {
        api = createApi(BASE_URL);
        refreshActiveEndpoint();

        // respect settings persistence flag from saved settings
        try {
            persistentCacheEnabled = SettingsManager.loadTranslationCachePersistence();
        } catch (Exception ignored) {
            persistentCacheEnabled = true;
        }

        // respect GPU Docker mode flag from settings
        try {
            useGpuDocker = SettingsManager.loadUseGpuDocker();
        } catch (Exception ignored) {
            useGpuDocker = false;
        }

        try {
            selectedBackend = parseTranslationBackend(SettingsManager.loadTranslationBackendName());
        } catch (Exception ignored) {
            selectedBackend = TranslationBackend.GOOGLE_WEB_FREE;
        }
        applySiliconFlowModelSelection(selectedBackend);

        ensurePersistentCacheLoaded();
        installCacheShutdownHook();
    }

    private static OkHttpClient buildTranslationClient() {
        int cores = Runtime.getRuntime().availableProcessors();
        int perHost = Math.max(4, Math.min(64, (int) Math.ceil(cores * PERFORMANCE_LOAD_FACTOR * 2.0)));
        int total = Math.max(perHost + 4, Math.min(96, perHost * 2));

        Dispatcher dispatcher = new Dispatcher();
        dispatcher.setMaxRequests(total);
        dispatcher.setMaxRequestsPerHost(perHost);

        return new OkHttpClient.Builder()
                .dispatcher(dispatcher)
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(300))
                .writeTimeout(Duration.ofSeconds(180))
                .callTimeout(Duration.ofSeconds(330))
                .retryOnConnectionFailure(true)
                .addInterceptor(chain -> chain.proceed(
                        chain.request().newBuilder()
                                .header("Accept", "application/json")
                                .build()))
                .build();
    }

    private static LibreTranslateApi createApi(String baseUrl) {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(normalizeBaseUrl(baseUrl))
                .addConverterFactory(GsonConverterFactory.create())
                .client(HTTP)
                .build();
        return retrofit.create(LibreTranslateApi.class);
    }

    public static synchronized void setSelectedBackend(TranslationBackend backend) {
        selectedBackend = (backend == null) ? TranslationBackend.GOOGLE_WEB_FREE : backend;
        applySiliconFlowModelSelection(selectedBackend);
        try {
            SettingsManager.saveTranslationBackendName(selectedBackend.name());
        } catch (Exception ignored) {
            // Keep runtime selection even if settings persistence is unavailable.
        }
        if (selectedBackend != TranslationBackend.LIBRE_TRANSLATE) {
            clearLastStartupFailureHint();
        }
    }

    public static void resetRunCharStats() {
        RUN_PREPARED_CHARS.set(0L);
        RUN_SENT_CHARS.set(0L);
        RUN_CACHE_HIT_CHARS.set(0L);
        RUN_RESULT_CHARS.set(0L);
        RUN_BACKEND_REQUESTS.set(0L);
        RUN_BACKEND_IN_CHARS.set(0L);
        RUN_BACKEND_OUT_CHARS.set(0L);
    }

    public static String runCharSummaryForPerf() {
        return "charsPrepared=" + RUN_PREPARED_CHARS.get()
                + ", charsSent=" + RUN_SENT_CHARS.get()
                + ", charsCacheHit=" + RUN_CACHE_HIT_CHARS.get()
                + ", charsResult=" + RUN_RESULT_CHARS.get()
                + ", backendCalls=" + RUN_BACKEND_REQUESTS.get()
                + ", backendIn=" + RUN_BACKEND_IN_CHARS.get()
                + ", backendOut=" + RUN_BACKEND_OUT_CHARS.get();
    }

    public static void setRuntimeGlossaryHints(Map<String, String> hints) {
        if (hints == null || hints.isEmpty()) {
            ACTIVE_GLOSSARY_HINTS.set(Collections.emptyMap());
            return;
        }
        ACTIVE_GLOSSARY_HINTS.set(Collections.unmodifiableMap(new LinkedHashMap<>(hints)));
    }

    public static void clearRuntimeGlossaryHints() {
        ACTIVE_GLOSSARY_HINTS.set(Collections.emptyMap());
    }

    static Map<String, String> currentRuntimeGlossaryHints() {
        return ACTIVE_GLOSSARY_HINTS.get();
    }

    static OkHttpClient sharedHttpClient() {
        return HTTP;
    }

    public static TranslationBackend getSelectedBackend() {
        return selectedBackend;
    }

    private static boolean isSiliconFlowBackend(TranslationBackend backend) {
        return backend == TranslationBackend.SILICONFLOW
                || backend == TranslationBackend.SILICONFLOW_DEEPSEEK_V3
                || backend == TranslationBackend.SILICONFLOW_M2M100;
    }

    public static boolean selectedBackendUsesSiliconFlowApi() {
        return isSiliconFlowBackend(selectedBackend);
    }

    private static void applySiliconFlowModelSelection(TranslationBackend backend) {
        if (backend == TranslationBackend.SILICONFLOW_DEEPSEEK_V3) {
            SiliconFlowTranslationProvider.setRuntimeModelOverride(SILICONFLOW_DEEPSEEK_MODEL_ID, true);
            return;
        }
        if (backend == TranslationBackend.SILICONFLOW_M2M100) {
            SiliconFlowTranslationProvider.setRuntimeModelOverride(SILICONFLOW_M2M100_MODEL_ID, true);
            return;
        }
        SiliconFlowTranslationProvider.setRuntimeModelOverride("", false);
    }

    public static boolean selectedBackendRequiresLocalServer() {
        return selectedBackend == TranslationBackend.LIBRE_TRANSLATE;
    }

    public static boolean backendSupportsGlossaryInflectionHints() {
        return selectedBackend == TranslationBackend.GEMINI
                || isSiliconFlowBackend(selectedBackend);
    }

    public static String selectedBackendLabel() {
        if (selectedBackend == TranslationBackend.GOOGLE_CLOUD) {
            return "Google Cloud Translate";
        }
        if (selectedBackend == TranslationBackend.CLOUDFLARE_M2M100) {
            return "Cloudflare Worker AI (M2M100)";
        }
        if (selectedBackend == TranslationBackend.GOOGLE_WEB_FREE) {
            return "Google Translate Free (Web)";
        }
        if (selectedBackend == TranslationBackend.GEMINI) {
            return "Gemini API";
        }
        if (selectedBackend == TranslationBackend.SILICONFLOW) {
            return "SiliconFlow API";
        }
        if (selectedBackend == TranslationBackend.SILICONFLOW_DEEPSEEK_V3) {
            return "SiliconFlow API (DeepSeek-V3.2)";
        }
        if (selectedBackend == TranslationBackend.SILICONFLOW_M2M100) {
            return "SiliconFlow API (M2M100)";
        }
        if (selectedBackend == TranslationBackend.DEEPL_FREE) {
            return "DeepL API Free";
        }
        return "LibreTranslate";
    }

    public static String describeSelectedBackend() {
        if (selectedBackend == TranslationBackend.GOOGLE_CLOUD) {
            return "Google Cloud Translate";
        }
        if (selectedBackend == TranslationBackend.CLOUDFLARE_M2M100) {
            return "Cloudflare Worker AI (M2M100)";
        }
        if (selectedBackend == TranslationBackend.GOOGLE_WEB_FREE) {
            return "Google Translate Free (Web)";
        }
        if (selectedBackend == TranslationBackend.GEMINI) {
            return "Gemini API";
        }
        if (selectedBackend == TranslationBackend.SILICONFLOW) {
            return "SiliconFlow API";
        }
        if (selectedBackend == TranslationBackend.SILICONFLOW_DEEPSEEK_V3) {
            return "SiliconFlow API (DeepSeek-V3.2)";
        }
        if (selectedBackend == TranslationBackend.SILICONFLOW_M2M100) {
            return "SiliconFlow API (M2M100)";
        }
        if (selectedBackend == TranslationBackend.DEEPL_FREE) {
            return "DeepL API Free";
        }
        return (isGpuActive() ? "GPU" : "CPU") + " @ " + BASE_URL;
    }

    public static boolean isSelectedBackendReady() {
        if (selectedBackend == TranslationBackend.GOOGLE_CLOUD) {
            return GoogleCloudTranslationProvider.isConfigured();
        }
        if (selectedBackend == TranslationBackend.CLOUDFLARE_M2M100) {
            return CloudflareM2M100TranslationProvider.isConfigured();
        }
        if (selectedBackend == TranslationBackend.GOOGLE_WEB_FREE) {
            return GoogleWebFreeTranslationProvider.isConfigured();
        }
        if (selectedBackend == TranslationBackend.GEMINI) {
            return GeminiTranslationProvider.isConfigured();
        }
        if (selectedBackend == TranslationBackend.SILICONFLOW_DEEPSEEK_V3) {
            return SiliconFlowDeepSeekV3TranslationProvider.isConfigured();
        }
        if (selectedBackend == TranslationBackend.SILICONFLOW_M2M100) {
            return SiliconFlowM2M100TranslationProvider.isConfigured();
        }
        if (selectedBackend == TranslationBackend.SILICONFLOW) {
            return SiliconFlowTranslationProvider.isConfigured();
        }
        if (selectedBackend == TranslationBackend.DEEPL_FREE) {
            return DeepLTranslationProvider.isConfigured();
        }
        return isLtAlive();
    }

    public static synchronized boolean ensureSelectedBackendAvailable() {
        clearLastStartupFailureHint();
        if (selectedBackend == TranslationBackend.GOOGLE_CLOUD) {
            if (GoogleCloudTranslationProvider.isConfigured()) {
                return true;
            }
            setLastStartupFailureHint(
                    "Google Cloud Translate requires GOOGLE_TRANSLATE_API_KEY or settings.properties google.translate.api.key."
            );
            return false;
        }
        if (selectedBackend == TranslationBackend.CLOUDFLARE_M2M100) {
            if (!CloudflareM2M100TranslationProvider.isConfigured()) {
                setLastStartupFailureHint(
                        "Cloudflare Worker AI (M2M100) requires CLOUDFLARE_ACCOUNT_ID and CLOUDFLARE_API_TOKEN "
                                + "or settings.properties cloudflare.account.id/cloudflare.api.token."
                );
                return false;
            }
            String hint = CloudflareM2M100TranslationProvider.checkAvailability(HTTP);
            if (hint == null || hint.isBlank()) {
                return true;
            }
            AppLog.error("[CLOUDFLARE-M2M100] availability check failed: " + hint);
            setLastStartupFailureHint(
                    hint
            );
            return false;
        }
        if (selectedBackend == TranslationBackend.GOOGLE_WEB_FREE) {
            String hint = GoogleWebFreeTranslationProvider.checkAvailability(HTTP);
            if (hint == null || hint.isBlank()) {
                return true;
            }
            AppLog.error("[GOOGLE-WEB-FREE] availability check failed: " + hint);
            setLastStartupFailureHint(hint);
            return false;
        }
        if (selectedBackend == TranslationBackend.GEMINI) {
            if (!GeminiTranslationProvider.isConfigured()) {
                setLastStartupFailureHint(
                        "Gemini API requires GEMINI_API_KEY or settings.properties gemini.api.key."
                );
                return false;
            }
            String hint = GeminiTranslationProvider.checkAvailability(HTTP);
            if (hint == null || hint.isBlank()) {
                return true;
            }
            AppLog.error("[GEMINI] availability check failed: " + hint);
            setLastStartupFailureHint(hint);
            return false;
        }
        if (selectedBackend == TranslationBackend.SILICONFLOW_DEEPSEEK_V3) {
            if (!SiliconFlowDeepSeekV3TranslationProvider.isConfigured()) {
                setLastStartupFailureHint(
                        "SiliconFlow API requires SILICONFLOW_API_KEY or settings.properties siliconflow.api.key."
                );
                return false;
            }
            String hint = SiliconFlowDeepSeekV3TranslationProvider.checkAvailability(HTTP);
            if (hint == null || hint.isBlank()) {
                return true;
            }
            AppLog.error("[SILICONFLOW] availability check failed: " + hint);
            setLastStartupFailureHint(hint);
            return false;
        }
        if (selectedBackend == TranslationBackend.SILICONFLOW_M2M100) {
            if (!SiliconFlowM2M100TranslationProvider.isConfigured()) {
                setLastStartupFailureHint(
                        "SiliconFlow API requires SILICONFLOW_API_KEY or settings.properties siliconflow.api.key."
                );
                return false;
            }
            String hint = SiliconFlowM2M100TranslationProvider.checkAvailability(HTTP);
            if (hint == null || hint.isBlank()) {
                return true;
            }
            AppLog.error("[SILICONFLOW] availability check failed: " + hint);
            setLastStartupFailureHint(hint);
            return false;
        }
        if (selectedBackend == TranslationBackend.SILICONFLOW) {
            if (!SiliconFlowTranslationProvider.isConfigured()) {
                setLastStartupFailureHint(
                        "SiliconFlow API requires SILICONFLOW_API_KEY or settings.properties siliconflow.api.key."
                );
                return false;
            }
            String hint = SiliconFlowTranslationProvider.checkAvailability(HTTP);
            if (hint == null || hint.isBlank()) {
                return true;
            }
            AppLog.error("[SILICONFLOW] availability check failed: " + hint);
            setLastStartupFailureHint(hint);
            return false;
        }
        if (selectedBackend == TranslationBackend.DEEPL_FREE) {
            if (!DeepLTranslationProvider.isConfigured()) {
                setLastStartupFailureHint(
                        "DeepL API Free requires DEEPL_API_KEY or settings.properties deepl.api.key."
                );
                return false;
            }
            String hint = DeepLTranslationProvider.checkAvailability(HTTP);
            if (hint == null || hint.isBlank()) {
                return true;
            }
            AppLog.error("[DEEPL] availability check failed: " + hint);
            setLastStartupFailureHint(hint);
            return false;
        }
        return ensureServerAvailable();
    }

    private static String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return DEFAULT_BASE_URL;
        }

        String normalized = baseUrl.trim();
        if (!normalized.endsWith("/")) {
            normalized = normalized + "/";
        }
        return normalized;
    }

    private static boolean isGpuBaseUrl(String baseUrl) {
        return normalizeBaseUrl(baseUrl).equals(normalizeBaseUrl(GPU_FALLBACK_BASE_URL));
    }

    private static TranslationBackend parseTranslationBackend(String backendName) {
        String normalized = backendName == null ? "" : backendName.trim();
        if (normalized.isEmpty()) {
            return TranslationBackend.GOOGLE_WEB_FREE;
        }
        try {
            String upper = normalized.toUpperCase(Locale.ROOT);
            if ("GOOGLE_FRAME_S2S100".equals(upper)) {
                return TranslationBackend.CLOUDFLARE_M2M100;
            }
            if ("SILICONFLOW_M2M100".equals(upper)) {
                return TranslationBackend.CLOUDFLARE_M2M100;
            }
            return TranslationBackend.valueOf(upper);
        } catch (IllegalArgumentException ex) {
            return TranslationBackend.GOOGLE_WEB_FREE;
        }
    }

    public static boolean isGpuActive() {
        return activeEndpointGpu;
    }

    public static synchronized void configureStartupLanguages(java.util.Collection<String> languageCodes) {
        // Language preloading is intentionally disabled.
        // The app only starts the server early; models are loaded on first real request.
    }

    private static boolean inferGpuForEndpoint(String baseUrl) {
        String normalized = normalizeBaseUrl(baseUrl);
        if (isGpuBaseUrl(normalized)) {
            return true;
        }
        if (!normalized.equals(normalizeBaseUrl(DEFAULT_BASE_URL))) {
            return false;
        }
        Process process = managedLtProcess;
        return managedLtGpu && process != null && process.isAlive();
    }

    private static int effectiveBatchMaxItems() {
        int base = Math.min(BATCH_MAX_ITEMS, GPU_BATCH_MAX_ITEMS);
        if (isSiliconFlowBackend(selectedBackend)) {
            // LLM-based endpoint is more stable with smaller chunk sizes.
            return Math.min(50, base);
        }
        return base;
    }

    private static int effectiveBatchMaxChars() {
        int base = Math.min(BATCH_MAX_CHARS, GPU_BATCH_MAX_CHARS);
        if (isSiliconFlowBackend(selectedBackend)) {
            // Reduce prompt/response pressure for multi-language runs.
            return Math.min(3_500, base);
        }
        return base;
    }

    private static int effectiveConcurrency() {
        int cores = Runtime.getRuntime().availableProcessors();
        if (!selectedBackendRequiresLocalServer()) {
            if (isSiliconFlowBackend(selectedBackend)) {
                // Free SiliconFlow models are strict on RPM/TPM. Single-flight is more stable.
                return 1;
            }
            if (selectedBackend == TranslationBackend.GOOGLE_WEB_FREE) {
                // Unofficial endpoint can throttle aggressively on parallel calls.
                return 1;
            }
            return Math.max(1, Math.min(4, Math.max(1, cores / 2)));
        }
        // CPU endpoint is usually a single local process and can be easily overloaded
        // by too many parallel requests; keep it conservative.
        if (!isGpuActive()) {
            return Math.max(1, Math.min(2, Math.max(1, cores / 4)));
        }
        int target = (int) Math.ceil(cores * PERFORMANCE_LOAD_FACTOR * 2.0);
        return Math.max(2, Math.min(64, target));
    }

    private static List<String> translatePreservingTagsBatchedSequential(
            LibreTranslateApi api,
            List<String> texts,
            String source,
            String target,
            java.util.function.BooleanSupplier stop,
            ProgressListener progress
    ) throws IOException, InterruptedException {
        // Sequential mode for small jobs and fallback.
        DedupedTexts deduped = dedupeTexts(texts);
        List<String> uniqueTexts = deduped.uniqueTexts;

        int maxItems = effectiveBatchMaxItems();
        int maxChars = effectiveBatchMaxChars();
        List<List<String>> parts = chunksByChars(uniqueTexts, maxItems, maxChars);

        List<String> uniqueOut = new ArrayList<>(uniqueTexts.size());
        int batchNo = 0;
        int total = parts.size();

        for (List<String> part : parts) {
            batchNo++;
            long t0 = System.currentTimeMillis();

            IOException last = null;
            for (int attempt = 1; attempt <= 3; attempt++) {
                if (stop != null && stop.getAsBoolean()) return expandDeduped(uniqueOut, deduped, texts.size());

                try {
                    List<String> translated = translatePreservingTags(api, part, source, target);
                    uniqueOut.addAll(translated);
                    long took = System.currentTimeMillis() - t0;
                    AppLog.info("[LT] preserveTags.batch " + batchNo + " OK in " + took + "ms");
                    break;
                } catch (IOException e) {
                    if (isNonRetryableBackendError(e)) {
                        throw e;
                    }
                    last = e;
                    long sleep = Math.min(700L * (1L << (attempt - 1)), 5_000L);
                    refreshActiveEndpoint();
                    Thread.sleep(sleep);
                    if (stop != null && stop.getAsBoolean()) return expandDeduped(uniqueOut, deduped, texts.size());
                    if (attempt == 3) throw last;
                }
            }

            if (progress != null) {
                progress.onProgress(batchNo / (double) total, "batch " + batchNo + "/" + total);
            }
        }

        if (progress != null) {
            progress.onProgress(1.0, "done");
        }

        return expandDeduped(uniqueOut, deduped, texts.size());
    }

    private static List<String> endpointCandidates() {
        LinkedHashSet<String> urls = new LinkedHashSet<>();

        String sys = System.getProperty("lt.baseUrl");
        if (sys != null && !sys.isBlank()) {
            urls.add(normalizeBaseUrl(sys));
        }

        String env = System.getenv("LT_BASE_URL");
        if (env != null && !env.isBlank()) {
            urls.add(normalizeBaseUrl(env));
        }

        urls.add(GPU_FALLBACK_BASE_URL);
        urls.add(normalizeBaseUrl(BASE_URL));
        urls.add(DEFAULT_BASE_URL);
        return new ArrayList<>(urls);
    }

    private static boolean probeBaseUrl(String baseUrl) {
        String normalized = normalizeBaseUrl(baseUrl);
        Request request = new Request.Builder()
                .url(normalized + "languages")
                .get()
                .build();

        try (okhttp3.Response response = HEALTH_HTTP.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                return false;
            }

            return response.body() != null && !response.body().string().isBlank();
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void activateEndpoint(String baseUrl) {
        String normalized = normalizeBaseUrl(baseUrl);
        boolean gpu = inferGpuForEndpoint(normalized);
        activeEndpointGpu = gpu;
        clearLastStartupFailureHint();
        if (!normalized.equals(BASE_URL)) {
            BASE_URL = normalized;
            api = createApi(BASE_URL);
            AppLog.info("[LT] active endpoint switched to " + BASE_URL
                    + " (" + (gpu ? "GPU" : "CPU") + ")");
        }
    }

    private static boolean waitForSpecificEndpoint(String source, String baseUrl, int attempts, long sleepMs)
            throws InterruptedException {
        String normalized = normalizeBaseUrl(baseUrl);
        for (int i = 0; i < attempts; i++) {
            if (probeBaseUrl(normalized)) {
                activateEndpoint(normalized);
                return true;
            }
            Process process = managedLtProcess;
            if (process != null && !process.isAlive()) {
                AppLog.warn("[LT] " + source + " exited early (code=" + safeExitCode(process) + ")");
                logLatestStartupFailureDetails(source);
                return false;
            }
            Thread.sleep(sleepMs);
        }
        AppLog.warn("[LT] " + source + " did not become ready on " + normalized);
        logLatestStartupFailureDetails(source);
        return false;
    }

    public static synchronized boolean refreshActiveEndpoint() {
        for (String candidate : endpointCandidates()) {
            if (!probeBaseUrl(candidate)) {
                continue;
            }

            activateEndpoint(candidate);
            return true;
        }
        return false;
    }

    public static LibreTranslateApi requireApi() throws IOException {
        // Hot path: translation batches may call this many times in parallel.
        // Avoid a blocking /languages probe on every request and only refresh the
        // endpoint when we have no API instance yet. Real request failures already
        // trigger refreshActiveEndpoint() and retries in the batch executors.
        LibreTranslateApi current = api;
        if (current != null) {
            return current;
        }

        synchronized (TranslationService.class) {
            current = api;
            if (current != null) {
                return current;
            }
            if (refreshActiveEndpoint()) {
                return api;
            }
        }

        throw new IOException("LibreTranslate is not reachable on " + endpointCandidates());
    }
    private static void splitTextByGeomAndQueue(
            String text,
            List<String> tokens,
            List<Integer> textPositions,
            List<TagFreezer.Frozen> frozenParts,
            List<String> toTranslate
    ) {
        if (text == null || text.isEmpty()) {
            tokens.add("");
            return;
        }

        java.util.regex.Matcher gm = GEOM_RUN.matcher(text);
        int tp = 0;

        while (gm.find()) {
            if (gm.start() > tp) {
                String t1 = text.substring(tp, gm.start());

                TagFreezer.Frozen f1 = TagFreezer.freezeRich(t1);
                String clean1 = sanitizeVisible(f1.protectedText);

                tokens.add(t1);
                if (!clean1.isEmpty()) {
                    textPositions.add(tokens.size() - 1);
                    frozenParts.add(f1);
                    toTranslate.add(clean1);
                }
            }

            // Geometric Unicode run stays untouched.
            tokens.add(gm.group());
            tp = gm.end();
        }

        if (tp < text.length()) {
            String t2 = text.substring(tp);

            TagFreezer.Frozen f2 = TagFreezer.freezeRich(t2);
            String clean2 = sanitizeVisible(f2.protectedText);

            tokens.add(t2);
            if (!clean2.isEmpty()) {
                textPositions.add(tokens.size() - 1);
                frozenParts.add(f2);
                toTranslate.add(clean2);
            }
        }
    }
    public static void cancelInFlight() {
        Call<?> c = inFlight.getAndSet(null);
        if (c != null) c.cancel(); // interrupts current execute() -> throws exception
    }

    // example usage inside translatePreservingTags/translate...
    private static <T> T executeTracked(Call<T> call) throws IOException {
        inFlight.set(call);
        try {
            return call.execute().body();
        } finally {
            inFlight.compareAndSet(call, null);
        }
    }
    private static final Pattern TAG_LIKE = Pattern.compile("(?s)<\\s*[/]?\\w+[^>]*>");

    private static boolean looksLikeHtml(String s) {
        // filter out cases like "2 < 5"
        if (s == null || s.indexOf('<') < 0 || s.indexOf('>') < 0) return false;
        return TAG_LIKE.matcher(s).find();
    }

    // Rebuild plan: either plain string or HTML fragment with text nodes
    private static abstract class RebuildPlan {
        abstract void consumeTranslated(ListIterator<String> it);
        abstract String result();
    }
    private static class PlainPlan extends RebuildPlan {
        private final TagFreezer.Frozen frozen;
        private String translated;

        PlainPlan(TagFreezer.Frozen frozen) {
            this.frozen = frozen;
        }

        @Override
        public void consumeTranslated(ListIterator<String> it) {
            String t = it.next();
            if (t == null) t = "";

            t = t.replace('\u00A0', ' ')
                    .replaceAll("[\\r\\n]+", " ")
                    .trim();

            translated = TagFreezer.unfreezeRich(t, frozen);
        }

        @Override
        public String result() {
            return translated;
        }
    }


    static String sanitizeVisible(String s) {
        if (s == null) return "";

        // 0) decode HTML entities
        s = Parser.unescapeEntities(s, true);

        // 1) normalize Unicode form
        s = Normalizer.normalize(s, Normalizer.Form.NFKC);

        // 2) remove invisible/control characters only
        //    (DO NOT touch Block Elements U+2580Ć„ĀÄā€Ā¬Äā‚¬Ā259F and Geometric Shapes U+25A0Ć„ĀÄā€Ā¬Äā‚¬Ā25FF!)
        s = s.replace('\u00A0', ' ')
                .replaceAll("[\\u0000-\\u001F\\u007F\\u200B-\\u200F\\u2028\\u2029\\u2060\\uFEFF]", "");

        // 3) collapse line breaks and multiple spaces
        s = s.replaceAll("[\\r\\n]+", " ").replaceAll("\\s+", " ").trim();

        // 4) normalize spacing around punctuation
        s = normalizePunctuationSpacing(s);

        return s;
    }

    static String normalizePunctuationSpacing(String s) {
        // remove spaces BEFORE punctuation
        s = s.replaceAll("\\s+([,:;!?])", "$1");
        // remove spaces after opening brackets/quotes and before closing ones
        s = s.replaceAll("([\\(\\[\\{Ć„ā‚¬Ä€Ā«])\\s+", "$1")
                .replaceAll("\\s+([\\)\\]\\}Ć„ā‚¬Ä€Ā»])", "$1");
        return s;
    }
    // Pass through sanitizeVisible: normalization, invisible chars, spacing.
    private static class PrepResult {
        final List<String> toTranslate;
        final List<RebuildPlan> plans;
        PrepResult(List<String> toTranslate, List<RebuildPlan> plans) {
            this.toTranslate = toTranslate; this.plans = plans;
        }
    }

    private static class DedupedTexts {
        final List<String> uniqueTexts;
        final int[] mapToUnique;

        DedupedTexts(List<String> uniqueTexts, int[] mapToUnique) {
            this.uniqueTexts = uniqueTexts;
            this.mapToUnique = mapToUnique;
        }
    }
    // Token: <...> (any tag, including self-closing). No parsing, just extract.
    private static final Pattern TAG_TOKEN = Pattern.compile("(?s)<[^>]*?>");

    // Rebuild plan: tags + text, only text is translated
// Rebuild plan for HTML-like strings:
// tags stay untouched, only text fragments are translated and then restored.
    private static class TokenPlan extends RebuildPlan {
        private final List<String> tokens;
        private final List<Integer> textPositions;
        private final List<TagFreezer.Frozen> frozenParts;

        TokenPlan(List<String> tokens,
                  List<Integer> textPositions,
                  List<TagFreezer.Frozen> frozenParts) {
            this.tokens = tokens;
            this.textPositions = textPositions;
            this.frozenParts = frozenParts;
        }

        @Override
        public void consumeTranslated(ListIterator<String> it) {
            for (int i = 0; i < textPositions.size(); i++) {
                int pos = textPositions.get(i);

                String t = it.next();
                if (t == null) t = "";

                t = t.replace('\u00A0', ' ')
                        .replaceAll("[\\r\\n]+", " ")
                        .trim();

                t = TagFreezer.unfreezeRich(t, frozenParts.get(i));
                tokens.set(pos, t);
            }
        }

        @Override
        public String result() {
            StringBuilder sb = new StringBuilder();
            for (String tk : tokens) sb.append(tk);
            return sb.toString();
        }
    }

    private static PrepResult prepareForTranslation(List<String> inputs) {
        List<String> toTranslate = new ArrayList<>();
        List<RebuildPlan> plans = new ArrayList<>(inputs.size());

        int htmlCnt = 0, plainCnt = 0;

        for (int i = 0; i < inputs.size(); i++) {
            String s = inputs.get(i);

            if (looksLikeHtml(s)) {
                htmlCnt++;

                List<String> tokens = new ArrayList<>();
                List<Integer> textPositions = new ArrayList<>();
                List<TagFreezer.Frozen> frozenParts = new ArrayList<>();

                int pos = 0;
                java.util.regex.Matcher m = TAG_TOKEN.matcher(s);

                while (m.find()) {
                    if (m.start() > pos) {
                        String text = s.substring(pos, m.start());
                        splitTextByGeomAndQueue(text, tokens, textPositions, frozenParts, toTranslate);
                    }

                    // Tag token itself is preserved and never translated.
                    tokens.add(s.substring(m.start(), m.end()));
                    pos = m.end();
                }

                if (pos < s.length()) {
                    String text = s.substring(pos);
                    splitTextByGeomAndQueue(text, tokens, textPositions, frozenParts, toTranslate);
                }

                plans.add(new TokenPlan(tokens, textPositions, frozenParts));

            } else {
                plainCnt++;

                TagFreezer.Frozen frozen = TagFreezer.freezeRich(s == null ? "" : s);
                plans.add(new PlainPlan(frozen));
                toTranslate.add(sanitizeVisible(frozen.protectedText));
            }
        }

        AppLog.info("[LT] prepare: inputs=" + inputs.size() +
                " htmlItems=" + htmlCnt + " plainItems=" + plainCnt +
                " toTranslate=" + toTranslate.size());

        return new PrepResult(toTranslate, plans);
    }
    public static Map<String, List<String>> translateAll(
            List<String> texts,
            String sourceIso,
            List<String> targetsIso,
            java.util.function.BooleanSupplier stop,
            ProgressListener progress
    ) throws IOException {

        if (texts == null) texts = java.util.Collections.emptyList();
        if (targetsIso == null || targetsIso.isEmpty())
            throw new IllegalArgumentException("targetsIso is empty");

        //
        Map<String, Integer> firstIndex = new java.util.LinkedHashMap<>();
        List<String> unique = new java.util.ArrayList<>();
        int[] mapToUnique = new int[texts.size()];

        for (int i = 0; i < texts.size(); i++) {
            String t = texts.get(i);
            Integer idx = firstIndex.get(t);
            if (idx == null) {
                idx = unique.size();
                firstIndex.put(t, idx);
                unique.add(t);
            }
            mapToUnique[i] = idx;
        }

        Map<String, List<String>> out = new java.util.LinkedHashMap<>();

        int totalLangs = targetsIso.size();
        for (int i = 0; i < totalLangs; i++) {
            if (stop != null && stop.getAsBoolean()) return out;

            String targetIso = targetsIso.get(i);
            int langNo = i + 1;

            //
            List<String> uniqOut;
            try {
                uniqOut = translatePreservingTagsBatched(
                        api, unique, sourceIso, targetIso, stop,
                        (partFraction, msg) -> {
                            if (progress == null) return;

                            double all = ((langNo - 1) + partFraction) / (double) totalLangs;

                            String line1 = sourceIso + " -> " + targetIso + " (" + langNo + "/" + totalLangs + ")";
                            String line2 = (msg == null ? "" : msg);

                            progress.onProgress(all, line1 + "||" + line2);
                        }
                );
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Translation interrupted", e);
            }

            if (stop != null && stop.getAsBoolean()) return out;

            // deduplicate restore
            List<String> fullOut = new java.util.ArrayList<>(texts.size());
            for (int k = 0; k < texts.size(); k++) {
                fullOut.add(uniqOut.get(mapToUnique[k]));
            }

            out.put(targetIso, fullOut);
        }

        return out;
    }

    static List<String> extractTranslations(JsonElement body, int expected) throws IOException {
        if (body == null || body.isJsonNull()) throw new IOException("Empty response from server");

        List<String> out = new ArrayList<>();

        if (body.isJsonArray()) {                 // [ ... ]
            for (JsonElement el : body.getAsJsonArray()) out.add(parseOneTranslation(el));
            return out;
        }

        if (body.isJsonObject()) {                // { ... }
            JsonObject obj = body.getAsJsonObject();
            if (obj.has("data") && obj.get("data").isJsonObject()) {
                JsonObject data = obj.getAsJsonObject("data");
                if (data.has("translations") && data.get("translations").isJsonArray()) {
                    for (JsonElement el : data.get("translations").getAsJsonArray()) {
                        out.add(parseOneTranslation(el));
                    }
                    return out;
                }
            }
            if (obj.has("translatedText")) {
                JsonElement t = obj.get("translatedText");
                if (t.isJsonArray()) {            // {"translatedText":[...]}
                    for (JsonElement el : t.getAsJsonArray()) out.add(el.getAsString());
                    return out;
                }
                if (t.isJsonPrimitive() && t.getAsJsonPrimitive().isString()) { // {"translatedText":"..."}
                    out.add(t.getAsString());
                    return out;
                }
            }
            if (obj.has("translations") && obj.get("translations").isJsonArray()) { // {"translations":[...]}
                for (JsonElement el : obj.get("translations").getAsJsonArray()) out.add(parseOneTranslation(el));
                return out;
            }
        }

        throw new IOException("Unexpected response format: " + body);
    }

    static String parseOneTranslation(JsonElement el) throws IOException {
        if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) {
            return el.getAsString();
        }
        if (el.isJsonObject() && el.getAsJsonObject().has("translatedText")) {
            JsonElement t = el.getAsJsonObject().get("translatedText");
            if (t.isJsonPrimitive() && t.getAsJsonPrimitive().isString()) return t.getAsString();
        }
        throw new IOException("Unexpected translation element: " + el);
    }
    private static List<String> rebuildFromTranslations(List<String> translated, List<RebuildPlan> plans) {
        List<String> out = new ArrayList<>(plans.size());
        ListIterator<String> it = translated.listIterator();
        for (RebuildPlan plan : plans) {
            plan.consumeTranslated(it);
            out.add(plan.result());
        }
        return out;
    }

    /**
     * Main method: translate list of strings while preserving HTML tags,
     * translating only text nodes inside.
     */
    public static List<String> translatePreservingTags(
            LibreTranslateApi api,
            List<String> inputs,
            String source,
            String target
    ) throws IOException {

        long t0 = System.currentTimeMillis();
        AppLog.info("[LT] translatePreservingTags start: items=" + inputs.size() +
                " chars=" + sumChars(inputs) + " " + source + "->" + target);

        // 
        PrepResult prep = prepareForTranslation(inputs);
        AppLog.info("[LT] prepared: toTranslate=" + prep.toTranslate.size() +
                " plans=" + prep.plans.size());

        if (prep.toTranslate.isEmpty()) {
            AppLog.info("[LT] nothing to translate, return input as-is");
            return new ArrayList<>(inputs);
        }

        List<String> translatedPrepared = new ArrayList<>(Collections.nCopies(prep.toTranslate.size(), null));
        List<String> uncachedInputs = new ArrayList<>();
        List<Integer> uncachedIndexes = new ArrayList<>();

        for (int i = 0; i < prep.toTranslate.size(); i++) {
            String preparedText = prep.toTranslate.get(i);
            String cached = cacheLookup(source, target, preparedText);
            if (cached != null) {
                translatedPrepared.set(i, cached);
            } else {
                uncachedIndexes.add(i);
                uncachedInputs.add(preparedText);
            }
        }

        int preparedChars = sumChars(prep.toTranslate);
        int uncachedChars = sumChars(uncachedInputs);
        int cachedChars = Math.max(0, preparedChars - uncachedChars);
        RUN_PREPARED_CHARS.addAndGet(preparedChars);
        RUN_SENT_CHARS.addAndGet(uncachedChars);
        RUN_CACHE_HIT_CHARS.addAndGet(cachedChars);
        AppLog.info("[LT] chars prepared=" + preparedChars
                + ", uncached(sent)=" + uncachedChars
                + ", cache-hit=" + cachedChars);

        if (!uncachedInputs.isEmpty()) {
            List<String> uncachedTranslated = translatePreparedTexts(uncachedInputs, source, target);
            AppLog.info("[LT] got " + uncachedTranslated.size() + " fresh translations for " + uncachedInputs.size()
                    + " uncached items (" + (prep.toTranslate.size() - uncachedInputs.size()) + " cache hits)");
            AppLog.info("[LT] chars fresh-translation in=" + uncachedChars
                    + ", out=" + sumChars(uncachedTranslated));

            if (uncachedTranslated.size() != uncachedInputs.size()) {
                throw new IllegalStateException("[LT] MISMATCH: in=" + uncachedInputs.size() + " out=" + uncachedTranslated.size());
            }

            for (int i = 0; i < uncachedIndexes.size(); i++) {
                int originalIndex = uncachedIndexes.get(i);
                String preparedText = prep.toTranslate.get(originalIndex);
                String translatedText = uncachedTranslated.get(i);
                translatedPrepared.set(originalIndex, translatedText);
                cacheStore(source, target, preparedText, translatedText);
            }
            AppLog.info("[LT] translation cache size now: " + TRANSLATION_CACHE.size());
        } else {
            AppLog.info("[LT] cache hit for all " + prep.toTranslate.size() + " prepared items");
        }

        List<String> out = rebuildFromTranslations(translatedPrepared, prep.plans);
        RUN_RESULT_CHARS.addAndGet(sumChars(out));
        long took = System.currentTimeMillis() - t0;
        AppLog.info("[LT] translatePreservingTags done in " + took + "ms"
                + ", resultChars=" + sumChars(out));
        return out;
    }

    private static List<String> translatePreparedTexts(
            List<String> uncachedInputs,
            String source,
            String target
    ) throws IOException {
        int requestChars = sumChars(uncachedInputs);
        RUN_BACKEND_REQUESTS.incrementAndGet();
        RUN_BACKEND_IN_CHARS.addAndGet(requestChars);
        AppLog.info("[LT] backend request: provider=" + selectedBackendLabel()
                + ", items=" + uncachedInputs.size()
                + ", chars=" + requestChars
                + ", " + source + "->" + target);

        List<String> translated;
        if (selectedBackend == TranslationBackend.GOOGLE_CLOUD) {
            translated = translatePreparedTextsWithGoogleCloud(uncachedInputs, source, target);
        } else if (selectedBackend == TranslationBackend.CLOUDFLARE_M2M100) {
            translated = translatePreparedTextsWithCloudflareM2M100(uncachedInputs, source, target);
        } else if (selectedBackend == TranslationBackend.GOOGLE_WEB_FREE) {
            translated = translatePreparedTextsWithGoogleWebFree(uncachedInputs, source, target);
        } else if (selectedBackend == TranslationBackend.GEMINI) {
            translated = translatePreparedTextsWithGemini(uncachedInputs, source, target);
        } else if (isSiliconFlowBackend(selectedBackend)) {
            translated = translatePreparedTextsWithSiliconFlow(uncachedInputs, source, target);
        } else if (selectedBackend == TranslationBackend.DEEPL_FREE) {
            translated = translatePreparedTextsWithDeepLFree(uncachedInputs, source, target);
        } else {
            translated = translatePreparedTextsWithLibreTranslate(uncachedInputs, source, target);
        }

        int responseChars = sumChars(translated);
        RUN_BACKEND_OUT_CHARS.addAndGet(responseChars);
        AppLog.info("[LT] backend response: provider=" + selectedBackendLabel()
                + ", items=" + translated.size()
                + ", chars=" + responseChars);
        return translated;
    }

    private static List<String> translatePreparedTextsWithLibreTranslate(
            List<String> uncachedInputs,
            String source,
            String target
    ) throws IOException {
        LibreTranslateApi effectiveApi = requireApi();
        return LibreTranslateProvider.translatePreparedTexts(
                uncachedInputs,
                source,
                target,
                effectiveApi,
                TranslationService::executeTrackedJsonResponse
        );
    }

    private static List<String> translatePreparedTextsWithGoogleCloud(
            List<String> uncachedInputs,
            String source,
            String target
    ) throws IOException {
        if (!GoogleCloudTranslationProvider.isConfigured()) {
            setLastStartupFailureHint(
                    "Google Cloud Translate requires GOOGLE_TRANSLATE_API_KEY or settings.properties google.translate.api.key."
            );
            throw new IOException("Google Cloud Translate is not configured");
        }
        return GoogleCloudTranslationProvider.translatePreparedTexts(
                uncachedInputs,
                source,
                target,
                HTTP
        );
    }

    private static List<String> translatePreparedTextsWithGoogleWebFree(
            List<String> uncachedInputs,
            String source,
            String target
    ) throws IOException {
        return GoogleWebFreeTranslationProvider.translatePreparedTexts(
                uncachedInputs,
                source,
                target,
                HTTP
        );
    }

    private static List<String> translatePreparedTextsWithCloudflareM2M100(
            List<String> uncachedInputs,
            String source,
            String target
    ) throws IOException {
        if (!CloudflareM2M100TranslationProvider.isConfigured()) {
            setLastStartupFailureHint(
                    "Cloudflare Worker AI (M2M100) requires CLOUDFLARE_ACCOUNT_ID and CLOUDFLARE_API_TOKEN "
                            + "or settings.properties cloudflare.account.id/cloudflare.api.token."
            );
            throw new IOException("Cloudflare Worker AI (M2M100) is not configured");
        }
        return CloudflareM2M100TranslationProvider.translatePreparedTexts(
                uncachedInputs,
                source,
                target,
                HTTP
        );
    }

    private static List<String> translatePreparedTextsWithGemini(
            List<String> uncachedInputs,
            String source,
            String target
    ) throws IOException {
        if (!GeminiTranslationProvider.isConfigured()) {
            setLastStartupFailureHint(
                    "Gemini API requires GEMINI_API_KEY or settings.properties gemini.api.key."
            );
            throw new IOException("Gemini API is not configured");
        }
        return GeminiTranslationProvider.translatePreparedTexts(
                uncachedInputs,
                source,
                target,
                HTTP
        );
    }

    private static List<String> translatePreparedTextsWithDeepLFree(
            List<String> uncachedInputs,
            String source,
            String target
    ) throws IOException {
        if (!DeepLTranslationProvider.isConfigured()) {
            setLastStartupFailureHint(
                    "DeepL API Free requires DEEPL_API_KEY or settings.properties deepl.api.key."
            );
            throw new IOException("DeepL API Free is not configured");
        }
        return DeepLTranslationProvider.translatePreparedTexts(
                uncachedInputs,
                source,
                target,
                HTTP
        );
    }

    private static List<String> translatePreparedTextsWithSiliconFlow(
            List<String> uncachedInputs,
            String source,
            String target
    ) throws IOException {
        if (selectedBackend == TranslationBackend.SILICONFLOW_DEEPSEEK_V3) {
            if (!SiliconFlowDeepSeekV3TranslationProvider.isConfigured()) {
                setLastStartupFailureHint(
                        "SiliconFlow API requires SILICONFLOW_API_KEY or settings.properties siliconflow.api.key."
                );
                throw new IOException("SiliconFlow DeepSeek-V3.2 is not configured");
            }
            return SiliconFlowDeepSeekV3TranslationProvider.translatePreparedTexts(
                    uncachedInputs,
                    source,
                    target,
                    HTTP
            );
        }
        if (selectedBackend == TranslationBackend.SILICONFLOW_M2M100) {
            if (!SiliconFlowM2M100TranslationProvider.isConfigured()) {
                setLastStartupFailureHint(
                        "SiliconFlow API requires SILICONFLOW_API_KEY or settings.properties siliconflow.api.key."
                );
                throw new IOException("SiliconFlow M2M100 is not configured");
            }
            return SiliconFlowM2M100TranslationProvider.translatePreparedTexts(
                    uncachedInputs,
                    source,
                    target,
                    HTTP
            );
        }
        if (!SiliconFlowTranslationProvider.isConfigured()) {
            setLastStartupFailureHint(
                    "SiliconFlow API requires SILICONFLOW_API_KEY or settings.properties siliconflow.api.key."
            );
            throw new IOException("SiliconFlow API is not configured");
        }
        return SiliconFlowTranslationProvider.translatePreparedTexts(
                uncachedInputs,
                source,
                target,
                HTTP
        );
    }

    private static String cacheKey(String source, String target, String text) {
        return normalizeCachePart(source) + '\u0001' + normalizeCachePart(target) + '\u0001' + (text == null ? "" : text);
    }

    private static String normalizeCachePart(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private static void ensureBackendReadyForBatchRun() throws IOException {
        if (!isSiliconFlowBackend(selectedBackend)) {
            return;
        }
        if (ensureSelectedBackendAvailable()) {
            return;
        }
        String hint = getLastStartupFailureHint();
        throw new IOException(hint == null || hint.isBlank()
                ? "SiliconFlow API is unavailable."
                : hint);
    }

    private static boolean isNonRetryableBackendError(IOException error) {
        if (error == null || error.getMessage() == null) {
            return false;
        }
        String msg = error.getMessage().toLowerCase(Locale.ROOT);
        return msg.contains("[non_retryable]") || msg.contains("http 401") || msg.contains("http 403");
    }

    private static String cacheLookup(String source, String target, String text) {
        ensurePersistentCacheLoaded();
        return TRANSLATION_CACHE.get(cacheKey(source, target, text));
    }

    private static void cacheStore(String source, String target, String text, String translated) {
        ensurePersistentCacheLoaded();
        TRANSLATION_CACHE.put(cacheKey(source, target, text), translated);
    }

    private static retrofit2.Response<JsonElement> executeTrackedJsonResponse(Call<JsonElement> call) throws IOException {
        return executeTrackedResponse(call);
    }

    private static <T> retrofit2.Response<T> executeTrackedResponse(Call<T> call) throws IOException {
        inFlight.set(call);
        try {
            return call.execute();
        } finally {
            inFlight.compareAndSet(call, null);
        }
    }

    public static List<String> translatePreservingTagsBatched(
            LibreTranslateApi api,
            List<String> texts,
            String source,
            java.util.function.BooleanSupplier stop,
            String target
    ) throws IOException, InterruptedException {

        if (texts == null || texts.isEmpty()) return Collections.emptyList();
        ensureBackendReadyForBatchRun();

        DedupedTexts deduped = dedupeTexts(texts);
        List<String> uniqueTexts = deduped.uniqueTexts;

        long tAll0 = System.currentTimeMillis();
        AppLog.info("[LT] preserveTags.BATCH start: items=" + texts.size() +
                " unique=" + uniqueTexts.size() +
                " chars=" + sumChars(uniqueTexts) + " " + source + "->" + target);

        int maxItems = effectiveBatchMaxItems();
        int maxChars = effectiveBatchMaxChars();
        List<List<String>> parts = chunksByChars(uniqueTexts, maxItems, maxChars);
        AppLog.info("[LT] preserveTags.batching: parts=" + parts.size()
                + " profile=" + (isGpuActive() ? "GPU" : "CPU")
                + " maxItems=" + maxItems + " maxChars=" + maxChars);

        List<String> uniqueOut = new ArrayList<>(uniqueTexts.size());
        int batchNo = 0;

        for (List<String> part : parts) {
            batchNo++;
            long t0 = System.currentTimeMillis();

            IOException last = null;
            for (int attempt = 1; attempt <= 3; attempt++) {
                if (stop != null && stop.getAsBoolean()) return expandDeduped(uniqueOut, deduped, texts.size());

                try {
                    AppLog.info("[LT] preserveTags.batch " + batchNo + "/" + parts.size() +
                            " attempt " + attempt + "/3 items=" + part.size() +
                            " chars=" + sumChars(part));

                    List<String> translated = translatePreservingTags(api, part, source, target);
                    uniqueOut.addAll(translated);

                    long took = System.currentTimeMillis() - t0;
                    AppLog.info("[LT] preserveTags.batch " + batchNo + " OK in " + took + "ms");
                    break;
                } catch (IOException e) {
                    if (isNonRetryableBackendError(e)) {
                        throw e;
                    }
                    last = e;
                    long sleep = Math.min(700L * (1L << (attempt - 1)), 5_000L);
                    refreshActiveEndpoint();
                    AppLog.info("[LT] preserveTags.batch " + batchNo +
                            " failed (" + e.getClass().getSimpleName() + "): " + e.getMessage() +
                            " -> retry in " + sleep + "ms");

                    try {
                        Thread.sleep(sleep);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return expandDeduped(uniqueOut, deduped, texts.size());
                    }

                    if (attempt == 3) throw last;
                }
            }
        }

        long tAll = System.currentTimeMillis() - tAll0;
        AppLog.info("[LT] preserveTags.BATCH done in " + tAll + "ms total");
        return expandDeduped(uniqueOut, deduped, texts.size());
    }
    public static List<String> translatePreservingTagsBatched(
            LibreTranslateApi api,
            List<String> texts,
            String source,
            String target,
            java.util.function.BooleanSupplier stop,
            ProgressListener progress
    ) throws IOException, InterruptedException {

        if (texts == null || texts.isEmpty()) return Collections.emptyList();
        ensureBackendReadyForBatchRun();

        DedupedTexts deduped = dedupeTexts(texts);
        List<String> uniqueTexts = deduped.uniqueTexts;

        long tAll0 = System.currentTimeMillis();
        AppLog.info("[LT] preserveTags.BATCH+PROGRESS start: items=" + texts.size() +
                " unique=" + uniqueTexts.size() +
                " chars=" + sumChars(uniqueTexts) + " " + source + "->" + target);

        int maxItems = effectiveBatchMaxItems();
        int maxChars = effectiveBatchMaxChars();
        List<List<String>> parts = chunksByChars(uniqueTexts, maxItems, maxChars);
        AppLog.info("[LT] preserveTags.batching: parts=" + parts.size()
                + " profile=" + (isGpuActive() ? "GPU" : "CPU")
                + " maxItems=" + maxItems + " maxChars=" + maxChars);

        int total = parts.size();
        if (progress != null) {
            progress.onProgress(0.0, "batch 0/" + total);
        }

        if (total <= 1) {
            // fallback to sequential for tiny jobs
            return translatePreservingTagsBatchedSequential(api, texts, source, target, stop, progress);
        }

        int concurrency = effectiveConcurrency();
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        ExecutorCompletionService<PartResult> completion = new ExecutorCompletionService<>(executor);

        for (int i = 0; i < total; i++) {
            final int batchIndex = i;
            final List<String> part = parts.get(i);

            completion.submit(() -> {
                int attempt = 1;
                while (true) {
                    if (stop != null && stop.getAsBoolean()) {
                        throw new InterruptedException("cancelled");
                    }

                    try {
                        AppLog.info("[LT] preserveTags.batch " + (batchIndex + 1) + "/" + total +
                                " attempt " + attempt + "/3 items=" + part.size() +
                                " chars=" + sumChars(part));

                        List<String> translated = translatePreservingTags(api, part, source, target);
                        AppLog.info("[LT] preserveTags.batch " + (batchIndex + 1) + " OK");
                        return new PartResult(batchIndex, translated);
                    } catch (IOException e) {
                        if (isNonRetryableBackendError(e)) {
                            throw e;
                        }
                        if (attempt >= 3) {
                            throw e;
                        }
                        long sleep = Math.min(700L * (1L << (attempt - 1)), 5_000L);
                        refreshActiveEndpoint();
                        AppLog.info("[LT] preserveTags.batch " + (batchIndex + 1) +
                                " failed (" + e.getClass().getSimpleName() + "): " + e.getMessage() +
                                " -> retry in " + sleep + "ms");
                        Thread.sleep(sleep);
                        attempt++;
                    }
                }
            });
        }

        executor.shutdown();

        @SuppressWarnings("unchecked")
        List<String>[] results = new List[total];
        int finished = 0;
        while (finished < total) {
            Future<PartResult> future = completion.take();
            try {
                PartResult r = future.get();
                results[r.batchIndex] = r.translated;
                finished++;

                if (progress != null) {
                    progress.onProgress(finished / (double) total,
                            "batch " + finished + "/" + total + " (concurrency=" + concurrency + ")");
                }
            } catch (java.util.concurrent.ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof IOException) {
                    throw (IOException) cause;
                } else if (cause instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                    break;
                } else if (cause instanceof RuntimeException) {
                    throw (RuntimeException) cause;
                } else {
                    throw new IOException(cause);
                }
            }
        }

        List<String> uniqueOut = new ArrayList<>(uniqueTexts.size());
        for (int i = 0; i < total; i++) {
            if (results[i] != null) uniqueOut.addAll(results[i]);
        }

        if (progress != null) {
            progress.onProgress(1.0, "done");
        }

        long tAll = System.currentTimeMillis() - tAll0;
        AppLog.info("[LT] preserveTags.BATCH+PROGRESS done in " + tAll + "ms total");
        return expandDeduped(uniqueOut, deduped, texts.size());
    }

    private static class PartResult {
        final int batchIndex;
        final List<String> translated;

        PartResult(int batchIndex, List<String> translated) {
            this.batchIndex = batchIndex;
            this.translated = translated;
        }
    }

    private static DedupedTexts dedupeTexts(List<String> texts) {
        Map<String, Integer> firstIndex = new LinkedHashMap<>();
        List<String> unique = new ArrayList<>();
        int[] mapToUnique = new int[texts.size()];

        for (int i = 0; i < texts.size(); i++) {
            String text = texts.get(i);
            Integer idx = firstIndex.get(text);
            if (idx == null) {
                idx = unique.size();
                firstIndex.put(text, idx);
                unique.add(text);
            }
            mapToUnique[i] = idx;
        }
        return new DedupedTexts(unique, mapToUnique);
    }

    private static List<String> expandDeduped(List<String> uniqueOut, DedupedTexts deduped, int originalSize) {
        List<String> out = new ArrayList<>(originalSize);
        for (int i = 0; i < originalSize; i++) {
            int idx = deduped.mapToUnique[i];
            out.add(idx < uniqueOut.size() ? uniqueOut.get(idx) : null);
        }
        return out;
    }

    private static List<List<String>> chunksByChars(List<String> src, int maxItems, int maxChars) {
        List<List<String>> batches = new ArrayList<>();
        if (src == null || src.isEmpty()) return batches;

        List<String> cur = new ArrayList<>();
        int curChars = 0;

        for (String s : src) {
            int len = (s != null ? s.length() : 0);
            boolean overflowItems = cur.size() >= maxItems;
            boolean overflowChars = (curChars + len) > maxChars;

            if (!cur.isEmpty() && (overflowItems || overflowChars)) {
                batches.add(cur);
                cur = new ArrayList<>();
                curChars = 0;
            }
            cur.add(s);
            curChars += len;
        }
        if (!cur.isEmpty()) batches.add(cur);
        return batches;
    }

    // ===== startup and health check =====
    public static boolean isLtAlive() {
        return refreshActiveEndpoint();
    }

    public static void waitLtReady(int attempts, long sleepMs) throws InterruptedException {
        for (int i = 0; i < attempts; i++) {
            if (refreshActiveEndpoint()) return;

            Process process = managedLtProcess;
            if (process != null && !process.isAlive()) {
                logLatestStartupFailureDetails("managed LibreTranslate process");
                throw new RuntimeException(
                        "LibreTranslate process exited early (code=" + safeExitCode(process)
                                + "). Check libretranslate installation and Python dependencies."
                );
            }
            Thread.sleep(sleepMs);
        }
        logLatestStartupFailureDetails("managed LibreTranslate process");
        throw new RuntimeException("LibreTranslate did not become ready on " + endpointCandidates());
    }

    public static synchronized boolean ensureServerAvailable() {
        if (shutdownRequested || Thread.currentThread().isInterrupted()) {
            return false;
        }
        clearLastStartupFailureHint();
        String gpuBase = normalizeBaseUrl(GPU_FALLBACK_BASE_URL);
        String cpuBase = normalizeBaseUrl(DEFAULT_BASE_URL);

        // Fast path: if endpoint is already alive on known ports, skip heavy capability checks/restarts.
        if (probeBaseUrl(gpuBase)) {
            activateEndpoint(gpuBase);
            AppLog.info("[LT] found running LibreTranslate GPU endpoint on " + gpuBase + ", reusing it");
            return true;
        }
        if (probeBaseUrl(cpuBase)) {
            activateEndpoint(cpuBase);
            AppLog.info("[LT] found running LibreTranslate CPU endpoint on " + cpuBase + ", reusing it");
            return true;
        }

        refreshCapabilityCache(false);
        boolean nvidiaPresent = hasNvidiaGpu();
        boolean localPythonCuda = nvidiaPresent && isLocalPythonCudaAvailable();
        boolean dockerUsable = isDockerUsable();
        boolean cliAvailable = resolveLibreTranslateCliCandidate() != null;
        boolean launcherAvailable = resolveLtLauncherScriptCandidate() != null;
        AppLog.info("[LT] ensureServerAvailable: nvidia=" + nvidiaPresent
                + ", localPythonCuda=" + localPythonCuda
                + ", docker=" + dockerUsable
                + ", python=" + pythonExecutable());
        if (!cachedPythonProbeSummary.isBlank()) {
            AppLog.info("[LT] python runtime probe: " + cachedPythonProbeSummary);
        }

        if (!dockerUsable && !cliAvailable && !launcherAvailable && hasIncompatibleLocalLibreTranslateDependencies()) {
            setLastStartupFailureHint(
                    "Incompatible local Python deps for LibreTranslate: expected numpy<2 and packaging==23.1."
            );
            AppLog.warn("[LT] detected incompatible local Python deps before startup; trying auto-repair");
            if (!runOnnxRuntimeRepairWithPip()) {
                AppLog.warn("[LT] auto-repair was not successful; continuing with CLI/exe/docker fallbacks");
            } else {
                refreshCapabilityCache(true);
                localPythonCuda = nvidiaPresent && isLocalPythonCudaAvailable();
            }
        }

        if (probeBaseUrl(cpuBase)) {
            activateEndpoint(cpuBase);
            AppLog.info("[LT] server became reachable on " + cpuBase + ", reusing existing instance");
            return true;
        }

        if (nvidiaPresent) {
            if (useGpuDocker && dockerUsable && !gpuDockerStartupFailed) {
                try {
                    AppLog.info("[LT] NVIDIA GPU detected, trying GPU LibreTranslate at " + gpuBase);
                    startGpuDockerProcess();
                    if (waitForSpecificEndpoint("docker GPU container", gpuBase,
                            LT_STARTUP_SLOW_ATTEMPTS, LT_DOCKER_STARTUP_SLEEP_MS)) {
                        return true;
                    }
                    // if server is not reachable after starting, mark failure
                    gpuDockerStartupFailed = true;
                } catch (IOException ex) {
                    gpuDockerStartupFailed = true;
                    setLastStartupFailureHint("Docker GPU LibreTranslate start failed: " + ex.getMessage());
                    AppLog.warn("[LT] GPU startup failed, will fall back if CPU is reachable: " + ex.getMessage());
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            } else if (useGpuDocker && dockerUsable) {
                AppLog.info("[LT] GPU Docker is known to be unavailable or previously failed; skipping GPU container startup.");
            } else if (!useGpuDocker) {
                AppLog.info("[LT] GPU Docker mode is disabled by settings; skipping container startup.");
            } else {
                AppLog.warn("[LT] NVIDIA GPU detected, but Docker is unavailable. GPU LibreTranslate is not started.");
            }

            if (localPythonCuda) {
                try {
                    AppLog.info("[LT] CUDA is available locally. Restarting LibreTranslate on " + cpuBase + " in GPU mode.");
                    restartLocalPythonGpuProcessOn5000();
                    if (waitForCpuEndpointAfterStart("local python LibreTranslate (GPU)", LT_STARTUP_SLOW_ATTEMPTS, LT_STARTUP_SLEEP_MS)) {
                        AppLog.info("[LT] local LibreTranslate is now running on " + cpuBase + " (GPU)");
                        return true;
                    }
                    AppLog.warn("[LT] local GPU startup did not become ready. Retrying local python in CPU mode.");
                    restartLocalPythonCpuProcessOn5000();
                    if (waitForCpuEndpointAfterStart("local python LibreTranslate (CPU fallback)", LT_STARTUP_SLOW_ATTEMPTS, LT_STARTUP_SLEEP_MS)) {
                        AppLog.info("[LT] local LibreTranslate is now running on " + cpuBase + " (CPU fallback)");
                        return true;
                    }
                } catch (IOException ex) {
                    AppLog.warn("[LT] local GPU restart on " + cpuBase + " failed: " + ex.getMessage());
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            } else {
                AppLog.warn("[LT] NVIDIA GPU detected, but local Python LibreTranslate cannot use CUDA.");
            }
        }

        if (probeBaseUrl(cpuBase)) {
            activateEndpoint(cpuBase);
            if (nvidiaPresent) {
                AppLog.warn("[LT] GPU endpoint " + gpuBase + " is not reachable. Falling back to CPU on " + cpuBase);
            }
            return true;
        }

        try {
            startLtProcess();
            waitLtReady(80, 1_500L);
            return refreshActiveEndpoint();
        } catch (Exception ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            if (attemptOnnxRuntimeRepairAndRetry()) {
                return true;
            }
            AppLog.error("[LT] unable to start or reconnect LibreTranslate: " + ex.getMessage());
            return false;
        }
    }

    public static String getLastStartupFailureHint() {
        String hint = lastStartupFailureHint;
        return hint == null ? "" : hint;
    }

    private static void clearLastStartupFailureHint() {
        lastStartupFailureHint = "";
    }

    private static void setLastStartupFailureHint(String hint) {
        lastStartupFailureHint = (hint == null) ? "" : hint;
    }

    private static boolean attemptOnnxRuntimeRepairAndRetry() {
        if (onnxRepairAttempted) {
            return false;
        }
        if (!hasOnnxRuntimeDllFailureInStartupLogs()) {
            return false;
        }

        onnxRepairAttempted = true;
        setLastStartupFailureHint(
                "onnxruntime/NumPy environment is incompatible. Install Microsoft Visual C++ Redistributable (x64) " +
                        "and reinstall: numpy<2, packaging==23.1, onnxruntime==1.24.1."
        );
        AppLog.warn("[LT] detected onnxruntime DLL load failure in startup logs; trying auto-repair via pip");

        if (!runOnnxRuntimeRepairWithPip()) {
            AppLog.warn("[LT] auto-repair for onnxruntime failed");
            return false;
        }

        AppLog.info("[LT] onnxruntime auto-repair finished, retrying LibreTranslate startup");
        try {
            startLtProcess();
            waitLtReady(80, 1_500L);
            boolean ready = refreshActiveEndpoint();
            if (ready) {
                setLastStartupFailureHint("");
            }
            return ready;
        } catch (Exception retryEx) {
            if (retryEx instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            AppLog.warn("[LT] startup retry after onnxruntime repair failed: " + retryEx.getMessage());
            return false;
        }
    }

    private static boolean hasOnnxRuntimeDllFailureInStartupLogs() {
        File logDir = new File(getPersistentArgosRuntimeRoot(), "startup-logs");
        File[] files = logDir.listFiles((dir, name) -> name != null && name.startsWith("lt-") && name.endsWith(".log"));
        if (files == null || files.length == 0) {
            return false;
        }

        Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        int checked = 0;
        for (File file : files) {
            if (file == null || !file.isFile()) {
                continue;
            }
            checked++;
            if (checked > 12) {
                break;
            }
            try {
                String text = Files.readString(file.toPath(), StandardCharsets.UTF_8).toLowerCase();
                if (text.contains("onnxruntime_pybind11_state")
                        || text.contains("importerror: dll load failed while importing onnxruntime")
                        || text.contains("a module that was compiled using numpy 1.x cannot be run in numpy 2")) {
                    return true;
                }
            } catch (Exception ignored) {
                // ignore unreadable log file and continue scanning
            }
        }
        return false;
    }

    private static boolean runOnnxRuntimeRepairWithPip() {
        File runtimeRoot = getPersistentArgosRuntimeRoot();
        File logDir = new File(runtimeRoot, "startup-logs");
        logDir.mkdirs();
        File repairLog = new File(logDir, "lt-repair-onnxruntime-" + System.currentTimeMillis() + ".log");

        ProcessBuilder pb = new ProcessBuilder(
                pythonExecutable(),
                "-m", "pip", "install", "--upgrade", "--force-reinstall",
                "numpy<2",
                "packaging==23.1",
                "onnxruntime==1.24.1"
        );
        configureStableLtWorkingDirectory(pb);
        pb.redirectOutput(ProcessBuilder.Redirect.appendTo(repairLog));
        pb.redirectError(ProcessBuilder.Redirect.appendTo(repairLog));

        Map<String, String> env = pb.environment();
        env.putIfAbsent("PYTHONUTF8", "1");
        sanitizePathForLocalPythonProcess(env);

        try {
            Process process = pb.start();
            boolean done = process.waitFor(LT_PIP_REPAIR_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!done) {
                process.destroyForcibly();
                AppLog.warn("[LT] onnxruntime pip repair timed out. Log: " + repairLog.getAbsolutePath());
                return false;
            }
            int exit = process.exitValue();
            if (exit != 0) {
                AppLog.warn("[LT] onnxruntime pip repair failed with exit code " + exit
                        + ". Log: " + repairLog.getAbsolutePath());
                return false;
            }
            AppLog.info("[LT] onnxruntime pip repair succeeded. Log: " + repairLog.getAbsolutePath());
            return true;
        } catch (Exception ex) {
            AppLog.warn("[LT] failed to run onnxruntime pip repair: " + ex.getMessage());
            return false;
        }
    }

    private static boolean hasIncompatibleLocalLibreTranslateDependencies() {
        PythonProbeResult selectedProbe = selectedPythonProbe();
        if (selectedProbe != null && !selectedProbe.hasLibreTranslate()) {
            AppLog.info("[LT] selected python has no libretranslate module: " + selectedProbe.resolvedCommand());
            return false;
        }

        String probe = "import numpy, packaging\n"
                + "nv=numpy.__version__\n"
                + "pv=packaging.__version__\n"
                + "ov=''\n"
                + "onnx_error=''\n"
                + "try:\n"
                + "    import onnxruntime\n"
                + "    ov=getattr(onnxruntime, '__version__', '')\n"
                + "except Exception as ex:\n"
                + "    onnx_error=(ex.__class__.__name__ + ':' + str(ex)).replace('|', '/').replace('\\n', ' ')\n"
                + "bad=(not nv.startswith('1.')) or (pv!='23.1')\n"
                + "status='BROKEN' if onnx_error else ('MISMATCH' if bad else 'OK')\n"
                + "parts=[status, 'numpy=' + nv, 'packaging=' + pv]\n"
                + "if ov:\n"
                + "    parts.append('onnxruntime=' + ov)\n"
                + "if onnx_error:\n"
                + "    parts.append('onnxruntime_error=' + onnx_error)\n"
                + "print('|'.join(parts))\n";
        try {
            ProcessBuilder pb = new ProcessBuilder(pythonExecutable(), "-c", probe)
                    .redirectError(ProcessBuilder.Redirect.DISCARD);
            configureStableLtWorkingDirectory(pb);
            sanitizePathForLocalPythonProcess(pb.environment());
            Process p = pb.start();
            boolean done = p.waitFor(15, TimeUnit.SECONDS);
            if (!done) {
                p.destroyForcibly();
                return false;
            }
            if (p.exitValue() != 0) {
                return false;
            }
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (out.startsWith("MISMATCH") || out.startsWith("BROKEN")) {
                AppLog.warn("[LT] local dependency issue detected: " + out);
                return true;
            }
            return false;
        } catch (Exception ex) {
            return false;
        }
    }

    public static Process startLtProcess() throws IOException {
        // OPTIMIZED: Check if server is already running before killing competing processes
        if (probeBaseUrl("http://127.0.0.1:5000/") || probeBaseUrl("http://127.0.0.1:5001/")) {
            AppLog.info("[LT] LibreTranslate already running, skipping restart");
            return managedLtProcess;
        }
        
        stopCompetingLibreTranslateProcesses(Set.of(5000, 5001));

        if (hasNvidiaGpu() && useGpuDocker && isDockerUsable() && !gpuDockerStartupFailed) {
            try {
                AppLog.info("[LT] trying docker GPU container");
                return startGpuDockerProcess();
            } catch (IOException ex) {
                gpuDockerStartupFailed = true;
                AppLog.warn("[LT] docker GPU container start failed, trying CPU fallbacks: " + ex.getMessage());
            }
        } else if (hasNvidiaGpu() && useGpuDocker && isDockerUsable()) {
            AppLog.info("[LT] skipping docker GPU container due previous nonrecoverable failure.");
        } else if (!useGpuDocker) {
            AppLog.info("[LT] GPU Docker mode is disabled by settings; use CPU or local process.");
        }

        // 1) LibreTranslate CLI Ć…Ā Ä†ĆøĆ…Ā Ä€Ā· PATH
        // Try LibreTranslate CLI from PATH, selected Python Scripts, or working directory.
        LibreTranslateCliCandidate cliCandidate = resolveLibreTranslateCliCandidate();
        if (cliCandidate != null) {
            AppLog.info("[LT] trying " + cliCandidate.description());
            ProcessBuilder pb = new ProcessBuilder(
                    cliCandidate.command(),
                    "--host", "127.0.0.1",
                    "--port", "5000"
            );
            configureStableLtWorkingDirectory(pb);
            configureArgosRuntimeEnvironment(pb.environment(), false);
            Process process = attachLtStartupLog(pb, cliCandidate.sourceTag()).start();
            managedLtProcess = process;
            managedLtGpu = false;
            tryRaiseProcessPriority(process, "High");
            try {
                if (waitForCpuEndpointAfterStart(cliCandidate.description(), LT_STARTUP_FAST_ATTEMPTS, LT_STARTUP_SLEEP_MS)) {
                    return process;
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while waiting " + cliCandidate.description() + " startup", ex);
            }
            stopManagedLtProcess();
            AppLog.warn("[LT] " + cliCandidate.description() + " did not become ready. Trying next fallback.");
        }

        // 2) local exe Ć…ĀÄā€Ā¬Ć…ĀÄ€ĆøĆ…Ā Ä€Ā´Ć…Ā Ä€Ā¾Ć…Ā Ä€Ā¼ Ć…ĀÄ€Ā Ć…Ā Ä†Ā¦Ć…ĀÄā€Ā¬Ć…Ā Ä€Ā¾Ć…Ā Ä€Ā³Ć…ĀÄā€Ā¬Ć…Ā Ä€Ā°Ć…Ā Ä€Ā¼Ć…Ā Ä€Ā¼Ć…Ā Ä€Ā¾Ć…Ā Ä€Ā¹
        // Fall back to the selected Python runtime if no standalone CLI worked.
        LtLauncherScriptCandidate launcherScript = resolveLtLauncherScriptCandidate();
        if (launcherScript != null) {
            AppLog.info("[LT] trying " + launcherScript.description());
            ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", launcherScript.command());
            configureStableLtWorkingDirectory(pb);
            Process process = attachLtStartupLog(pb, launcherScript.sourceTag()).start();
            managedLtProcess = process;
            managedLtGpu = false;
            tryRaiseProcessPriority(process, "High");
            try {
                if (waitForCpuEndpointAfterStart(launcherScript.description(),
                        LT_STARTUP_FAST_ATTEMPTS, LT_STARTUP_SLEEP_MS)) {
                    return process;
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while waiting " + launcherScript.description() + " startup", ex);
            }
            stopManagedLtProcess();
            AppLog.warn("[LT] " + launcherScript.description() + " did not become ready. Trying next fallback.");
        }

        try {
            Process process = startLocalPythonLibreTranslateProcess();
            try {
                if (waitForCpuEndpointAfterStart("python -m libretranslate.main", LT_STARTUP_SLOW_ATTEMPTS, LT_STARTUP_SLEEP_MS)) {
                    return process;
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while waiting python LibreTranslate startup", ex);
            }
            if (managedLtGpu) {
                AppLog.warn("[LT] local python GPU startup did not become ready. Retrying in CPU mode.");
                stopManagedLtProcess();
                process = startLocalPythonLibreTranslateProcess(false);
                try {
                    if (waitForCpuEndpointAfterStart("python -m libretranslate.main (CPU fallback)", LT_STARTUP_SLOW_ATTEMPTS, LT_STARTUP_SLEEP_MS)) {
                        return process;
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while waiting python LibreTranslate CPU fallback startup", ex);
                }
            }
            stopManagedLtProcess();
            AppLog.warn("[LT] Python LibreTranslate process did not become ready. Trying Docker CPU fallback.");
        } catch (IOException ex) {
            AppLog.warn("[LT] local python LibreTranslate start failed: " + ex.getMessage());
        }

        // 4) Docker Ć…ĀÄā‚¬ĀĆ…Ā Ä€Ā¾Ć…Ā Ä€Ā»Ć…ĀÄ€ĀĆ…Ā Ć…ā€”Ć…Ā Ä€Ā¾ Ć…Ā Ä€Ā² Ć…ĀÄ€ĀĆ…Ā Ä€Ā°Ć…Ā Ä€Ā¼Ć…Ā Ä€Ā¾Ć…Ā Ä€Ā¼ Ć…Ā Ć…ā€”Ć…Ā Ä€Ā¾Ć…Ā Ä€Ā½Ć…ĀÄā‚¬Ā Ć…Ā Ä€Āµ
        if (isDockerUsable()) {
            AppLog.info("[LT] trying docker CPU container");
            ProcessBuilder pb = new ProcessBuilder(
                    "docker", "run", "--rm",
                    "-p", "127.0.0.1:5000:5000",
                    "libretranslate/libretranslate"
            );
            configureStableLtWorkingDirectory(pb);
            Process process = attachLtStartupLog(pb, "lt-docker-cpu").start();
            managedLtProcess = process;
            managedLtGpu = false;
            tryRaiseProcessPriority(process, "High");
            try {
                if (waitForCpuEndpointAfterStart("docker CPU container", LT_STARTUP_SLOW_ATTEMPTS, LT_DOCKER_STARTUP_SLEEP_MS)) {
                    return process;
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while waiting docker CPU startup", ex);
            }
            stopManagedLtProcess();
            AppLog.warn("[LT] docker CPU container did not become ready.");
        }

        throw new IllegalStateException(
                "LibreTranslate launch failed: no working libretranslate/python/docker found"
        );
    }

    private static boolean waitForCpuEndpointAfterStart(String source, int attempts, long sleepMs) throws InterruptedException {
        String cpuBase = normalizeBaseUrl(DEFAULT_BASE_URL);
        for (int i = 0; i < attempts; i++) {
            if (probeBaseUrl(cpuBase)) {
                activateEndpoint(cpuBase);
                return true;
            }
            Process process = managedLtProcess;
            if (process != null && !process.isAlive()) {
                AppLog.warn("[LT] " + source + " exited early (code=" + safeExitCode(process) + ")");
                logLatestStartupFailureDetails(source);
                return false;
            }
            Thread.sleep(sleepMs);
        }
        AppLog.warn("[LT] " + source + " did not become ready on " + cpuBase);
        logLatestStartupFailureDetails(source);
        return false;
    }

    private static LibreTranslateCliCandidate resolveLibreTranslateCliCandidate() {
        LibreTranslateCliCandidate envOverride = createLibreTranslateCliCandidate(
                System.getenv("LT_CLI"),
                "lt-cli-env",
                "LibreTranslate CLI from LT_CLI"
        );
        if (envOverride != null) {
            return envOverride;
        }

        if (isOnPath("libretranslate")) {
            return new LibreTranslateCliCandidate(
                    "libretranslate",
                    "lt-cli",
                    "LibreTranslate CLI from PATH"
            );
        }

        PythonProbeResult selectedPython = selectedPythonProbe();
        if (selectedPython != null) {
            File pythonFile = new File(selectedPython.resolvedCommand());
            File pythonDir = pythonFile.getParentFile();
            if (pythonDir != null) {
                LibreTranslateCliCandidate fromScripts = firstLibreTranslateCliCandidate(
                        new File(pythonDir, "Scripts"),
                        "lt-cli-python-scripts",
                        "LibreTranslate CLI from selected Python Scripts"
                );
                if (fromScripts != null) {
                    return fromScripts;
                }
            }
        }

        return createLibreTranslateCliCandidate(
                new File("libretranslate.exe").getAbsolutePath(),
                "lt-local-exe",
                "local libretranslate.exe"
        );
    }

    private static LtLauncherScriptCandidate resolveLtLauncherScriptCandidate() {
        LtLauncherScriptCandidate envOverride = createLtLauncherScriptCandidate(
                System.getenv("LT_LAUNCHER"),
                "lt-launcher-env",
                "LibreTranslate launcher from LT_LAUNCHER"
        );
        if (envOverride != null) {
            return envOverride;
        }

        Path bundledScript = resolveBundledLtLauncherScript();
        if (bundledScript != null) {
            return createLtLauncherScriptCandidate(
                    bundledScript.toAbsolutePath().toString(),
                    "lt-launcher-app",
                    "bundled LibreTranslate launcher"
            );
        }

        return createLtLauncherScriptCandidate(
                Path.of(System.getProperty("user.dir", "."), "tools", "start-local-libretranslate.cmd").toString(),
                "lt-launcher-cwd",
                "project LibreTranslate launcher"
        );
    }

    private static Path resolveBundledLtLauncherScript() {
        try {
            Path appLocation = Path.of(Objects.requireNonNull(
                    TranslationService.class.getProtectionDomain().getCodeSource().getLocation().toURI()));
            Path appDir = Files.isDirectory(appLocation) ? appLocation : appLocation.getParent();
            if (appDir == null) {
                return null;
            }

            Path rootScript = appDir.resolve("start-local-libretranslate.cmd");
            if (Files.isRegularFile(rootScript)) {
                return rootScript;
            }

            Path toolsScript = appDir.resolve("tools").resolve("start-local-libretranslate.cmd");
            if (Files.isRegularFile(toolsScript)) {
                return toolsScript;
            }
        } catch (URISyntaxException | RuntimeException ignored) {
            // fallback below
        }
        return null;
    }

    private static LtLauncherScriptCandidate createLtLauncherScriptCandidate(
            String command,
            String sourceTag,
            String description
    ) {
        if (command == null || command.isBlank()) {
            return null;
        }

        File file = new File(command.trim());
        if (!file.isFile()) {
            return null;
        }
        return new LtLauncherScriptCandidate(file.getAbsolutePath(), sourceTag, description);
    }

    private static LibreTranslateCliCandidate firstLibreTranslateCliCandidate(
            File directory,
            String sourceTag,
            String description
    ) {
        if (directory == null || !directory.isDirectory()) {
            return null;
        }

        LibreTranslateCliCandidate exe = createLibreTranslateCliCandidate(
                new File(directory, "libretranslate.exe").getAbsolutePath(),
                sourceTag,
                description
        );
        if (exe != null) {
            return exe;
        }

        LibreTranslateCliCandidate cmd = createLibreTranslateCliCandidate(
                new File(directory, "libretranslate.cmd").getAbsolutePath(),
                sourceTag,
                description
        );
        if (cmd != null) {
            return cmd;
        }

        return createLibreTranslateCliCandidate(
                new File(directory, "libretranslate.bat").getAbsolutePath(),
                sourceTag,
                description
        );
    }

    private static LibreTranslateCliCandidate createLibreTranslateCliCandidate(
            String command,
            String sourceTag,
            String description
    ) {
        if (command == null || command.isBlank()) {
            return null;
        }

        String trimmed = command.trim();
        File file = new File(trimmed);
        if (file.isAbsolute() && !file.isFile()) {
            return null;
        }

        return new LibreTranslateCliCandidate(trimmed, sourceTag, description);
    }

    private static boolean isOnPath(String tool) {
        try {
            Process p = new ProcessBuilder("cmd", "/c", "where", tool).start();
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }
    private static boolean isDockerUsable() {
        refreshCapabilityCache(false);
        return cachedDockerUsable;
    }

    private static boolean isDockerUsableRaw() {
        try {
            Process p = new ProcessBuilder("docker", "version")
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();

            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static Process startGpuDockerProcess() throws IOException {
        stopCompetingLibreTranslateProcesses(Set.of(5001));

        // OPTIMIZED: Docker GPU with maximum resource allocation and async processing
        ProcessBuilder pb = new ProcessBuilder(
                "docker", "run", "--rm",
                "--gpus", "all",
                "--memory", "8g",              // OPTIMIZED: Allocate 8GB RAM
                "--cpus", "4.0",               // OPTIMIZED: 4 CPU cores
                "--shm-size", "2g",            // OPTIMIZED: Shared memory for GPU
                "-p", "127.0.0.1:5001:5000",
                "-e", "LIBRETRANSLATE_PORT=5000",
                "-e", "LIBRETRANSLATE_THREADS=8",
                "-e", "CUDA_DEVICE_ORDER=PCI_BUS_ID",
                "-e", "CUDA_LAUNCH_BLOCKING=0",
                "libretranslate/libretranslate:latest-cuda"
        );
        configureStableLtWorkingDirectory(pb);
        Process process = attachLtStartupLog(pb, "lt-docker-gpu").start();
        managedLtProcess = process;
        managedLtGpu = true;
        tryRaiseProcessPriority(process, "High");
        return process;
    }

    private static Process startLocalPythonLibreTranslateProcess() throws IOException {
        boolean requestCuda = hasNvidiaGpu() && isLocalPythonCudaAvailable();
        return startLocalPythonLibreTranslateProcess(requestCuda);
    }

    private static Process startLocalPythonLibreTranslateProcess(boolean requestCuda) throws IOException {
        File runtimeRoot = getPersistentArgosRuntimeRoot();
        File configDir = new File(runtimeRoot, "config");
        File dataDir = new File(runtimeRoot, "data");
        File cacheDir = new File(runtimeRoot, "cache");
        File packagesDir = new File(runtimeRoot, "packages");

        ensureArgosRuntimeDirectories(configDir, dataDir, cacheDir, packagesDir);

        if (hasNvidiaGpu() && !requestCuda) {
            AppLog.warn("[LT] NVIDIA detected, but local Python LibreTranslate has no CUDA support. Starting in CPU mode.");
        }

        AppLog.info("[LT] trying local python LibreTranslate on 127.0.0.1:5000"
                + " (" + (requestCuda ? "GPU" : "CPU") + ")");
        AppLog.info("[LT] Argos runtime: " + runtimeRoot.getAbsolutePath());

        PythonProbeResult selectedPython = selectedPythonProbe();
        if (selectedPython != null) {
            AppLog.info("[LT] local python selection: " + selectedPython.describe());
            if (!selectedPython.hasLibreTranslateMain()) {
                String message = "Selected Python cannot import libretranslate.main: "
                        + selectedPython.resolvedCommand();
                setLastStartupFailureHint(message);
                throw new IOException(message);
            }
        }

        stopCompetingLibreTranslateProcesses(Set.of(5000));

        ProcessBuilder pb = new ProcessBuilder(
                pythonExecutable(),
                "-m", "libretranslate.main",
                "--host", "127.0.0.1",
                "--port", "5000",
                "--disable-web-ui"
        );
        configureStableLtWorkingDirectory(pb);

        configureArgosRuntimeEnvironment(pb.environment(), requestCuda);

        Process process = attachLtStartupLog(pb, requestCuda ? "lt-python-gpu" : "lt-python-cpu")
                .start();
        managedLtProcess = process;
        managedLtGpu = requestCuda;
        tryRaiseProcessPriority(process, "High");
        return process;
    }

    public static void requestTranslationPerformanceMode() {
        if (PERFORMANCE_MODE_LEASES.incrementAndGet() == 1) {
            long currentPid = ProcessHandle.current().pid();
            // Maximize CPU for preparation/dedup/glossary + translation IPC,
            // even if the UI becomes less responsive while a translation is running.
            tryRaiseProcessPriorityByPid(currentPid, "High", "APP");
        }

        Process ltProcess = managedLtProcess;
        if (ltProcess != null && ltProcess.isAlive()) {
            tryRaiseProcessPriority(ltProcess, "High");
        }
    }

    public static void restoreTranslationPerformanceMode() {
        int leases = PERFORMANCE_MODE_LEASES.updateAndGet(current -> Math.max(0, current - 1));
        if (leases == 0) {
            long currentPid = ProcessHandle.current().pid();
            tryRaiseProcessPriorityByPid(currentPid, "High", "APP");
        }
    }

    public static void shutdown() {
        shutdownRequested = true;
        cancelInFlight();
        stopManagedLtProcess();
        savePersistentTranslationCache();
    }

    private static File getPersistentArgosRuntimeRoot() {
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null && !localAppData.isBlank()) {
            return new File(localAppData, "LocalizationEditorSC2KSP\\argos-runtime");
        }
        return new File(System.getProperty("user.home"), ".localization-editor-argos-runtime");
    }

    private static void ensureArgosRuntimeDirectories(
            File configDir,
            File dataDir,
            File cacheDir,
            File packagesDir
    ) {
        configDir.mkdirs();
        dataDir.mkdirs();
        cacheDir.mkdirs();
        packagesDir.mkdirs();
    }

    private static void configureStableLtWorkingDirectory(ProcessBuilder pb) {
        File runtimeRoot = getPersistentArgosRuntimeRoot();
        if (!runtimeRoot.exists() && !runtimeRoot.mkdirs()) {
            AppLog.warn("[LT] failed to create runtime directory: " + runtimeRoot.getAbsolutePath());
        }
        pb.directory(runtimeRoot);
    }

    private static void configureArgosRuntimeEnvironment(Map<String, String> env, boolean requestCuda) {
        File runtimeRoot = getPersistentArgosRuntimeRoot();
        File configDir = new File(runtimeRoot, "config");
        File dataDir = new File(runtimeRoot, "data");
        File cacheDir = new File(runtimeRoot, "cache");
        File packagesDir = new File(runtimeRoot, "packages");
        ensureArgosRuntimeDirectories(configDir, dataDir, cacheDir, packagesDir);

        env.put("XDG_CONFIG_HOME", configDir.getAbsolutePath());
        env.put("XDG_DATA_HOME", dataDir.getAbsolutePath());
        env.put("XDG_CACHE_HOME", cacheDir.getAbsolutePath());
        env.put("ARGOS_PACKAGES_DIR", packagesDir.getAbsolutePath());
        env.put("ARGOS_DEVICE_TYPE", requestCuda ? "cuda" : "cpu");
        env.putIfAbsent("PYTHONUTF8", "1");
        env.putIfAbsent("PYTHONUNBUFFERED", "1");

        int threads = Runtime.getRuntime().availableProcessors();
        if (requestCuda) {
            threads = Math.max(threads * 2, 16);
        }
        env.putIfAbsent("OMP_NUM_THREADS", String.valueOf(threads));
        env.putIfAbsent("OPENBLAS_NUM_THREADS", String.valueOf(threads));
        env.putIfAbsent("MKL_NUM_THREADS", String.valueOf(threads));
        env.putIfAbsent("NUMEXPR_NUM_THREADS", String.valueOf(threads));
        if (requestCuda) {
            env.putIfAbsent("CUDA_DEVICE_ORDER", "PCI_BUS_ID");
            env.putIfAbsent("CUDA_LAUNCH_BLOCKING", "0");
            env.putIfAbsent("TF_FORCE_GPU_ALLOW_GROWTH", "true");
        }

        sanitizePathForLocalPythonProcess(env);
    }

    private static void sanitizePathForLocalPythonProcess(Map<String, String> env) {
        if (env == null || env.isEmpty()) {
            return;
        }

        String pathKey = resolvePathKey(env);
        String currentPath = env.get(pathKey);
        if (currentPath == null || currentPath.isBlank()) {
            currentPath = System.getenv("PATH");
        }

        LinkedHashSet<String> cleanedSeen = new LinkedHashSet<>();
        List<String> cleaned = new ArrayList<>();

        String javaHome = System.getProperty("java.home", "");
        String javaHomeNorm = javaHome == null || javaHome.isBlank()
                ? ""
                : normalizePath(new File(javaHome).getAbsolutePath());
        String javaRuntimeBinNorm = normalizePath(new File(javaHome, "bin").getAbsolutePath());
        String legacyAppRuntimeNorm = "\\localization editor sc2 ksp\\runtime\\";

        if (currentPath != null && !currentPath.isBlank()) {
            String[] parts = currentPath.split(";");
            for (String part : parts) {
                if (part == null) {
                    continue;
                }
                String trimmed = part.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                String norm = normalizePath(trimmed);
                if (norm.equals(javaRuntimeBinNorm)) {
                    continue;
                }
                if (!javaHomeNorm.isBlank() && norm.startsWith(javaHomeNorm)) {
                    continue;
                }
                if (norm.contains(legacyAppRuntimeNorm)) {
                    continue;
                }
                if (cleanedSeen.add(norm)) {
                    cleaned.add(trimmed);
                }
            }
        }

        List<String> prefixed = new ArrayList<>();
        LinkedHashSet<String> finalSeen = new LinkedHashSet<>();
        String systemRoot = env.getOrDefault("SystemRoot", System.getenv("SystemRoot"));
        if (systemRoot != null && !systemRoot.isBlank()) {
            appendUniquePath(prefixed, finalSeen, new File(systemRoot, "System32").getAbsolutePath());
            appendUniquePath(prefixed, finalSeen, systemRoot);
        }

        String pythonExec = pythonExecutable();
        File pythonFile = new File(pythonExec);
        if (pythonFile.isAbsolute()) {
            File pythonDir = pythonFile.getParentFile();
            if (pythonDir != null) {
                appendUniquePath(prefixed, finalSeen, pythonDir.getAbsolutePath());
                appendUniquePath(prefixed, finalSeen, new File(pythonDir, "Scripts").getAbsolutePath());
            }
        }

        for (String path : cleaned) {
            appendUniquePath(prefixed, finalSeen, path);
        }
        env.put(pathKey, String.join(";", prefixed));
    }

    private static String resolvePathKey(Map<String, String> env) {
        for (String key : env.keySet()) {
            if (key != null && key.equalsIgnoreCase("PATH")) {
                return key;
            }
        }
        return "PATH";
    }

    private static void appendUniquePath(List<String> paths, Set<String> seen, String path) {
        if (path == null || path.isBlank()) {
            return;
        }
        String norm = normalizePath(path);
        if (seen.add(norm)) {
            paths.add(path);
        }
    }

    private static String normalizePath(String value) {
        return value.replace('/', '\\').toLowerCase(Locale.ROOT);
    }

    private static ProcessBuilder attachLtStartupLog(ProcessBuilder pb, String sourceTag) {
        File runtimeRoot = getPersistentArgosRuntimeRoot();
        File logDir = new File(runtimeRoot, "startup-logs");
        logDir.mkdirs();
        File startupLog = new File(logDir, sourceTag + "-" + System.currentTimeMillis() + ".log");
        pb.redirectOutput(ProcessBuilder.Redirect.appendTo(startupLog));
        pb.redirectError(ProcessBuilder.Redirect.appendTo(startupLog));
        lastLtStartupLogPath = startupLog.toPath();
        AppLog.info("[LT] startup log for " + sourceTag + ": " + startupLog.getAbsolutePath());
        return pb;
    }

    private static Path getPersistentTranslationCachePath() {
        File runtimeRoot = getPersistentArgosRuntimeRoot();
        runtimeRoot.mkdirs();
        return runtimeRoot.toPath().resolve("translation-cache.xml");
    }

    private static void restartLocalPythonGpuProcessOn5000() throws IOException {
        // OPTIMIZED: Reuse already running GPU process instead of restarting
        Process process = managedLtProcess;
        if (process != null && process.isAlive() && managedLtGpu) {
            AppLog.info("[LT] GPU process already running on 127.0.0.1:5000, reusing existing");
            return;
        }
        
        // Process is not running or is CPU mode - restart with GPU
        stopManagedLtProcess();
        stopCompetingLibreTranslateProcesses(Set.of(5000, 5001));
        startLocalPythonLibreTranslateProcess(true);
    }

    private static void restartLocalPythonCpuProcessOn5000() throws IOException {
        stopManagedLtProcess();
        stopCompetingLibreTranslateProcesses(Set.of(5000, 5001));
        startLocalPythonLibreTranslateProcess(false);
    }

    private static void stopManagedLtProcess() {
        Process process = managedLtProcess;
        managedLtProcess = null;
        managedLtGpu = false;
        if (process == null) {
            return;
        }
        destroyProcess(process.toHandle(), "[LT] stopped managed LibreTranslate process");
    }

    private static void stopCompetingLibreTranslateProcesses(Set<Integer> ports) {
        long currentPid = ProcessHandle.current().pid();
        ProcessHandle.allProcesses()
                .filter(handle -> handle.pid() != currentPid)
                .filter(handle -> handle.isAlive())
                .filter(handle -> looksLikeLibreTranslateProcess(handle, ports))
                .forEach(handle -> destroyProcess(handle,
                        "[LT] stopped stale LibreTranslate process (pid=" + handle.pid() + ")"));
    }

    private static boolean looksLikeLibreTranslateProcess(ProcessHandle handle, Set<Integer> ports) {
        ProcessHandle.Info info = handle.info();
        String command = info.command().orElse("").toLowerCase();
        String commandLine = info.commandLine().orElse("").toLowerCase();
        if (!command.contains("python") && !command.contains("libretranslate")) {
            return false;
        }
        Set<Integer> checkedPorts = (ports == null) ? Set.of() : ports;
        boolean portMatched = checkedPorts.isEmpty();
        if (!portMatched) {
            for (Integer port : checkedPorts) {
                if (port != null && commandLine.contains(String.valueOf(port))) {
                    portMatched = true;
                    break;
                }
            }
        }
        if (!portMatched) {
            return false;
        }
        return commandLine.contains("libretranslate.main")
                || commandLine.contains("libretranslate.exe")
                || commandLine.contains(" libretranslate ");
    }

    private static void destroyProcess(ProcessHandle handle, String successLog) {
        try {
            handle.destroy();
            try {
                handle.onExit().get(java.time.Duration.ofSeconds(5).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            } catch (Exception ignored) {
                if (handle.isAlive()) {
                    handle.destroyForcibly();
                }
            }
            AppLog.info(successLog);
        } catch (Exception ex) {
            AppLog.warn("[LT] failed to stop stale LibreTranslate process: " + ex.getMessage());
        }
    }

    private static String safeErrorBody(retrofit2.Response<?> resp) {
        if (resp == null) {
            return "null";
        }
        okhttp3.ResponseBody errorBody = resp.errorBody();
        if (errorBody == null) {
            return "null";
        }
        try {
            return errorBody.string();
        } catch (IOException e) {
            return "<failed to read error body: " + e.getMessage() + ">";
        }
    }

    private static boolean probeLocalPythonCudaSupport() {
        if (probeLocalPythonCuda(
                "import ctranslate2; print('1' if ctranslate2.get_cuda_device_count() > 0 else '0')")) {
            return true;
        }
        try {
            return probeLocalPythonCuda(
                    "import torch; print('1' if torch.cuda.is_available() else '0')");
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isLocalPythonCudaAvailable() {
        refreshCapabilityCache(false);
        return cachedLocalPythonCuda;
    }

    private static boolean probeLocalPythonCuda(String command) {
        try {
            Process p = new ProcessBuilder(pythonExecutable(), "-c", command)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            byte[] out = p.getInputStream().readAllBytes();
            if (p.waitFor() != 0) {
                return false;
            }
            return new String(out).trim().equals("1");
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean probeNvidiaGpu() {
        if (probeNvidiaSmi("nvidia-smi")) {
            return true;
        }
        return probeNvidiaSmi("C:\\Windows\\System32\\nvidia-smi.exe");
    }

    private static boolean hasNvidiaGpu() {
        refreshCapabilityCache(false);
        return cachedNvidiaGpu;
    }

    private static boolean shouldRefreshCapabilityCache(long now, boolean force) {
        if (force) {
            return true;
        }
        return capabilityCacheTs <= 0 || (now - capabilityCacheTs) >= CAPABILITY_CACHE_TTL_MS;
    }

    private static void refreshCapabilityCache(boolean force) {
        long now = System.currentTimeMillis();
        synchronized (TranslationService.class) {
            if (!shouldRefreshCapabilityCache(now, force)) {
                return;
            }
        }

        // Heavy probes are executed outside synchronized block.
        boolean nvidia = probeNvidiaGpu();
        boolean docker = isDockerUsableRaw();
        boolean localCuda = nvidia && probeLocalPythonCudaSupport();

        synchronized (TranslationService.class) {
            long now2 = System.currentTimeMillis();
            if (!shouldRefreshCapabilityCache(now2, force)) {
                return;
            }
            cachedNvidiaGpu = nvidia;
            cachedDockerUsable = docker;
            cachedLocalPythonCuda = localCuda;
            capabilityCacheTs = now2;
        }
    }

    private static boolean probeNvidiaSmi(String executable) {
        try {
            Process p = new ProcessBuilder(executable, "-L")
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static int safeExitCode(Process process) {
        if (process == null) {
            return -1;
        }
        try {
            return process.exitValue();
        } catch (IllegalThreadStateException ex) {
            return -1;
        }
    }

    private static synchronized String pythonExecutable() {
        if (cachedPythonExecutable != null && !cachedPythonExecutable.isBlank()) {
            return cachedPythonExecutable;
        }

        PythonProbeResult selected = selectPythonRuntime();
        cachedPythonProbeResult = selected;
        if (selected != null) {
            cachedPythonExecutable = selected.resolvedCommand();
            cachedPythonProbeSummary = selected.describe();
            AppLog.info("[LT] selected python executable: " + cachedPythonProbeSummary);
            return cachedPythonExecutable;
        }

        cachedPythonExecutable = "python";
        cachedPythonProbeSummary = "python (fallback; no runnable interpreter discovered)";
        AppLog.warn("[LT] no runnable Python interpreter was discovered explicitly. Falling back to 'python'.");
        return cachedPythonExecutable;
    }

    private static PythonProbeResult selectedPythonProbe() {
        PythonProbeResult probe = cachedPythonProbeResult;
        if (probe != null) {
            return probe;
        }
        pythonExecutable();
        return cachedPythonProbeResult;
    }

    private static PythonProbeResult selectPythonRuntime() {
        PythonProbeResult firstRunnable = null;
        PythonProbeResult firstWithLibreTranslate = null;

        for (String candidate : discoverPythonCandidates()) {
            PythonProbeResult probe = probePythonCandidate(candidate);
            AppLog.info("[LT] python probe: " + probe.describe());
            if (!probe.runnable()) {
                continue;
            }
            if (firstRunnable == null) {
                firstRunnable = probe;
            }
            if (probe.hasLibreTranslateMain()) {
                return probe;
            }
            if (firstWithLibreTranslate == null && probe.hasLibreTranslate()) {
                firstWithLibreTranslate = probe;
            }
        }

        return firstWithLibreTranslate != null ? firstWithLibreTranslate : firstRunnable;
    }

    private static List<String> discoverPythonCandidates() {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();

        addPythonCandidate(candidates, System.getenv("LT_PYTHON"));

        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null && !localAppData.isBlank()) {
            addPythonExecutablesFromParent(candidates, new File(localAppData, "Programs\\Python"));
        }

        String programFiles = System.getenv("ProgramFiles");
        if (programFiles != null && !programFiles.isBlank()) {
            addPythonExecutablesFromParent(candidates, new File(programFiles));
        }

        String programFilesX86 = System.getenv("ProgramFiles(x86)");
        if (programFilesX86 != null && !programFilesX86.isBlank()) {
            addPythonExecutablesFromParent(candidates, new File(programFilesX86));
        }

        String systemDrive = System.getenv("SystemDrive");
        if (systemDrive == null || systemDrive.isBlank()) {
            systemDrive = "C:";
        }
        addPythonExecutablesFromParent(candidates, new File(systemDrive + "\\"));

        addPythonCandidate(candidates, "py");
        addPythonCandidate(candidates, "python");

        return new ArrayList<>(candidates);
    }

    private static void addPythonCandidate(Set<String> candidates, String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return;
        }

        String trimmed = candidate.trim();
        File file = new File(trimmed);
        if (file.isAbsolute()) {
            if (!file.isFile()) {
                return;
            }
            candidates.add(file.getAbsolutePath());
            return;
        }
        candidates.add(trimmed);
    }

    private static void addPythonExecutablesFromParent(Set<String> candidates, File parent) {
        if (parent == null || !parent.isDirectory()) {
            return;
        }

        File[] dirs = parent.listFiles(file -> file.isDirectory()
                && file.getName() != null
                && file.getName().toLowerCase(Locale.ROOT).startsWith("python"));
        if (dirs == null || dirs.length == 0) {
            return;
        }

        Arrays.sort(dirs, (a, b) -> {
            int versionCompare = Integer.compare(
                    extractPythonVersionSortKey(b.getName()),
                    extractPythonVersionSortKey(a.getName())
            );
            if (versionCompare != 0) {
                return versionCompare;
            }
            return b.getName().compareToIgnoreCase(a.getName());
        });

        for (File dir : dirs) {
            addPythonCandidate(candidates, new File(dir, "python.exe").getAbsolutePath());
        }
    }

    private static int extractPythonVersionSortKey(String name) {
        if (name == null || name.isBlank()) {
            return 0;
        }

        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char ch = name.charAt(i);
            if (Character.isDigit(ch)) {
                digits.append(ch);
            }
        }

        if (digits.isEmpty()) {
            return 0;
        }

        try {
            return Integer.parseInt(digits.toString());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static PythonProbeResult probePythonCandidate(String command) {
        String probe = "import os, site, sys\n"
                + "roots=[]\n"
                + "try:\n"
                + "    roots.extend(site.getsitepackages())\n"
                + "except Exception:\n"
                + "    pass\n"
                + "try:\n"
                + "    roots.append(site.getusersitepackages())\n"
                + "except Exception:\n"
                + "    pass\n"
                + "roots.extend([p for p in sys.path if p])\n"
                + "seen=set()\n"
                + "uniq=[]\n"
                + "for root in roots:\n"
                + "    root=os.path.abspath(root)\n"
                + "    if root not in seen:\n"
                + "        seen.add(root)\n"
                + "        uniq.append(root)\n"
                + "origin=''\n"
                + "has_lt=False\n"
                + "has_lt_main=False\n"
                + "for root in uniq:\n"
                + "    pkg_dir=os.path.join(root, 'libretranslate')\n"
                + "    init_py=os.path.join(pkg_dir, '__init__.py')\n"
                + "    main_py=os.path.join(pkg_dir, 'main.py')\n"
                + "    if not has_lt and (os.path.isfile(init_py) or os.path.isdir(pkg_dir)):\n"
                + "        has_lt=True\n"
                + "        origin=init_py if os.path.isfile(init_py) else pkg_dir\n"
                + "    if not has_lt_main and os.path.isfile(main_py):\n"
                + "        has_lt_main=True\n"
                + "        if not origin:\n"
                + "            origin=main_py\n"
                + "print('exe=' + sys.executable)\n"
                + "print('version=' + sys.version.split()[0])\n"
                + "print('lt=' + ('1' if has_lt else '0'))\n"
                + "print('lt_main=' + ('1' if has_lt_main else '0'))\n"
                + "print('origin=' + origin)\n";
        try {
            ProcessBuilder pb = new ProcessBuilder(command, "-c", probe)
                    .redirectErrorStream(true);
            configureStableLtWorkingDirectory(pb);
            pb.environment().putIfAbsent("PYTHONUTF8", "1");
            Process process = pb.start();
            boolean done = process.waitFor(PYTHON_PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!done) {
                process.destroyForcibly();
                return new PythonProbeResult(command, "", "", false, false, "", false, "timed out");
            }

            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (process.exitValue() != 0) {
                return new PythonProbeResult(command, "", "", false, false, "", false,
                        output.isBlank() ? "exit=" + process.exitValue() : shortenDiagnostic(output));
            }

            Map<String, String> values = parseKeyValueProbeOutput(output);
            String executable = values.getOrDefault("exe", "");
            String version = values.getOrDefault("version", "");
            boolean hasLibreTranslate = "1".equals(values.get("lt"));
            boolean hasLibreTranslateMain = "1".equals(values.get("lt_main"));
            String origin = values.getOrDefault("origin", "");
            return new PythonProbeResult(command, executable, version,
                    hasLibreTranslate, hasLibreTranslateMain, origin, true, "");
        } catch (Exception ex) {
            return new PythonProbeResult(command, "", "", false, false, "", false, ex.getMessage());
        }
    }

    private static Map<String, String> parseKeyValueProbeOutput(String output) {
        Map<String, String> values = new HashMap<>();
        if (output == null || output.isBlank()) {
            return values;
        }

        String[] lines = output.split("\\R");
        for (String line : lines) {
            if (line == null || line.isBlank()) {
                continue;
            }
            int idx = line.indexOf('=');
            if (idx <= 0) {
                continue;
            }
            values.put(line.substring(0, idx).trim(), line.substring(idx + 1).trim());
        }
        return values;
    }

    private static void logLatestStartupFailureDetails(String source) {
        Path logPath = lastLtStartupLogPath;
        if (logPath == null || !Files.isRegularFile(logPath)) {
            return;
        }

        String diagnostic = readStartupDiagnosticLine(logPath);
        StringBuilder message = new StringBuilder("[LT] ")
                .append(source)
                .append(" startup log: ")
                .append(logPath.toAbsolutePath());
        if (!diagnostic.isBlank()) {
            message.append(" | ").append(diagnostic);
        }
        AppLog.warn(message.toString());
        setLastStartupFailureHint(buildStartupFailureHint(logPath, diagnostic));
    }

    private static String readStartupDiagnosticLine(Path logPath) {
        Deque<String> tail = new ArrayDeque<>();
        try (BufferedReader reader = Files.newBufferedReader(logPath, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (tail.size() >= 40) {
                    tail.removeFirst();
                }
                tail.addLast(line);
            }
        } catch (Exception ex) {
            return "";
        }

        String fallback = "";
        while (!tail.isEmpty()) {
            String candidate = normalizeDiagnosticLine(tail.removeLast());
            if (candidate.isBlank()) {
                continue;
            }
            if (fallback.isEmpty()) {
                fallback = candidate;
            }

            String lower = candidate.toLowerCase(Locale.ROOT);
            if (lower.contains("error")
                    || lower.contains("exception")
                    || lower.contains("traceback")
                    || lower.contains("no module named")
                    || lower.contains("modulenotfounderror")
                    || lower.contains("importerror")
                    || lower.contains("onnxruntime")
                    || lower.contains("address already in use")
                    || lower.contains("failed")) {
                return candidate;
            }
        }
        return fallback;
    }

    private static String normalizeDiagnosticLine(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.replace('\t', ' ').trim().replaceAll("\\s+", " ");
        return shortenDiagnostic(normalized);
    }

    private static String shortenDiagnostic(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() <= 240) {
            return trimmed;
        }
        return trimmed.substring(0, 237) + "...";
    }

    private static String buildStartupFailureHint(Path logPath, String diagnostic) {
        String lower = diagnostic == null ? "" : diagnostic.toLowerCase(Locale.ROOT);
        if (lower.contains("no module named") && lower.contains("libretranslate")) {
            return "Selected Python has no libretranslate module: " + pythonExecutable();
        }
        if (lower.contains("address already in use")) {
            return "LibreTranslate cannot bind to port 5000 because it is already in use.";
        }
        if (lower.contains("onnxruntime")
                || lower.contains("numpy 1.x cannot be run in numpy 2")
                || lower.contains("onnxruntime_pybind11_state")) {
            return "onnxruntime/NumPy environment is incompatible. See startup log: " + logPath.toAbsolutePath();
        }
        if (lower.contains("winerror 1114")) {
            return "Windows failed to initialize a LibreTranslate dependency (WinError 1114). See startup log: "
                    + logPath.toAbsolutePath();
        }
        if (diagnostic != null && !diagnostic.isBlank()) {
            return diagnostic;
        }
        return "Startup log: " + logPath.toAbsolutePath();
    }

    private record PythonProbeResult(
            String candidate,
            String executable,
            String version,
            boolean hasLibreTranslate,
            boolean hasLibreTranslateMain,
            String origin,
            boolean runnable,
            String error
    ) {
        String resolvedCommand() {
            return executable != null && !executable.isBlank() ? executable : candidate;
        }

        String describe() {
            String prefix = candidate == null || candidate.isBlank() || candidate.equalsIgnoreCase(resolvedCommand())
                    ? resolvedCommand()
                    : candidate + " -> " + resolvedCommand();
            if (!runnable) {
                String reason = (error == null || error.isBlank()) ? "unavailable" : error;
                return prefix + " (" + reason + ")";
            }

            StringBuilder sb = new StringBuilder(prefix);
            if (version != null && !version.isBlank()) {
                sb.append(", version=").append(version);
            }
            sb.append(", libretranslate=");
            if (hasLibreTranslateMain) {
                sb.append("main");
            } else if (hasLibreTranslate) {
                sb.append("package-only");
            } else {
                sb.append("missing");
            }
            if (origin != null && !origin.isBlank()) {
                sb.append(", origin=").append(origin);
            }
            return sb.toString();
        }
    }

    private record LibreTranslateCliCandidate(
            String command,
            String sourceTag,
            String description
    ) {
    }

    private record LtLauncherScriptCandidate(
            String command,
            String sourceTag,
            String description
    ) {
    }

    public static boolean isPersistentCacheEnabled() {
        return persistentCacheEnabled;
    }

    public static void setPersistentCacheEnabled(boolean enabled) {
        persistentCacheEnabled = enabled;
    }

    public static boolean isGpuDockerEnabled() {
        return useGpuDocker;
    }

    public static void setGpuDockerEnabled(boolean enabled) {
        useGpuDocker = enabled;
        if (!enabled) {
            gpuDockerStartupFailed = true;
        }
    }

    public static void clearTranslationCache() {
        synchronized (TRANSLATION_CACHE) {
            TRANSLATION_CACHE.clear();
        }
        Path path = getPersistentTranslationCachePath();
        try {
            Files.deleteIfExists(path);
            AppLog.info("[LT] translation cache cleared");
        } catch (Exception ex) {
            AppLog.warn("[LT] failed to delete translation cache file: " + ex.getMessage());
        }
        persistentCacheLoaded = false;
    }

    private static void ensurePersistentCacheLoaded() {
        if (!persistentCacheEnabled) {
            // Keep runtime cache only; avoid loading disk persistence.
            persistentCacheLoaded = true;
            return;
        }

        if (persistentCacheLoaded) {
            return;
        }
        synchronized (CACHE_IO_LOCK) {
            if (persistentCacheLoaded) {
                return;
            }

            Path path = getPersistentTranslationCachePath();
            if (Files.isRegularFile(path)) {
                Properties props = new Properties();
                try (InputStream in = Files.newInputStream(path)) {
                    props.loadFromXML(in);
                    for (String key : props.stringPropertyNames()) {
                        TRANSLATION_CACHE.put(key, props.getProperty(key));
                    }
                    AppLog.info("[LT] persistent translation cache loaded: " + props.size() + " entries");
                } catch (Exception ex) {
                    AppLog.warn("[LT] failed to load persistent translation cache: " + ex.getMessage());
                }
            }
            persistentCacheLoaded = true;
        }
    }

    private static void installCacheShutdownHook() {
        if (shutdownHookInstalled) {
            return;
        }
        synchronized (CACHE_IO_LOCK) {
            if (shutdownHookInstalled) {
                return;
            }
            Runtime.getRuntime().addShutdownHook(new Thread(
                    TranslationService::savePersistentTranslationCache,
                    "lt-cache-shutdown-save"
            ));
            shutdownHookInstalled = true;
        }
    }

    private static void savePersistentTranslationCache() {
        if (!persistentCacheEnabled) {
            AppLog.info("[LT] persistent translation cache saving is disabled");
            return;
        }

        ensurePersistentCacheLoaded();
        synchronized (CACHE_IO_LOCK) {
            try {
                Path path = getPersistentTranslationCachePath();
                Properties props = new Properties();
                synchronized (TRANSLATION_CACHE) {
                    for (Map.Entry<String, String> entry : TRANSLATION_CACHE.entrySet()) {
                        props.setProperty(entry.getKey(), entry.getValue());
                    }
                }
                try (OutputStream out = Files.newOutputStream(path)) {
                    props.storeToXML(out, "Localization Editor translation cache", "UTF-8");
                }
                AppLog.info("[LT] persistent translation cache saved: " + props.size() + " entries");
            } catch (Exception ex) {
                AppLog.warn("[LT] failed to save persistent translation cache: " + ex.getMessage());
            }
        }
    }

    private static void tryRaiseProcessPriority(Process process, String priorityClass) {
        if (process == null) {
            return;
        }
        long pid = process.pid();
        if (pid <= 0) {
            return;
        }
        tryRaiseProcessPriorityByPid(pid, priorityClass, "LT");
    }

    private static void tryRaiseProcessPriorityByPid(long pid, String priorityClass, String label) {
        if (pid <= 0) {
            return;
        }
        String ps = "$p=Get-Process -Id " + pid
                + " -ErrorAction SilentlyContinue; "
                + "if($p){$p.PriorityClass='" + priorityClass + "'}";
        try {
            new ProcessBuilder("powershell", "-NoProfile", "-Command", ps)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            AppLog.info("[LT] process priority requested (" + label + "): " + priorityClass + " (pid=" + pid + ")");
        } catch (Exception ex) {
            AppLog.warn("[LT] failed to raise process priority: " + ex.getMessage());
        }
    }
    static List<String> translateBatch(
            LibreTranslateApi api,
            List<String> texts,
            String source, String target
    ) throws IOException {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("q", texts);               //  <= ARRAY
        body.put("source", source);         // "auto" or ISO code
        body.put("target", target);         // ISO code
        body.put("format", "text");


        TranslateRequest translateRequest = new TranslateRequest(String.join(SEP, texts), source, target);
        Call<TranslateResponse> call = requireApi().translate(translateRequest);
        //   call.timeout().timeout(600, java.util.concurrent.TimeUnit.SECONDS);
        TranslateResponse resp = executeTracked(call);
        if (resp == null || resp.getTranslatedText() == null) {
            throw new IOException("Empty translation response");
        }
        AppLog.info(translateRequest + "->" + resp);


        List<String> translated = Arrays.asList(resp.getTranslatedText().split(SEP));


        if (translated.size() != texts.size()) {

            throw new IllegalStateException(
                    "sent: " + (String.join(SEP, texts)) +
                            "\n received: " + resp.getTranslatedText());
        }
//        AppLog.info(texts.size() + "
        return translated;
//        Response<List<String>> resp = api.translate(body).execute();
//        if (!resp.isSuccessful() || resp.body() == null) {
//            throw new IOException("LT error " + resp.code() + ": " + resp.errorBody());
//        }
//        return resp.body();                 // 
    }

    public static List<String> translateOnceEnRu(List<String> texts) throws IOException {
        long t0 = System.nanoTime();
        // single run, hardcoded EN -> RU
        List<String> out = translateBatch(TranslationService.api, texts, "en", "ru");
        long ms = (System.nanoTime() - t0) / 1_000_000L;
        AppLog.info("EN->RU: batch=" + texts.size() + " took " + ms + " ms");
        return out;
    }

    static <T> List<List<T>> chunks(List<T> src, int size) {
        List<List<T>> out = new ArrayList<>();
        for (int i = 0; i < src.size(); i += size) {
            out.add(src.subList(i, Math.min(i + size, src.size())));
        }
        return out;
    }

    public static Map<String, List<String>> translateAll(
            List<String> texts,
            String sourceIso,
            List<String> targetsIso
    ) throws IOException {
        return translateAll(texts, sourceIso, targetsIso, null, null);
    }


    private static int sumChars(List<String> list) {
        int n = 0;
        if (list == null) return 0;
        for (String s : list) n += (s == null ? 0 : s.length());
        return n;
    }

    public static void saveTranslated(File originalFile, String targetLang, String translatedText) throws IOException {
        File out = FileUtil.resolveTargetFile(originalFile, targetLang);
        AppLog.info("[SAVE] from: " + originalFile.getAbsolutePath());
        AppLog.info("[SAVE] to  : " + out.getAbsolutePath());
        FileUtil.writeUtf8Atomic(out, translatedText);
        AppLog.info("[SAVE] done");
    }

}


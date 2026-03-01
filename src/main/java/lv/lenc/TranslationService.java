// TranslationService.java
package lv.lenc;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.GsonBuilder;
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;
import org.jsoup.parser.Parser;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.io.File;
import java.io.IOException;
import java.text.Normalizer;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.NodeTraversor;
import org.jsoup.select.NodeVisitor;

public final class TranslationService {


    public static final String BASE_URL = "http://127.0.0.1:5000/";
    public static final String SEP = "\n";
    // Теги мы уже не шлём. Дополнительно защищаем «геометрические» символы (шкалы).
    private static final Pattern GEOM_RUN = Pattern.compile("[\\u2580-\\u259F\\u25A0-\\u25FF]+");
    private static final AtomicReference<Call<?>> inFlight = new AtomicReference<>();
//    private static final Retrofit RT = new Retrofit.Builder()
//            .baseUrl(BASE_URL)
//            .client(OK)
//            .addConverterFactory(
//                    GsonConverterFactory.create(new GsonBuilder().setLenient().create())
//            )
//            .build();

    public static final LibreTranslateApi api; // = RT.create(LibreTranslateApi.class);

    static {
        // 1) Настраиваем Dispatcher
        Dispatcher dispatcher = new Dispatcher();
        dispatcher.setMaxRequests(1);
        dispatcher.setMaxRequestsPerHost(1);

        // 2) Собираем OkHttpClient с нужными таймаутами
        OkHttpClient http = new OkHttpClient.Builder()
                .dispatcher(dispatcher)
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(180))
                .writeTimeout(Duration.ofSeconds(120))
                .callTimeout(Duration.ofSeconds(150))
                .retryOnConnectionFailure(true)
                //.protocols(Collections.singletonList(Protocol.HTTP_1_1)) // если нужно
                .addInterceptor(chain -> chain.proceed(
                        chain.request().newBuilder()
                                .header("Accept", "application/json")
                                .build()))
                .build();

        // 3) Создаём Retrofit с этим клиентом
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .client(http)   // <- вот здесь клиент подключается!
                .build();

        // 4) Создаём API
        api = retrofit.create(LibreTranslateApi.class);

    }
    private static void splitTextByGeomAndQueue(
            String text,
            List<String> tokens,
            List<Integer> textPositions,
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
                String clean1 = sanitizeVisible(t1);
                tokens.add(t1); // кладём оригинал; заменим на перевод при сборке
                if (!clean1.isEmpty()) {
                    textPositions.add(tokens.size() - 1);
                    toTranslate.add(clean1);
                }
            }
            // серия «квадратиков» — не переводим, просто сохраняем как отдельный токен
            tokens.add(gm.group());
            tp = gm.end();
        }
        if (tp < text.length()) {
            String t2 = text.substring(tp);
            String clean2 = sanitizeVisible(t2);
            tokens.add(t2);
            if (!clean2.isEmpty()) {
                textPositions.add(tokens.size() - 1);
                toTranslate.add(clean2);
            }
        }
    }
    public static void cancelInFlight() {
        Call<?> c = inFlight.getAndSet(null);
        if (c != null) c.cancel(); // прерывает текущий execute() -> выбросит исключение
    }

    // пример внутри твоего translatePreservingTags/translate...
    private static <T> T executeTracked(Call<T> call) throws Exception {
        inFlight.set(call);
        try {
            return call.execute().body();
        } finally {
            inFlight.compareAndSet(call, null);
        }
    }
    private static final Pattern TAG_LIKE = Pattern.compile("(?s)<\\s*[/]?\\w+[^>]*>");

    private static boolean looksLikeHtml(String s) {
        // отсекаем случаи "2 < 5"
        if (s == null || s.indexOf('<') < 0 || s.indexOf('>') < 0) return false;
        return TAG_LIKE.matcher(s).find();
    }

    // План сборки: либо plain строка, либо HTML-фрагмент с текстовыми узлами
    private static abstract class RebuildPlan {
        abstract void consumeTranslated(ListIterator<String> it);
        abstract String result();
    }
    private static final Pattern EMPTY_SC_PAIR =
            Pattern.compile("(?i)<(s|c)\\b([^>]*)></\\1>");
    private static final Pattern N_PAIR =
            Pattern.compile("(?i)<n\\b([^>]*)></n>");

    private static String normalizeCustomTags(String html) {
        // <s ...></s>  или  <c ...></c>  ->  <s ...> / <c ...>
        html = EMPTY_SC_PAIR.matcher(html).replaceAll("<$1$2>");
        // <n ...></n> -> <n .../>   (на случай если парсер развернул)
        html = N_PAIR.matcher(html).replaceAll("<n$1/>");
        return html;
    }
    // Простой случай – не HTML
    private static class PlainPlan extends RebuildPlan {
        private String translated;
        @Override public void consumeTranslated(ListIterator<String> it) {
            String t = sanitizeVisible(it.next());
            if (t != null) t = t.replace('\u00A0',' ').replaceAll("[\\r\\n]+", " ");
            translated = (t == null ? "" : t);
        }
        @Override public String result() { return translated; }
    }


    private static String sanitizeVisible(String s) {
        if (s == null) return "";

        // 0) раскодировать html-сущности
        s = Parser.unescapeEntities(s, true);

        // 1) нормализовать форму
        s = Normalizer.normalize(s, Normalizer.Form.NFKC);

        // 2) убрать только невидимые/управляющие символы
        //    (НЕ трогаем Block Elements U+2580–259F и Geometric Shapes U+25A0–25FF!)
        s = s.replace('\u00A0', ' ')
                .replaceAll("[\\u0000-\\u001F\\u007F\\u200B-\\u200F\\u2028\\u2029\\u2060\\uFEFF]", "");

        // 3) схлопнуть переводы строк и многократные пробелы
        s = s.replaceAll("[\\r\\n]+", " ").replaceAll("\\s+", " ").trim();

        // 4) поправить пробелы вокруг пунктуации: убрать перед ,:;!? и гарантировать один после
        s = normalizePunctuationSpacing(s);

        return s;
    }

    private static String normalizePunctuationSpacing(String s) {
        // убрать пробелы ПЕРЕД знаками
        s = s.replaceAll("\\s+([,:;!?])", "$1");
        // гарантировать один пробел ПОСЛЕ (если дальше буква/цифра/кавычка)
        s = s.replaceAll("([,:;!?])(?!\\s|$)", "$1 ");
        // убрать пробелы сразу после открывающих скобок/кавычек и перед закрывающими
        s = s.replaceAll("([\\(\\[\\{«])\\s+", "$1")
                .replaceAll("\\s+([\\)\\]\\}»])", "$1");
        return s;
    }
    // --- ДОБАВИТЬ где-нибудь рядом с sanitizeVisible ---
    private static String extractVisibleText(String s) {
        if (s == null || s.isEmpty()) return "";
        // Jsoup парсит фрагмент и возвращает "видимый" текст без тегов,
        // с развёрнутыми сущностями и схлопнутыми пробелами между узлами.
        String text = Jsoup.parseBodyFragment(s).text();
        // Прогоним через вашу очистку: нормализация, невидимые, пробелы.
        return sanitizeVisible(text);
    }

    // Готовим батч к переводу: извлекаем текстовые узлы для HTML, plain – как есть
    private static class PrepResult {
        final List<String> toTranslate;
        final List<RebuildPlan> plans;
        PrepResult(List<String> toTranslate, List<RebuildPlan> plans) {
            this.toTranslate = toTranslate; this.plans = plans;
        }
    }
    // Токен: <...> (любой тег, вкл. самозакрывающиеся). Без «парсинга», просто вырезаем.
    private static final Pattern TAG_TOKEN = Pattern.compile("(?s)<[^>]*?>");

    // План сборки «теги + тексты»: в перевод уходит только текст
    private static class TokenPlan extends RebuildPlan {
        private final List<String> tokens;        // чередуются: текст/тег/текст...
        private final List<Integer> textPositions; // индексы токенов, которые были текстом и ушли в перевод

        TokenPlan(List<String> tokens, List<Integer> textPositions) {
            this.tokens = tokens;
            this.textPositions = textPositions;
        }

        @Override public void consumeTranslated(ListIterator<String> it) {
            for (int pos : textPositions) {
                // получить следующий переведённый фрагмент
                String t = it.next();
                if (t == null) t = "";
                // подправим пробелы/переводы (та же политика, что и PlainPlan)
                t = t.replace('\u00A0',' ').replaceAll("[\\r\\n]+", " ").trim();
                tokens.set(pos, t);
            }
        }

        @Override public String result() {
            // просто склеиваем без форматного pretty-print
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

                // Токенизируем по тегам; внутри текстовых сегментов дополнительно режем по GEOM_RUN.
                List<String> tokens = new ArrayList<>();
                List<Integer> textPositions = new ArrayList<>();
                int pos = 0;

                java.util.regex.Matcher m = TAG_TOKEN.matcher(s);
                while (m.find()) {
                    if (m.start() > pos) {
                        String text = s.substring(pos, m.start());
                        splitTextByGeomAndQueue(text, tokens, textPositions, toTranslate);
                    }
                    // сам тег — отдельный токен, не идёт в перевод
                    tokens.add(s.substring(m.start(), m.end()));
                    pos = m.end();
                }
                // хвост после последнего тега
                if (pos < s.length()) {
                    String text = s.substring(pos);
                    splitTextByGeomAndQueue(text, tokens, textPositions, toTranslate);
                }

                // План сборки: заменим только отмеченные текстовые токены
                plans.add(new TokenPlan(tokens, textPositions));

            } else {
                plainCnt++;
                plans.add(new PlainPlan());
                toTranslate.add(s == null ? "" : sanitizeVisible(s));
            }
        }

        System.out.println("[LT] prepare: inputs=" + inputs.size() +
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
    ) throws Exception {

        if (texts == null) texts = java.util.Collections.emptyList();
        if (targetsIso == null || targetsIso.isEmpty())
            throw new IllegalArgumentException("targetsIso is empty");

        // дедуп
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

            // перевод unique с прогрессом по батчам
            List<String> uniqOut = translatePreservingTagsBatched(
                    api, unique, sourceIso, targetIso, stop,
                    (partFraction, msg) -> {
                        if (progress == null) return;

                        double all = ((langNo - 1) + partFraction) / (double) totalLangs;


                        String line1 = sourceIso + " -> " + targetIso + " (" + langNo + "/" + totalLangs + ")";
                        String line2 = (msg == null ? "" : msg);


                        progress.onProgress(all, line1 + "||" + line2);

                    }
            );

            if (stop != null && stop.getAsBoolean()) return out;

            // раздедуп
            List<String> fullOut = new java.util.ArrayList<>(texts.size());
            for (int k = 0; k < texts.size(); k++) {
                fullOut.add(uniqOut.get(mapToUnique[k]));
            }

            out.put(targetIso, fullOut);
        }

        return out;
    }

    static List<String> extractTranslations(JsonElement body, int expected) throws IOException {
        if (body == null || body.isJsonNull()) throw new IOException("Пустой ответ от сервера");

        List<String> out = new ArrayList<>();

        if (body.isJsonArray()) {                 // [ ... ]
            for (JsonElement el : body.getAsJsonArray()) out.add(parseOneTranslation(el));
            return out;
        }

        if (body.isJsonObject()) {                // { ... }
            JsonObject obj = body.getAsJsonObject();
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

        throw new IOException("Неожиданный формат ответа: " + body);
    }

    static String parseOneTranslation(JsonElement el) throws IOException {
        if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) {
            return el.getAsString();
        }
        if (el.isJsonObject() && el.getAsJsonObject().has("translatedText")) {
            JsonElement t = el.getAsJsonObject().get("translatedText");
            if (t.isJsonPrimitive() && t.getAsJsonPrimitive().isString()) return t.getAsString();
        }
        throw new IOException("Неожиданный элемент перевода: " + el);
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
     * Главный метод: переводим список строк, где HTML-теги сохраняем как есть,
     * а переводим только текстовые узлы внутри.
     */
    private static final int BATCH_MAX_ITEMS = 120;

    private static final int BATCH_MAX_CHARS = 18_000;
    /**
     * Переводит список строк, сохраняя теги (переводим только текстовые узлы).
     * <n/> и любые <c ...> остаются как есть.
     */
    public static List<String> translatePreservingTags(
            LibreTranslateApi api,
            List<String> inputs,
            String source,
            String target
    ) throws IOException {

        long t0 = System.currentTimeMillis();
        System.out.println("[LT] translatePreservingTags start: items=" + inputs.size() +
                " chars=" + sumChars(inputs) + " " + source + "->" + target);

        // подготовка: вытаскиваем только тексты из HTML, plain — как есть
        PrepResult prep = prepareForTranslation(inputs);
        System.out.println("[LT] prepared: toTranslate=" + prep.toTranslate.size() +
                " plans=" + prep.plans.size());

        if (prep.toTranslate.isEmpty()) {
            System.out.println("[LT] nothing to translate, return input as-is");
            return new ArrayList<>(inputs);
        }

        // запрос массивом q (никакого join/split!)
        Map<String, Object> body = new HashMap<>();
        body.put("q", prep.toTranslate);
        body.put("source", source);
        body.put("target", target);
        body.put("format", "text"); // теги мы уже «сохранили», переводим только текст

        var resp = api.translateAny(body).execute();
        int code = (resp != null ? resp.code() : -1);
        if (!resp.isSuccessful() || resp.body() == null) {
            String err = resp.errorBody() != null ? resp.errorBody().string() : "null";
            throw new IOException("[LT] HTTP " + code + ": " + err);
        }

// универсальный разбор
        List<String> tr = extractTranslations(resp.body(), prep.toTranslate.size());
        System.out.println("[LT] got " + tr.size() + " translations for " + prep.toTranslate.size());

        if (tr.size() != prep.toTranslate.size()) {
            System.out.println("[LT] raw: " + resp.body()); // подсказка в лог
            throw new IllegalStateException("[LT] MISMATCH: in=" + prep.toTranslate.size() + " out=" + tr.size());
        }

        List<String> out = rebuildFromTranslations(tr, prep.plans);
        long took = System.currentTimeMillis() - t0;
        System.out.println("[LT] translatePreservingTags done in " + took + "ms");
        return out;
    }

    public static List<String> translatePreservingTagsBatched(
            LibreTranslateApi api,
            List<String> texts,
            String source,
            String target
    ) throws IOException, InterruptedException {

        if (texts == null || texts.isEmpty()) return Collections.emptyList();

        long tAll0 = System.currentTimeMillis();
        System.out.println("[LT] preserveTags.BATCH start: items=" + texts.size() +
                " chars=" + sumChars(texts) + " " + source + "->" + target);

        List<List<String>> parts = chunksByChars(texts, BATCH_MAX_ITEMS, BATCH_MAX_CHARS);
        System.out.println("[LT] preserveTags.batching: parts=" + parts.size());

        List<String> out = new ArrayList<>(texts.size());
        int batchNo = 0;

        for (List<String> part : parts) {
            batchNo++;
            long t0 = System.currentTimeMillis();

            IOException last = null;
            for (int attempt = 1; attempt <= 3; attempt++) {
                try {
                    System.out.println("[LT] preserveTags.batch " + batchNo + "/" + parts.size() +
                            " attempt " + attempt + "/3 items=" + part.size() +
                            " chars=" + sumChars(part));

                    // ⬇⬇ ВАЖНО: вызываем НЕбатчевую версию
                    List<String> translated = translatePreservingTags(api, part, source, target);

                    out.addAll(translated);
                    long took = System.currentTimeMillis() - t0;
                    System.out.println("[LT] preserveTags.batch " + batchNo + " OK in " + took + "ms");
                    break;
                } catch (IOException e) {
                    last = e;
                    long sleep = Math.min(700L * (1L << (attempt - 1)), 5_000L);
                    System.out.println("[LT] preserveTags.batch " + batchNo +
                            " failed (" + e.getClass().getSimpleName() + "): " + e.getMessage() +
                            " -> retry in " + sleep + "ms");
                    Thread.sleep(sleep);
                    if (attempt == 3) throw last;
                }
            }
        }

        long tAll = System.currentTimeMillis() - tAll0;
        System.out.println("[LT] preserveTags.BATCH done in " + tAll + "ms total");
        return out;
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

        long tAll0 = System.currentTimeMillis();
        System.out.println("[LT] preserveTags.BATCH+PROGRESS start: items=" + texts.size() +
                " chars=" + sumChars(texts) + " " + source + "->" + target);

        List<List<String>> parts = chunksByChars(texts, BATCH_MAX_ITEMS, BATCH_MAX_CHARS);
        System.out.println("[LT] preserveTags.batching: parts=" + parts.size());

        List<String> out = new ArrayList<>(texts.size());
        int total = parts.size();
        int batchNo = 0;

        for (List<String> part : parts) {
            if (stop != null && stop.getAsBoolean()) return out;

            batchNo++;
            long t0 = System.currentTimeMillis();

            if (progress != null) {
                double frac = (batchNo - 1) / (double) total;
                progress.onProgress(frac, "batch " + (batchNo - 1) + "/" + total);
            }

            IOException last = null;
            for (int attempt = 1; attempt <= 3; attempt++) {
                if (stop != null && stop.getAsBoolean()) return out;

                try {
                    System.out.println("[LT] preserveTags.batch " + batchNo + "/" + total +
                            " attempt " + attempt + "/3 items=" + part.size() +
                            " chars=" + sumChars(part));

                    // вызываем НЕбатчевую версию
                    List<String> translated = translatePreservingTags(api, part, source, target);
                    out.addAll(translated);

                    long took = System.currentTimeMillis() - t0;
                    System.out.println("[LT] preserveTags.batch " + batchNo + " OK in " + took + "ms");
                    break;
                } catch (IOException e) {
                    last = e;
                    long sleep = Math.min(700L * (1L << (attempt - 1)), 5_000L);
                    System.out.println("[LT] preserveTags.batch " + batchNo +
                            " failed (" + e.getClass().getSimpleName() + "): " + e.getMessage() +
                            " -> retry in " + sleep + "ms");
                    Thread.sleep(sleep);
                    if (attempt == 3) throw last;
                }
            }

            if (progress != null) {
                double frac = batchNo / (double) total;
                progress.onProgress(frac, "batch " + batchNo + "/" + total);
            }
        }

        long tAll = System.currentTimeMillis() - tAll0;
        System.out.println("[LT] preserveTags.BATCH+PROGRESS done in " + tAll + "ms total");
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

    // ===== пример использования вместо translateBatch(...) =====
    public static List<String> translateOnceEnRuPreservingTags(List<String> texts)
            throws IOException, InterruptedException {
        long t0 = System.nanoTime();
        List<String> out = translatePreservingTagsBatched(TranslationService.api, texts, "en", "ru");
        long ms = (System.nanoTime() - t0) / 1_000_000L;
        System.out.println("EN->RU (preserve tags): batch=" + texts.size() + " заняло " + ms + " ms");
        return out;
    }
    // ===== запуск и проверка =====
    public static boolean isLtAlive() {
        try (java.net.Socket s = new java.net.Socket()) {
            s.connect(new java.net.InetSocketAddress("127.0.0.1", 5000), 800);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    public static void waitLtReady(int attempts, long sleepMs) throws InterruptedException {
        for (int i = 0; i < attempts; i++) {
            if (isLtAlive()) return;
            Thread.sleep(sleepMs);
        }
        throw new RuntimeException("LibreTranslate не поднялся на 127.0.0.1:5000");
    }

    public static Process startLtProcess() throws Exception {

        // 1)
        if (isOnPath("docker")) {
            return new ProcessBuilder(
                    "docker", "run", "-d", "--rm", "--name", "libretranslate",
                    "-p", "5000:5000", "libretranslate/libretranslate"
            )
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
        }

        // 2)
        if (isOnPath("libretranslate")) {
            return new ProcessBuilder(
                    "libretranslate", "--host", "0.0.0.0", "--port", "5000"
            )
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
        }

        // 3)
        File localExe = new File("libretranslate.exe");
        if (localExe.exists()) {
            return new ProcessBuilder(
                    localExe.getPath(), "--host", "0.0.0.0", "--port", "5000"
            )
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
        }

        // 4)
        if (isOnPath("python")) {
            return new ProcessBuilder(
                    "python", "-m", "libretranslate",
                    "--host", "0.0.0.0", "--port", "5000"
            )
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
        }

        throw new IllegalStateException(
                "Не найден docker, libretranslate или python в PATH."
        );
    }

    private static boolean isOnPath(String tool) {
        try {
            Process p = new ProcessBuilder("cmd", "/c", "where", tool).start();
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    static List<String> translateBatch(
            LibreTranslateApi api,
            List<String> texts,
            String source, String target
    ) throws IOException {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("q", texts);               // <= МАССИВ
        body.put("source", source);         // "auto" или ISO-код
        body.put("target", target);         // ISO-код
        body.put("format", "text");


        TranslateRequest translateRequest = new TranslateRequest(String.join(SEP, texts), source, target);
        Call<TranslateResponse> call = api.translate(translateRequest);
        //   call.timeout().timeout(600, java.util.concurrent.TimeUnit.SECONDS);
        TranslateResponse resp = call.execute().body();
        System.out.println(translateRequest + "->" + resp);


        List<String> translated = Arrays.asList(resp.getTranslatedText().split(SEP));


        if (translated.size() != texts.size()) {

            throw new IllegalStateException(
                    "отправили: " + (String.join(SEP, texts)) +
                            "\n получили: " + resp.getTranslatedText());
        }
//        System.out.println(texts.size() + "получили " + translated.size());
        return translated;
//        Response<List<String>> resp = api.translate(body).execute();
//        if (!resp.isSuccessful() || resp.body() == null) {
//            throw new IOException("LT error " + resp.code() + ": " + resp.errorBody());
//        }
//        return resp.body();                 // тот же порядок, что и вход
    }

    public static List<String> translateOnceEnRu(List<String> texts) throws IOException {
        long t0 = System.nanoTime();
        // один прогон, жёстко EN -> RU
        List<String> out = translateBatch(TranslationService.api, texts, "en", "ru");
        long ms = (System.nanoTime() - t0) / 1_000_000L;
        System.out.println("EN->RU: batch=" + texts.size() + " заняло " + ms + " ms");
        return out;
    }

    private static int countOccurrences(String text, String delimiter) {
        if (text == null || delimiter == null || delimiter.isEmpty()) return 0;
        int count = 0, from = 0;
        while (true) {
            int idx = text.indexOf(delimiter, from);
            if (idx < 0) break;
            count++;
            from = idx + delimiter.length();
        }
        return count;
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
    ) throws Exception {
        return translateAll(texts, sourceIso, targetsIso, null, null);
    }


    private static int sumChars(List<String> list) {
        int n = 0;
        if (list == null) return 0;
        for (String s : list) n += (s == null ? 0 : s.length());
        return n;
    }

    private static String preview(String s, int max) {
        if (s == null) return "null";
        s = s.replace("\n", "\\n");
        return s.length() <= max ? s : (s.substring(0, max - 1) + "…");
    }

    private static <T> List<T> previewList(List<T> list, int limit) {
        if (list == null) return Collections.emptyList();
        int n = Math.min(list.size(), limit);
        return new ArrayList<>(list.subList(0, n));
    }
    public static void saveTranslated(File originalFile, String targetLang, String translatedText) throws IOException {
        File out = FileUtil.resolveTargetFile(originalFile, targetLang);
        System.out.println("[SAVE] from: " + originalFile.getAbsolutePath());
        System.out.println("[SAVE] to  : " + out.getAbsolutePath());
        FileUtil.writeUtf8Atomic(out, translatedText);
        System.out.println("[SAVE] done");
    }

}

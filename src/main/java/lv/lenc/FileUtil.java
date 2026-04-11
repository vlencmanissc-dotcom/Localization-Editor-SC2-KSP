package lv.lenc;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.Field;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javafx.application.Platform;
import systems.crigges.jmpq3.JMpqEditor;

@SuppressWarnings("all")
public class FileUtil {
    private static final Pattern LOCALE_DIR =
            Pattern.compile("^[A-Za-z]{4}\\.SC2Data$", Pattern.CASE_INSENSITIVE);
    private static final String LOCALIZED_DATA = "LocalizedData";
    private static final String EXT_TXT = ".txt";
    private static final String EXT_SC2MAP = ".sc2map";
    private static final String EXT_SC2MOD = ".sc2mod";
    private static final String EXT_MPQ = ".mpq";
    private static final String LISTFILE_ENTRY = "(listfile)";
    private static final String COMPONENT_LIST_FILE = "ComponentList.SC2Components";
    private static final String COMPONENTS_CLOSE_TAG = "</Components>";
    private static final int MAX_ARCHIVE_OPEN_OPTIONS = 4;
    private static final List<String> ARCHIVE_FALLBACK_FILES = List.of(
            "GameStrings.txt",
            "ObjectStrings.txt",
            "TriggerStrings.txt",
            "GameHotkeys.txt"
    );
    private static final List<String> KNOWN_UI_LANGS = List.of(
            "ruRU", "deDE", "enUS", "esMX", "esES", "frFR", "itIT", "plPL", "ptBR", "koKR", "zhCN", "zhTW"
    );
    private static final Pattern MPQ_LOCALIZED_TXT = Pattern.compile(
            "(?i)^.*?([a-z]{2}[a-z]{2})\\.SC2Data[\\\\/]+LocalizedData[\\\\/]+(.+\\.txt)$"
    );
    private static final Map<String, File> RESOLVED_INPUT_CACHE = new HashMap<>();
    private static final byte[] MPQ_MAGIC = new byte[]{'M', 'P', 'Q', 0x1A};

    private static final class MpqSession implements AutoCloseable {
        private final JMpqEditor editor;
        private final Path tempCopy;

        MpqSession(JMpqEditor editor, Path tempCopy) {
            this.editor = editor;
            this.tempCopy = tempCopy;
        }

        JMpqEditor editor() {
            return editor;
        }

        @Override
        public void close() {
            closeReadOnlyMpqEditor(editor);
            if (tempCopy != null) {
                deleteRecursively(tempCopy.getParent());
            }
        }
    }

    private static final class MpqHeaderInfo {
        private final int headerOffset;
        private final int headerSize;
        private final int archiveSize;
        private final short formatVersion;
        private final short discBlockSize;
        private final int hashPos;
        private final int blockPos;
        private final int hashSize;
        private final int blockSize;
        private final byte[] rawHeaderBytes;

        MpqHeaderInfo(int headerOffset,
                      int headerSize,
                      int archiveSize,
                      short formatVersion,
                      short discBlockSize,
                      int hashPos,
                      int blockPos,
                      int hashSize,
                      int blockSize,
                      byte[] rawHeaderBytes) {
            this.headerOffset = headerOffset;
            this.headerSize = headerSize;
            this.archiveSize = archiveSize;
            this.formatVersion = formatVersion;
            this.discBlockSize = discBlockSize;
            this.hashPos = hashPos;
            this.blockPos = blockPos;
            this.hashSize = hashSize;
            this.blockSize = blockSize;
            this.rawHeaderBytes = rawHeaderBytes;
        }
    }

    public static final class OpenPlan {
        private final List<String> fileOptions;
        private final Map<String, List<String>> mainLanguagesByFile;
        private final String defaultFileOption;
        private final String defaultMainLanguage;

        OpenPlan(List<String> fileOptions,
                 Map<String, List<String>> mainLanguagesByFile,
                 String defaultFileOption,
                 String defaultMainLanguage) {
            this.fileOptions = fileOptions;
            this.mainLanguagesByFile = mainLanguagesByFile;
            this.defaultFileOption = defaultFileOption;
            this.defaultMainLanguage = defaultMainLanguage;
        }

        public List<String> getFileOptions() { return fileOptions; }
        public List<String> getMainLanguages() { return getMainLanguages(defaultFileOption); }
        public List<String> getMainLanguages(String fileOption) {
            List<String> langs = mainLanguagesByFile.get(fileOption);
            return langs != null ? langs : Collections.emptyList();
        }
        public String getDefaultFileOption() { return defaultFileOption; }
        public String getDefaultMainLanguage() { return defaultMainLanguage; }
    }
    public static List<File> listDirsByMask(File fileSelected) {
        if (fileSelected == null) return Collections.emptyList();
        // ALE
        Pattern pattern = Pattern.compile("^[A-Za-z]{4}\\.SC2Data.*", Pattern.CASE_INSENSITIVE);
        try {
          //  System.out.println("Len1: " + fileSelected.getAbsolutePath());

            // Safely go up 3 directory levels
            File parent = fileSelected.getParentFile();
            if (parent != null) parent = parent.getParentFile();
            if (parent != null) parent = parent.getParentFile();

            if (parent != null) {
                File[] files = parent.listFiles();
                if (files != null) {
                    List<File> list = Arrays.asList(files);
                    List<File> filtered = list.stream()
                            .filter(file -> pattern.matcher(file.getName()).matches())
                            .toList();
                    // AppLog.info("[FileUtil] locale dirs: " + filtered);
                    return filtered;
                } else {
                    // AppLog.info("[FileUtil] parent directory inaccessible or empty");
                }
            } else {
                // AppLog.info("[FileUtil] parent directory three levels up does not exist");
            }
        } catch (Exception e2) {
            AppLog.warn("[FileUtil] listDirsByMask failed: " + e2.getMessage());
            AppLog.exception(e2);
        }
        return Collections.emptyList();
    }


    public static Map<String, File> listFilesByMask(File file, String llName) { // deDE.SC2Data
        String name = file.getName();
        String langCode = normalizeLocale(name.length() >= 4 ? name.substring(0, 4) : name);

        File[] children = file.listFiles();
        if (children == null) return Collections.emptyMap();
        List<File> files2 = Arrays.asList(children);
        Map<String, File>  resList = new HashMap<>();

        File locFile =  files2.stream()
                .filter(ffff -> ffff.isDirectory())
                .filter(ffff -> "LocalizedData".equals(ffff.getName()))
                .findFirst().orElse(null);
        if (locFile != null) {
                List<File> filesSmal = Arrays.asList(locFile.listFiles());
            File res = filesSmal.stream()
                        .filter(ppp -> ppp.getName().endsWith(llName))
                    .findFirst().orElse(null);
            if (res != null) {
                resList.put(langCode, res);
            }
        }

        return resList;
    }
    public static boolean hasLocaleDirs(File fileSelected) {
        List<File> dirs = listDirsByMask(fileSelected);
        return dirs != null && !dirs.isEmpty();
    }
    public static void loadSelectedFile(File selected, CustomTableView tableView) {
        if (selected == null) return;
        if (!loadSelectedFile2(selected, tableView)) {
            tableView.loadLanguagesToTable(selected);
        }
    }
//    public static void loadSelectedFile(File selected, CustomTableView tableView) {
//        if (selected == null) return;
//
//        if (FileUtil.hasLocaleDirs(selected)) {
//            Map<String, File> files2 = FileUtil.listDirsByMask(selected).stream() // Get into SC2Map
//                    .map(d -> FileUtil.listFilesByMask(d, selected.getName())) // GET language Folder
//                    .flatMap(m -> m.entrySet().stream()) //
//                    .collect(
//                            java.util.stream.Collectors.toMap(
//                                    Map.Entry::getKey,   // 
//                                    Map.Entry::getValue, //
//                                    (v1, v2) -> v1       //
//                            )
//                    );
//            tableView.loadLanguagesToTable(files2); // [NAME FOLDER]:[FILE] -> File key:value into table
//        } else {
//            tableView.loadLanguagesToTable(selected);
//        }
//    }
public static boolean loadSelectedFile2(File fileSelected, CustomTableView tableView) {
    File resolved = resolveLoadInput(fileSelected);
    if (resolved == null || !resolved.exists() || !resolved.isFile()) return false;
    fileSelected = resolved;

    //
    if (Platform.isFxApplicationThread()) {
        tableView.getItems().clear();
        tableView.refresh();
    } else {
        Platform.runLater(() -> {
            tableView.getItems().clear();
            tableView.refresh();
        });
    }

    // 1) root  .../xxXX.SC2Data
    Optional<File> rootOpt = findLocaleRoot(fileSelected);
    if (rootOpt.isEmpty()) {
        tableView.loadLanguagesToTable(fileSelected);
        return !tableView.getItems().isEmpty();
    }

    File localeRoot   = rootOpt.get();               // .../koKR.SC2Data
    File projectRoot  = localeRoot.getParentFile();  // 

    // 2*.SC2Data / *.sc2data ()
    File[] dirs = projectRoot.listFiles(f ->
            f.isDirectory() && f.getName().matches("(?i)[a-z]{4}\\.sc2data.*")
    );
    if (dirs == null || dirs.length == 0) return false;

    // 3)LocalizedData
    File cur = fileSelected;
    File selectedLocalizedDir = null;
    while (cur != null) {
        if ("LocalizedData".equals(cur.getName())) {
            selectedLocalizedDir = cur;
            break;
        }
        cur = cur.getParentFile();
    }

    // 4) Relation path LocalizedData
    String relPath;
    if (selectedLocalizedDir != null) {
        Path base = selectedLocalizedDir.toPath();
        relPath = base.relativize(fileSelected.toPath()).toString();
    } else {
        // fallback
        relPath = fileSelected.getName();
    }

    // 5) get all language
    Map<String, File> langFiles = new LinkedHashMap<>();
    for (File d : dirs) {
        String langCode = normalizeLocale(d.getName().substring(0, 4));
        File localizedData = new File(d, "LocalizedData");
        if (!localizedData.isDirectory()) continue;

        File langFile = new File(localizedData, relPath);
        langFiles.put(langCode, langFile.isFile() ? langFile : null);
    }

    //  fallback
    if (langFiles.isEmpty()) {
        tableView.loadLanguagesToTable(fileSelected);
        return !tableView.getItems().isEmpty();
    }

    tableView.loadLanguagesToTable(langFiles);
    return !tableView.getItems().isEmpty();
}

    public static boolean isArchiveInput(File selected) {
        return isArchiveLikeInput(selected);
    }

    public static Map<String, File> resolveArchiveLanguageFiles(File archiveFile,
                                                                String preferredRelativePath,
                                                                String preferredMainLanguage) {
        if (!isArchiveLikeInput(archiveFile) || preferredRelativePath == null || preferredRelativePath.isBlank()) {
            return Collections.emptyMap();
        }
        LinkedHashMap<String, File> combined = new LinkedHashMap<>();
        try (MpqSession session = openMpqSession(archiveFile)) {
            combined.putAll(extractArchiveLanguageFiles(session.editor(), preferredRelativePath, preferredMainLanguage));
        } catch (Exception ex) {
            AppLog.warn("[FileUtil] resolveArchiveLanguageFiles failed: " + ex.getMessage());
        }
        try {
            Map<String, File> stormFiles = extractArchiveLanguageFilesWithStorm(archiveFile, preferredRelativePath, preferredMainLanguage);
            for (Map.Entry<String, File> entry : stormFiles.entrySet()) {
                combined.putIfAbsent(entry.getKey(), entry.getValue());
            }
        } catch (Exception ex) {
            AppLog.warn("[FileUtil] resolveArchiveLanguageFiles(storm) failed: " + ex.getMessage());
        }
        return combined;
    }

    public static OpenPlan buildOpenPlan(File selected) {
        if (selected == null || !selected.exists()) return null;

        Map<String, Set<String>> fileToLangs = new LinkedHashMap<>();
        if (selected.isFile() && selected.getName().toLowerCase(Locale.ROOT).endsWith(EXT_TXT)) {
            fileToLangs.put(selected.getName(), new LinkedHashSet<>(KNOWN_UI_LANGS));
        } else if (selected.isDirectory()) {
            Map<String, Set<String>> fromRoot = collectCandidatesFromLocaleProjectRoot(selected);
            if (!fromRoot.isEmpty()) {
                fileToLangs.putAll(fromRoot);
            } else {
                fileToLangs.putAll(collectCandidatesFromContainerDirectory(selected));
            }
        } else if (selected.isFile()) {
            String lower = selected.getName().toLowerCase(Locale.ROOT);
            if (lower.endsWith(EXT_MPQ) || lower.endsWith(EXT_SC2MAP) || lower.endsWith(EXT_SC2MOD)) {
                fileToLangs.putAll(collectCandidatesFromMpq(selected));
            }
        }

        if (fileToLangs.isEmpty() && isArchiveLikeInput(selected)) {
            for (String fallback : ARCHIVE_FALLBACK_FILES) {
                fileToLangs.put(fallback, new LinkedHashSet<>(List.of("enUS")));
            }
        }
        if (fileToLangs.isEmpty()) return null;

        List<String> fileOptions = new ArrayList<>(fileToLangs.keySet());
        fileOptions.sort((a, b) -> Integer.compare(scoreLocalizedTxtPath(b), scoreLocalizedTxtPath(a)));
        if (isArchiveLikeInput(selected)) {
            fileOptions = limitArchiveOpenOptions(fileOptions);
        }
        String defaultFile = fileOptions.get(0);

        LinkedHashMap<String, List<String>> mainLanguagesByFile = new LinkedHashMap<>();
        LinkedHashSet<String> unionLangs = new LinkedHashSet<>();
        for (String f : fileOptions) {
            Set<String> ls = fileToLangs.get(f);
            LinkedHashSet<String> langsForFile = new LinkedHashSet<>();
            if (ls != null) langsForFile.addAll(ls);
            if (langsForFile.isEmpty()) langsForFile.addAll(KNOWN_UI_LANGS);
            List<String> ordered = new ArrayList<>(langsForFile);
            ordered.sort((a, b) -> Integer.compare(langPriority(a), langPriority(b)));
            mainLanguagesByFile.put(f, ordered);
            unionLangs.addAll(ordered);
        }
        if (unionLangs.isEmpty()) unionLangs.addAll(KNOWN_UI_LANGS);
        List<String> defaultLanguages = mainLanguagesByFile.getOrDefault(defaultFile, new ArrayList<>(unionLangs));
        String defaultMain = defaultLanguages.get(0);

        return new OpenPlan(fileOptions, mainLanguagesByFile, defaultFile, defaultMain);
    }

    private static boolean isArchiveLikeInput(File selected) {
        if (selected == null) return false;
        String lower = selected.getName().toLowerCase(Locale.ROOT);
        return lower.endsWith(EXT_MPQ) || lower.endsWith(EXT_SC2MAP) || lower.endsWith(EXT_SC2MOD);
    }

    private static List<String> limitArchiveOpenOptions(List<String> sortedOptions) {
        if (sortedOptions == null || sortedOptions.isEmpty()) return Collections.emptyList();

        List<String> ordered = new ArrayList<>();
        addArchiveOptionByName(ordered, sortedOptions, "gamestrings.txt");
        addArchiveOptionByName(ordered, sortedOptions, "objectstrings.txt");
        addArchiveOptionByName(ordered, sortedOptions, "triggerstrings.txt");
        addArchiveOptionByName(ordered, sortedOptions, "gamehotkeys.txt");

        for (String option : sortedOptions) {
            if (!ordered.contains(option)) {
                ordered.add(option);
            }
            if (ordered.size() >= MAX_ARCHIVE_OPEN_OPTIONS) {
                break;
            }
        }

        if (ordered.size() > MAX_ARCHIVE_OPEN_OPTIONS) {
            return new ArrayList<>(ordered.subList(0, MAX_ARCHIVE_OPEN_OPTIONS));
        }
        return ordered;
    }

    private static void addArchiveOptionByName(List<String> ordered, List<String> sortedOptions, String targetName) {
        for (String option : sortedOptions) {
            String lower = option.toLowerCase(Locale.ROOT);
            if (lower.equals(targetName) || lower.endsWith("/" + targetName)) {
                if (!ordered.contains(option)) {
                    ordered.add(option);
                }
                return;
            }
        }
    }

    public static File resolveLoadInput(File selected) {
        return resolveLoadInput(selected, null, null);
    }

    public static File resolveLoadInput(File selected, String preferredRelativePath) {
        return resolveLoadInput(selected, preferredRelativePath, null);
    }

    public static File resolveLoadInput(File selected, String preferredRelativePath, String preferredMainLanguage) {
        if (selected == null || !selected.exists()) return null;
        String cacheKey = buildCacheKey(selected, preferredRelativePath, preferredMainLanguage);
        File cached = RESOLVED_INPUT_CACHE.get(cacheKey);
        if (cached != null && cached.exists() && cached.isFile()) {
            return cached;
        }

        if (selected.isFile() && selected.getName().toLowerCase(Locale.ROOT).endsWith(EXT_TXT)) {
            return selected;
        }

        if (selected.isFile()) {
            String lower = selected.getName().toLowerCase(Locale.ROOT);
            if (lower.endsWith(EXT_MPQ) || lower.endsWith(EXT_SC2MAP) || lower.endsWith(EXT_SC2MOD)) {
                File fromArchive = resolveTxtFromMpqArchive(selected, preferredRelativePath, preferredMainLanguage);
                if (fromArchive != null) {
                    RESOLVED_INPUT_CACHE.put(cacheKey, fromArchive);
                    return fromArchive;
                }
                AppLog.warn("[FileUtil] Failed to read MPQ archive: " + selected.getAbsolutePath());
                return null;
            }
        }

        if (selected.isDirectory()) {
            // If user selected project root containing xxXX.SC2Data folders,
            // auto-pick a common localization txt so all languages can be loaded immediately.
            File fromLocaleProjectRoot = resolveTxtFromLocaleProjectRoot(selected, preferredRelativePath);
            if (fromLocaleProjectRoot != null) {
                RESOLVED_INPUT_CACHE.put(cacheKey, fromLocaleProjectRoot);
                return fromLocaleProjectRoot;
            }

            // Support selecting container folders such as *.SC2Map / *.SC2Mod.
            File fromContainer = resolveTxtFromContainerDirectory(selected, preferredRelativePath);
            if (fromContainer != null) {
                RESOLVED_INPUT_CACHE.put(cacheKey, fromContainer);
                return fromContainer;
            }

            // Fallback: any txt in selected directory tree.
            File anyTxt = findFirstTxtRecursively(selected);
            if (anyTxt != null) {
                RESOLVED_INPUT_CACHE.put(cacheKey, anyTxt);
                return anyTxt;
            }
        }

        String lower = selected.getName().toLowerCase(Locale.ROOT);
        if (lower.endsWith(EXT_MPQ) || lower.endsWith(EXT_SC2MAP) || lower.endsWith(EXT_SC2MOD)) {
            AppLog.warn("[FileUtil] Archive/container selected: " + selected.getAbsolutePath()
                    + ". Please select extracted folder or a .txt in LocalizedData.");
            return null;
        }

        return selected.isFile() ? selected : null;
    }

    private static File resolveTxtFromContainerDirectory(File containerDir, String preferredRelativePath) {
        if (containerDir == null || !containerDir.isDirectory()) return null;

        if (preferredRelativePath != null && !preferredRelativePath.isBlank()) {
            File preferred = resolvePreferredPathInContainer(containerDir, preferredRelativePath);
            if (preferred != null) return preferred;
        }

        if (LOCALIZED_DATA.equalsIgnoreCase(containerDir.getName())) {
            File fromLocalizedData = findBestTxtInTree(containerDir);
            if (fromLocalizedData != null) return fromLocalizedData;
        }

        if (containerDir.getName().matches("(?i)[a-z]{4}\\.sc2data.*")) {
            File localizedData = new File(containerDir, LOCALIZED_DATA);
            if (localizedData.isDirectory()) {
                File fromLocaleRoot = findBestTxtInTree(localizedData);
                if (fromLocaleRoot != null) return fromLocaleRoot;
            }
        }

        // 1) Prefer GameStrings.txt in any locale LocalizedData folder.
        File gameStrings = findPreferredTxt(containerDir, "GameStrings.txt");
        if (gameStrings != null) return gameStrings;

        // 2) Then any txt inside LocalizedData.
        File localizedAny = findAnyTxtInLocalizedData(containerDir);
        if (localizedAny != null) return localizedAny;

        // 3) Last fallback: any txt in tree.
        return findFirstTxtRecursively(containerDir);
    }

    private static File resolveTxtFromLocaleProjectRoot(File rootDir, String preferredRelativePath) {
        if (rootDir == null || !rootDir.isDirectory()) return null;

        File[] localeDirs = rootDir.listFiles(f ->
                f.isDirectory() && f.getName().matches("(?i)[a-z]{4}\\.sc2data.*"));
        if (localeDirs == null || localeDirs.length == 0) return null;

        // relPath -> language coverage count
        Map<String, Integer> relCoverage = new HashMap<>();
        // relPath -> one physical sample file
        Map<String, File> relSampleFile = new HashMap<>();

        for (File localeDir : localeDirs) {
            File localizedData = new File(localeDir, LOCALIZED_DATA);
            if (!localizedData.isDirectory()) continue;
            collectRelativeTxtPaths(localizedData, localizedData, relCoverage, relSampleFile);
        }

        if (relCoverage.isEmpty()) return null;

        if (preferredRelativePath != null && !preferredRelativePath.isBlank()) {
            String normalizedPreferred = preferredRelativePath.replace('\\', '/');
            File preferred = findRelativePathInLocaleProjectRoot(rootDir, localeDirs, normalizedPreferred);
            if (preferred != null) return preferred;
        }

        String bestRel = null;
        int bestScore = Integer.MIN_VALUE;
        for (Map.Entry<String, Integer> e : relCoverage.entrySet()) {
            String rel = e.getKey();
            int coverage = e.getValue();
            File sample = relSampleFile.get(rel);
            int score = coverage * 1500 + scoreLocalizedTxtPath(rel);
            if (sample != null) {
                score += Math.min(3000, countKeyValueLines(sample, 1200) * 4);
            }
            if (score > bestScore) {
                bestScore = score;
                bestRel = rel;
            }
        }

        if (bestRel == null) return null;

        return findRelativePathInLocaleProjectRoot(rootDir, localeDirs, bestRel);
    }

    private static void collectRelativeTxtPaths(File base, File current,
                                                Map<String, Integer> relCoverage,
                                                Map<String, File> relSampleFile) {
        File[] children = current.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isDirectory()) {
                collectRelativeTxtPaths(base, child, relCoverage, relSampleFile);
                continue;
            }
            if (!child.getName().toLowerCase(Locale.ROOT).endsWith(EXT_TXT)) continue;
            String rel = base.toPath().relativize(child.toPath()).toString().replace('\\', '/');
            Integer oldCount = relCoverage.get(rel);
            relCoverage.put(rel, oldCount == null ? 1 : oldCount + 1);
            relSampleFile.putIfAbsent(rel, child);
        }
    }

    private static File findPreferredTxt(File root, String filename) {
        File[] localeDirs = root.listFiles(f ->
                f.isDirectory() && f.getName().matches("(?i)[a-z]{4}\\.sc2data.*"));
        if (localeDirs == null) return null;

        Arrays.sort(localeDirs, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        for (File localeDir : localeDirs) {
            File localizedData = new File(localeDir, LOCALIZED_DATA);
            if (!localizedData.isDirectory()) continue;
            File candidate = findFileByNameRecursively(localizedData, filename);
            if (candidate != null) return candidate;
        }
        return null;
    }

    private static File findAnyTxtInLocalizedData(File root) {
        File[] localeDirs = root.listFiles(f ->
                f.isDirectory() && f.getName().matches("(?i)[a-z]{4}\\.sc2data.*"));
        if (localeDirs == null) return null;
        Arrays.sort(localeDirs, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        for (File localeDir : localeDirs) {
            File localizedData = new File(localeDir, LOCALIZED_DATA);
            if (!localizedData.isDirectory()) continue;
            File txt = findFirstTxtRecursively(localizedData);
            if (txt != null) return txt;
        }
        return null;
    }

    private static String buildCacheKey(File selected, String preferredRelativePath, String preferredMainLanguage) {
        String pref = (preferredRelativePath == null) ? "" : preferredRelativePath.replace('\\', '/').toLowerCase(Locale.ROOT);
        String mainLang = normalizeLocale(preferredMainLanguage);
        return selected.getAbsolutePath() + "::" + selected.lastModified() + "::" + selected.length() + "::" + pref + "::" + mainLang;
    }

    private static int langPriority(String lang) {
        int idx = KNOWN_UI_LANGS.indexOf(lang);
        return idx >= 0 ? idx : 10_000;
    }

    private static File findRelativePathInLocaleProjectRoot(File rootDir, File[] localeDirs, String relPath) {
        if (relPath == null || relPath.isBlank()) return null;
        String normalized = relPath.replace('\\', '/');
        for (String pref : List.of("enUS", "ruRU")) {
            File p = new File(new File(new File(rootDir, pref + ".SC2Data"), LOCALIZED_DATA), normalized);
            if (p.isFile()) {
                return p;
            }
        }
        if (localeDirs != null) {
            for (File localeDir : localeDirs) {
                File p = new File(new File(localeDir, LOCALIZED_DATA), normalized);
                if (p.isFile()) return p;
            }
        }
        return null;
    }

    private static File resolvePreferredPathInContainer(File containerDir, String preferredRelativePath) {
        String normalized = preferredRelativePath.replace('\\', '/');
        if (LOCALIZED_DATA.equalsIgnoreCase(containerDir.getName())) {
            File p = new File(containerDir, normalized);
            if (p.isFile()) return p;
        }
        if (containerDir.getName().matches("(?i)[a-z]{4}\\.sc2data.*")) {
            File localizedData = new File(containerDir, LOCALIZED_DATA);
            File p = new File(localizedData, normalized);
            if (p.isFile()) return p;
        }
        File[] localeDirs = containerDir.listFiles(f ->
                f.isDirectory() && f.getName().matches("(?i)[a-z]{4}\\.sc2data.*"));
        if (localeDirs != null && localeDirs.length > 0) {
            return findRelativePathInLocaleProjectRoot(containerDir, localeDirs, normalized);
        }
        return null;
    }

    private static Map<String, Set<String>> collectCandidatesFromLocaleProjectRoot(File rootDir) {
        Map<String, Set<String>> out = new LinkedHashMap<>();
        if (rootDir == null || !rootDir.isDirectory()) return out;
        File[] localeDirs = rootDir.listFiles(f ->
                f.isDirectory() && f.getName().matches("(?i)[a-z]{4}\\.sc2data.*"));
        if (localeDirs == null || localeDirs.length == 0) return out;

        for (File localeDir : localeDirs) {
            String lang = normalizeLocale(localeDir.getName().substring(0, 4));
            File localizedData = new File(localeDir, LOCALIZED_DATA);
            collectRelativeTxtPathsForPlan(localizedData, localizedData, lang, out);
        }
        return out;
    }

    private static Map<String, Set<String>> collectCandidatesFromContainerDirectory(File dir) {
        Map<String, Set<String>> out = new LinkedHashMap<>();
        if (dir == null || !dir.isDirectory()) return out;

        if (LOCALIZED_DATA.equalsIgnoreCase(dir.getName())) {
            String lang = Optional.ofNullable(dir.getParentFile())
                    .map(File::getName)
                    .filter(n -> n.matches("(?i)[a-z]{4}\\.sc2data.*"))
                    .map(n -> normalizeLocale(n.substring(0, 4)))
                    .orElse("enUS");
            collectRelativeTxtPathsForPlan(dir, dir, lang, out);
            return out;
        }

        if (dir.getName().matches("(?i)[a-z]{4}\\.sc2data.*")) {
            String lang = normalizeLocale(dir.getName().substring(0, 4));
            File localizedData = new File(dir, LOCALIZED_DATA);
            collectRelativeTxtPathsForPlan(localizedData, localizedData, lang, out);
            return out;
        }

        File[] localeDirs = dir.listFiles(f ->
                f.isDirectory() && f.getName().matches("(?i)[a-z]{4}\\.sc2data.*"));
        if (localeDirs != null && localeDirs.length > 0) {
            for (File localeDir : localeDirs) {
                String lang = normalizeLocale(localeDir.getName().substring(0, 4));
                File localizedData = new File(localeDir, LOCALIZED_DATA);
                collectRelativeTxtPathsForPlan(localizedData, localizedData, lang, out);
            }
            return out;
        }

        // Fallback: list txt names from the container directory.
        List<File> txts = new ArrayList<>();
        collectTxtFiles(dir, txts);
        for (File f : txts) {
            out.computeIfAbsent(f.getName(), k -> new LinkedHashSet<>()).addAll(KNOWN_UI_LANGS);
        }
        return out;
    }

    private static Map<String, Set<String>> collectCandidatesFromMpq(File archiveFile) {
        Map<String, Set<String>> discovered = new LinkedHashMap<>();
        boolean stormAvailable = StormLibBridge.isAvailable();
        if (stormAvailable) {
            try {
                addLocalizedTxtCandidates(discovered, StormLibBridge.readListfileEntries(archiveFile));
            } catch (Exception ex) {
                AppLog.warn("[FileUtil] collectCandidatesFromMpq(storm) failed: " + ex.getMessage());
            }
            // When StormLib is available, avoid JMpq probing here: some archives trigger
            // noisy native popups in JMpq even though StormLib can handle them.
            return discovered;
        }
        try (MpqSession session = openMpqSession(archiveFile)) {
            addLocalizedTxtCandidates(discovered, session.editor().getFileNames());
        } catch (Exception ex) {
            AppLog.warn("[FileUtil] collectCandidatesFromMpq failed: " + ex.getMessage());
        }
        return discovered;
    }

    private static void addLocalizedTxtCandidates(Map<String, Set<String>> discovered, List<String> names) {
        if (discovered == null || names == null) {
            return;
        }
        for (String name : names) {
            if (name == null) continue;
            Matcher m = MPQ_LOCALIZED_TXT.matcher(name.replace('/', '\\'));
            if (!m.matches()) continue;

            String lang = normalizeLocale(m.group(1));
            String rel = m.group(2).replace('\\', '/');
            if (!isLocalizationTxtPath(rel)) continue;

            discovered.computeIfAbsent(rel, k -> new LinkedHashSet<>()).add(lang);
        }
    }

    public static OpenPlan buildFallbackArchivePlan() {
        LinkedHashMap<String, List<String>> langsByFile = new LinkedHashMap<>();
        for (String fallback : ARCHIVE_FALLBACK_FILES) {
            langsByFile.put(fallback, new ArrayList<>(List.of("enUS")));
        }
        List<String> files = new ArrayList<>(langsByFile.keySet());
        return new OpenPlan(files, langsByFile, files.get(0), "enUS");
    }

    private static void collectRelativeTxtPathsForPlan(File base, File current, String lang, Map<String, Set<String>> out) {
        if (base == null || current == null || !current.exists()) return;
        File[] children = current.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isDirectory()) {
                collectRelativeTxtPathsForPlan(base, child, lang, out);
                continue;
            }
            if (!child.getName().toLowerCase(Locale.ROOT).endsWith(EXT_TXT)) continue;
            String rel = base.toPath().relativize(child.toPath()).toString().replace('\\', '/');
            if (!isLocalizationTxtPath(rel)) continue;
            out.computeIfAbsent(rel, k -> new LinkedHashSet<>()).add(lang);
        }
    }

    private static File findFileByNameRecursively(File root, String fileName) {
        if (root == null || !root.exists()) return null;
        if (root.isFile()) {
            return root.getName().equalsIgnoreCase(fileName) ? root : null;
        }
        File[] children = root.listFiles();
        if (children == null) return null;
        Arrays.sort(children, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        for (File child : children) {
            File found = findFileByNameRecursively(child, fileName);
            if (found != null) return found;
        }
        return null;
    }

    private static File findFirstTxtRecursively(File root) {
        if (root == null || !root.exists()) return null;
        if (root.isFile()) {
            return root.getName().toLowerCase(Locale.ROOT).endsWith(EXT_TXT) ? root : null;
        }
        File[] children = root.listFiles();
        if (children == null) return null;
        Arrays.sort(children, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        for (File child : children) {
            File found = findFirstTxtRecursively(child);
            if (found != null) return found;
        }
        return null;
    }

    private static File findBestTxtInTree(File root) {
        List<File> candidates = new ArrayList<>();
        collectTxtFiles(root, candidates);
        if (candidates.isEmpty()) return null;
        candidates.sort(Comparator.comparingInt(FileUtil::scoreTxtCandidate).reversed());
        return candidates.get(0);
    }

    private static void collectTxtFiles(File root, List<File> out) {
        if (root == null || !root.exists()) return;
        if (root.isFile()) {
            if (root.getName().toLowerCase(Locale.ROOT).endsWith(EXT_TXT)) {
                out.add(root);
            }
            return;
        }
        File[] children = root.listFiles();
        if (children == null) return;
        for (File child : children) {
            collectTxtFiles(child, out);
        }
    }

    private static int scoreTxtCandidate(File file) {
        String p = file.getAbsolutePath().replace('\\', '/').toLowerCase(Locale.ROOT);
        int score = 0;
        if (p.contains("/localizeddata/")) score += 800;
        if (p.endsWith("/gamestrings.txt")) score += 1200;
        if (p.endsWith("/button_localization_ksp.txt")) score += 700;
        int kvLines = countKeyValueLines(file, 1200);
        score += Math.min(3000, kvLines * 4);
        score += Math.max(0, 300 - file.getName().length());
        return score;
    }

    private static int countKeyValueLines(File file, int maxLinesToScan) {
        if (file == null || !file.isFile()) return 0;
        int kv = 0;
        int scanned = 0;
        try (BufferedReader br = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = br.readLine()) != null) {
                scanned++;
                if (!line.isEmpty() && line.charAt(0) != '#') {
                    int eq = line.indexOf('=');
                    if (eq > 0 && eq < line.length() - 1) kv++;
                }
                if (scanned >= maxLinesToScan) break;
            }
        } catch (IOException ignored) {
            return 0;
        }
        return kv;
    }

    private static File resolveTxtFromMpqArchive(File archiveFile,
                                                 String preferredRelativePath,
                                                 String preferredMainLanguage) {
        File stormResolved = resolveTxtFromMpqArchiveWithStorm(archiveFile, preferredRelativePath, preferredMainLanguage);
        if (stormResolved != null) {
            return stormResolved;
        }
        if (StormLibBridge.isAvailable()) {
            // Keep UI stable: do not fall through to JMpq on Storm-capable setups because
            // failed JMpq probes may show modal native error dialogs.
            return null;
        }

        try (MpqSession session = openMpqSession(archiveFile)) {
            JMpqEditor mpq = session.editor();
            List<String> names = new ArrayList<>();
            for (Object item : mpq.getFileNames()) {
                if (item != null) names.add(String.valueOf(item));
            }
            if (names.isEmpty()) {
                AppLog.warn("[FileUtil] MPQ has no listfile entries, using full extract fallback: " + archiveFile.getAbsolutePath());
                if (preferredRelativePath != null && !preferredRelativePath.isBlank()) {
                    File preferredFromFull = extractPreferredTxtAfterFullExtract(mpq, preferredRelativePath);
                    if (preferredFromFull != null && preferredFromFull.isFile()) {
                        AppLog.info("[FileUtil] Loaded preferred MPQ file from full-extract fallback: " + preferredRelativePath);
                        return preferredFromFull;
                    }
                    AppLog.warn("[FileUtil] Preferred MPQ entry not found in full-extract fallback: " + preferredRelativePath);
                    return null;
                }
                return extractBestTxtAfterFullExtract(mpq);
            }

            if (preferredRelativePath != null && !preferredRelativePath.isBlank()) {
                String normalizedPreferred = preferredRelativePath.replace('\\', '/');

                File preferred = extractLocalizedRelativePath(mpq, names, normalizedPreferred, preferredMainLanguage);
                if (preferred != null && preferred.isFile()) {
                    AppLog.info("[FileUtil] Loaded preferred MPQ path: " + preferredRelativePath);
                    return preferred;
                }
                File preferredEntry = extractExactEntryPath(mpq, names, normalizedPreferred);
                if (preferredEntry != null && preferredEntry.isFile()) {
                    AppLog.info("[FileUtil] Loaded preferred MPQ entry: " + preferredRelativePath);
                    return preferredEntry;
                }
                File preferredNamed = extractPreferredTxtFromNamedEntries(mpq, names, normalizedPreferred);
                if (preferredNamed != null && preferredNamed.isFile()) {
                    AppLog.info("[FileUtil] Loaded preferred MPQ named entry: " + preferredRelativePath);
                    return preferredNamed;
                }
                File preferredFromFull = extractPreferredTxtAfterFullExtract(mpq, normalizedPreferred);
                if (preferredFromFull != null && preferredFromFull.isFile()) {
                    AppLog.info("[FileUtil] Loaded preferred MPQ from full-extract fallback: " + preferredRelativePath);
                    return preferredFromFull;
                }

                AppLog.warn("[FileUtil] Preferred MPQ entry not found/extractable, trying family fallback: " + preferredRelativePath);
                List<String> familyEntries = filterMpqTxtEntriesByPreferredFamily(names, normalizedPreferred);
                File familyFallback = extractBestTxtFromNamedEntries(mpq, familyEntries);
                if (isUsableLocalizationFile(familyFallback)) {
                    AppLog.info("[FileUtil] Loaded MPQ fallback from same file family: " + familyFallback.getName());
                    return familyFallback;
                }
                AppLog.warn("[FileUtil] Preferred MPQ entry cannot be opened by current library: " + preferredRelativePath);
                return null;
            }
            List<String> relCandidates = listPreferredLocalizedRelativePaths(names);
            for (String relPath : relCandidates) {
                File extracted = extractLocalizedRelativePath(mpq, names, relPath, preferredMainLanguage);
                if (isUsableLocalizationFile(extracted)) {
                    AppLog.info("[FileUtil] Loaded from MPQ localized path: " + relPath);
                    return extracted;
                }
            }

            File extractedNamed = extractBestTxtFromNamedEntries(mpq, names);
            if (isUsableLocalizationFile(extractedNamed)) {
                AppLog.info("[FileUtil] Loaded from MPQ named-entry fallback: " + extractedNamed.getName());
                return extractedNamed;
            }

            File extractedFull = extractBestTxtAfterFullExtract(mpq);
            if (isUsableLocalizationFile(extractedFull)) {
                AppLog.info("[FileUtil] Loaded from MPQ full-extract fallback: " + extractedFull.getAbsolutePath());
                return extractedFull;
            }

            // Return best effort even if key=value detection failed (some custom formats may still be parseable).
            File bestEffort = extractedFull != null ? extractedFull : extractedNamed;
            if (bestEffort != null) {
                return bestEffort;
            }
            return null;

        } catch (Exception ex) {
            AppLog.warn("[FileUtil] MPQ open/extract failed: " + archiveFile.getAbsolutePath() + " :: " + ex.getMessage());
            return null;
        }
    }

    private static File resolveTxtFromMpqArchiveWithStorm(File archiveFile,
                                                          String preferredRelativePath,
                                                          String preferredMainLanguage) {
        if (archiveFile == null || !archiveFile.isFile() || !StormLibBridge.isAvailable()) {
            return null;
        }
        List<String> names;
        try {
            names = StormLibBridge.readListfileEntries(archiveFile);
        } catch (Exception ex) {
            AppLog.warn("[FileUtil] storm listfile read failed while resolving input: " + ex.getMessage());
            return null;
        }
        if (names == null || names.isEmpty()) {
            return null;
        }

        if (preferredRelativePath != null && !preferredRelativePath.isBlank()) {
            String normalizedPreferred = preferredRelativePath.replace('\\', '/');
            Map<String, File> preferredFiles = extractArchiveLanguageFilesWithStorm(
                    archiveFile, names, normalizedPreferred, preferredMainLanguage);
            File preferred = pickPreferredLanguageFile(preferredFiles, preferredMainLanguage);
            if (isUsableLocalizationFile(preferred)) {
                AppLog.info("[FileUtil] Loaded preferred MPQ path via StormLib: " + preferredRelativePath);
                return preferred;
            }
            if (preferred != null) {
                return preferred;
            }
        }

        List<String> relCandidates = listPreferredLocalizedRelativePaths(names);
        if (relCandidates.isEmpty()) {
            relCandidates = new ArrayList<>(ARCHIVE_FALLBACK_FILES);
        }

        File bestEffort = null;
        for (String relPath : relCandidates) {
            Map<String, File> files = extractArchiveLanguageFilesWithStorm(
                    archiveFile, names, relPath, preferredMainLanguage);
            File extracted = pickPreferredLanguageFile(files, preferredMainLanguage);
            if (isUsableLocalizationFile(extracted)) {
                AppLog.info("[FileUtil] Loaded from MPQ via StormLib: " + relPath);
                return extracted;
            }
            if (bestEffort == null && extracted != null && extracted.isFile()) {
                bestEffort = extracted;
            }
        }
        return bestEffort;
    }

    private static List<String> listPreferredLocalizedRelativePaths(List<String> names) {
        Map<String, Integer> relScores = new HashMap<>();
        for (String name : names) {
            Matcher m = MPQ_LOCALIZED_TXT.matcher(name.replace('/', '\\'));
            if (!m.matches()) continue;
            String rel = m.group(2).replace('\\', '/');
            if (!isLocalizationTxtPath(rel)) continue;
            int score = scoreLocalizedTxtPath(rel);
            Integer oldScore = relScores.get(rel);
            relScores.put(rel, oldScore == null ? score : Math.max(oldScore, score));
        }
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(relScores.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        List<String> out = new ArrayList<>(sorted.size());
        for (Map.Entry<String, Integer> e : sorted) out.add(e.getKey());
        return out;
    }

    private static File extractLocalizedRelativePath(JMpqEditor mpq,
                                                     List<String> names,
                                                     String relPath,
                                                     String preferredMainLanguage) {
        Map<String, File> all = extractArchiveLanguageFiles(mpq, names, relPath, preferredMainLanguage);
        if (all.isEmpty()) {
            return null;
        }
        String preferred = normalizeLocale(preferredMainLanguage);
        File preferredFile = all.get(preferred);
        if (preferredFile != null && preferredFile.isFile()) {
            return preferredFile;
        }
        return all.values().iterator().next();
    }

    private static Map<String, File> extractArchiveLanguageFiles(JMpqEditor mpq,
                                                                 String relPath,
                                                                 String preferredMainLanguage) {
        if (mpq == null) {
            return Collections.emptyMap();
        }
        List<String> names = new ArrayList<>();
        for (Object item : mpq.getFileNames()) {
            if (item != null) names.add(String.valueOf(item));
        }
        return extractArchiveLanguageFiles(mpq, names, relPath, preferredMainLanguage);
    }

    private static Map<String, File> extractArchiveLanguageFiles(JMpqEditor mpq,
                                                                 List<String> names,
                                                                 String relPath,
                                                                 String preferredMainLanguage) {
        if (relPath == null || relPath.isBlank()) return Collections.emptyMap();
        Map<String, String> byLangEntry = collectArchiveLanguageEntryNames(names, relPath);
        if (byLangEntry.isEmpty()) return Collections.emptyMap();

        try {
            Path tempRoot = Files.createTempDirectory("le-mpq-load-");
            LinkedHashMap<String, File> extracted = new LinkedHashMap<>();
            List<Map.Entry<String, String>> orderedEntries = new ArrayList<>(byLangEntry.entrySet());
            orderedEntries.sort((a, b) ->
                    Integer.compare(languageExtractPriority(a.getKey(), preferredMainLanguage),
                            languageExtractPriority(b.getKey(), preferredMainLanguage)));

            for (Map.Entry<String, String> e : orderedEntries) {
                String lang = e.getKey();
                String entryName = e.getValue();
                Path out = tempRoot
                        .resolve(lang + ".SC2Data")
                        .resolve(LOCALIZED_DATA)
                        .resolve(relPath);
                try {
                    Files.createDirectories(out.getParent());
                    mpq.extractFile(entryName, out.toFile());
                    if (out.toFile().isFile()) {
                        extracted.put(lang, out.toFile());
                    }
                } catch (Exception ex) {
                    AppLog.warn("[FileUtil] extractLocalizedRelativePath failed for "
                            + relPath + " [" + lang + "]: " + ex.getMessage());
                }
            }
            return extracted;
        } catch (Exception ex) {
            AppLog.warn("[FileUtil] extractLocalizedRelativePath failed for " + relPath + ": " + ex.getMessage());
            return Collections.emptyMap();
        }
    }

    private static Map<String, File> extractArchiveLanguageFilesWithStorm(File archiveFile,
                                                                          String relPath,
                                                                          String preferredMainLanguage) {
        if (archiveFile == null || relPath == null || relPath.isBlank() || !StormLibBridge.isAvailable()) {
            return Collections.emptyMap();
        }

        List<String> names;
        try {
            names = StormLibBridge.readListfileEntries(archiveFile);
        } catch (Exception ex) {
            AppLog.warn("[FileUtil] storm listfile read failed: " + ex.getMessage());
            return Collections.emptyMap();
        }
        return extractArchiveLanguageFilesWithStorm(archiveFile, names, relPath, preferredMainLanguage);
    }

    private static Map<String, File> extractArchiveLanguageFilesWithStorm(File archiveFile,
                                                                          List<String> names,
                                                                          String relPath,
                                                                          String preferredMainLanguage) {
        if (archiveFile == null || relPath == null || relPath.isBlank() || !StormLibBridge.isAvailable()) {
            return Collections.emptyMap();
        }
        if (names == null || names.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, String> byLangEntry = collectArchiveLanguageEntryNames(names, relPath);
        if (byLangEntry.isEmpty()) {
            return Collections.emptyMap();
        }

        try {
            Path tempRoot = Files.createTempDirectory("le-storm-load-");
            LinkedHashMap<String, File> extracted = new LinkedHashMap<>();
            List<Map.Entry<String, String>> orderedEntries = new ArrayList<>(byLangEntry.entrySet());
            orderedEntries.sort((a, b) ->
                    Integer.compare(languageExtractPriority(a.getKey(), preferredMainLanguage),
                            languageExtractPriority(b.getKey(), preferredMainLanguage)));

            for (Map.Entry<String, String> e : orderedEntries) {
                String lang = e.getKey();
                String entryName = e.getValue();
                Path out = tempRoot
                        .resolve(lang + ".SC2Data")
                        .resolve(LOCALIZED_DATA)
                        .resolve(relPath.replace('/', File.separatorChar));
                try {
                    Files.createDirectories(out.getParent());
                    StormLibBridge.extractEntry(archiveFile, entryName, out.toFile());
                    if (out.toFile().isFile()) {
                        extracted.put(lang, out.toFile());
                    }
                } catch (Exception ex) {
                    AppLog.warn("[FileUtil] storm extract failed for " + relPath + " [" + lang + "]: " + ex.getMessage());
                }
            }
            return extracted;
        } catch (Exception ex) {
            AppLog.warn("[FileUtil] extractArchiveLanguageFilesWithStorm failed: " + ex.getMessage());
            return Collections.emptyMap();
        }
    }

    private static File pickPreferredLanguageFile(Map<String, File> filesByLang, String preferredMainLanguage) {
        if (filesByLang == null || filesByLang.isEmpty()) {
            return null;
        }
        String preferred = normalizeLocale(preferredMainLanguage);
        if (!preferred.isBlank()) {
            File preferredFile = filesByLang.get(preferred);
            if (preferredFile != null && preferredFile.isFile()) {
                return preferredFile;
            }
        }
        File english = filesByLang.get("enUS");
        if (english != null && english.isFile()) {
            return english;
        }
        for (File candidate : filesByLang.values()) {
            if (candidate != null && candidate.isFile()) {
                return candidate;
            }
        }
        return null;
    }

    private static Map<String, String> collectArchiveLanguageEntryNames(List<String> names, String relPath) {
        if (relPath == null || relPath.isBlank()) {
            return Collections.emptyMap();
        }
        String normalizedRequested = relPath.replace('\\', '/');
        String requestedBaseName = fileNameOnly(normalizedRequested).toLowerCase(Locale.ROOT);
        boolean requestedIsShortName = normalizedRequested.indexOf('/') < 0;
        LinkedHashMap<String, String> byLangEntry = new LinkedHashMap<>();
        if (names == null) {
            return byLangEntry;
        }

        for (String name : names) {
            Matcher m = MPQ_LOCALIZED_TXT.matcher(name.replace('/', '\\'));
            if (!m.matches()) continue;
            String lang = normalizeLocale(m.group(1));
            String rel = m.group(2).replace('\\', '/');
            if (!isLocalizationTxtPath(rel)) continue;
            String relBaseName = fileNameOnly(rel).toLowerCase(Locale.ROOT);
            boolean sameExact = rel.equalsIgnoreCase(normalizedRequested);
            boolean sameByFileName = requestedIsShortName && relBaseName.equals(requestedBaseName);
            if (sameExact || sameByFileName) {
                byLangEntry.putIfAbsent(lang, name.replace('/', '\\'));
            }
        }
        return byLangEntry;
    }

    private static int languageExtractPriority(String lang, String preferredMainLanguage) {
        String normalizedLang = normalizeLocale(lang);
        String preferred = normalizeLocale(preferredMainLanguage);
        if (!preferred.isBlank() && preferred.equalsIgnoreCase(normalizedLang)) {
            return -1000;
        }
        int known = langPriority(normalizedLang);
        return known >= 0 ? known : 10_000;
    }

    private static int scoreLocalizedTxtPath(String rel) {
        String lower = rel.toLowerCase(Locale.ROOT);
        int score = 0;
        if (lower.endsWith("/gamestrings.txt") || lower.equals("gamestrings.txt")) score += 1000;
        if (lower.endsWith("/objectstrings.txt") || lower.equals("objectstrings.txt")) score += 940;
        if (lower.endsWith("/triggerstrings.txt") || lower.equals("triggerstrings.txt")) score += 920;
        if (lower.endsWith("/gamehotkeys.txt") || lower.equals("gamehotkeys.txt")) score += 900;
        if (lower.endsWith("/button_localization_ksp.txt") || lower.equals("button_localization_ksp.txt")) score += 700;
        if (lower.contains("/localizeddata/")) score += 500;
        score += Math.max(0, 300 - lower.length());
        return score;
    }

    private static boolean isLocalizationTxtPath(String rel) {
        if (rel == null) return false;
        String lower = rel.replace('\\', '/').toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".txt")) return false;

        String fileName = fileNameOnly(lower).toLowerCase(Locale.ROOT);
        for (String known : ARCHIVE_FALLBACK_FILES) {
            if (fileName.equalsIgnoreCase(known)) {
                return true;
            }
        }
        if (fileName.endsWith("strings.txt")) return true;
        if (fileName.endsWith("hotkeys.txt")) return true;
        if (fileName.endsWith("localization_ksp.txt")) return true;
        return false;
    }

    private static String fileNameOnly(String path) {
        if (path == null || path.isBlank()) return "";
        String normalized = path.replace('\\', '/');
        int idx = normalized.lastIndexOf('/');
        return idx >= 0 ? normalized.substring(idx + 1) : normalized;
    }

    private static String normalizeFileKey(String value) {
        if (value == null || value.isBlank()) return "";
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "");
    }

    private static String canonicalArchiveFamilyToken(String value) {
        String normalized = normalizeFileKey(fileNameOnly(value).replace(".txt", ""));
        if (normalized.contains("gamestring")) return "gamestrings";
        if (normalized.contains("objectstring")) return "objectstrings";
        if (normalized.contains("triggerstring")) return "triggerstrings";
        if (normalized.contains("gamehotkey") || normalized.contains("hotkey")) return "gamehotkeys";
        return normalized;
    }

    private static List<String> filterMpqTxtEntriesByPreferredFamily(List<String> names, String preferredPath) {
        if (names == null || names.isEmpty()) return Collections.emptyList();
        String preferredFamily = canonicalArchiveFamilyToken(preferredPath);

        List<String> out = new ArrayList<>();
        for (String name : names) {
            if (name == null) continue;
            String lower = name.replace('\\', '/').toLowerCase(Locale.ROOT);
            if (!lower.endsWith(".txt")) continue;

            if (preferredFamily == null || preferredFamily.isBlank()) {
                out.add(name);
                continue;
            }

            String baseFamily = canonicalArchiveFamilyToken(lower);
            if (!baseFamily.isBlank() && baseFamily.equals(preferredFamily)) {
                out.add(name);
            }
        }
        return out;
    }

    private static File extractBestTxtFromNamedEntries(JMpqEditor mpq, List<String> names) {
        List<String> txtEntries = new ArrayList<>();
        for (String name : names) {
            if (name != null && name.toLowerCase(Locale.ROOT).endsWith(".txt")) {
                txtEntries.add(name);
            }
        }
        if (txtEntries.isEmpty()) return null;

        txtEntries.sort((a, b) -> Integer.compare(scoreLocalizedTxtPath(b), scoreLocalizedTxtPath(a)));
        File bestEffort = null;
        int limit = Math.min(40, txtEntries.size());

        for (int i = 0; i < limit; i++) {
            String entry = txtEntries.get(i);
            try {
                Path tempRoot = Files.createTempDirectory("le-mpq-entry-");
                String safe = entry.replace('\\', '/');
                Path out = tempRoot.resolve("enUS.SC2Data").resolve(LOCALIZED_DATA).resolve(safe);
                Files.createDirectories(out.getParent());
                mpq.extractFile(entry, out.toFile());
                if (bestEffort == null) bestEffort = out.toFile();
                if (isUsableLocalizationFile(out.toFile())) {
                    return out.toFile();
                }
            } catch (Exception ex) {
                AppLog.warn("[FileUtil] extractBestTxtFromNamedEntries entry failed: " + entry + " :: " + ex.getMessage());
            }
        }
        return bestEffort;
    }

    private static File extractPreferredTxtFromNamedEntries(JMpqEditor mpq, List<String> names, String preferredPath) {
        if (preferredPath == null || preferredPath.isBlank() || names == null || names.isEmpty()) {
            return null;
        }

        String normalizedPreferred = preferredPath.replace('\\', '/');
        String preferredLower = normalizedPreferred.toLowerCase(Locale.ROOT);
        String preferredFile = fileNameOnly(normalizedPreferred).toLowerCase(Locale.ROOT);
        String preferredKey = normalizeFileKey(preferredFile.replace(".txt", ""));
        String preferredFamily = canonicalArchiveFamilyToken(preferredFile);

        List<String> matched = new ArrayList<>();
        for (String name : names) {
            if (name == null) continue;
            String normalized = name.replace('\\', '/');
            String lower = normalized.toLowerCase(Locale.ROOT);
            if (!lower.endsWith(".txt")) continue;

            String baseName = fileNameOnly(normalized).toLowerCase(Locale.ROOT);
            String baseKey = normalizeFileKey(baseName.replace(".txt", ""));
            String baseFamily = canonicalArchiveFamilyToken(baseName);
            boolean exactPath = lower.equals(preferredLower);
            boolean endsWithPath = lower.endsWith("/" + preferredLower);
            boolean sameFileName = baseName.equals(preferredFile);
            boolean sameFamily = !preferredFamily.isBlank()
                    && !baseFamily.isBlank()
                    && preferredFamily.equals(baseFamily);
            boolean fuzzyName = !preferredKey.isBlank() && !baseKey.isBlank()
                    && (baseKey.equals(preferredKey) || baseKey.startsWith(preferredKey));
            if (exactPath || endsWithPath || sameFileName || sameFamily || fuzzyName) {
                matched.add(name);
            }
        }

        if (matched.isEmpty()) return null;
        matched.sort((a, b) -> Integer.compare(scoreLocalizedTxtPath(b), scoreLocalizedTxtPath(a)));

        for (String entry : matched) {
            try {
                Path tempRoot = Files.createTempDirectory("le-mpq-pref-name-");
                String safe = entry.replace('\\', '/');
                Path out = tempRoot.resolve("enUS.SC2Data").resolve(LOCALIZED_DATA).resolve(safe);
                Files.createDirectories(out.getParent());
                mpq.extractFile(entry, out.toFile());
                if (out.toFile().isFile()) {
                    return out.toFile();
                }
            } catch (Exception ex) {
                AppLog.warn("[FileUtil] extractPreferredTxtFromNamedEntries failed: " + entry + " :: " + ex.getMessage());
            }
        }
        return null;
    }

    private static File extractExactEntryPath(JMpqEditor mpq, List<String> names, String preferredEntryPath) {
        if (preferredEntryPath == null || preferredEntryPath.isBlank()) return null;
        String normalizedPreferred = preferredEntryPath.replace('\\', '/');
        String foundEntry = null;
        for (String name : names) {
            if (name == null) continue;
            String normalized = name.replace('\\', '/');
            if (normalized.equalsIgnoreCase(normalizedPreferred)) {
                foundEntry = name;
                break;
            }
        }
        if (foundEntry == null) return null;

        try {
            Path tempRoot = Files.createTempDirectory("le-mpq-pref-entry-");
            Path out = tempRoot.resolve("enUS.SC2Data").resolve(LOCALIZED_DATA).resolve(foundEntry.replace('\\', '/'));
            Files.createDirectories(out.getParent());
            mpq.extractFile(foundEntry, out.toFile());
            return out.toFile();
        } catch (Exception ex) {
            AppLog.warn("[FileUtil] extractExactEntryPath failed for " + preferredEntryPath + ": " + ex.getMessage());
            return null;
        }
    }

    private static File extractBestTxtAfterFullExtract(JMpqEditor mpq) {
        try {
            Path tempRoot = Files.createTempDirectory("le-mpq-full-");
            mpq.extractAllFiles(tempRoot.toFile());
            File best = findBestTxtInTree(tempRoot.toFile());
            if (best == null) {
                AppLog.warn("[FileUtil] Full-extract MPQ fallback found no .txt");
            }
            return best;
        } catch (Exception ex) {
            AppLog.warn("[FileUtil] Full-extract MPQ fallback failed: " + ex.getMessage());
            return null;
        }
    }

    private static File extractPreferredTxtAfterFullExtract(JMpqEditor mpq, String preferredPath) {
        if (preferredPath == null || preferredPath.isBlank()) return null;
        try {
            Path tempRoot = Files.createTempDirectory("le-mpq-full-pref-");
            mpq.extractAllFiles(tempRoot.toFile());
            List<File> txts = new ArrayList<>();
            collectTxtFiles(tempRoot.toFile(), txts);
            if (txts.isEmpty()) return null;

            String preferredFileName = fileNameOnly(preferredPath).toLowerCase(Locale.ROOT);
            String preferredKey = normalizeFileKey(preferredFileName.replace(".txt", ""));
            String preferredFamily = canonicalArchiveFamilyToken(preferredFileName);
            txts.sort(Comparator.comparingInt(FileUtil::scoreTxtCandidate).reversed());
            for (File f : txts) {
                if (f == null || !f.isFile()) continue;
                String name = f.getName().toLowerCase(Locale.ROOT);
                String nameKey = normalizeFileKey(name.replace(".txt", ""));
                String nameFamily = canonicalArchiveFamilyToken(name);
                boolean exact = name.equals(preferredFileName);
                boolean sameFamily = !preferredFamily.isBlank()
                        && !nameFamily.isBlank()
                        && preferredFamily.equals(nameFamily);
                boolean fuzzy = !preferredKey.isBlank() && !nameKey.isBlank()
                        && (nameKey.equals(preferredKey) || nameKey.startsWith(preferredKey));
                if (exact || sameFamily || fuzzy) {
                    return f;
                }
            }
            return null;
        } catch (Exception ex) {
            AppLog.warn("[FileUtil] extractPreferredTxtAfterFullExtract failed: " + ex.getMessage());
            return null;
        }
    }

    private static MpqSession openMpqSession(File archiveFile) throws IOException {
        if (archiveFile == null) {
            throw new IOException("archiveFile is null");
        }
        Path tempRoot = Files.createTempDirectory("le-mpq-src-");
        Path tempCopy = tempRoot.resolve(archiveFile.getName());
        try {
            Files.copy(archiveFile.toPath(), tempCopy, StandardCopyOption.REPLACE_EXISTING);
            JMpqEditor mpq = new JMpqEditor(tempCopy.toFile());
            return new MpqSession(mpq, tempCopy);
        } catch (Exception ex) {
            deleteRecursively(tempRoot);
            throw new IOException("Failed to open archive via read-only temp copy: " + archiveFile.getAbsolutePath(), ex);
        }
    }

    private static void closeReadOnlyMpqEditor(JMpqEditor editor) {
        if (editor == null) return;
        try {
            Field fcField = JMpqEditor.class.getDeclaredField("fc");
            fcField.setAccessible(true);
            Object rawChannel = fcField.get(editor);
            if (rawChannel instanceof FileChannel channel && channel.isOpen()) {
                channel.close();
            }
        } catch (Exception ex) {
            AppLog.warn("[FileUtil] Failed to close MPQ editor read-only handle safely: " + ex.getMessage());
        }
    }

    private static void deleteRecursively(Path root) {
        if (root == null) return;
        try {
            ArrayDeque<Path> stack = new ArrayDeque<>();
            stack.push(root);
            ArrayList<Path> ordered = new ArrayList<>();
            while (!stack.isEmpty()) {
                Path p = stack.pop();
                ordered.add(p);
                if (Files.isDirectory(p)) {
                    try (var stream = Files.list(p)) {
                        stream.forEach(stack::push);
                    }
                }
            }
            for (int i = ordered.size() - 1; i >= 0; i--) {
                try {
                    Files.deleteIfExists(ordered.get(i));
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static boolean isUsableLocalizationFile(File file) {
        return file != null && file.isFile() && countKeyValueLines(file, 1500) > 0;
    }

    public static String normalizeLocale(String code) {
        if (code == null) return "";
        code = code.replaceAll("[^A-Za-z]", "");
        if (code.length() == 2) {
            return code.toLowerCase(Locale.ROOT);
        }
        if (code.length() == 4) {
            return code.substring(0,2).toLowerCase(Locale.ROOT)
                    + code.substring(2,4).toUpperCase(Locale.ROOT);
        }
        return code;
    }

    /** true, если файл лежит внутри структуры xxXX.SC2Data/LocalizedData/... */
    public static Optional<File> findLocaleRoot(File f) {
        File cur = f;
        while (cur != null) {
            if (LOCALIZED_DATA.equals(cur.getName())) {
                File root = cur.getParentFile();
                if (root != null && LOCALE_DIR.matcher(root.getName()).matches()) {
                    return Optional.of(root);
                }
            }
            cur = cur.getParentFile();
        }
        return Optional.empty();
    }

    public static String deriveLocalizedRelativePath(File file) {
        if (file == null) return null;
        Optional<File> maybeRoot = findLocaleRoot(file);
        if (maybeRoot.isEmpty()) {
            return file.getName();
        }
        File localizedData = new File(maybeRoot.get(), LOCALIZED_DATA);
        return localizedData.toPath()
                .relativize(file.getAbsoluteFile().toPath())
                .toString()
                .replace('\\', '/');
    }

    /** Вычисляет целевой файл; гарантирует, что исходник не перезаписывается. */
    public static File resolveTargetFile(File originalFile, String targetLocale) throws IOException {
        targetLocale = normalizeLocale(targetLocale);
        Objects.requireNonNull(originalFile, "originalFile is null");

        final Path srcPath = originalFile.getAbsoluteFile().toPath();

        // 1) Multi
        Optional<File> maybeRoot = findLocaleRoot(originalFile);
        if (maybeRoot.isPresent()) {
            File srcRoot = maybeRoot.get();                   //  ruRU.SC2Data
            File projectRoot = srcRoot.getParentFile();       //  ruRU.SC2Data
            if (projectRoot == null) projectRoot = srcRoot.getAbsoluteFile().getParentFile();

            File targetRoot = new File(projectRoot, targetLocale + ".SC2Data");
            File targetLoc  = new File(targetRoot, LOCALIZED_DATA);

            // relative path LocalizedData to file
            File localizedData = new File(srcRoot, LOCALIZED_DATA);
            Path rel = localizedData.toPath().relativize(srcPath);
            File targetFile = new File(targetLoc, rel.toString());

            //
            if (targetFile.getAbsoluteFile().toPath().equals(srcPath)) {
                targetFile = withLocaleSuffixSibling(originalFile, targetLocale);
            }

            //
            Files.createDirectories(targetFile.getParentFile().toPath());
            return targetFile;
        }

        // 2) No SC2Data structure in source path:
        //    create it near the source file so save behavior is consistent.
        File sourceParent = originalFile.getParentFile();
        if (sourceParent == null) {
            // Last-resort fallback for root-level files.
            return withLocaleSuffixSibling(originalFile, targetLocale);
        }

        File targetRoot = new File(sourceParent, targetLocale + ".SC2Data");
        File targetLoc = new File(targetRoot, LOCALIZED_DATA);
        File targetFile = new File(targetLoc, originalFile.getName());
        Files.createDirectories(targetFile.getParentFile().toPath());
        return targetFile;
    }

    private static File withLocaleSuffixSibling(File originalFile, String locale) {
        String name = originalFile.getName();
        int dot = name.lastIndexOf('.');
        String base = (dot >= 0 ? name.substring(0, dot) : name);
        String ext  = (dot >= 0 ? name.substring(dot)      : "");
        return new File(originalFile.getParentFile(), base + "." + locale + ext);
    }

    /** Атомарная запись UTF-8 без BOM с бэкапом при перезаписи. */
    public static void writeUtf8Atomic(File target, String content) throws IOException {
        Path targetPath = target.getAbsoluteFile().toPath();
        Path dir = targetPath.getParent();
        if (dir != null) Files.createDirectories(dir);

        // backUP
        if (Files.exists(targetPath)) {
            Path bak = targetPath.resolveSibling(target.getName() + ".bak");
            try {
                Files.copy(targetPath, bak, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                AppLog.info("[SAVE] backup -> " + bak.toAbsolutePath());
            } catch (Exception ignored) { /* не критично */ }
        }

        // write
        Path tmp = Files.createTempFile(dir, target.getName(), ".tmp");
        Files.write(tmp, content.getBytes(StandardCharsets.UTF_8), StandardOpenOption.TRUNCATE_EXISTING);
        Files.move(tmp, targetPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
    public static boolean saveToTargetLanguage(File openedFile,
                                               File projectRoot,
                                               String targetUiLang,
                                               String fileText) {
        return saveToTargetLanguage(openedFile, projectRoot, null, null, targetUiLang, fileText);
    }

    public static boolean saveToTargetLanguage(File openedFile,
                                               File projectRoot,
                                               File sourceInput,
                                               String archiveRelativePath,
                                               String targetUiLang,
                                               String fileText) {
        return saveToTargetLanguage(openedFile, projectRoot, sourceInput, archiveRelativePath, targetUiLang, List.of(targetUiLang), fileText);
    }

    public static boolean saveToTargetLanguage(File openedFile,
                                               File projectRoot,
                                               File sourceInput,
                                               String archiveRelativePath,
                                               String targetUiLang,
                                               List<String> relatedUiLangs,
                                               String fileText) {
        try {
            if (openedFile == null) return false;
            if (sourceInput != null && isArchiveLikeInput(sourceInput)) {
                String relPath = archiveRelativePath != null && !archiveRelativePath.isBlank()
                        ? archiveRelativePath.replace('\\', '/')
                        : deriveLocalizedRelativePath(openedFile);
                boolean savedToArchive = saveToArchiveTargetLanguage(sourceInput, relPath, targetUiLang, relatedUiLangs, fileText);
                if (!savedToArchive) {
                    return false;
                }
                invalidateResolvedInputCache(sourceInput);
                AppLog.info("[SAVE] OK -> archive " + sourceInput.getAbsolutePath() + " :: " + relPath);
                return true;
            }

            File out = resolveTargetFile(openedFile, targetUiLang);
            writeUtf8Atomic(out, fileText);
            File effectiveProjectRoot = resolveProjectRootForSavedFile(out, projectRoot);
            ensureComponentListHasLocale(effectiveProjectRoot, targetUiLang);
            AppLog.info("[SAVE] OK -> " + out.getAbsolutePath());
            return true;

        } catch (Exception e) {
            AppLog.error("[SAVE] failed: " + e.getMessage());
            AppLog.exception(e);
            return false;
        }
    }

    private static boolean saveToArchiveTargetLanguage(File archiveFile,
                                                       String archiveRelativePath,
                                                       String targetUiLang,
                                                       List<String> relatedUiLangs,
                                                       String fileText) throws IOException {
        if (archiveFile == null || archiveRelativePath == null || archiveRelativePath.isBlank()) {
            return false;
        }

        String normalizedLocale = normalizeLocale(targetUiLang);
        String normalizedRelativePath = archiveRelativePath.replace('\\', '/');
        File archiveDirFile = archiveFile.getAbsoluteFile().getParentFile();
        Path saveBase = archiveDirFile != null && archiveDirFile.isDirectory()
                ? archiveDirFile.toPath()
                : Path.of(System.getProperty("java.io.tmpdir"));
        Path tempRoot = Files.createTempDirectory(saveBase, "le-mpq-save-");
        Path tempArchive = tempRoot.resolve(archiveFile.getName());
        MpqHeaderInfo originalHeader = readMpqHeaderInfo(archiveFile);
        boolean requiresExtendedMpqSupport = requiresExtendedMpqSupport(originalHeader);

        try {
            Files.copy(archiveFile.toPath(), tempArchive, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);

            Path payloadPath = tempRoot
                    .resolve(normalizedLocale + ".SC2Data")
                    .resolve(LOCALIZED_DATA)
                    .resolve(normalizedRelativePath.replace('/', File.separatorChar));
            writeUtf8Atomic(payloadPath.toFile(), fileText);

            String localizedEntry = normalizedLocale + ".SC2Data\\" + LOCALIZED_DATA + "\\" + normalizedRelativePath.replace('/', '\\');
            File componentListTemp;
            File listfileTemp;
            boolean stormAvailable = StormLibBridge.isAvailable();
            if (!stormAvailable && requiresExtendedMpqSupport) {
                throw new IOException("StormLib is required to save this MPQ/SC2 archive safely. "
                        + "The archive uses an extended MPQ header, and the JMpq fallback would break compatibility with external tools.");
            }
            if (stormAvailable) {
                // Avoid forcing JMpq read when StormLib is present: some valid archives
                // may fail to open in JMpq but are fully handled by StormLib.
                componentListTemp = buildUpdatedComponentListTempFile(null, archiveFile, tempRoot, normalizedLocale);
                listfileTemp = buildUpdatedListfileTempFile(
                        null,
                        archiveFile,
                        tempRoot,
                        localizedEntry,
                        buildRelatedLocalizedEntries(normalizedRelativePath, relatedUiLangs)
                );
            } else {
                try (MpqSession session = openMpqSession(archiveFile)) {
                    JMpqEditor editor = session.editor();
                    componentListTemp = buildUpdatedComponentListTempFile(editor, archiveFile, tempRoot, normalizedLocale);
                    listfileTemp = buildUpdatedListfileTempFile(
                            editor,
                            archiveFile,
                            tempRoot,
                            localizedEntry,
                            buildRelatedLocalizedEntries(normalizedRelativePath, relatedUiLangs)
                    );
                }
            }

            if (stormAvailable) {
                LinkedHashMap<String, File> entries = new LinkedHashMap<>();
                entries.put(localizedEntry, payloadPath.toFile());
                if (componentListTemp != null && componentListTemp.isFile()) {
                    entries.put(COMPONENT_LIST_FILE, componentListTemp);
                }
                if (listfileTemp != null && listfileTemp.isFile()) {
                    entries.put(LISTFILE_ENTRY, listfileTemp);
                }
                StormLibBridge.upsertEntries(tempArchive.toFile(), entries);
                validateSavedArchiveWithStorm(tempArchive, localizedEntry, normalizedLocale, normalizedRelativePath, originalHeader);
            } else {
                JMpqEditor editor = null;
                try {
                    editor = new JMpqEditor(tempArchive.toFile());
                    upsertArchiveEntry(editor, localizedEntry, payloadPath.toFile());
                    if (componentListTemp != null && componentListTemp.isFile()) {
                        upsertArchiveEntry(editor, COMPONENT_LIST_FILE, componentListTemp);
                    }
                    if (listfileTemp != null && listfileTemp.isFile()) {
                        upsertArchiveEntry(editor, LISTFILE_ENTRY, listfileTemp);
                    }
                    editor.close();
                    editor = null;
                } finally {
                    if (editor != null) {
                        closeReadOnlyMpqEditor(editor);
                    }
                }
                repairSavedMpqArchive(tempArchive, originalHeader);
                validateSavedArchive(tempArchive, localizedEntry, normalizedLocale, normalizedRelativePath);
            }

            Path backup = archiveFile.toPath().resolveSibling(archiveFile.getName() + ".bak");
            try {
                Files.copy(archiveFile.toPath(), backup, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            } catch (Exception ignored) {
            }

            try {
                Files.move(tempArchive, archiveFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception moveEx) {
                Files.move(tempArchive, archiveFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (Exception ex) {
            throw new IOException("Failed to save localized file back into archive: " + archiveFile.getAbsolutePath(), ex);
        } finally {
            deleteRecursively(tempRoot);
        }
    }

    private static File resolveProjectRootForSavedFile(File targetFile, File projectRoot) {
        if (projectRoot != null && projectRoot.isDirectory()) {
            return projectRoot;
        }
        Optional<File> localeRoot = findLocaleRoot(targetFile);
        return localeRoot.map(File::getParentFile).orElse(null);
    }

    private static void ensureComponentListHasLocale(File projectRoot, String targetUiLang) throws IOException {
        if (projectRoot == null || targetUiLang == null || targetUiLang.isBlank()) {
            return;
        }

        String normalizedLocale = normalizeLocale(targetUiLang);
        File componentList = new File(projectRoot, COMPONENT_LIST_FILE);

        if (!componentList.isFile()) {
            String newContent = ensureComponentListContentHasLocale("", normalizedLocale);
            writeUtf8Atomic(componentList, newContent);
            AppLog.info("[SAVE] ComponentList created -> " + componentList.getAbsolutePath());
            return;
        }

        String content = Files.readString(componentList.toPath(), StandardCharsets.UTF_8);
        String updated = ensureComponentListContentHasLocale(content, normalizedLocale);
        if (updated.equals(content)) {
            return;
        }

        writeUtf8Atomic(componentList, updated);
        AppLog.info("[SAVE] ComponentList locale added -> " + normalizedLocale);
    }

    private static File buildUpdatedComponentListTempFile(JMpqEditor editor,
                                                          File archiveFile,
                                                          Path tempRoot,
                                                          String targetUiLang) throws Exception {
        Path componentFile = tempRoot.resolve(COMPONENT_LIST_FILE);
        String content = "";
        boolean loaded = false;
        boolean componentExistsInArchive = false;

        if (archiveFile != null && StormLibBridge.isAvailable()) {
            try {
                componentExistsInArchive = StormLibBridge.hasFile(archiveFile, COMPONENT_LIST_FILE);
                if (componentExistsInArchive) {
                    StormLibBridge.extractEntry(archiveFile, COMPONENT_LIST_FILE, componentFile.toFile());
                    content = Files.readString(componentFile, StandardCharsets.UTF_8);
                    loaded = true;
                }
            } catch (Exception ex) {
                AppLog.warn("[SAVE] storm ComponentList extract failed: " + ex.getMessage());
            }
        }

        if (!loaded) {
            String existingEntry = findArchiveEntryName(safeArchiveFileNames(editor), COMPONENT_LIST_FILE);
            if (existingEntry != null) {
                editor.extractFile(existingEntry, componentFile.toFile());
                content = Files.readString(componentFile, StandardCharsets.UTF_8);
                loaded = true;
            }
        }

        // Never overwrite an existing component list with synthetic content if we failed to read it.
        if (!loaded && (componentExistsInArchive || (archiveFile != null && StormLibBridge.isAvailable()))) {
            AppLog.warn("[SAVE] skip ComponentList update: unable to read existing ComponentList safely");
            return null;
        }

        String updated = ensureComponentListContentHasLocale(content, targetUiLang);
        writeUtf8Atomic(componentFile.toFile(), updated);
        return componentFile.toFile();
    }

    private static File buildUpdatedListfileTempFile(JMpqEditor editor,
                                                     File archiveFile,
                                                     Path tempRoot,
                                                     String localizedEntry,
                                                     List<String> relatedLocalizedEntries) throws Exception {
        Path listfile = tempRoot.resolve("__storm_listfile.txt");
        LinkedHashSet<String> entries = new LinkedHashSet<>();
        entries.addAll(safeArchiveFileNames(editor));

        boolean loadedFromStorm = false;
        boolean loadedExistingListfile = false;
        boolean listfileExistsInArchive = false;
        if (archiveFile != null && StormLibBridge.isAvailable()) {
            try {
                listfileExistsInArchive = StormLibBridge.hasFile(archiveFile, LISTFILE_ENTRY);
                List<String> stormEntries = StormLibBridge.readListfileEntries(archiveFile);
                if (!stormEntries.isEmpty()) {
                    entries.addAll(stormEntries);
                    loadedExistingListfile = true;
                }
                loadedFromStorm = true;
            } catch (Exception ex) {
                AppLog.warn("[SAVE] storm listfile extract failed: " + ex.getMessage());
            }
        }

        if (!loadedFromStorm) {
            String existingEntry = findArchiveEntryName(entries.stream().toList(), LISTFILE_ENTRY);
            if (existingEntry != null) {
                try {
                    editor.extractFile(existingEntry, listfile.toFile());
                    String content = Files.readString(listfile, StandardCharsets.UTF_8);
                    entries.addAll(parseListfileEntries(content));
                    loadedExistingListfile = true;
                } catch (Exception ex) {
                    AppLog.warn("[SAVE] existing listfile extract failed: " + ex.getMessage());
                }
            }
        }

        // If we could not reconstruct a reliable base listfile, do not overwrite it
        // with a tiny synthetic list that may break external SC2 archive tooling.
        if (entries.isEmpty() && !loadedExistingListfile) {
            AppLog.warn("[SAVE] skip listfile update: no reliable base entries (archive="
                    + (archiveFile != null ? archiveFile.getAbsolutePath() : "n/a")
                    + ", listfileExists=" + listfileExistsInArchive + ")");
            return null;
        }

        entries.add(LISTFILE_ENTRY);
        entries.add(COMPONENT_LIST_FILE);
        entries.add(localizedEntry);
        if (relatedLocalizedEntries != null) {
            entries.addAll(relatedLocalizedEntries);
        }

        StringBuilder sb = new StringBuilder();
        for (String entry : entries) {
            if (entry == null || entry.isBlank()) continue;
            sb.append(entry.replace('/', '\\')).append("\r\n");
        }
        writeUtf8Atomic(listfile.toFile(), sb.toString());
        return listfile.toFile();
    }

    private static List<String> buildRelatedLocalizedEntries(String normalizedRelativePath, List<String> relatedUiLangs) {
        if (normalizedRelativePath == null || normalizedRelativePath.isBlank() || relatedUiLangs == null || relatedUiLangs.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<String> entries = new LinkedHashSet<>();
        for (String uiLang : relatedUiLangs) {
            String normalizedLocale = normalizeLocale(uiLang);
            if (normalizedLocale == null || normalizedLocale.isBlank() || !KNOWN_UI_LANGS.contains(normalizedLocale)) {
                continue;
            }
            entries.add(normalizedLocale + ".SC2Data\\" + LOCALIZED_DATA + "\\" + normalizedRelativePath.replace('/', '\\'));
        }
        return new ArrayList<>(entries);
    }

    private static String ensureComponentListContentHasLocale(String content, String targetUiLang) {
        String normalizedLocale = normalizeLocale(targetUiLang);
        String lineBreak = content != null && content.contains("\r\n") ? "\r\n" : "\n";
        String entryLine = "    <DataComponent Type=\"text\" Locale=\"" + normalizedLocale + "\">GameText</DataComponent>";
        String safeContent = content == null ? "" : content;

        Pattern localePattern = Pattern.compile(
                "(?is)<DataComponent\\b[^>]*Type\\s*=\\s*\"text\"[^>]*Locale\\s*=\\s*\""
                        + Pattern.quote(normalizedLocale)
                        + "\"[^>]*>\\s*GameText\\s*</DataComponent>"
        );
        if (localePattern.matcher(safeContent).find()) {
            return safeContent;
        }

        if (safeContent.isBlank()) {
            return "<?xml version=\"1.0\" encoding=\"utf-8\"?>" + lineBreak
                    + "<Components>" + lineBreak
                    + entryLine + lineBreak
                    + COMPONENTS_CLOSE_TAG + lineBreak;
        }

        int closingIndex = safeContent.lastIndexOf(COMPONENTS_CLOSE_TAG);
        if (closingIndex >= 0) {
            String prefix = safeContent.substring(0, closingIndex).stripTrailing();
            String suffix = safeContent.substring(closingIndex);
            return prefix + lineBreak + entryLine + lineBreak + suffix;
        }

        return safeContent.stripTrailing() + lineBreak + entryLine + lineBreak;
    }

    private static void upsertArchiveEntry(JMpqEditor editor, String entryPath, File payloadFile) throws Exception {
        String normalizedEntry = entryPath.replace('/', '\\');
        String existing = findArchiveEntryName(safeArchiveFileNames(editor), normalizedEntry);
        if (existing != null) {
            editor.deleteFile(existing);
        }
        editor.insertFile(normalizedEntry, payloadFile, false);
    }

    private static List<String> safeArchiveFileNames(JMpqEditor editor) {
        if (editor == null) {
            return Collections.emptyList();
        }
        try {
            List<String> out = new ArrayList<>();
            for (Object item : editor.getFileNames()) {
                if (item == null) continue;
                String value = String.valueOf(item).trim();
                if (!value.isEmpty()) {
                    out.add(value.replace('/', '\\'));
                }
            }
            return out;
        } catch (Exception ex) {
            AppLog.warn("[FileUtil] safeArchiveFileNames failed: " + ex.getMessage());
            return Collections.emptyList();
        }
    }

    private static List<String> parseListfileEntries(String content) {
        if (content == null || content.isBlank()) {
            return Collections.emptyList();
        }
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String line : content.split("[\\r\\n\\u0000]+")) {
            String value = line == null ? "" : line.trim();
            if (value.isEmpty()) continue;
            out.add(value.replace('/', '\\'));
        }
        return new ArrayList<>(out);
    }

    private static String findArchiveEntryName(List<String> names, String expectedEntryPath) {
        if (names == null || expectedEntryPath == null) return null;
        String normalizedExpected = normalizeArchiveEntryPath(expectedEntryPath);
        for (String name : names) {
            if (name == null) continue;
            if (normalizeArchiveEntryPath(name).equals(normalizedExpected)) {
                return name;
            }
        }
        return null;
    }

    private static String normalizeArchiveEntryPath(String path) {
        return path.replace('\\', '/').toLowerCase(Locale.ROOT);
    }

    private static MpqHeaderInfo readMpqHeaderInfo(File archiveFile) {
        if (archiveFile == null || !archiveFile.isFile()) {
            return null;
        }
        try (RandomAccessFile raf = new RandomAccessFile(archiveFile, "r")) {
            long size = raf.length();
            long maxOffset = Math.max(0, size - 32);
            for (long pos = 0; pos <= maxOffset; pos += 512) {
                raf.seek(pos);
                byte[] magic = new byte[4];
                raf.readFully(magic);
                if (!Arrays.equals(magic, MPQ_MAGIC)) {
                    continue;
                }
                int headerSize = readIntLE(raf);
                int archiveSize = readIntLE(raf);
                short formatVersion = readShortLE(raf);
                short discBlockSize = readShortLE(raf);
                int hashPos = readIntLE(raf);
                int blockPos = readIntLE(raf);
                int hashSize = readIntLE(raf);
                int blockSize = readIntLE(raf);
                byte[] rawHeaderBytes = new byte[Math.max(0, headerSize)];
                raf.seek(pos);
                raf.readFully(rawHeaderBytes);
                return new MpqHeaderInfo(
                        (int) pos,
                        headerSize,
                        archiveSize,
                        formatVersion,
                        discBlockSize,
                        hashPos,
                        blockPos,
                        hashSize,
                        blockSize,
                        rawHeaderBytes
                );
            }
        } catch (Exception ex) {
            AppLog.warn("[FileUtil] readMpqHeaderInfo failed: " + ex.getMessage());
        }
        return null;
    }

    private static void repairSavedMpqArchive(Path archivePath, MpqHeaderInfo originalHeader) {
        if (archivePath == null || !Files.isRegularFile(archivePath)) {
            return;
        }

        MpqHeaderInfo savedHeader = readMpqHeaderInfo(archivePath.toFile());
        if (savedHeader == null) {
            return;
        }

        try (RandomAccessFile raf = new RandomAccessFile(archivePath.toFile(), "rw")) {
            long actualSize = raf.length();
            long expectedPhysicalSize = (long) savedHeader.headerOffset + Integer.toUnsignedLong(savedHeader.archiveSize);
            if (actualSize == expectedPhysicalSize + 1L) {
                raf.setLength(expectedPhysicalSize);
                actualSize = expectedPhysicalSize;
                AppLog.info("[SAVE] trimmed MPQ trailing byte -> " + archivePath.getFileName());
            }

            int actualArchiveSize = (int) Math.max(0L, actualSize - savedHeader.headerOffset);
            if (actualArchiveSize != savedHeader.archiveSize) {
                raf.seek(savedHeader.headerOffset + 8L);
                writeIntLE(raf, actualArchiveSize);
                AppLog.info("[SAVE] fixed MPQ archiveSize header -> " + actualArchiveSize);
            }

            if (originalHeader != null
                    && originalHeader.rawHeaderBytes != null
                    && savedHeader.rawHeaderBytes != null
                    && originalHeader.rawHeaderBytes.length == savedHeader.rawHeaderBytes.length) {
                byte[] repairedHeader = Arrays.copyOf(originalHeader.rawHeaderBytes, originalHeader.rawHeaderBytes.length);
                writeIntLE(repairedHeader, 4, savedHeader.headerSize);
                writeIntLE(repairedHeader, 8, actualArchiveSize);
                writeShortLE(repairedHeader, 12, savedHeader.formatVersion);
                writeShortLE(repairedHeader, 14, originalHeader.discBlockSize);
                writeIntLE(repairedHeader, 16, savedHeader.hashPos);
                writeIntLE(repairedHeader, 20, savedHeader.blockPos);
                writeIntLE(repairedHeader, 24, savedHeader.hashSize);
                writeIntLE(repairedHeader, 28, savedHeader.blockSize);
                rewriteExtendedMpqHeader(repairedHeader, actualArchiveSize, savedHeader.hashSize, savedHeader.blockSize);

                raf.seek(savedHeader.headerOffset);
                raf.write(repairedHeader);
                AppLog.info("[SAVE] rebuilt MPQ extended header fields");
            }
            raf.getChannel().force(true);
        } catch (Exception ex) {
            AppLog.warn("[FileUtil] repairSavedMpqArchive failed: " + ex.getMessage());
        }
    }

    private static void validateSavedArchive(Path archivePath,
                                             String localizedEntry,
                                             String normalizedLocale,
                                             String normalizedRelativePath) throws IOException {
        MpqHeaderInfo savedHeader = readMpqHeaderInfo(archivePath.toFile());
        if (savedHeader == null) {
            throw new IOException("Saved archive header cannot be read");
        }

        long actualSize;
        try {
            actualSize = Files.size(archivePath);
        } catch (Exception ex) {
            throw new IOException("Saved archive size cannot be determined", ex);
        }
        long expectedPhysicalSize = (long) savedHeader.headerOffset + Integer.toUnsignedLong(savedHeader.archiveSize);
        if (actualSize != expectedPhysicalSize) {
            throw new IOException("Saved archive size mismatch: actual=" + actualSize + " expected=" + expectedPhysicalSize);
        }

        if (savedHeader.rawHeaderBytes != null && savedHeader.rawHeaderBytes.length >= 80) {
            long archiveSize64 = readLongLE(savedHeader.rawHeaderBytes, 40);
            long hashTableSize64 = readLongLE(savedHeader.rawHeaderBytes, 64);
            long blockTableSize64 = readLongLE(savedHeader.rawHeaderBytes, 72);
            if (archiveSize64 != Integer.toUnsignedLong(savedHeader.archiveSize)) {
                throw new IOException("Saved archive ArchiveSize64 mismatch: " + archiveSize64);
            }
            if (hashTableSize64 != (long) savedHeader.hashSize * 16L) {
                throw new IOException("Saved archive HashTableSize64 mismatch: " + hashTableSize64);
            }
            if (blockTableSize64 != (long) savedHeader.blockSize * 16L) {
                throw new IOException("Saved archive BlockTableSize64 mismatch: " + blockTableSize64);
            }
            if (savedHeader.rawHeaderBytes.length >= 104) {
                if (readLongLE(savedHeader.rawHeaderBytes, 48) != 0L
                        || readLongLE(savedHeader.rawHeaderBytes, 56) != 0L
                        || readLongLE(savedHeader.rawHeaderBytes, 80) != 0L
                        || readLongLE(savedHeader.rawHeaderBytes, 88) != 0L
                        || readLongLE(savedHeader.rawHeaderBytes, 96) != 0L) {
                    throw new IOException("Saved archive still references unsupported extended MPQ tables");
                }
            }
            if (savedHeader.rawHeaderBytes.length >= 108) {
                int rawChunkSize = readIntLE(savedHeader.rawHeaderBytes, 104);
                if (rawChunkSize != 0) {
                    throw new IOException("Saved archive rawChunkSize must be zero but is " + rawChunkSize);
                }
            }
        }

        try (MpqSession session = openMpqSession(archivePath.toFile())) {
            JMpqEditor editor = session.editor();
            String entry = findArchiveEntryName(editor.getFileNames(), localizedEntry);
            if (entry == null) {
                throw new IOException("Saved localized entry is missing: " + localizedEntry);
            }
            String componentEntry = findArchiveEntryName(editor.getFileNames(), COMPONENT_LIST_FILE);
            if (componentEntry == null) {
                throw new IOException("Saved archive has no ComponentList");
            }
            Path verifyRoot = Files.createTempDirectory("le-mpq-verify-");
            try {
                Path out = verifyRoot.resolve(normalizedLocale + ".SC2Data")
                        .resolve(LOCALIZED_DATA)
                        .resolve(normalizedRelativePath.replace('/', File.separatorChar));
                Files.createDirectories(out.getParent());
                editor.extractFile(entry, out.toFile());
                if (!Files.isRegularFile(out) || Files.size(out) == 0) {
                    throw new IOException("Saved localized entry cannot be extracted");
                }
            } finally {
                deleteRecursively(verifyRoot);
            }
        } catch (IOException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IOException("Saved archive validation failed", ex);
        }
    }

    private static void validateSavedArchiveWithStorm(Path archivePath,
                                                      String localizedEntry,
                                                      String normalizedLocale,
                                                      String normalizedRelativePath,
                                                      MpqHeaderInfo originalHeader) throws IOException {
        MpqHeaderInfo savedHeader = readMpqHeaderInfo(archivePath.toFile());
        if (savedHeader == null) {
            throw new IOException("Saved archive header cannot be read");
        }

        long actualSize;
        try {
            actualSize = Files.size(archivePath);
        } catch (Exception ex) {
            throw new IOException("Saved archive size cannot be determined", ex);
        }

        long expectedPhysicalSize = (long) savedHeader.headerOffset + Integer.toUnsignedLong(savedHeader.archiveSize);
        if (actualSize != expectedPhysicalSize) {
            throw new IOException("Saved archive size mismatch: actual=" + actualSize + " expected=" + expectedPhysicalSize);
        }

        if (requiresExtendedMpqSupport(originalHeader)) {
            int expectedVersion = Short.toUnsignedInt(originalHeader.formatVersion);
            int savedVersion = Short.toUnsignedInt(savedHeader.formatVersion);
            if (savedVersion != expectedVersion) {
                throw new IOException("Saved archive formatVersion changed: expected=" + expectedVersion + " actual=" + savedVersion);
            }
            if (savedHeader.headerSize < originalHeader.headerSize) {
                throw new IOException("Saved archive headerSize shrank: expected>=" + originalHeader.headerSize + " actual=" + savedHeader.headerSize);
            }
            if (hasExtendedTableOffsets(originalHeader) && !hasExtendedTableOffsets(savedHeader)) {
                throw new IOException("Saved archive lost extended MPQ table offsets required by the original SC2 archive");
            }
        }

        StormLibBridge.validateArchive(archivePath.toFile(), localizedEntry, COMPONENT_LIST_FILE, LISTFILE_ENTRY);

        Path verifyRoot = Files.createTempDirectory("le-storm-verify-");
        try {
            Path out = verifyRoot.resolve(normalizedLocale + ".SC2Data")
                    .resolve(LOCALIZED_DATA)
                    .resolve(normalizedRelativePath.replace('/', File.separatorChar));
            Files.createDirectories(out.getParent());
            StormLibBridge.extractEntry(archivePath.toFile(), localizedEntry, out.toFile());
            if (!Files.isRegularFile(out) || Files.size(out) == 0) {
                throw new IOException("Saved localized entry cannot be extracted");
            }
        } finally {
            deleteRecursively(verifyRoot);
        }
    }

    private static int readIntLE(RandomAccessFile raf) throws IOException {
        int b0 = raf.readUnsignedByte();
        int b1 = raf.readUnsignedByte();
        int b2 = raf.readUnsignedByte();
        int b3 = raf.readUnsignedByte();
        return (b3 << 24) | (b2 << 16) | (b1 << 8) | b0;
    }

    private static short readShortLE(RandomAccessFile raf) throws IOException {
        int b0 = raf.readUnsignedByte();
        int b1 = raf.readUnsignedByte();
        return (short) ((b1 << 8) | b0);
    }

    private static void writeIntLE(RandomAccessFile raf, int value) throws IOException {
        raf.writeByte(value & 0xFF);
        raf.writeByte((value >>> 8) & 0xFF);
        raf.writeByte((value >>> 16) & 0xFF);
        raf.writeByte((value >>> 24) & 0xFF);
    }

    private static void writeIntLE(byte[] target, int offset, int value) {
        if (target == null || offset < 0 || offset + 4 > target.length) {
            return;
        }
        target[offset] = (byte) (value & 0xFF);
        target[offset + 1] = (byte) ((value >>> 8) & 0xFF);
        target[offset + 2] = (byte) ((value >>> 16) & 0xFF);
        target[offset + 3] = (byte) ((value >>> 24) & 0xFF);
    }

    private static void writeShortLE(byte[] target, int offset, short value) {
        if (target == null || offset < 0 || offset + 2 > target.length) {
            return;
        }
        target[offset] = (byte) (value & 0xFF);
        target[offset + 1] = (byte) ((value >>> 8) & 0xFF);
    }

    private static int readIntLE(byte[] source, int offset) {
        if (source == null || offset < 0 || offset + 4 > source.length) {
            return 0;
        }
        int b0 = source[offset] & 0xFF;
        int b1 = source[offset + 1] & 0xFF;
        int b2 = source[offset + 2] & 0xFF;
        int b3 = source[offset + 3] & 0xFF;
        return (b3 << 24) | (b2 << 16) | (b1 << 8) | b0;
    }

    private static long readLongLE(byte[] source, int offset) {
        long lo = Integer.toUnsignedLong(readIntLE(source, offset));
        long hi = Integer.toUnsignedLong(readIntLE(source, offset + 4));
        return lo | (hi << 32);
    }

    private static void writeLongLE(byte[] target, int offset, long value) {
        if (target == null || offset < 0 || offset + 8 > target.length) {
            return;
        }
        writeIntLE(target, offset, (int) (value & 0xFFFFFFFFL));
        writeIntLE(target, offset + 4, (int) ((value >>> 32) & 0xFFFFFFFFL));
    }

    private static void zeroRange(byte[] target, int startInclusive, int endExclusive) {
        if (target == null || startInclusive >= endExclusive) {
            return;
        }
        int from = Math.max(0, startInclusive);
        int to = Math.min(target.length, endExclusive);
        if (from < to) {
            Arrays.fill(target, from, to, (byte) 0);
        }
    }

    private static void rewriteExtendedMpqHeader(byte[] headerBytes,
                                                 int actualArchiveSize,
                                                 int hashEntryCount,
                                                 int blockEntryCount) {
        if (headerBytes == null || headerBytes.length <= 32) {
            return;
        }

        // jmpq3 rewrites only classic tables and does not preserve SC2 HET/BET/MD5 sections.
        // Rebuild the extended header into a consistent "classic tables only" state so
        // external MPQ tools do not follow stale offsets from the original archive.
        zeroRange(headerBytes, 32, headerBytes.length);

        if (headerBytes.length >= 48) {
            writeLongLE(headerBytes, 40, Integer.toUnsignedLong(actualArchiveSize));
        }
        if (headerBytes.length >= 72) {
            writeLongLE(headerBytes, 64, (long) hashEntryCount * 16L);
        }
        if (headerBytes.length >= 80) {
            writeLongLE(headerBytes, 72, (long) blockEntryCount * 16L);
        }
        if (headerBytes.length >= 108) {
            writeIntLE(headerBytes, 104, 0);
        }
    }

    private static boolean requiresExtendedMpqSupport(MpqHeaderInfo header) {
        if (header == null) {
            return false;
        }
        return header.headerSize > 32 || Short.toUnsignedInt(header.formatVersion) > 0;
    }

    private static boolean hasExtendedTableOffsets(MpqHeaderInfo header) {
        if (header == null || header.rawHeaderBytes == null || header.rawHeaderBytes.length < 104) {
            return false;
        }
        return readLongLE(header.rawHeaderBytes, 48) != 0L
                || readLongLE(header.rawHeaderBytes, 56) != 0L
                || readLongLE(header.rawHeaderBytes, 80) != 0L
                || readLongLE(header.rawHeaderBytes, 88) != 0L
                || readLongLE(header.rawHeaderBytes, 96) != 0L;
    }

    private static void invalidateResolvedInputCache(File selected) {
        if (selected == null) {
            return;
        }
        String prefix = selected.getAbsolutePath() + "::";
        RESOLVED_INPUT_CACHE.keySet().removeIf(key -> key != null && key.startsWith(prefix));
    }

}

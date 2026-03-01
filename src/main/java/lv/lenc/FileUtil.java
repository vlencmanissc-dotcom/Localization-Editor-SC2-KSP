package lv.lenc;

import javafx.application.Platform;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.regex.Pattern;

public class FileUtil {
    private static final Pattern LOCALE_PATTERN =
            Pattern.compile("^[A-Za-z]{4}\\.SC2Data$", Pattern.CASE_INSENSITIVE);

    private static final Pattern LOCALE_DIR =
            Pattern.compile("^[A-Za-z]{4}\\.SC2Data$", Pattern.CASE_INSENSITIVE);
    private static final String LOCALIZED_DATA = "LocalizedData";
    public static List<File> listDirsByMask(File fileSelected) {
        if (fileSelected == null) return Collections.emptyList();
        ;
        // ALE
        Pattern pattern = Pattern.compile("^[A-Za-z]{4}\\.SC2Data.*", Pattern.CASE_INSENSITIVE);
        try {
          //  System.out.println("Len1: " + fileSelected.getAbsolutePath());

            // Безопасно поднимаемся на 3 уровня вверх
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
                    System.out.println(">" + filtered);
                    return filtered;
                } else {
                    System.out.println("В родительской директории нет файлов или она недоступна.");
                }
            } else {
                System.out.println("Родительская директория на 3 уровня выше не существует.");
            }
        } catch (Exception e2) {
            e2.printStackTrace();
        }
        return Collections.emptyList();
    }


    public static Map<String, File> listFilesByMask(File file, String llName) { // deDE.SC2Data
        System.out.println("llName: " + llName);
        String name = file.getName();
        String langCode = normalizeLocale(name.length() >= 4 ? name.substring(0, 4) : name);

        File[] children = file.listFiles();
        if (children == null) return Collections.emptyMap();
        List<File> files2 = Arrays.asList(children);
        System.out.println("langCode: " + langCode);
        Map<String, File>  resList = new HashMap<>();

        Map<String, Map<String, String>> perLang2 = new LinkedHashMap<>();
        File locFile =  files2.stream()
                .filter(ffff -> ffff.isDirectory())
                .filter(ffff -> "LocalizedData".equals(ffff.getName()))
                .findFirst().orElse(null);
        if (locFile != null) {
                List<File> filesSmal = Arrays.asList(locFile.listFiles());
            System.out.println("filesSmal: " + filesSmal.stream().map(o -> o.getName()).toList());
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
//                    .flatMap(m -> m.entrySet().stream()) //Преобразуем Map-ы в поток пар (язык, файл)
//                    .collect(
//                            java.util.stream.Collectors.toMap(
//                                    Map.Entry::getKey,   // язык (ruRU, enUS и т.д.)
//                                    Map.Entry::getValue, // файл File
//                                    (v1, v2) -> v1       // если дубли — берём первый найденный
//                            )
//                    );
//            tableView.loadLanguagesToTable(files2); // [NAME FOLDER]:[FILE] -> File key:value into table
//        } else {
//            tableView.loadLanguagesToTable(selected);
//        }
//    }
public static boolean loadSelectedFile2(File fileSelected, CustomTableView tableView) {
    if (fileSelected == null || !fileSelected.exists()) return false;

    // 0) чистим таблицу (FX thread)
    if (Platform.isFxApplicationThread()) {
        tableView.getItems().clear();
        tableView.refresh();
    } else {
        Platform.runLater(() -> {
            tableView.getItems().clear();
            tableView.refresh();
        });
    }

    // 1) root вида .../xxXX.SC2Data
    Optional<File> rootOpt = findLocaleRoot(fileSelected);
    if (rootOpt.isEmpty()) {
        tableView.loadLanguagesToTable(fileSelected);
        return !tableView.getItems().isEmpty();
    }

    File localeRoot   = rootOpt.get();               // .../koKR.SC2Data
    File projectRoot  = localeRoot.getParentFile();  // где лежат все *.SC2Data

    // 2) все соседние *.SC2Data / *.sc2data (любой регистр)
    File[] dirs = projectRoot.listFiles(f ->
            f.isDirectory() && f.getName().matches("(?i)[a-z]{4}\\.sc2data.*")
    );
    if (dirs == null || dirs.length == 0) return false;

    // 3) найдём папку LocalizedData в пути выбранного файла
    File cur = fileSelected;
    File selectedLocalizedDir = null;
    while (cur != null) {
        if ("LocalizedData".equals(cur.getName())) {
            selectedLocalizedDir = cur;
            break;
        }
        cur = cur.getParentFile();
    }

    // 4) относительный путь внутри LocalizedData (важно для подпапок)
    String relPath;
    if (selectedLocalizedDir != null) {
        Path base = selectedLocalizedDir.toPath();
        relPath = base.relativize(fileSelected.toPath()).toString();
    } else {
        // fallback (если файл вообще не под LocalizedData)
        relPath = fileSelected.getName();
    }

    // 5) собираем ВСЕ языки, даже если файла нет (будет null/пусто)
    Map<String, File> langFiles = new LinkedHashMap<>();
    for (File d : dirs) {
        String langCode = normalizeLocale(d.getName().substring(0, 4));
        File localizedData = new File(d, "LocalizedData");
        if (!localizedData.isDirectory()) continue;

        File langFile = new File(localizedData, relPath);
        langFiles.put(langCode, langFile.isFile() ? langFile : null);
    }

    // если вообще ни одного языка не нашли — fallback на одиночное
    if (langFiles.isEmpty()) {
        tableView.loadLanguagesToTable(fileSelected);
        return !tableView.getItems().isEmpty();
    }

    tableView.loadLanguagesToTable(langFiles);
    return !tableView.getItems().isEmpty();
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

    /** Вычисляет целевой файл; гарантирует, что исходник не перезаписывается. */
    public static File resolveTargetFile(File originalFile, String targetLocale) throws IOException {
        targetLocale = normalizeLocale(targetLocale);
        Objects.requireNonNull(originalFile, "originalFile is null");

        final Path srcPath = originalFile.getAbsoluteFile().toPath();

        // 1) Мульти-локальная структура
        Optional<File> maybeRoot = findLocaleRoot(originalFile);
        if (maybeRoot.isPresent()) {
            File srcRoot = maybeRoot.get();                   // напр. ruRU.SC2Data
            File projectRoot = srcRoot.getParentFile();       // родитель над ruRU.SC2Data
            if (projectRoot == null) projectRoot = srcRoot.getAbsoluteFile().getParentFile();

            File targetRoot = new File(projectRoot, targetLocale + ".SC2Data");
            File targetLoc  = new File(targetRoot, LOCALIZED_DATA);

            // относительный путь от LocalizedData до файла
            File localizedData = new File(srcRoot, LOCALIZED_DATA);
            Path rel = localizedData.toPath().relativize(srcPath);
            File targetFile = new File(targetLoc, rel.toString());

            // если вдруг совпало с исходником — добавим суффикс
            if (targetFile.getAbsoluteFile().toPath().equals(srcPath)) {
                targetFile = withLocaleSuffixSibling(originalFile, targetLocale);
            }

            // создаём директории
            Files.createDirectories(targetFile.getParentFile().toPath());
            return targetFile;
        }

        // 2) Обычная структура — рядом с исходником, добавив .<ll> перед расширением
        return withLocaleSuffixSibling(originalFile, targetLocale);
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

        // Бэкап, если уже есть файл
        if (Files.exists(targetPath)) {
            Path bak = targetPath.resolveSibling(target.getName() + ".bak");
            try {
                Files.copy(targetPath, bak, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                System.out.println("[SAVE] backup -> " + bak.toAbsolutePath());
            } catch (Exception ignored) { /* не критично */ }
        }

        // Пишем во временный и атомарно переносим
        Path tmp = Files.createTempFile(dir, target.getName(), ".tmp");
        Files.write(tmp, content.getBytes(StandardCharsets.UTF_8), StandardOpenOption.TRUNCATE_EXISTING);
        Files.move(tmp, targetPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
    public static boolean saveToTargetLanguage(File openedFile,
                                               File projectRoot,
                                               String targetUiLang,
                                               String fileText) {
        try {
            if (openedFile == null || projectRoot == null) return false;

            File targetDir = new File(projectRoot,
                    targetUiLang + ".SC2Data" + File.separator + "LocalizedData");

            if (!targetDir.exists() && !targetDir.mkdirs()) return false;

            File out = new File(targetDir, openedFile.getName());
            java.nio.file.Files.writeString(out.toPath(), fileText);
            System.out.println("[SAVE] OK -> " + out.getAbsolutePath());
            return true;

        } catch (Exception e) {
            System.err.println("[SAVE] failed: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

}


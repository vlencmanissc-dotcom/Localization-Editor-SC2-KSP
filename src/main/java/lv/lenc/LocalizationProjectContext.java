package lv.lenc;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;

public final class LocalizationProjectContext {

    private File openedFile;   // выбранный пользователем файл (GameStrings.txt)
    private File projectRoot;  // папка где лежат xxXX.SC2Data рядом

    public boolean isReady() {
        return openedFile != null && projectRoot != null;
    }

    public File getOpenedFile() {
        return openedFile;
    }

    public File getProjectRoot() {
        return projectRoot;
    }

    public boolean open(File fileSelected, CustomTableView tableView) {
        if (fileSelected == null || !fileSelected.exists()) return false;

        boolean loaded = FileUtil.loadSelectedFile2(fileSelected, tableView);
        if (!loaded) {
            clear();
            return false;
        }

        openedFile = fileSelected;
        projectRoot = findProjectRootFromFile(fileSelected).orElse(null);

        // Файл загружен — значит open успешен
        return true;
    }

    public void clear() {
        openedFile = null;
        projectRoot = null;
    }


    public boolean saveTarget(CustomTableView tableView, String targetUiLang) {
        if (!isReady()) return false;
        if (targetUiLang == null || targetUiLang.isBlank()) return false;

        String fileText = tableView.buildFileTextForLang(targetUiLang);
        return FileUtil.saveToTargetLanguage(openedFile, projectRoot, targetUiLang, fileText);
    }
    public boolean saveAllTargets(CustomTableView tableView) {
        if (!isReady()) return false;

        // взять языки прямо из колонок таблицы (кроме N и key)
        List<String> langs = tableView.getColumns().stream()
                .map(c -> c.getText())
                .filter(t -> t != null && t.matches("[a-z]{2}[A-Z]{2}")) // ruRU, enUS, zhCN...
                .toList();

        boolean ok = true;
        for (String ui : langs) {
            ok &= saveTarget(tableView, ui);
        }
        return ok;
    }
    // ===== helpers =====
    private Optional<File> findProjectRootFromFile(File file) {
        // ищем вверх по папкам: .../<lang>.SC2Data/LocalizedData/<file>
        // projectRoot = папка, где лежит <lang>.SC2Data
        File p = file.getParentFile(); // LocalizedData
        while (p != null) {
            File name = p.getName() != null ? p : null;
            if (name != null && p.getName().endsWith(".SC2Data")) {
                return Optional.ofNullable(p.getParentFile());
            }
            p = p.getParentFile();
        }
        return Optional.empty();
    }
}

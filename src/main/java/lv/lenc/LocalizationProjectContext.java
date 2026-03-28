package lv.lenc;

import java.io.File;
import java.util.List;
import java.util.Optional;

public final class LocalizationProjectContext {

    private File openedFile;   // file selected by the user (GameStrings.txt)
    private File projectRoot;  // folder containing xxXX.SC2Data directories

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

        // File loaded successfully — open succeeded
        return true;
    }

    public void clear() {
        openedFile = null;
        projectRoot = null;
    }


    public boolean saveTarget(CustomTableView tableView, String targetUiLang) {
        if (openedFile == null) return false;
        if (targetUiLang == null || targetUiLang.isBlank()) return false;

        String fileText = tableView.buildFileTextForLang(targetUiLang);
        return FileUtil.saveToTargetLanguage(openedFile, projectRoot, targetUiLang, fileText);
    }
    public boolean saveAllTargets(CustomTableView tableView) {
        if (openedFile == null) return false;

        // get languages directly from table columns (excluding N and key)
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
    // ===== Helper methods =====
    private Optional<File> findProjectRootFromFile(File file) {
        // search upward: .../<lang>.SC2Data/LocalizedData/<file>
        // projectRoot = folder containing <lang>.SC2Data
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

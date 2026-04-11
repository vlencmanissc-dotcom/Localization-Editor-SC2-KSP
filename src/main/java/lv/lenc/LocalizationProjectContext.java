package lv.lenc;

import java.io.File;
import java.util.List;
import java.util.Optional;

public final class LocalizationProjectContext {

    private File openedFile;   // extracted/loaded file used by the table
    private File projectRoot;  // folder containing xxXX.SC2Data directories
    private File sourceInput;  // original user-selected input (.txt / folder / .SC2Map)
    private String archiveRelativePath; // relative path inside LocalizedData for archive sessions

    public boolean isReady() {
        return openedFile != null;
    }

    public File getOpenedFile() {
        return openedFile;
    }

    public File getProjectRoot() {
        return projectRoot;
    }

    public File getSourceInput() {
        return sourceInput;
    }

    public String getArchiveRelativePath() {
        return archiveRelativePath;
    }

    public boolean open(File fileSelected, CustomTableView tableView) {
        if (fileSelected == null || !fileSelected.exists()) return false;
        File loadTarget = FileUtil.resolveLoadInput(fileSelected);
        if (loadTarget == null || !loadTarget.exists()) return false;
        return open(fileSelected, loadTarget, null, null, tableView);
    }

    public boolean open(File sourceInput,
                        File loadTarget,
                        String archiveRelativePath,
                        String preferredMainLanguage,
                        CustomTableView tableView) {
        if (loadTarget == null || !loadTarget.exists()) return false;

        boolean loaded;
        File effectiveOpenedFile = loadTarget;
        if (sourceInput != null && FileUtil.isArchiveInput(sourceInput) && archiveRelativePath != null && !archiveRelativePath.isBlank()) {
            var archiveLangFiles = FileUtil.resolveArchiveLanguageFiles(sourceInput, archiveRelativePath, preferredMainLanguage);
            if (!archiveLangFiles.isEmpty()) {
                tableView.loadLanguagesToTable(archiveLangFiles);
                loaded = !tableView.getItems().isEmpty();
                String preferred = preferredMainLanguage == null ? "" : preferredMainLanguage.trim();
                if (!preferred.isBlank() && archiveLangFiles.containsKey(preferred)) {
                    effectiveOpenedFile = archiveLangFiles.get(preferred);
                } else {
                    effectiveOpenedFile = archiveLangFiles.values().iterator().next();
                }
            } else {
                loaded = FileUtil.loadSelectedFile2(loadTarget, tableView);
            }
        } else {
            loaded = FileUtil.loadSelectedFile2(loadTarget, tableView);
        }
        if (!loaded) {
            // Fallback parser path for archives/custom txt structures.
            FileUtil.loadSelectedFile(loadTarget, tableView);
            loaded = !tableView.getItems().isEmpty();
        }
        if (!loaded) {
            clear();
            return false;
        }

        openedFile = effectiveOpenedFile;
        this.sourceInput = sourceInput != null ? sourceInput : loadTarget;
        this.archiveRelativePath = archiveRelativePath != null && !archiveRelativePath.isBlank()
                ? archiveRelativePath.replace('\\', '/')
                : FileUtil.deriveLocalizedRelativePath(effectiveOpenedFile);
        projectRoot = findProjectRootFromFile(effectiveOpenedFile).orElse(null);

        return true;
    }

    public void clear() {
        openedFile = null;
        projectRoot = null;
        sourceInput = null;
        archiveRelativePath = null;
    }

    public boolean saveTarget(CustomTableView tableView, String targetUiLang) {
        return saveTarget(tableView, targetUiLang, List.of(targetUiLang));
    }

    private boolean saveTarget(CustomTableView tableView, String targetUiLang, List<String> knownTargetUiLangs) {
        if (openedFile == null) return false;
        if (targetUiLang == null || targetUiLang.isBlank()) return false;

        String fileText = tableView.buildFileTextForLang(targetUiLang);
        return FileUtil.saveToTargetLanguage(
                openedFile,
                projectRoot,
                sourceInput,
                archiveRelativePath,
                targetUiLang,
                knownTargetUiLangs,
                fileText
        );
    }

    public boolean saveAllTargets(CustomTableView tableView) {
        if (openedFile == null) return false;

        List<String> langs = tableView.getColumns().stream()
                .map(c -> c.getText())
                .filter(t -> t != null && t.matches("[a-z]{2}[A-Z]{2}"))
                .toList();

        boolean ok = true;
        for (String ui : langs) {
            ok &= saveTarget(tableView, ui, langs);
        }
        return ok;
    }

    private Optional<File> findProjectRootFromFile(File file) {
        File p = file.getParentFile();
        while (p != null) {
            if (p.getName() != null && p.getName().endsWith(".SC2Data")) {
                return Optional.ofNullable(p.getParentFile());
            }
            p = p.getParentFile();
        }
        return Optional.empty();
    }
}

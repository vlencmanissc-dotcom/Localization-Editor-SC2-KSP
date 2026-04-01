package lv.lenc.cli;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

public final class DiscoveredFiles {

    private final LinkedHashMap<String, File> languageFiles;
    private final String projectRoot;
    private final String relativePath;
    private final boolean sc2Layout;

    public DiscoveredFiles(Map<String, File> languageFiles,
                           String projectRoot,
                           String relativePath,
                           boolean sc2Layout) {
        this.languageFiles = new LinkedHashMap<>(languageFiles);
        this.projectRoot = projectRoot;
        this.relativePath = relativePath;
        this.sc2Layout = sc2Layout;
    }

    public LinkedHashMap<String, File> getLanguageFiles() {
        return new LinkedHashMap<>(languageFiles);
    }

    public String getProjectRoot() {
        return projectRoot;
    }

    public String getRelativePath() {
        return relativePath;
    }

    public boolean isSc2Layout() {
        return sc2Layout;
    }
}

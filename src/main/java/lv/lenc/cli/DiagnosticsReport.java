package lv.lenc.cli;

import java.util.List;
import java.util.Locale;

public final class DiagnosticsReport {

    private final String selectedFile;
    private final String projectRoot;
    private final String relativePath;
    private final boolean sc2Layout;
    private final List<String> languages;
    private final int warningCount;
    private final int errorCount;
    private final List<DiagnosticIssue> issues;

    public DiagnosticsReport(String selectedFile,
                             String projectRoot,
                             String relativePath,
                             boolean sc2Layout,
                             List<String> languages,
                             int warningCount,
                             int errorCount,
                             List<DiagnosticIssue> issues) {
        this.selectedFile = selectedFile;
        this.projectRoot = projectRoot;
        this.relativePath = relativePath;
        this.sc2Layout = sc2Layout;
        this.languages = List.copyOf(languages);
        this.warningCount = warningCount;
        this.errorCount = errorCount;
        this.issues = List.copyOf(issues);
    }

    public String getSelectedFile() {
        return selectedFile;
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

    public List<String> getLanguages() {
        return languages;
    }

    public int getWarningCount() {
        return warningCount;
    }

    public int getErrorCount() {
        return errorCount;
    }

    public List<DiagnosticIssue> getIssues() {
        return issues;
    }

    public boolean hasIssues() {
        return warningCount > 0 || errorCount > 0;
    }

    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        appendJsonField(sb, 1, "selectedFile", selectedFile, true);
        appendJsonField(sb, 1, "projectRoot", projectRoot, true);
        appendJsonField(sb, 1, "relativePath", relativePath, true);
        appendJsonField(sb, 1, "sc2Layout", sc2Layout, true);
        appendJsonArray(sb, 1, "languages", languages, true);
        appendJsonField(sb, 1, "warningCount", warningCount, true);
        appendJsonField(sb, 1, "errorCount", errorCount, true);
        appendIssuesArray(sb, 1, issues);
        sb.append("}\n");
        return sb.toString();
    }

    public String toText() {
        StringBuilder sb = new StringBuilder();
        sb.append("status: ").append(hasIssues() ? "ISSUES" : "OK").append(System.lineSeparator());
        sb.append("selected_file: ").append(selectedFile).append(System.lineSeparator());
        sb.append("layout: ").append(sc2Layout ? "sc2data" : "standalone").append(System.lineSeparator());
        if (projectRoot != null) {
            sb.append("project_root: ").append(projectRoot).append(System.lineSeparator());
        }
        sb.append("relative_path: ").append(relativePath).append(System.lineSeparator());
        sb.append("languages: ").append(String.join(", ", languages)).append(System.lineSeparator());
        sb.append("warnings: ").append(warningCount).append(System.lineSeparator());
        sb.append("errors: ").append(errorCount).append(System.lineSeparator());

        if (!issues.isEmpty()) {
            sb.append(System.lineSeparator()).append("issues:").append(System.lineSeparator());
            for (DiagnosticIssue issue : issues) {
                sb.append("- ")
                        .append(issue.getSeverity().name().toLowerCase(Locale.ROOT))
                        .append(' ')
                        .append(issue.getCode());

                if (issue.getLanguage() != null) {
                    sb.append(" language=").append(issue.getLanguage());
                }
                if (issue.getKey() != null) {
                    sb.append(" key=").append(issue.getKey());
                }
                if (issue.getLine() != null) {
                    sb.append(" line=").append(issue.getLine());
                }

                sb.append(System.lineSeparator())
                        .append("  ")
                        .append(issue.getMessage())
                        .append(System.lineSeparator());
            }
        }

        return sb.toString();
    }

    private static void appendIssuesArray(StringBuilder sb, int indentLevel, List<DiagnosticIssue> issues) {
        indent(sb, indentLevel).append("\"issues\": [");
        if (!issues.isEmpty()) {
            sb.append('\n');
            for (int i = 0; i < issues.size(); i++) {
                DiagnosticIssue issue = issues.get(i);
                indent(sb, indentLevel + 1).append("{\n");
                appendJsonField(sb, indentLevel + 2, "severity", issue.getSeverity().name(), true);
                appendJsonField(sb, indentLevel + 2, "code", issue.getCode(), true);
                appendJsonField(sb, indentLevel + 2, "language", issue.getLanguage(), true);
                appendJsonField(sb, indentLevel + 2, "key", issue.getKey(), true);
                appendJsonField(sb, indentLevel + 2, "line", issue.getLine(), true);
                appendJsonField(sb, indentLevel + 2, "message", issue.getMessage(), false);
                indent(sb, indentLevel + 1).append("}");
                if (i + 1 < issues.size()) {
                    sb.append(',');
                }
                sb.append('\n');
            }
            indent(sb, indentLevel).append("]\n");
        } else {
            sb.append("]\n");
        }
    }

    private static void appendJsonArray(StringBuilder sb, int indentLevel, String name,
                                        List<String> values, boolean trailingComma) {
        indent(sb, indentLevel).append('"').append(escapeJson(name)).append("\": [");
        if (!values.isEmpty()) {
            sb.append('\n');
            for (int i = 0; i < values.size(); i++) {
                indent(sb, indentLevel + 1).append('"').append(escapeJson(values.get(i))).append('"');
                if (i + 1 < values.size()) {
                    sb.append(',');
                }
                sb.append('\n');
            }
            indent(sb, indentLevel).append(']');
        } else {
            sb.append(']');
        }
        if (trailingComma) {
            sb.append(',');
        }
        sb.append('\n');
    }

    private static void appendJsonField(StringBuilder sb, int indentLevel, String name,
                                        Object value, boolean trailingComma) {
        indent(sb, indentLevel).append('"').append(escapeJson(name)).append("\": ");
        if (value == null) {
            sb.append("null");
        } else if (value instanceof Number || value instanceof Boolean) {
            sb.append(value);
        } else {
            sb.append('"').append(escapeJson(String.valueOf(value))).append('"');
        }
        if (trailingComma) {
            sb.append(',');
        }
        sb.append('\n');
    }

    private static StringBuilder indent(StringBuilder sb, int level) {
        for (int i = 0; i < level; i++) {
            sb.append("  ");
        }
        return sb;
    }

    private static String escapeJson(String value) {
        StringBuilder escaped = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '\\':
                    escaped.append("\\\\");
                    break;
                case '"':
                    escaped.append("\\\"");
                    break;
                case '\n':
                    escaped.append("\\n");
                    break;
                case '\r':
                    escaped.append("\\r");
                    break;
                case '\t':
                    escaped.append("\\t");
                    break;
                default:
                    if (ch < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) ch));
                    } else {
                        escaped.append(ch);
                    }
                    break;
            }
        }
        return escaped.toString();
    }
}

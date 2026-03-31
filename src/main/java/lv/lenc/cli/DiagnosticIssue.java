package lv.lenc.cli;

import java.util.Objects;

public final class DiagnosticIssue {

    private final Severity severity;
    private final String code;
    private final String language;
    private final String key;
    private final Integer line;
    private final String message;

    public DiagnosticIssue(Severity severity,
                           String code,
                           String language,
                           String key,
                           Integer line,
                           String message) {
        this.severity = Objects.requireNonNull(severity, "severity");
        this.code = Objects.requireNonNull(code, "code");
        this.language = language;
        this.key = key;
        this.line = line;
        this.message = Objects.requireNonNull(message, "message");
    }

    public Severity getSeverity() {
        return severity;
    }

    public String getCode() {
        return code;
    }

    public String getLanguage() {
        return language;
    }

    public String getKey() {
        return key;
    }

    public Integer getLine() {
        return line;
    }

    public String getMessage() {
        return message;
    }
}

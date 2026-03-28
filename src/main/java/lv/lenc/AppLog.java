package lv.lenc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public final class AppLog {
    private static final Logger LOG = Logger.getLogger(AppLog.class.getName());
    private static final String APP_NAME = "Localization Editor SC2 KSP";
    private static volatile boolean initialized;
    private static volatile Path logDirectory;

    private AppLog() {
    }

    static {
        init();
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }

        try {
            logDirectory = resolveLogDirectory();
            Files.createDirectories(logDirectory);

            // 5 rotating files * 2MB each
            String pattern = logDirectory.resolve("app-%g.log").toString();
            FileHandler fileHandler = new FileHandler(pattern, 2 * 1024 * 1024, 5, true);
            fileHandler.setEncoding("UTF-8");
            fileHandler.setFormatter(new OneLineFormatter());
            fileHandler.setLevel(Level.ALL);

            LOG.setUseParentHandlers(false);
            LOG.setLevel(Level.ALL);
            for (Handler handler : LOG.getHandlers()) {
                LOG.removeHandler(handler);
            }
            LOG.addHandler(fileHandler);
            initialized = true;

            LOG.info("[LOG] Logging initialized. Directory: " + logDirectory.toAbsolutePath());
        } catch (Exception ex) {
            // Fallback: do not crash app if logging setup failed.
            LOG.setUseParentHandlers(true);
            LOG.log(Level.WARNING, "[LOG] Failed to initialize file logging: " + ex.getMessage(), ex);
            initialized = true;
        }
    }

    public static String getLogDirectory() {
        init();
        if (logDirectory == null) {
            return "(unavailable)";
        }
        return logDirectory.toAbsolutePath().toString();
    }

    public static void info(String message) {
        init();
        LOG.info(message);
    }

    public static void warn(String message) {
        init();
        LOG.warning(message);
    }

    public static void error(String message) {
        init();
        LOG.severe(message);
    }

    public static void exception(Throwable throwable) {
        init();
        if (throwable == null) {
            return;
        }
        LOG.log(Level.SEVERE, throwable.getMessage(), throwable);
    }

    public static void error(String message, Throwable throwable) {
        init();
        LOG.log(Level.SEVERE, message, throwable);
    }

    private static Path resolveLogDirectory() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            String localAppData = System.getenv("LOCALAPPDATA");
            if (localAppData != null && !localAppData.isBlank()) {
                return Paths.get(localAppData, APP_NAME, "logs");
            }
        }
        return Paths.get(System.getProperty("user.home"), "." + APP_NAME.replace(' ', '_'), "logs");
    }

    private static final class OneLineFormatter extends Formatter {
        @Override
        public String format(LogRecord record) {
            String ts = java.time.ZonedDateTime.now().toString();
            String lvl = record.getLevel().getName();
            String msg = formatMessage(record);
            String logger = record.getLoggerName();
            String base = ts + " [" + lvl + "] " + logger + " - " + msg + System.lineSeparator();
            if (record.getThrown() == null) {
                return base;
            }
            StringBuilder sb = new StringBuilder(base);
            Throwable t = record.getThrown();
            sb.append(t).append(System.lineSeparator());
            for (StackTraceElement ste : t.getStackTrace()) {
                sb.append("    at ").append(ste).append(System.lineSeparator());
            }
            return sb.toString();
        }
    }
}

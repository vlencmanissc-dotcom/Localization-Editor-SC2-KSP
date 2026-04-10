package lv.lenc;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AppLauncher {
    public static void main(String[] args) {
        suppressJavaFxWarnings();
        AppLog.init();
        AppLog.info("[APP] Starting application");
        AppLog.info("[APP] Log directory: " + AppLog.getLogDirectory());
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) ->
                AppLog.error("[APP] Uncaught exception in thread " + thread.getName(), throwable));

        // Prefer hardware acceleration, but keep software fallback on machines with unstable GPU drivers.
        System.setProperty("prism.order", "d3d,sw");
        System.setProperty("prism.verbose", "false");
        System.setProperty("sun.java2d.noddraw", "false");
        System.setProperty("javafx.animation.fullspeed", "true");
        if (isForceGpuRequested()) {
            System.setProperty("prism.forceGPU", "true");
            System.setProperty("sun.java2d.d3d", "true");
            System.setProperty("sun.java2d.opengl", "true");
            AppLog.info("[GPU] LE_FORCE_GPU=true, forcing hardware pipeline");
        }

        // On Windows, request "High Performance" GPU profile for java/javaw executables.
        // This writes per-user preference and does not require admin rights.
        configureWindowsHighPerformanceGpuPreference();
        requestHighPriorityForCurrentProcess();

        Main.main(args);
    }

    private static void suppressJavaFxWarnings() {
        try {
            installJavaFxErrFilter();
            Logger.getLogger("com.sun.javafx.application.PlatformImpl").setLevel(Level.SEVERE);
            Logger.getLogger("javafx.scene.CssStyleHelper").setLevel(Level.SEVERE);
            Logger.getLogger("javafx").setLevel(Level.SEVERE);
        } catch (Exception ignored) {
            // Keep startup safe even if logging implementation changes.
        }
    }

    private static void installJavaFxErrFilter() {
        PrintStream originalErr = System.err;
        if (originalErr instanceof FilteringPrintStream) {
            return;
        }
        System.setErr(new FilteringPrintStream(originalErr));
    }

    private static boolean shouldSuppressJavaFxErrLine(String line) {
        if (line == null || line.isBlank()) {
            return false;
        }
        String normalized = line.trim();
        return normalized.contains("Unsupported JavaFX configuration: classes were loaded from 'unnamed module")
                || normalized.contains("javafx.scene.CssStyleHelper calculateValue")
                || normalized.contains("com.sun.javafx.application.PlatformImpl startup")
                || normalized.contains("java.lang.ClassCastException: class java.lang.String cannot be cast to class javafx.scene.paint.")
                || normalized.contains("while converting value for '-fx-background-color' from rule '*.progress-bar>*.track'")
                || normalized.contains("while converting value for '-fx-background-color' from rule '*.progress-bar>*.bar'");
    }

    private static boolean isForceGpuRequested() {
        String value = System.getenv("LE_FORCE_GPU");
        return value != null && Boolean.parseBoolean(value.trim());
    }

    private static void configureWindowsHighPerformanceGpuPreference() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (!os.contains("win")) {
            return;
        }

        String javaHome = System.getProperty("java.home");
        if (javaHome == null || javaHome.isBlank()) {
            return;
        }

        setGpuPreferenceIfExists(new File(javaHome, "bin\\java.exe"));
        setGpuPreferenceIfExists(new File(javaHome, "bin\\javaw.exe"));
    }

    private static void setGpuPreferenceIfExists(File exe) {
        if (exe == null || !exe.exists()) {
            return;
        }

        String path = exe.getAbsolutePath();
        try {
            Process p = new ProcessBuilder(
                    "reg", "add",
                    "HKCU\\Software\\Microsoft\\DirectX\\UserGpuPreferences",
                    "/v", path,
                    "/t", "REG_SZ",
                    "/d", "GpuPreference=2;",
                    "/f"
            )
                    .redirectErrorStream(true)
                    .start();

            int exit = p.waitFor();
            if (exit == 0) {
                AppLog.info("[GPU] High-performance GPU preference enabled for: " + path);
            } else {
                AppLog.warn("[GPU] Failed to set Windows GPU preference for: " + path + " (exit=" + exit + ")");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            AppLog.warn("[GPU] Unable to configure Windows GPU preference: interrupted");
        } catch (IOException ex) {
            AppLog.warn("[GPU] Unable to configure Windows GPU preference: " + ex.getMessage());
        }
    }

    private static void requestHighPriorityForCurrentProcess() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (!os.contains("win")) {
            return;
        }

        long pid = ProcessHandle.current().pid();
        if (pid <= 0) {
            return;
        }

        String ps = "$p=Get-Process -Id " + pid
                + " -ErrorAction SilentlyContinue; "
                + "if($p){$p.PriorityClass='High'}";
        try {
            new ProcessBuilder("powershell", "-NoProfile", "-Command", ps)
                    .redirectErrorStream(true)
                    .start();
            AppLog.info("[APP] High priority requested for pid=" + pid);
        } catch (IOException ex) {
            AppLog.warn("[APP] Unable to request high process priority: " + ex.getMessage());
        }
    }

    private static final class FilteringPrintStream extends PrintStream {
        private FilteringPrintStream(PrintStream delegate) {
            super(new FilteringOutputStream(delegate), true, StandardCharsets.UTF_8);
        }
    }

    private static final class FilteringOutputStream extends OutputStream {
        private final PrintStream delegate;
        private final StringBuilder lineBuffer = new StringBuilder(256);

        private FilteringOutputStream(PrintStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public void write(int b) {
            char ch = (char) (b & 0xFF);
            if (ch == '\r') {
                flushLine();
                return;
            }
            if (ch == '\n') {
                flushLine();
                return;
            }
            lineBuffer.append(ch);
        }

        @Override
        public void flush() {
            flushLine();
            delegate.flush();
        }

        private void flushLine() {
            if (lineBuffer.isEmpty()) {
                return;
            }
            String line = lineBuffer.toString();
            lineBuffer.setLength(0);
            if (shouldSuppressJavaFxErrLine(line)) {
                return;
            }
            delegate.println(line);
        }
    }
}

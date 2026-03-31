package lv.lenc.cli;

import java.io.File;

public final class LocalizationCli {

    private LocalizationCli() {
    }

    public static void main(String[] args) {
        int exitCode = run(args);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    static int run(String[] args) {
        if (args == null || args.length == 0 || isHelp(args[0])) {
            printUsage();
            return 0;
        }

        String command = args[0];
        if (!"check-missing".equalsIgnoreCase(command)) {
            System.err.println("Unknown command: " + command);
            printUsage();
            return 2;
        }

        if (args.length < 2) {
            System.err.println("Missing file path for check-missing command.");
            printUsage();
            return 2;
        }

        String format = "text";
        for (int i = 2; i < args.length; i++) {
            String arg = args[i];
            if ("--json".equalsIgnoreCase(arg)) {
                format = "json";
                continue;
            }
            if ("--text".equalsIgnoreCase(arg)) {
                format = "text";
                continue;
            }
            if (arg.startsWith("--format=")) {
                format = arg.substring("--format=".length()).trim().toLowerCase();
                continue;
            }

            System.err.println("Unknown option: " + arg);
            printUsage();
            return 2;
        }

        File selectedFile = new File(args[1]);
        if (!selectedFile.isFile()) {
            System.err.println("File not found: " + selectedFile.getAbsolutePath());
            return 2;
        }

        try {
            DiagnosticsReport report =
                    new LocalizationDiagnosticsService().analyzeMissingTexts(selectedFile);

            if ("json".equals(format)) {
                System.out.println(report.toJson());
            } else {
                System.out.print(report.toText());
            }

            return report.hasIssues() ? 1 : 0;
        } catch (Exception ex) {
            System.err.println("CLI error: " + ex.getMessage());
            return 3;
        }
    }

    private static boolean isHelp(String arg) {
        return "-h".equalsIgnoreCase(arg)
                || "--help".equalsIgnoreCase(arg)
                || "help".equalsIgnoreCase(arg);
    }

    private static void printUsage() {
        System.out.println("Localization CLI");
        System.out.println();
        System.out.println("Usage:");
        System.out.println("  check-missing <file> [--text|--json]");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  check-missing \"C:\\Maps\\MyMap\\enUS.SC2Data\\LocalizedData\\GameStrings.txt\"");
        System.out.println("  check-missing \"C:\\Maps\\MyMap\\enUS.SC2Data\\LocalizedData\\GameStrings.txt\" --json");
        System.out.println();
        System.out.println("Exit codes:");
        System.out.println("  0 = no issues found");
        System.out.println("  1 = warnings or errors found");
        System.out.println("  2 = usage or input error");
        System.out.println("  3 = unexpected runtime error");
    }
}
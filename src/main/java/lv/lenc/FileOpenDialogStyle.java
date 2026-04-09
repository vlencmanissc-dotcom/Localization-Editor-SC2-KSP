package lv.lenc;

import javafx.scene.Parent;

final class FileOpenDialogStyle {
    private FileOpenDialogStyle() {
    }

    static void ensureStylesheet(Parent root) {
        AppStyles.applyFileDialogStyles(root);
    }

    static String overlayDimStyle() {
        return "-fx-background-color: rgba(0, 0, 0, 0.34);";
    }

    static String panelStyle() {
        return "-fx-background-color: linear-gradient(to bottom, rgba(5, 16, 13, 0.98), rgba(7, 22, 18, 0.98));"
                + "-fx-border-color: rgba(80, 255, 208, 0.36);"
                + "-fx-border-width: 1;"
                + "-fx-border-radius: 6;"
                + "-fx-background-radius: 6;"
                + "-fx-effect: dropshadow(gaussian, rgba(0, 0, 0, 0.30), 18, 0.18, 0, 3);";
    }

    static String titleStyle(double fontSize) {
        return "-fx-font-family: 'Segoe UI';"
                + "-fx-font-size: " + fontSize + "px;"
                + "-fx-font-weight: 600;"
                + "-fx-text-fill: #9ef4d8;";
    }

    static String statusStyle(double fontSize) {
        return "-fx-font-family: 'Segoe UI';"
                + "-fx-font-size: " + fontSize + "px;"
                + "-fx-text-fill: #77cdb2;";
    }

    static String formLabelStyle(double fontSize) {
        return "-fx-font-family: 'Segoe UI';"
                + "-fx-font-size: " + fontSize + "px;"
                + "-fx-text-fill: #87e1c3;";
    }

    static String fieldStyle(double fontSize) {
        return "-fx-font-family: 'Segoe UI';"
                + "-fx-font-size: " + fontSize + "px;"
                + "-fx-text-fill: #b9ffe9;"
                + "-fx-prompt-text-fill: rgba(185, 255, 233, 0.48);"
                + "-fx-highlight-fill: rgba(56, 180, 132, 0.58);"
                + "-fx-highlight-text-fill: #ecfff7;"
                + "-fx-accent: #4be0af;"
                + "-fx-focus-color: rgba(75, 224, 175, 0.50);"
                + "-fx-faint-focus-color: rgba(75, 224, 175, 0.15);"
                + "-fx-background-color: rgba(6, 18, 15, 0.98);"
                + "-fx-border-color: rgba(104, 255, 214, 0.28);"
                + "-fx-border-width: 1;"
                + "-fx-background-radius: 4;"
                + "-fx-border-radius: 4;";
    }

    static String listSurfaceStyle(double fontSize) {
        return "-fx-font-family: 'Segoe UI';"
                + "-fx-font-size: " + fontSize + "px;"
                + "-fx-control-inner-background: rgba(6, 18, 15, 0.98);"
                + "-fx-background-color: rgba(6, 18, 15, 0.98);"
                + "-fx-border-color: rgba(104, 255, 214, 0.22);"
                + "-fx-border-width: 1;"
                + "-fx-background-radius: 4;"
                + "-fx-border-radius: 4;"
                + "-fx-cell-hover-color: rgba(22, 58, 46, 0.95);"
                + "-fx-selection-bar: rgba(32, 84, 66, 0.95);"
                + "-fx-selection-bar-text: #e8fff7;"
                + "-fx-text-fill: #a8f5da;";
    }

    static String tableSurfaceStyle(double fontSize) {
        return "-fx-font-family: 'Segoe UI';"
                + "-fx-font-size: " + fontSize + "px;"
                + "-fx-background-color: rgba(6, 18, 15, 0.98);"
                + "-fx-control-inner-background: rgba(6, 18, 15, 0.98);"
                + "-fx-table-cell-border-color: rgba(104, 255, 214, 0.12);"
                + "-fx-border-color: rgba(104, 255, 214, 0.22);"
                + "-fx-selection-bar: rgba(32, 84, 66, 0.95);"
                + "-fx-selection-bar-text: #ecfff8;"
                + "-fx-text-background-color: #a8f5da;";
    }
}

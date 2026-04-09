package lv.lenc;

import java.util.List;

import javafx.collections.ObservableList;
import javafx.scene.Parent;
import javafx.scene.Scene;

final class AppStyles {
    private static final List<String> MAIN_SCENE_STYLES = List.of(
            UiAssets.css("ThemeTokens.css"),
            UiAssets.css("CustomAlternativeButton.css"),
            UiAssets.css("CustomComboBoxClassic.css"),
            UiAssets.css("CustomFileChooser.css"),
            UiAssets.css("CustomLongButton.css"),
            UiAssets.css("GlowingLabel.css"),
            UiAssets.css("GlowingLabelBorder.css"),
            UiAssets.css("HeaderFlashOverlay.css"),
            UiAssets.css("KeyFilterBase.css"),
            UiAssets.css("KeyFilter.css"),
            UiAssets.css("TableSearchPopup.css"),
            UiAssets.css("ArchiveOpen.css"),
            UiAssets.css("SquareDiscordURL.css"),
            UiAssets.css("translation-progress.css")
    );

    private static final List<String> SETTINGS_STYLES = List.of(
            UiAssets.css("ThemeTokens.css"),
            UiAssets.css("custom-checkbox.css"),
            UiAssets.css("CustomAlternativeButton.css"),
            UiAssets.css("CustomCloseButton.css"),
            UiAssets.css("CustomComboBoxClassic.css"),
            UiAssets.css("CustomLanguageButton.css"),
            UiAssets.css("CustomSlider.css"),
            UiAssets.css("GlowingLabel.css")
    );

    private static final List<String> FILE_DIALOG_STYLES = List.of(
            UiAssets.css("ThemeTokens.css"),
            UiAssets.css("FileOpenDialog.css")
    );

    private static final List<String> ALERT_STYLES = List.of(
            UiAssets.css("ThemeTokens.css"),
            UiAssets.css("alert-box.css"),
            UiAssets.css("CustomLongButton.css")
    );

    private static final List<String> TABLE_SEARCH_STYLES = List.of(
            UiAssets.css("ThemeTokens.css"),
            UiAssets.css("KeyFilterBase.css"),
            UiAssets.css("TableSearchPopup.css")
    );

    private static final List<String> PROGRESS_STYLES = List.of(
            UiAssets.css("ThemeTokens.css"),
            UiAssets.css("translation-progress.css")
    );

    private AppStyles() {
    }

    static void applyMainScene(Scene scene) {
        apply(scene, MAIN_SCENE_STYLES);
    }

    static void applySettings(Scene scene) {
        apply(scene, SETTINGS_STYLES);
    }

    static void applyFileDialogStyles(Parent root) {
        apply(root, FILE_DIALOG_STYLES);
    }

    static void applyAlertStyles(Scene scene) {
        apply(scene, ALERT_STYLES);
    }

    static void applyTableSearchStyles(Parent root) {
        apply(root, TABLE_SEARCH_STYLES);
    }

    static void applyProgressStyles(Scene scene) {
        apply(scene, PROGRESS_STYLES);
    }

    private static void apply(Scene scene, List<String> styles) {
        if (scene == null) {
            return;
        }
        addMissing(scene.getStylesheets(), styles);
    }

    private static void apply(Parent root, List<String> styles) {
        if (root == null) {
            return;
        }
        addMissing(root.getStylesheets(), styles);
    }

    private static void addMissing(ObservableList<String> stylesheets, List<String> styles) {
        for (String stylesheet : styles) {
            if (!stylesheets.contains(stylesheet)) {
                stylesheets.add(stylesheet);
            }
        }
    }
}

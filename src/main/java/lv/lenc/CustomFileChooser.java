package lv.lenc;

import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class CustomFileChooser extends StackPane implements Disabable {

    private final LocalizationManager localization;
    private final Button button;
    private final double widthFullHD;
    private final double heightFullHD;
    private File selectedFile;
    private final FileSelectable fileSelectable;

    @Override
    public void disable(Boolean value) {
        // Protect against null and apply disabled state consistently
        boolean disabled = value != null && value;
        this.setDisable(disabled);
        button.setDisable(disabled);
    }

    public CustomFileChooser(LocalizationManager localization,
                             FileSelectable fileSelectable,
                             double widthFullHD,
                             double heightFullHD) {
        this.localization = localization;
        this.fileSelectable = fileSelectable;
        this.widthFullHD = widthFullHD;
        this.heightFullHD = heightFullHD;

        button = new Button();
        button.getStyleClass().add("file-chooser-btn");
        button.setFocusTraversable(false);
        UiSoundManager.bindBnetButton(button);

        button.setOnAction(e -> {
            Platform.runLater(() -> {
                InAppSwingFileChooserDialog.show(this, this.localization, selectedFile, file -> {
                    if (file != null) {
                        selectedFile = file;
                        readFile();
                        this.fileSelectable.onSelect(file);
                    }
                });
            });
        });

        getChildren().add(button);

        // (optional) center button inside StackPane
        StackPane.setAlignment(button, javafx.geometry.Pos.CENTER);

        applyScaledSize();

        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) return;
            UiScaleHelper.refreshFromScene(newScene);
            applyScaledSize();
            newScene.widthProperty().addListener((o, ov, nv) -> {
                UiScaleHelper.refreshFromScene(newScene);
                applyScaledSize();
            });
            newScene.heightProperty().addListener((o, ov, nv) -> {
                UiScaleHelper.refreshFromScene(newScene);
                applyScaledSize();
            });
        });
    }

    private void applyScaledSize() {
        double width = UiScaleHelper.scaleX(widthFullHD);
        double height = UiScaleHelper.scaleY(heightFullHD);

        this.setMinSize(width, height);
        this.setPrefSize(width, height);
        this.setMaxSize(width, height);

        button.setMinSize(width, height);
        button.setPrefSize(width, height);
        button.setMaxSize(width, height);
        button.setStyle("-fx-background-size: " + width + "px " + height + "px;");
    }
    /**
     * Reads file content using UTF-8 encoding.
     */
    public void readFile() {
        if (selectedFile == null) return;
        if (!(selectedFile.isFile() && selectedFile.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".txt"))) {
            return;
        }

        try {
            Files.readString(selectedFile.toPath(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            AppLog.warn("[FileChooser] File read error: " + e.getMessage());
        }
    }

    public String getFile() {
        if (selectedFile == null) return null;
        if (!(selectedFile.isFile() && selectedFile.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".txt"))) {
            return null;
        }

        try {
            return Files.readString(selectedFile.toPath(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    public File getSelectedFile() {
        return selectedFile;
    }
}

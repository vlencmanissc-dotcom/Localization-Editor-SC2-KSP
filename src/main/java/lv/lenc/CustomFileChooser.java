package lv.lenc;

import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class CustomFileChooser extends StackPane implements Disabable {

    private final FileChooser fileChooser;
    private final Button button;
    private File selectedFile;
    private final FileSelectable fileSelectable;

    @Override
    public void disable(Boolean value) {
        // Protect against null and apply disabled state consistently
        boolean disabled = value != null && value;
        this.setDisable(disabled);
        button.setDisable(disabled);
    }

    public CustomFileChooser(FileSelectable fileSelectable, double widthFullHD, double heightFullHD) {
        this.fileSelectable = fileSelectable;

        // Scale size relative to design resolution (1920x1080)
        //  double width  = UiScaleHelper.SCREEN_WIDTH  * (widthFullHD  / 1920.0);
        //double height = UiScaleHelper.SCREEN_HEIGHT * (heightFullHD / 1080.0);
        double width = UiScaleHelper.scaleX(widthFullHD);
        double height = UiScaleHelper.scaleY(heightFullHD);
        // Root size
        this.setMinSize(width, height);
        this.setPrefSize(width, height);
        this.setMaxSize(width, height);

        fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("LocalizedData", "*.txt"));

        button = new Button();
        button.getStyleClass().add("file-chooser-btn");

        // Button size = same as control size
        button.setMinSize(width, height);
        button.setPrefSize(width, height);
        button.setMaxSize(width, height);

        // Make CSS background scale exactly to computed size
        button.setStyle("-fx-background-size: " + width + "px " + height + "px;");

        button.setFocusTraversable(false);

        button.setOnAction(e -> {
            Stage stage = (Stage) getScene().getWindow();
            Platform.runLater(() -> {
                File file = openFile(stage);
                if (file != null) {
                    selectedFile = file;
                    readFile();
                    this.fileSelectable.onSelect(file);
                }
            });
        });

        getChildren().add(button);

        // (optional) center button inside StackPane
        StackPane.setAlignment(button, javafx.geometry.Pos.CENTER);
    }

    public File openFile(Stage stage) {
        return fileChooser.showOpenDialog(stage);
    }

    /**
     * Reads file content using UTF-8 encoding.
     */
    public void readFile() {
        if (selectedFile == null) return;

        try {
            Files.readString(selectedFile.toPath(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            AppLog.warn("[FileChooser] File read error: " + e.getMessage());
        }
    }

    public String getFile() {
        if (selectedFile == null) return null;

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

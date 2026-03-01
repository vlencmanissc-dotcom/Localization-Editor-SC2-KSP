package lv.lenc;

import com.almasb.fxgl.ui.UIFactoryService;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

public final class TranslationProgressWindow {

    private final Stage stage;
    private final LocalizationManager localization;

    private final Label title;
    private final Label line1;
    private final Label line2;
    private final Label percent;
    private final ProgressBar bar;

    public TranslationProgressWindow(Stage owner, LocalizationManager localization) {
        this.localization = localization;

        stage = new Stage(StageStyle.TRANSPARENT);
        stage.initOwner(owner);
        stage.initModality(Modality.NONE);
        stage.setResizable(false);
        stage.setAlwaysOnTop(false);
        stage.setOnCloseRequest(e -> e.consume());

        title = new Label();
        line1 = new Label();
        line2 = new Label();
        percent = new Label("0%");
        bar = new ProgressBar(0);

// контент
        VBox content = new VBox(8, title, line1, line2, percent, bar);
        content.setAlignment(Pos.CENTER);

        VBox inner = new VBox(content);
        inner.setAlignment(Pos.CENTER);
        inner.getStyleClass().add("settingbox-inner-frame");


        VBox frame = new VBox(inner);
        frame.getStyleClass().add("nova-progress-frame");

        Scene scene = new Scene(frame);
        scene.getStylesheets().add(
                TranslationProgressWindow.class
                        .getResource("/Assets/Style/translation-progress.css")
                        .toExternalForm()
        );
        stage.setScene(scene);

// classes на контролы
        title.getStyleClass().add("nova-title");
        line1.getStyleClass().add("nova-line1");
        line2.getStyleClass().add("nova-line2");
        percent.getStyleClass().add("nova-percent");
        bar.getStyleClass().add("translation-progress-bar");

// ширины как в “панели”
        frame.setMinWidth(520);
        frame.setPrefWidth(520);

        bar.setPrefWidth(420);
        bar.setMaxWidth(420);
        scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
    }

    public void showReset() {
        Platform.runLater(() -> {
            String loading = (localization != null)
                    ? localization.get("translating.loading")
                    : "-";

            title.setText(loading);
            line1.setText("");
            line2.setText("");
            percent.setText("0%");
            bar.setProgress(0);

            if (!stage.isShowing()) stage.show();
            stage.sizeToScene();

            // ✅ сверху по центру относительно главного окна
            Window owner = stage.getOwner();
            if (owner != null) {
                double x = owner.getX() + (owner.getWidth() - stage.getWidth()) / 2.0;
                double y = owner.getY() + 80;
                stage.setX(Math.max(0, x));
                stage.setY(Math.max(0, y));
            }

            stage.toFront();
        });
    }

    public void update(double value01, String l1, String l2) {
        Platform.runLater(() -> {
            double v = Math.max(0, Math.min(1, value01));
            bar.setProgress(v);
            percent.setText((int) Math.round(v * 100) + "%");
            line1.setText(l1 == null ? "" : l1);
            line2.setText(l2 == null ? "" : l2);
        });
    }

    // принимает строку "строка1||строка2"
    public void updateFromProgress(double value01, String packedText) {
        String l1 = "";
        String l2 = "";
        if (packedText != null) {
            String[] parts = packedText.split("\\|\\|", 2);
            l1 = parts.length > 0 ? parts[0] : "";
            l2 = parts.length > 1 ? parts[1] : "";
        }
        update(value01, l1, l2);
    }

    // лучше hide, а не close — можно показывать много раз
    public void close() {
        if (Platform.isFxApplicationThread()) {
            stage.hide();
        } else {
            Platform.runLater(stage::hide);
        }
    }

}

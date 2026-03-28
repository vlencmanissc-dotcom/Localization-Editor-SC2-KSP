package lv.lenc;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public final class TranslationProgressOverlay extends StackPane {

    private final LocalizationManager localization;

    private final Label title = new Label();
    private final Label line1 = new Label();
    private final Label line2 = new Label();
    private final Label percent = new Label("0%");
    private final ProgressBar bar = new ProgressBar(0);

    public TranslationProgressOverlay(LocalizationManager localization) {
        this.localization = localization;

        setPickOnBounds(false);
        setMouseTransparent(true);
        setVisible(false);
        setManaged(false);

        addEventFilter(MouseEvent.ANY, e -> {
            if (isVisible() && !isMouseTransparent()) {
                e.consume();
            }
        });

        VBox content = new VBox(8, title, line1, line2, percent, bar);
        content.setAlignment(Pos.CENTER);

        VBox inner = new VBox(content);
        inner.setAlignment(Pos.CENTER);
        inner.getStyleClass().add("settingbox-inner-frame");

        VBox frame = new VBox(inner);
        frame.getStyleClass().add("nova-progress-frame");

        frame.setMinWidth(UiScaleHelper.scaleX(520));
        frame.setPrefWidth(UiScaleHelper.scaleX(520));
        frame.setMaxWidth(UiScaleHelper.scaleX(520));

        frame.setMinHeight(UiScaleHelper.scaleY(190));
        frame.setPrefHeight(UiScaleHelper.scaleY(190));
        frame.setMaxHeight(UiScaleHelper.scaleY(190));

        StackPane.setAlignment(frame, Pos.TOP_CENTER);
        StackPane.setMargin(frame, new Insets(80, 0, 0, 0));

        getChildren().add(frame);

        title.getStyleClass().add("nova-title");
        line1.getStyleClass().add("nova-line1");
        line2.getStyleClass().add("nova-line2");
        percent.getStyleClass().add("nova-percent");
        bar.getStyleClass().add("translation-progress-bar");
        bar.setMaxWidth(Double.MAX_VALUE);
        bar.prefWidthProperty().bind(frame.widthProperty().multiply(0.90));
    }

    public void showReset() {
        Runnable action = () -> {
            String loading = (localization != null)
                    ? localization.get("translating.loading")
                    : "-";

            title.setText(loading);
            line1.setText("");
            line2.setText("");
            percent.setText("0%");
            bar.setProgress(0);

            setMouseTransparent(false);
            setManaged(true);
            setVisible(true);
            toFront();
        };

        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            Platform.runLater(action);
        }
    }

    public void update(double value01, String l1, String l2) {
        Runnable action = () -> {
            double v = Math.max(0, Math.min(1, value01));
            bar.setProgress(v);
            percent.setText((int) Math.round(v * 100) + "%");
            line1.setText(l1 == null ? "" : l1);
            line2.setText(l2 == null ? "" : l2);

            setMouseTransparent(false);
            setManaged(true);
            setVisible(true);
            toFront();
        };

        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            Platform.runLater(action);
        }
    }

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

    public void close() {
        if (Platform.isFxApplicationThread()) {
            setMouseTransparent(true);
            setVisible(false);
            setManaged(false);
        } else {
            Platform.runLater(() -> {
                setMouseTransparent(true);
                setVisible(false);
                setManaged(false);
            });
        }
    }
}

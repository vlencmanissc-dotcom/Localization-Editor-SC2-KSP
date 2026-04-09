package lv.lenc;

import javafx.animation.Interpolator;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.TextAlignment;
import javafx.util.Duration;

public final class TranslationProgressOverlay extends StackPane {
    private static final double SETTINGS_OPEN_LIFT_Y = 84.0;

    private final LocalizationManager localization;

    private final Label title = new Label();
    private final Label line1 = new Label();
    private final Label line2 = new Label();
    private final Label percent = new Label("0%");
    private final ProgressBar bar = new ProgressBar(0);
    private final String texturePath = UiAssets.textureRoot();
    private final VBox content;
    private final VBox inner;
    private final VBox frame;
    private TranslateTransition positionTransition;

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

        content = new VBox(UiScaleHelper.scaleY(8), title, line1, line2, percent, bar);
        content.setAlignment(Pos.CENTER);

        inner = new VBox(content);
        inner.setAlignment(Pos.CENTER);
        inner.getStyleClass().add("settingbox-inner-frame");

        frame = new VBox(inner);
        frame.setAlignment(Pos.CENTER);
        frame.getStyleClass().add("nova-progress-frame");

        Rectangle frameClip = new Rectangle();
        frameClip.widthProperty().bind(frame.widthProperty());
        frameClip.heightProperty().bind(frame.heightProperty());
        frame.setClip(frameClip);

        Rectangle innerClip = new Rectangle();
        innerClip.widthProperty().bind(inner.widthProperty());
        innerClip.heightProperty().bind(inner.heightProperty());
        inner.setClip(innerClip);

        getChildren().add(frame);

        title.getStyleClass().add("nova-title");
        line1.getStyleClass().add("nova-line1");
        line2.getStyleClass().add("nova-line2");
        percent.getStyleClass().add("nova-percent");
        bar.getStyleClass().add("translation-progress-bar");

        line1.setWrapText(true);
        line2.setWrapText(true);
        line1.setTextAlignment(TextAlignment.CENTER);
        line2.setTextAlignment(TextAlignment.CENTER);
        line1.setAlignment(Pos.CENTER);
        line2.setAlignment(Pos.CENTER);

        bar.setMaxWidth(Double.MAX_VALUE);
        bar.prefWidthProperty().bind(inner.widthProperty().multiply(1.0));

        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) return;
            ensureProgressStylesheet(newScene);
            UiScaleHelper.refreshFromScene(newScene);
            applyScaleMetrics();
            newScene.widthProperty().addListener((o, ov, nv) -> {
                UiScaleHelper.refreshFromScene(newScene);
                applyScaleMetrics();
            });
            newScene.heightProperty().addListener((o, ov, nv) -> {
                UiScaleHelper.refreshFromScene(newScene);
                applyScaleMetrics();
            });
        });

        SettingBox.addVisibilityListener(visible -> Platform.runLater(() -> updateAnchorPosition(true)));
    }

    public boolean overlayVisible() {
        return isVisible() && !isMouseTransparent();
    }

    public static boolean isOverlayVisible() {
        return overlayVisibleStatic;
    }

    private static boolean overlayVisibleStatic;

    private static void setOverlayVisibleStatic(boolean visible) {
        overlayVisibleStatic = visible;
    }

    private double sf(double fullHdPx, double minPx) {
        return Math.max(UiScaleHelper.scaleY(fullHdPx), minPx);
    }

    private double sfx(double fullHdPx, double minPx) {
        return Math.max(UiScaleHelper.scaleX(fullHdPx), minPx);
    }

    private static double clamp(double value, double min, double max) {
        if (max < min) return min;
        return Math.max(min, Math.min(max, value));
    }

    private void applyScaleMetrics() {
        if (getScene() != null) {
            ensureProgressStylesheet(getScene());
            UiScaleHelper.refreshFromScene(getScene());
        }

        double sceneScale = UiScaleHelper.UNIFORM_SCALE;
        double sceneW = UiScaleHelper.REAL_SCREEN_WIDTH;
        double sceneH = UiScaleHelper.REAL_SCREEN_HEIGHT;
        if (getScene() != null && getScene().getWidth() > 1 && getScene().getHeight() > 1) {
            sceneW = getScene().getWidth();
            sceneH = getScene().getHeight();
            double sx = sceneW / UiScaleHelper.BASE_W;
            double sy = sceneH / UiScaleHelper.BASE_H;
            sceneScale = Math.min(sx, sy);
        }

        content.setSpacing(sf(7, 4));
        content.setPadding(new Insets(sf(8, 4), sfx(12, 8), sf(8, 4), sfx(12, 8)));

        double frameWidth = clamp(520.0 * sceneScale, 320.0, sceneW * 0.62);
        double frameHeight = clamp(190.0 * sceneScale, 130.0, sceneH * 0.33);
        frame.setMinWidth(frameWidth);
        frame.setPrefWidth(frameWidth);
        frame.setMaxWidth(frameWidth);
        frame.setMinHeight(frameHeight);
        frame.setPrefHeight(frameHeight);
        frame.setMaxHeight(frameHeight);

        double innerWidth = Math.max(frameWidth - sfx(26, 16), 220);
        double innerHeight = Math.max(frameHeight - sf(24, 14), 100);
        inner.setMinWidth(innerWidth);
        inner.setPrefWidth(innerWidth);
        inner.setMaxWidth(innerWidth);
        inner.setMinHeight(innerHeight);
        inner.setPrefHeight(innerHeight);
        inner.setMaxHeight(innerHeight);

        StackPane.setAlignment(frame, Pos.TOP_CENTER);
        StackPane.setMargin(frame, new Insets(clamp(80.0 * sceneScale, 24.0, sceneH * 0.18), 0, 0, 0));

        title.setStyle("-fx-font-size: " + sf(20, 13) + "px;");
        line1.setStyle("-fx-font-size: " + sf(15, 10) + "px;");
        line2.setStyle("-fx-font-size: " + sf(15, 10) + "px;");
        percent.setStyle("-fx-font-size: " + sf(16, 11) + "px;");

        line1.setMaxWidth(sfx(470, 300));
        line2.setMaxWidth(sfx(470, 300));

        // Keep old visual style, only scale bar height.
        bar.setMinHeight(sf(22, 12));
        bar.setPrefHeight(sf(22, 12));
        bar.setMaxHeight(sf(22, 12));

        updateAnchorPosition(false);

        applyCss();
        layout();
        applyLegacyProgressBarSkinFallback();
    }

    private void ensureProgressStylesheet(Scene scene) {
        AppStyles.applyProgressStyles(scene);
    }

    // If an external/user-agent style forces white bar, restore the legacy SC2 look.
    private void applyLegacyProgressBarSkinFallback() {
        Node trackNode = bar.lookup(".track");
        if (trackNode != null) {
            trackNode.setStyle(
                    "-fx-background-color: transparent;"
                            + "-fx-background-insets: 0;"
                            + "-fx-padding: 0;"
                            + "-fx-border-color: transparent;"
                            + "-fx-border-width: 0;"
                            + "-fx-border-insets: 0;"
                            + "-fx-effect: null;"
                            + "-fx-border-image-source: url('" + texturePath + "ui_nova_global_scrollbar_bg.png');"
                            + "-fx-border-image-slice: 14 fill;"
                            + "-fx-border-image-width: 5;"
                            + "-fx-border-image-insets: 0;"
                            + "-fx-border-image-repeat: stretch;"
                            + "-fx-background-radius: 0;"
            );
        }
        Node barNode = bar.lookup(".bar");
        if (barNode != null) {
            barNode.setStyle(
                    "-fx-background-color: rgba(98, 202, 162, 1);"
                            + "-fx-background-radius: 0;"
                            + "-fx-background-insets: 0.38em 0.35em 0.38em 0.35em;"
                            + "-fx-border-color: rgba(132, 222, 188, 1);"
                            + "-fx-border-width: 0.1em;"
                            + "-fx-border-insets: 0.38em 0.35em 0.38em 0.35em;"
                            + "-fx-effect: dropshadow(gaussian, rgba(24, 100, 65, 1), 6, 0.6, 0, 0),"
                            + "dropshadow(gaussian, rgba(14, 60, 39, 0.8), 12, 0.4, 0, 0),"
                            + "dropshadow(gaussian, rgba(7, 30, 20, 0.6), 18, 0.2, 0, 0);"
            );
        }
    }

    private void updateAnchorPosition(boolean animated) {
        double targetY = SettingBox.isOverlayVisible() ? -sf(SETTINGS_OPEN_LIFT_Y, 44) : 0.0;
        if (!animated) {
            frame.setTranslateY(targetY);
            return;
        }
        if (positionTransition != null) {
            positionTransition.stop();
        }
        positionTransition = new TranslateTransition(Duration.millis(220), frame);
        positionTransition.setInterpolator(Interpolator.EASE_BOTH);
        positionTransition.setFromY(frame.getTranslateY());
        positionTransition.setToY(targetY);
        positionTransition.play();
    }

    public void showReset() {
        Runnable action = () -> {
            applyScaleMetrics();
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
            setOverlayVisibleStatic(true);
            SettingBox.syncOverlayOffset();
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
            applyScaleMetrics();
            double v = Math.max(0, Math.min(1, value01));
            bar.setProgress(v);
            percent.setText((int) Math.round(v * 100) + "%");
            line1.setText(l1 == null ? "" : l1);
            line2.setText(l2 == null ? "" : l2);

            setMouseTransparent(false);
            setManaged(true);
            setVisible(true);
            setOverlayVisibleStatic(true);
            SettingBox.syncOverlayOffset();
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
            setOverlayVisibleStatic(false);
            SettingBox.syncOverlayOffset();
        } else {
            Platform.runLater(() -> {
                setMouseTransparent(true);
                setVisible(false);
                setManaged(false);
                setOverlayVisibleStatic(false);
                SettingBox.syncOverlayOffset();
            });
        }
    }

    public void showError(String titleText, String l1, String l2) {
        Runnable action = () -> {
            applyScaleMetrics();
            title.setText((titleText == null || titleText.isBlank()) ? "Error" : titleText);
            line1.setText(l1 == null ? "" : l1);
            line2.setText(l2 == null ? "" : l2);
            line1.setWrapText(true);
            line2.setWrapText(true);
            line1.setMaxWidth(UiScaleHelper.scaleX(470));
            line2.setMaxWidth(UiScaleHelper.scaleX(470));
            bar.setProgress(0);
            percent.setText("0%");

            setMouseTransparent(true);
            setManaged(true);
            setVisible(true);
            setOverlayVisibleStatic(true);
            SettingBox.syncOverlayOffset();
            toFront();
        };

        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            Platform.runLater(action);
        }
    }
}

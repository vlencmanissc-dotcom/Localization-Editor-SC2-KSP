package lv.lenc;

import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.util.Duration;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.scene.Node;
import javafx.scene.effect.GaussianBlur;
import java.net.URL;
import java.util.function.Consumer;

public class ExitConfirmDialog {

    // one overlay instance (reused)
    private static StackPane overlayRoot;
    private static StackPane dialogHolder;
    // blur background (only underlying content)
    private static final GaussianBlur blurEffect = new GaussianBlur(0);
    private static Node blurredTarget;
    private ExitConfirmDialog() {}

    /**
     * In-app modal confirm dialog (overlay).
     * Calls callback with true/false.
     */
    public static void showConfirm(
            StackPane appRoot,
            Label descriptionLabel,
            LocalizationManager localization,
            Consumer<Boolean> onResult
    ) {
        if (appRoot == null) throw new IllegalArgumentException("appRoot is null (StackPane root is required)");
        if (onResult == null) throw new IllegalArgumentException("onResult is null");

        if (overlayRoot == null) {
            overlayRoot = buildOverlay(appRoot);
            appRoot.getChildren().add(overlayRoot);
        }

        // rebuild dialog each time (safe + fresh texts/sizes)
        dialogHolder.getChildren().clear();
        Pane dialog = buildDialog(appRoot.getScene(), descriptionLabel, localization, onResult);
        dialogHolder.getChildren().add(dialog);

        ensureStylesheet(appRoot.getScene());
// blur ONLY background layer (not the overlay itself)
        blurredTarget = (!appRoot.getChildren().isEmpty()) ? appRoot.getChildren().get(0) : appRoot;

        if (blurredTarget.getEffect() == null) {
            blurEffect.setRadius(0);
            blurredTarget.setEffect(blurEffect);
        }
        overlayRoot.setVisible(true);
        overlayRoot.setMouseTransparent(false);

        overlayRoot.setOpacity(0);
        Platform.runLater(() -> {
            overlayRoot.applyCss();
            overlayRoot.layout();

            overlayRoot.setOpacity(1);
            overlayRoot.requestFocus();
            playOpen(dialog);
            Timeline blurIn = new Timeline(
                    new KeyFrame(Duration.millis(0),
                            new KeyValue(blurEffect.radiusProperty(), 0)),
                    new KeyFrame(Duration.millis(220),
                            new KeyValue(blurEffect.radiusProperty(), UiScaleHelper.scaleY(10), Interpolator.EASE_OUT))
            );
            blurIn.play();
        });
    }

    private static StackPane buildOverlay(StackPane appRoot) {
        Region dim = new Region();
        dim.setStyle("-fx-background-color: rgba(0,0,0,0.60);");
        dim.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        dialogHolder = new StackPane();
        dialogHolder.setPickOnBounds(false);
        StackPane.setAlignment(dialogHolder, Pos.CENTER);

        StackPane overlay = new StackPane(dim, dialogHolder);
        overlay.setVisible(false);
        overlay.setMouseTransparent(true);
        overlay.setFocusTraversable(true);

        overlay.prefWidthProperty().bind(appRoot.widthProperty());
        overlay.prefHeightProperty().bind(appRoot.heightProperty());

        // clicking outside = "No"
        dim.setOnMouseClicked(e -> closeWith(false, null));

        return overlay;
    }

    private static Pane buildDialog(
            Scene scene,
            Label descriptionLabel,
            LocalizationManager localization,
            Consumer<Boolean> onResult
    ) {
        String texturePath = mustResource("/Assets/Textures/").toExternalForm();

        double windowWidth = UiScaleHelper.scaleX(825);
        double windowHeight = UiScaleHelper.scaleY(750);

        // Background
        Image backgroundImg = new Image(texturePath + "ui_battlenet_glues_pageassets_dialogstandardbgUPSCALE3_APS.png");
        BackgroundImage backgroundImage = new BackgroundImage(
                backgroundImg,
                BackgroundRepeat.NO_REPEAT, BackgroundRepeat.NO_REPEAT,
                BackgroundPosition.CENTER,
                new BackgroundSize(windowWidth, windowHeight, false, true, true, false)
        );

        // Hex pattern
        ImageView topHex = createHexImage(texturePath, windowWidth, false);
        ImageView bottomHex = createHexImage(texturePath, windowWidth, true);

        StackPane hexPatternPane = new StackPane(topHex, bottomHex);
        StackPane.setAlignment(topHex, Pos.TOP_CENTER);
        StackPane.setAlignment(bottomHex, Pos.BOTTOM_CENTER);
        topHex.setTranslateY(UiScaleHelper.scaleY(275));
        bottomHex.setTranslateY(-UiScaleHelper.scaleY(275));

        // Description
        descriptionLabel.setStyle(getScaledFontStyle(20));
        descriptionLabel.getStyleClass().add("alert-description");

        // Buttons
        Button yesButton = createScaledButton(localization.get("button.yes"), true, texturePath);
        Button noButton  = createScaledButton(localization.get("button.no"), false, texturePath);

        yesButton.setOnAction(e -> closeWith(true, onResult));
        noButton.setOnAction(e -> closeWith(false, onResult));

        HBox buttonContainer = new HBox(UiScaleHelper.scaleX(20));
        buttonContainer.getChildren().addAll(yesButton, noButton);
        buttonContainer.setAlignment(Pos.CENTER);

        VBox contentLayout = new VBox(UiScaleHelper.scaleY(20));
        contentLayout.setAlignment(Pos.CENTER);
        contentLayout.setPadding(new Insets(UiScaleHelper.scaleY(20)));
        contentLayout.getChildren().addAll(descriptionLabel, buttonContainer);

        StackPane rootLayout = new StackPane();
        rootLayout.getStyleClass().add("alert-root");
        rootLayout.setBackground(new Background(backgroundImage));
        rootLayout.getChildren().addAll(hexPatternPane, contentLayout);

        // Make it fixed-size like window
        rootLayout.setPrefSize(windowWidth, windowHeight);
        rootLayout.setMinSize(windowWidth, windowHeight);
        rootLayout.setMaxSize(windowWidth, windowHeight);

        // ESC = No
        rootLayout.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                closeWith(false, onResult);
                e.consume();
            }
        });

        // ensure focus to catch ESC
        Platform.runLater(rootLayout::requestFocus);

        return rootLayout;
    }

    private static void closeWith(boolean result, Consumer<Boolean> onResult) {
        if (overlayRoot == null) {
            if (onResult != null) onResult.accept(result);
            return;
        }

        // animate out the dialog node
        Pane dialog = null;
        if (dialogHolder != null && !dialogHolder.getChildren().isEmpty()) {
            if (dialogHolder.getChildren().get(0) instanceof Pane p) dialog = p;
        }

        if (dialog == null) {
            overlayRoot.setVisible(false);
            overlayRoot.setMouseTransparent(true);
            if (onResult != null) blurOutAndClear();onResult.accept(result);
            return;
        }

        FadeTransition fadeOut = new FadeTransition(Duration.millis(220), dialog);
        fadeOut.setFromValue(1.0);
        fadeOut.setToValue(0.0);

        ScaleTransition scaleOut = new ScaleTransition(Duration.millis(220), dialog);
        scaleOut.setFromX(1.0);
        scaleOut.setFromY(1.0);
        scaleOut.setToX(0.985);
        scaleOut.setToY(0.985);

        ParallelTransition pt = new ParallelTransition(fadeOut, scaleOut);
        pt.setOnFinished(e -> {
            blurOutAndClear();
            overlayRoot.setVisible(false);
            overlayRoot.setMouseTransparent(true);
            if (onResult != null) onResult.accept(result);
        });
        pt.play();
    }
    private static void blurOutAndClear() {
        if (blurredTarget == null) return;
        if (blurredTarget.getEffect() != blurEffect) return;

        Timeline blurOut = new Timeline(
                new KeyFrame(Duration.millis(0),
                        new KeyValue(blurEffect.radiusProperty(), blurEffect.getRadius())),
                new KeyFrame(Duration.millis(180),
                        new KeyValue(blurEffect.radiusProperty(), 0, Interpolator.EASE_IN))
        );
        blurOut.setOnFinished(ev -> blurredTarget.setEffect(null));
        blurOut.play();
    }
    private static void playOpen(Pane dialog) {
        dialog.setOpacity(0.0);
        dialog.setScaleX(1.01);
        dialog.setScaleY(1.01);

        FadeTransition fadeIn = new FadeTransition(Duration.millis(250), dialog);
        fadeIn.setFromValue(0.0);
        fadeIn.setToValue(1.0);

        ScaleTransition scaleIn = new ScaleTransition(Duration.millis(250), dialog);
        scaleIn.setFromX(1.01);
        scaleIn.setFromY(1.01);
        scaleIn.setToX(1.0);
        scaleIn.setToY(1.0);

        ParallelTransition pt = new ParallelTransition(fadeIn, scaleIn);
        pt.play();
    }

    private static void ensureStylesheet(Scene scene) {
        if (scene == null) return;

        URL cssUrl = ExitConfirmDialog.class.getResource("/Assets/Style/alert-box.css");
        if (cssUrl == null) {
            System.err.println("[AlertBox] CSS not found: /Assets/Style/alert-box.css");
            return;
        }

        String css = cssUrl.toExternalForm();
        if (!scene.getStylesheets().contains(css)) {
            scene.getStylesheets().add(css);
        }
    }

    // ========= HELPERS =========

    private static URL mustResource(String resourcePath) {
        URL url = ExitConfirmDialog.class.getResource(resourcePath);
        if (url == null) {
            throw new IllegalStateException("Resource not found: " + resourcePath +
                    " (check src/main/resources path + exact folder/file case)");
        }
        return url;
    }

    private static ImageView createHexImage(String texturePath, double width, boolean rotated) {
        ImageView hex = new ImageView(new Image(texturePath + "ui_battlenet_glues_pageassets_dialog_hexpattern.png"));
        hex.setFitWidth(width);
        hex.setPreserveRatio(true);
        if (rotated) hex.setRotate(180);
        return hex;
    }

    private static Button createScaledButton(String text, boolean isGreen, String texturePath) {
        Button btn = UIElementFactory.createCustomLongButton(text, texturePath, isGreen, 0.5, 0.6);
        btn.setPrefSize(UiScaleHelper.scaleX(320), UiScaleHelper.scaleY(58));
        return btn;
    }

    private static String getScaledFontStyle(double fullHDPx) {
        double size = UiScaleHelper.scaleY(fullHDPx);
        return "-fx-font-size: " + size + "px;";
    }
    public static void prewarm(
            StackPane appRoot,
            Label descriptionLabel,
            LocalizationManager localization
    ) {
        if (overlayRoot == null) {
            overlayRoot = buildOverlay(appRoot);
            appRoot.getChildren().add(overlayRoot);
        }

        Platform.runLater(() -> {
            overlayRoot.applyCss();
            overlayRoot.layout();
            overlayRoot.setVisible(false);
            overlayRoot.setMouseTransparent(true);
            overlayRoot.setOpacity(1);
        });
    }
}
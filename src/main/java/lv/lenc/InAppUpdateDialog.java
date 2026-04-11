package lv.lenc;

import java.util.function.Consumer;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderImage;
import javafx.scene.layout.BorderRepeat;
import javafx.scene.layout.BorderWidths;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

final class InAppUpdateDialog {
    private static StackPane overlayRoot;
    private static StackPane dialogHolder;
    private static Node blurredTarget;
    private static final GaussianBlur blurEffect = new GaussianBlur(0);
    private static Consumer<Boolean> pendingResult;

    private InAppUpdateDialog() {
    }

    static void showInfo(
            StackPane appRoot,
            String title,
            String message,
            String buttonText
    ) {
        showChoice(appRoot, title, message, buttonText, null, ignored -> {});
    }

    static void showChoice(
            StackPane appRoot,
            String title,
            String message,
            String primaryButtonText,
            String secondaryButtonText,
            Consumer<Boolean> onResult
    ) {
        if (appRoot == null) {
            return;
        }
        if (overlayRoot == null) {
            overlayRoot = buildOverlay(appRoot);
            appRoot.getChildren().add(overlayRoot);
        }

        pendingResult = onResult == null ? ignored -> {} : onResult;
        dialogHolder.getChildren().clear();
        dialogHolder.getChildren().add(buildDialog(title, message, primaryButtonText, secondaryButtonText));

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
            Node dialog = dialogHolder.getChildren().isEmpty() ? null : dialogHolder.getChildren().get(0);
            if (dialog != null) {
                playOpen(dialog);
            }
            Timeline blurIn = new Timeline(
                    new KeyFrame(Duration.ZERO, new KeyValue(blurEffect.radiusProperty(), 0)),
                    new KeyFrame(Duration.millis(220),
                            new KeyValue(blurEffect.radiusProperty(), UiScaleHelper.scaleY(10), Interpolator.EASE_OUT))
            );
            blurIn.play();
        });
    }

    private static StackPane buildOverlay(StackPane appRoot) {
        Region dim = new Region();
        dim.setStyle("-fx-background-color: rgba(0,0,0,0.62);");
        dim.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        dim.setOnMouseClicked(e -> close(false));

        dialogHolder = new StackPane();
        dialogHolder.setPickOnBounds(false);
        StackPane.setAlignment(dialogHolder, Pos.CENTER);

        StackPane overlay = new StackPane(dim, dialogHolder);
        overlay.setVisible(false);
        overlay.setMouseTransparent(true);
        overlay.setFocusTraversable(true);
        overlay.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                close(false);
                e.consume();
            }
        });

        overlay.prefWidthProperty().bind(appRoot.widthProperty());
        overlay.prefHeightProperty().bind(appRoot.heightProperty());
        return overlay;
    }

    private static StackPane buildDialog(
            String title,
            String message,
            String primaryButtonText,
            String secondaryButtonText
    ) {
        String texturePath = UiAssets.textureRoot();
        boolean hasSecondary = secondaryButtonText != null && !secondaryButtonText.isBlank();
        String safeMessage = message == null ? "" : message.trim();
        int messageLines = safeMessage.isEmpty() ? 0 : safeMessage.split("\\R", -1).length;
        int roughMessageLength = safeMessage.length();

        double fullHdDialogW = hasSecondary ? 560 : 500;
        double fullHdDialogH = hasSecondary ? 236 : 214;
        if (messageLines >= 3 || roughMessageLength > 95) {
            fullHdDialogH += 24;
        }
        if (messageLines >= 4 || roughMessageLength > 150) {
            fullHdDialogH += 22;
        }
        double dialogW = UiScaleHelper.scaleX(fullHdDialogW);
        double dialogH = UiScaleHelper.scaleY(fullHdDialogH);

        BorderImage borderImage = new BorderImage(
                new javafx.scene.image.Image(texturePath + "ui_nova_archives_listitem_selected.png"),
                new BorderWidths(UiScaleHelper.scale(8)),
                Insets.EMPTY,
                new BorderWidths(UiScaleHelper.scale(8)),
                true,
                BorderRepeat.STRETCH,
                BorderRepeat.STRETCH
        );

        GlowingLabel titleLabel = new GlowingLabel(title == null ? "" : title);
        titleLabel.setFont(javafx.scene.text.Font.font("Arial Black", UiScaleHelper.scaleY(22)));
        titleLabel.setAlignment(Pos.CENTER);
        titleLabel.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        titleLabel.setWrapText(true);
        titleLabel.setPrefWidth(dialogW - UiScaleHelper.scaleX(40));

        GlowingLabel messageLabel = new GlowingLabel(safeMessage);
        messageLabel.setFont(javafx.scene.text.Font.font("Arial Black", UiScaleHelper.scaleY(15.5)));
        messageLabel.setAlignment(Pos.CENTER);
        messageLabel.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        messageLabel.setWrapText(true);
        messageLabel.setPrefWidth(dialogW - UiScaleHelper.scaleX(60));

        CustomAlternativeButton primaryButton = new CustomAlternativeButton(
                primaryButtonText == null ? "OK" : primaryButtonText,
                0.6, 0.8, 214.0, 56.0, 11.2
        );
        primaryButton.setOnAction(e -> close(true));

        HBox buttonRow = new HBox(UiScaleHelper.scaleX(12));
        buttonRow.setAlignment(Pos.CENTER);
        buttonRow.getChildren().add(primaryButton);

        if (hasSecondary) {
            CustomAlternativeButton secondaryButton = new CustomAlternativeButton(
                    secondaryButtonText,
                    0.6, 0.8, 214.0, 56.0, 11.2
            );
            secondaryButton.setOnAction(e -> close(false));
            buttonRow.getChildren().add(secondaryButton);
        }

        VBox content = new VBox(UiScaleHelper.scaleY(12));
        content.setAlignment(Pos.CENTER);
        content.getChildren().add(titleLabel);
        if (!safeMessage.isEmpty()) {
            content.getChildren().add(messageLabel);
        }
        content.getChildren().add(buttonRow);
        content.setPadding(new Insets(
                UiScaleHelper.scaleY(14),
                UiScaleHelper.scaleX(16),
                UiScaleHelper.scaleY(12),
                UiScaleHelper.scaleX(16)
        ));

        StackPane card = new StackPane(content);
        card.setPrefSize(dialogW, dialogH);
        card.setMinSize(dialogW, dialogH);
        card.setMaxSize(dialogW, dialogH);
        card.setBackground(new Background(new BackgroundFill(
                javafx.scene.paint.Color.web("#1e0a05", 0.96),
                CornerRadii.EMPTY,
                Insets.EMPTY
        )));
        card.setBorder(new Border(borderImage));
        card.setStyle("-fx-effect: dropshadow(gaussian, rgba(255,140,54,0.35), " + UiScaleHelper.scaleY(22)
                + ", 0.25, 0, 0);");

        return card;
    }

    private static void playOpen(Node dialog) {
        dialog.setOpacity(0.0);
        dialog.setScaleX(1.02);
        dialog.setScaleY(1.02);

        FadeTransition fadeIn = new FadeTransition(Duration.millis(220), dialog);
        fadeIn.setFromValue(0.0);
        fadeIn.setToValue(1.0);

        ScaleTransition scaleIn = new ScaleTransition(Duration.millis(220), dialog);
        scaleIn.setFromX(1.02);
        scaleIn.setFromY(1.02);
        scaleIn.setToX(1.0);
        scaleIn.setToY(1.0);

        new ParallelTransition(fadeIn, scaleIn).play();
    }

    private static void close(boolean primaryChoice) {
        if (overlayRoot == null) {
            return;
        }
        Node dialog = dialogHolder.getChildren().isEmpty() ? null : dialogHolder.getChildren().get(0);
        if (dialog == null) {
            hideOverlay();
            pendingResult.accept(primaryChoice);
            return;
        }

        FadeTransition fadeOut = new FadeTransition(Duration.millis(180), dialog);
        fadeOut.setFromValue(1.0);
        fadeOut.setToValue(0.0);
        ScaleTransition scaleOut = new ScaleTransition(Duration.millis(180), dialog);
        scaleOut.setFromX(1.0);
        scaleOut.setFromY(1.0);
        scaleOut.setToX(0.985);
        scaleOut.setToY(0.985);

        ParallelTransition pt = new ParallelTransition(fadeOut, scaleOut);
        pt.setOnFinished(e -> {
            hideOverlay();
            pendingResult.accept(primaryChoice);
        });
        pt.play();
    }

    private static void hideOverlay() {
        if (overlayRoot == null) {
            return;
        }
        overlayRoot.setVisible(false);
        overlayRoot.setMouseTransparent(true);

        if (blurredTarget != null && blurredTarget.getEffect() == blurEffect) {
            Timeline blurOut = new Timeline(
                    new KeyFrame(Duration.ZERO, new KeyValue(blurEffect.radiusProperty(), blurEffect.getRadius())),
                    new KeyFrame(Duration.millis(160),
                            new KeyValue(blurEffect.radiusProperty(), 0, Interpolator.EASE_IN))
            );
            blurOut.setOnFinished(ev -> blurredTarget.setEffect(null));
            blurOut.play();
        }
    }
}

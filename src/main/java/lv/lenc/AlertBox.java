package lv.lenc;

import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import javafx.util.Duration;

import java.net.URL;

public class AlertBox {

    public static boolean display(Window owner, Label titleLabel, Label descriptionLabel, LocalizationManager localization) {
        final boolean[] result = {false};

        // Your current resources folder (keep exact case)
        String texturePath = mustResource("/Assets/Textures/").toExternalForm();

        double windowWidth = UiScaleHelper.scaleX(825);
        double windowHeight = UiScaleHelper.scaleY(750);

        Stage window = new Stage();

        if (owner != null) window.initOwner(owner);
        window.initModality(Modality.WINDOW_MODAL);
        window.setAlwaysOnTop(true);
        window.initStyle(StageStyle.TRANSPARENT);


        window.setTitle(localization.get("label.ExitConfirmation"));
        window.setWidth(windowWidth);
        window.setHeight(windowHeight);
        window.initStyle(StageStyle.TRANSPARENT);

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

        // Description (font size stays dynamic)
        descriptionLabel.setStyle(getScaledFontStyle(20));
        descriptionLabel.getStyleClass().add("alert-description");

        // Buttons
        Button yesButton = createScaledButton(localization.get("button.yes"), true, texturePath);
        yesButton.setOnAction(e -> {
            result[0] = true;
            fadeOutAndClose(window);
        });

        Button noButton = createScaledButton(localization.get("button.no"), false, texturePath);
        noButton.setOnAction(e -> {
            result[0] = false;
            fadeOutAndClose(window);   //
        });

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

        Scene scene = new Scene(rootLayout, windowWidth, windowHeight);
        scene.setFill(null);

        //
        URL cssUrl = AlertBox.class.getResource("/Assets/Style/alert-box.css");
        if (cssUrl != null) {
            scene.getStylesheets().add(cssUrl.toExternalForm());
        } else {
            System.err.println("[AlertBox] CSS not found: /Assets/Style/alert-box.css");
        }

        window.setScene(scene);

        //
        window.setOnCloseRequest(e -> {
            e.consume();
            result[0] = false;
            fadeOutAndClose(window);
        });

        fadeIn(window);
        window.showAndWait();
        return result[0];
    }

    // ========= HELPERS =========

    private static URL mustResource(String resourcePath) {
        URL url = AlertBox.class.getResource(resourcePath);
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

    private static void fadeIn(Stage window) {
        FadeTransition fadeIn = new FadeTransition(Duration.millis(250), window.getScene().getRoot());
        fadeIn.setFromValue(0.0);
        fadeIn.setToValue(1.0);
        fadeIn.play();
    }

    //
    private static void fadeOutAndClose(Stage window) {
        var root = window.getScene().getRoot();

        FadeTransition fadeOut = new FadeTransition(Duration.millis(220), root);
        fadeOut.setFromValue(1.0);
        fadeOut.setToValue(0.0);

        ScaleTransition scaleOut = new ScaleTransition(Duration.millis(220), root);
        scaleOut.setFromX(1.0);
        scaleOut.setFromY(1.0);
        scaleOut.setToX(0.985);
        scaleOut.setToY(0.985);

        ParallelTransition pt = new ParallelTransition(fadeOut, scaleOut);
        pt.setOnFinished(e -> window.close());
        pt.play();
    }
}
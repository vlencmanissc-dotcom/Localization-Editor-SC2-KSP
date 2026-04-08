package lv.lenc;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.scene.effect.Blend;
import javafx.scene.effect.BlendMode;
import javafx.scene.effect.ColorInput;
import javafx.util.Duration;

public class TitleLabelGlow extends StackPane {
    private static final Color GLOW_COLOR = Color.rgb(0, 218, 205, 1.0); // turquoise
    private final Label label;
    private final ImageView glowImage;
    private final String loc;

    public TitleLabelGlow(String text, LocalizationManager localizationManager) {
        this.loc = localizationManager.getCurrentLanguage();

        double fontSize = UiScaleHelper.scaleFont(54, 20); //

        label = new Label(text.toUpperCase());
        label.setFont(Font.font("Arial", FontWeight.BOLD, fontSize));
        label.setTextAlignment(TextAlignment.CENTER);
        label.setAlignment(Pos.CENTER);
        label.setStyle("-fx-text-fill: white;");

        DropShadow glow = new DropShadow();
        glow.setColor(GLOW_COLOR);
        glow.setOffsetX(0.00001);
        glow.setOffsetY(0.00001);
        glow.setRadius(UiScaleHelper.scale(5));
        glow.setSpread(0.24);
        label.setEffect(glow);

        glowImage = new ImageView(
                new Image(getClass().getResource("/Assets/Textures/ui_nova_storymode_titleglow.png").toExternalForm())
        );
        glowImage.setPreserveRatio(false);
        glowImage.setOpacity(0.3);

        double baseGlowWidth = UiScaleHelper.scaleX(675);//525
        double baseGlowHeight = UiScaleHelper.scaleY(80);

        ColorInput colorInput = new ColorInput(0, 0, baseGlowWidth, baseGlowHeight, GLOW_COLOR);
        Blend colorize = new Blend(BlendMode.SRC_ATOP, null, colorInput);
        glowImage.setEffect(colorize);

        label.widthProperty().addListener((obs, oldVal, newVal) -> {
            double padding = loc.equals("en") ? UiScaleHelper.scaleX(28) : UiScaleHelper.scaleY(68);
            glowImage.setFitWidth(newVal.doubleValue() + padding);
            glowImage.setTranslateX(UiScaleHelper.scaleX(5));
        });

        label.heightProperty().addListener((obs, oldVal, newVal) -> {
            glowImage.setFitHeight(newVal.doubleValue() - UiScaleHelper.scale(5));
        });

        //
        Timeline timeline = new Timeline(
                new KeyFrame(Duration.seconds(0),   new KeyValue(glowImage.opacityProperty(), 0.25, Interpolator.EASE_BOTH)),
                new KeyFrame(Duration.seconds(1.9), new KeyValue(glowImage.opacityProperty(), 0.25, Interpolator.EASE_BOTH)),
                new KeyFrame(Duration.seconds(2.5), new KeyValue(glowImage.opacityProperty(), 1.0, Interpolator.EASE_BOTH)),
                new KeyFrame(Duration.seconds(2.75),new KeyValue(glowImage.opacityProperty(), 1.0, Interpolator.EASE_BOTH)),
                new KeyFrame(Duration.seconds(3.4), new KeyValue(glowImage.opacityProperty(), 0.25, Interpolator.EASE_BOTH))
        );

       // timeline.setCycleCount(Animation.INDEFINITE);
        Platform.runLater(() -> {
            timeline.play();
        });

        this.getChildren().addAll(glowImage, label);
        this.setAlignment(Pos.CENTER);
    }

    public void setText(String text) {
        label.setText(text.toUpperCase());
    }

    public String getText() {
        return label.getText();
    }
}

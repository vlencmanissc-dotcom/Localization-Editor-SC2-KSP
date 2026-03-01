package lv.lenc;

import javafx.scene.control.Button;
import javafx.scene.effect.Glow;
import javafx.scene.text.Font;

public class CustomAlternativeButton extends Button implements Disabable {

    private final Glow glow;
    private final double glowDefault;
    private final double glowHover;

    private final double widthPxFullHD;
    private final double heightPxFullHD;
    private final double fontPxFullHD;

    /**
     * Constructor exactly matching your call:
     *
     * new CustomAlternativeButton(text, glow1, glow2, 170, 54, 14);
     */
    public CustomAlternativeButton(String text,
                                   double strengthGlow,
                                   double strengthGlowMAX,
                                   double widthPxFullHD,
                                   double heightPxFullHD,
                                   double fontPxFullHD) {

        super(text);

        this.glowDefault = strengthGlow;
        this.glowHover = strengthGlowMAX;

        this.widthPxFullHD = widthPxFullHD;
        this.heightPxFullHD = heightPxFullHD;
        this.fontPxFullHD = fontPxFullHD;

        getStyleClass().add("alt-button");

        applyScaledSizing();

        this.glow = new Glow(glowDefault);
        setEffect(glow);

        bindBehavior();
    }

    private void applyScaledSizing() {

        double w = UiScaleHelper.scaleX(widthPxFullHD);
        double h = UiScaleHelper.scaleY(heightPxFullHD);

        // uniform font scaling (no crooked text)
        double fontScale = Math.min(
                UiScaleHelper.scaleX(1.0),
                UiScaleHelper.scaleY(1.0)
        );

        double fontPx = fontPxFullHD * fontScale;

        setPrefSize(w, h);
        setMaxSize(w, h);

        setFont(Font.font("Arial Black", fontPx));
    }

    private void bindBehavior() {

        hoverProperty().addListener((obs, wasHover, isHover) -> {
            if (!isDisabled()) {
                glow.setLevel(isHover ? glowHover : glowDefault);
            }
        });

        disabledProperty().addListener((obs, wasDisabled, isNowDisabled) -> {
            glow.setLevel(isNowDisabled ? 0.0 : (isHover() ? glowHover : glowDefault));
        });
    }

    @Override
    public void disable(Boolean value) {
        setDisable(Boolean.TRUE.equals(value));
        if (!isDisabled()) setOpacity(1.0);
    }
}
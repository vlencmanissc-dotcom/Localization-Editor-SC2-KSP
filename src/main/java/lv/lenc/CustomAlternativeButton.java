package lv.lenc;

import javafx.scene.control.Button;
import javafx.scene.effect.Glow;
import javafx.scene.Scene;
import javafx.scene.text.Font;
import javafx.beans.value.ChangeListener;

public class CustomAlternativeButton extends Button implements Disabable {

    private final Glow glow;
    private final double glowDefault;
    private final double glowHover;

    private final double widthPxFullHD;
    private final double heightPxFullHD;
    private final double fontPxFullHD;
    private final ChangeListener<Number> sceneScaleListener = (obs, oldV, newV) -> refreshScaledSizing();

    /**
     * Constructor exactly matching your call:
     * <p>
     * new CustomAlternativeButton(text, glow1, glow2, 170, 54, 14);
     */
    public CustomAlternativeButton(String text, double strengthGlow, double strengthGlowMAX, double widthPxFullHD, double heightPxFullHD, double fontPxFullHD) {

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
        bindScaleRefresh();
    }

    private void applyScaledSizing() {

        double w = UiScaleHelper.scaleX(widthPxFullHD);
        double h = UiScaleHelper.scaleY(heightPxFullHD);

        // uniform font scaling with a safe floor so text never disappears
        double fontPx = UiScaleHelper.scaleFont(fontPxFullHD, 10.0);

        setMinSize(w, h);
        setPrefSize(w, h);
        setMaxSize(w, h);

        setFont(Font.font("Arial Black", fontPx));
    }

    private void refreshScaledSizing() {
        Scene scene = getScene();
        if (scene != null) {
            UiScaleHelper.refreshFromScene(scene);
        }
        applyScaledSizing();
    }

    private void bindScaleRefresh() {
        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (oldScene != null) {
                oldScene.widthProperty().removeListener(sceneScaleListener);
                oldScene.heightProperty().removeListener(sceneScaleListener);
            }
            if (newScene != null) {
                UiScaleHelper.refreshFromScene(newScene);
                newScene.widthProperty().addListener(sceneScaleListener);
                newScene.heightProperty().addListener(sceneScaleListener);
                applyScaledSizing();
            }
        });
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

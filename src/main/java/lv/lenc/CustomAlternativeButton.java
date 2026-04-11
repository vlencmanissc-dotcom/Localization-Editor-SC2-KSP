package lv.lenc;

import javafx.scene.control.Button;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
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
        blockSecondaryClickVisualState();
        bindScaleRefresh();
        UiSoundManager.bindNovaButton(this);
    }

    private void applyScaledSizing() {

        double w = UiScaleHelper.scaleX(widthPxFullHD);
        double h = UiScaleHelper.scaleY(heightPxFullHD);

        // uniform font scaling with a safe floor so text never disappears
        double fontBoost = 1.0;
        Object fontBoostValue = getProperties().get("lv.lenc.fontScaleBoost");
        if (fontBoostValue instanceof Number n) {
            fontBoost = Math.max(0.75, n.doubleValue());
        }
        double fontPx = UiScaleHelper.scaleFont(fontPxFullHD, 10.0) * fontBoost;

        setMinSize(w, h);
        setPrefSize(w, h);
        setMaxSize(w, h);

        setFont(Font.font("Arial Black", fontPx));
        String style = mergeInlineDeclaration(getStyle(), "-fx-font-size", fontPx + "px");
        style = mergeInlineDeclaration(style, "-fx-font-weight", "900");
        setStyle(style);
    }

    private static String mergeInlineDeclaration(String currentStyle, String property, String value) {
        String style = currentStyle == null ? "" : currentStyle.trim();
        String normalizedProperty = property.trim().toLowerCase();

        String[] declarations = style.isEmpty() ? new String[0] : style.split(";");
        StringBuilder rebuilt = new StringBuilder();
        boolean replaced = false;
        for (String declaration : declarations) {
            String part = declaration.trim();
            if (part.isEmpty()) {
                continue;
            }
            int colon = part.indexOf(':');
            if (colon > 0) {
                String key = part.substring(0, colon).trim().toLowerCase();
                if (key.equals(normalizedProperty)) {
                    if (!replaced) {
                        rebuilt.append(property).append(": ").append(value).append("; ");
                        replaced = true;
                    }
                    continue;
                }
            }
            rebuilt.append(part).append("; ");
        }
        if (!replaced) {
            rebuilt.append(property).append(": ").append(value).append(";");
        }
        return rebuilt.toString().trim();
    }

    private void refreshScaledSizing() {
        if (Boolean.TRUE.equals(getProperties().get("lv.lenc.freezeScale"))) {
            return;
        }
        Scene scene = getScene();
        if (scene != null) {
            UiScaleHelper.refreshFromScene(scene);
        }
        applyScaledSizing();
    }

    public void refreshScaledSizingNow() {
        refreshScaledSizing();
    }

    private void bindScaleRefresh() {
        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (oldScene != null) {
                oldScene.widthProperty().removeListener(sceneScaleListener);
                oldScene.heightProperty().removeListener(sceneScaleListener);
            }
            if (newScene != null) {
                if (Boolean.TRUE.equals(getProperties().get("lv.lenc.freezeScale"))) {
                    return;
                }
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

    private void blockSecondaryClickVisualState() {
        addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            if (event.getButton() == MouseButton.SECONDARY) {
                disarm();
                event.consume();
            }
        });
        addEventFilter(MouseEvent.MOUSE_RELEASED, event -> {
            if (event.getButton() == MouseButton.SECONDARY) {
                disarm();
                event.consume();
            }
        });
        addEventFilter(ContextMenuEvent.CONTEXT_MENU_REQUESTED, event -> disarm());
    }

    @Override
    public void disable(Boolean value) {
        setDisable(Boolean.TRUE.equals(value));
        if (!isDisabled()) setOpacity(1.0);
    }
}

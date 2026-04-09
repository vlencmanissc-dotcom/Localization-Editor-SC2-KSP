package lv.lenc;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.effect.Glow;
import javafx.scene.input.MouseButton;

final class TextureButtonSupport {
    record TextureSet(String normal, String hover, String pressed, String disabled) {
    }

    record TextFillSet(String normal, String hover, String pressed, String disabled) {
    }

    record ButtonSpec(
            double width,
            double height,
            double fontSize,
            Insets padding,
            Pos alignment,
            String fontFamily,
            double glowDefault,
            double glowHover,
            TextureSet textures,
            TextFillSet textFills
    ) {
    }

    private TextureButtonSupport() {
    }

    static MyButton create(String text, String texturePath, ButtonSpec spec) {
        MyButton button = new MyButton(text);
        apply(button, texturePath, spec);
        return button;
    }

    static void apply(MyButton button, String texturePath, ButtonSpec spec) {
        final boolean[] pressed = {false};
        Glow glow = new Glow(spec.glowDefault());

        button.setPrefSize(spec.width(), spec.height());
        button.setMinSize(spec.width(), spec.height());
        button.setMaxSize(spec.width(), spec.height());
        button.setAlignment(spec.alignment());
        button.setEffect(glow);

        Runnable applyNormalState = () -> {
            boolean disabled = button.isDisabled();
            button.setStyle(buildStyle(
                    texturePath,
                    disabled ? spec.textures().disabled() : spec.textures().normal(),
                    spec.fontFamily(),
                    spec.fontSize(),
                    disabled ? spec.textFills().disabled() : spec.textFills().normal(),
                    spec.padding()
            ));
            glow.setLevel(disabled ? 0.0 : spec.glowDefault());
        };

        applyNormalState.run();

        button.disabledProperty().addListener((obs, oldVal, newVal) -> applyNormalState.run());

        button.setOnMouseEntered(event -> {
            if (pressed[0] || button.isDisabled()) {
                return;
            }
            button.setStyle(buildStyle(
                    texturePath,
                    spec.textures().hover(),
                    spec.fontFamily(),
                    spec.fontSize(),
                    spec.textFills().hover(),
                    spec.padding()
            ));
            glow.setLevel(spec.glowHover());
        });

        button.setOnMouseExited(event -> {
            if (!pressed[0]) {
                applyNormalState.run();
            }
        });

        button.setOnMousePressed(event -> {
            if (event.getButton() != MouseButton.PRIMARY || button.isDisabled()) {
                return;
            }
            pressed[0] = true;
            button.setStyle(buildStyle(
                    texturePath,
                    spec.textures().pressed(),
                    spec.fontFamily(),
                    spec.fontSize(),
                    spec.textFills().pressed(),
                    spec.padding()
            ));
        });

        button.setOnMouseReleased(event -> {
            if (event.getButton() != MouseButton.PRIMARY || button.isDisabled()) {
                return;
            }
            pressed[0] = false;
            applyNormalState.run();
        });
    }

    private static String buildStyle(
            String texturePath,
            String textureName,
            String fontFamily,
            double fontSize,
            String textFill,
            Insets padding
    ) {
        return "-fx-background-image: url('" + texturePath + textureName + "'); "
                + "-fx-background-size: 100% 100%; "
                + "-fx-background-repeat: no-repeat; "
                + "-fx-background-color: transparent; "
                + "-fx-font-family: '" + fontFamily + "'; "
                + "-fx-font-size: " + fontSize + "px; "
                + "-fx-text-fill: " + textFill + "; "
                + "-fx-padding: " + padding.getTop() + " " + padding.getRight() + " "
                + padding.getBottom() + " " + padding.getLeft() + ";";
    }
}

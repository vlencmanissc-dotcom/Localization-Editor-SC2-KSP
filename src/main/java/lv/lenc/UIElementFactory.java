package lv.lenc;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.text.Font;

public class UIElementFactory {

    private UIElementFactory() {
    }

    public static Button createButton(String text, double width, double height, double fontSize) {
        Button button = new Button(text);
        button.setPrefSize(width, height);
        button.setMaxSize(width, height);
        button.setFont(Font.font(fontSize));
        return button;
    }

    public static MyButton createCustomLongButton(
            String text,
            String texturePath,
            boolean isGreen,
            double strengthGlow,
            double strengthGlowMAX
    ) {
        return new CustomLongButton(text, isGreen, 330, 58, 16, strengthGlow, strengthGlowMAX);
    }

    public static MyButton createCustomLanguageButton(String text, String texturePath) {
        return TextureButtonSupport.create(text, texturePath, languageButtonSpec());
    }

    public static MyButton createCustomQuitButton(String text, String texturePath) {
        return TextureButtonSupport.create(text, texturePath, quitButtonSpec());
    }

    public static MyButton createCustomLongAlternativeButton(String text, String texturePath, double strengthGlow, double strengthGlowMAX) {
        return TextureButtonSupport.create(text, texturePath, mediumGreenButtonSpec(strengthGlow, strengthGlowMAX));
    }

    private static TextureButtonSupport.ButtonSpec languageButtonSpec() {
        return new TextureButtonSupport.ButtonSpec(
                UiScaleHelper.scaleX(75),
                UiScaleHelper.scaleY(54),
                UiScaleHelper.scaleY(16),
                Insets.EMPTY,
                Pos.CENTER,
                "Arial Black",
                0.15,
                0.25,
                new TextureButtonSupport.TextureSet(
                        "ui_glues_greenbuttons_squarebackbuttonnormal.png",
                        "ui_glues_greenbuttons_squarebackbuttonover.png",
                        "ui_glues_greenbuttons_squarebackbuttondown.png",
                        "ui_glues_greenbuttons_squarebackbuttondisabled.png"
                ),
                new TextureButtonSupport.TextFillSet(
                        "linear-gradient(from 0% 0% to 0% 100%, white, limegreen)",
                        "linear-gradient(from 0% 0% to 100% 100%, #E6FFE6, #2E8B57)",
                        "linear-gradient(from 0% 0% to 0% 100%, darkgreen, limegreen)",
                        "linear-gradient(from 0% 0% to 0% 100%, white, limegreen)"
                )
        );
    }

    private static TextureButtonSupport.ButtonSpec quitButtonSpec() {
        return new TextureButtonSupport.ButtonSpec(
                UiScaleHelper.scaleX(270),
                UiScaleHelper.scaleY(78),
                UiScaleHelper.scaleY(19),
                Insets.EMPTY,
                Pos.CENTER,
                "Arial Black",
                0.2,
                0.35,
                new TextureButtonSupport.TextureSet(
                        "ui_nova_global_largebuttongreen_normal.png",
                        "ui_nova_global_largebuttongreen_over.png",
                        "ui_nova_global_largebuttongreen_down.png",
                        "ui_nova_global_largebuttongreen_disabled.png"
                ),
                new TextureButtonSupport.TextFillSet(
                        "linear-gradient(from 0% 0% to 0% 100%, white, limegreen)",
                        "linear-gradient(from 0% 0% to 100% 100%, #E6FFE6, #2E8B57)",
                        "linear-gradient(from 0% 0% to 0% 100%, darkgreen, limegreen)",
                        "linear-gradient(from 0% 0% to 0% 100%, white, limegreen)"
                )
        );
    }

    private static TextureButtonSupport.ButtonSpec mediumGreenButtonSpec(double strengthGlow, double strengthGlowMAX) {
        return new TextureButtonSupport.ButtonSpec(
                UiScaleHelper.scaleX(170),
                UiScaleHelper.scaleY(54),
                UiScaleHelper.scaleY(14),
                Insets.EMPTY,
                Pos.CENTER,
                "Arial Black",
                strengthGlow,
                strengthGlowMAX,
                new TextureButtonSupport.TextureSet(
                        "ui_nova_global_mediumbutton_green_normal.png",
                        "ui_nova_global_mediumbutton_green_over.png",
                        "ui_nova_global_mediumbutton_green_down.png",
                        "ui_nova_global_mediumbutton_green_disabled.png"
                ),
                new TextureButtonSupport.TextFillSet(
                        "linear-gradient(from 0% 0% to 20% 100%, white, #90EE90)",
                        "linear-gradient(from 0% 0% to 100% 100%, white, #F0FFF0)",
                        "linear-gradient(from 0% 0% to 100% 100%, white, #F0FFF0)",
                        "linear-gradient(from 0% 0% to 20% 100%, rgba(255,255,255,0.5), rgba(144,238,144,0.5))"
                )
        );
    }
}

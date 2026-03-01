package lv.lenc;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

public class GlowingLabel extends Label {

    public GlowingLabel(String text) {
        super(text);

        getStyleClass().add("glowing-label");
        getStyleClass().add("glow-green"); // default

        setWrapText(true);
        setTextAlignment(TextAlignment.CENTER);
        setAlignment(Pos.CENTER);
        setMaxWidth(Double.MAX_VALUE);
        setFont(Font.font("Arial Black", UiScaleHelper.scaleY(18)));
    }

    public void setGlowOrange(boolean orange) {
        if (orange) {
            getStyleClass().remove("glow-green");
            if (!getStyleClass().contains("glow-orange")) getStyleClass().add("glow-orange");
        } else {
            getStyleClass().remove("glow-orange");
            if (!getStyleClass().contains("glow-green")) getStyleClass().add("glow-green");
        }
    }
}
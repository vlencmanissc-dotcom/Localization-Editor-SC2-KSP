package lv.lenc;

import javafx.scene.control.Button;
import javafx.scene.text.Font;

public class CustomLanguageButton extends Button implements Disabable {

    public CustomLanguageButton(String text, double widthFullHD, double heightFullHD, double fontSizeFullHD) {
        super(text);

        double width = UiScaleHelper.SCREEN_WIDTH * (widthFullHD / 1920.0);
        double height = UiScaleHelper.SCREEN_HEIGHT * (heightFullHD / 1080.0);
        double fontSize = UiScaleHelper.scale(fontSizeFullHD);

        setPrefSize(width, height);
        setMaxSize(width, height);

        // CSS-класс
        getStyleClass().add("lang-btn");
        double baseFont = UiScaleHelper.scaleY(17); // или то, что в Main как база
        setStyle("-fx-font-size: " + baseFont + "px;");
    }

    @Override
    public void disable(Boolean bol) {
        setDisable(bol != null && bol);
        setOpacity(1); // как было
    }
}
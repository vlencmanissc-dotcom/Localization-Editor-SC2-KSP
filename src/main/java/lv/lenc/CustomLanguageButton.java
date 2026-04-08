package lv.lenc;

import javafx.scene.control.Button;

public class CustomLanguageButton extends Button implements Disabable {

    public CustomLanguageButton(String text, double widthFullHD, double heightFullHD, double fontSizeFullHD) {
        super(text);

        double width = UiScaleHelper.SCREEN_WIDTH * (widthFullHD / 1920.0);
        double height = UiScaleHelper.SCREEN_HEIGHT * (heightFullHD / 1080.0);
        double fontSize = UiScaleHelper.scaleFont(fontSizeFullHD, 10.0);

        setPrefSize(width, height);
        setMaxSize(width, height);

        // CSS class
        getStyleClass().add("lang-btn");
        setStyle("-fx-font-size: " + fontSize + "px;");
    }

    @Override
    public void disable(Boolean bol) {
        setDisable(bol != null && bol);
        setOpacity(1); // 
    }
}

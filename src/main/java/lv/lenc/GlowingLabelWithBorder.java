package lv.lenc;

import javafx.geometry.Pos;
import javafx.scene.layout.StackPane;

public class GlowingLabelWithBorder extends StackPane {

    private final GlowingLabel label;

    public GlowingLabelWithBorder(String text,
                                  double widthFullHD, double heightFullHD,
                                  double fontSizeFullHD) {
        this.label = new GlowingLabel(text);

        getStyleClass().add("glowing-label-border");
        setAlignment(Pos.CENTER);

        // === SIZE: design relative to 1920x1080 ===
        double w = UiScaleHelper.SCREEN_WIDTH * (widthFullHD / 1920.0);
        double h = UiScaleHelper.SCREEN_HEIGHT * (heightFullHD / 1080.0);

        setPrefSize(w, h);
        setMinSize(w, h);
        setMaxSize(w, h);

        // === font size scaling (
        double fs = UiScaleHelper.scaleFont(fontSizeFullHD, 10.0);
        label.setStyle("-fx-font-family: 'Arial Black'; -fx-font-size: " + fs + "px;");
        label.setAlignment(Pos.CENTER);
        label.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        label.setWrapText(true);

        getChildren().add(label);
    }

    //
    public GlowingLabelWithBorder(String text) {
        this(text, 220, 70, 17); // 
    }

    public void setText(String text) {
        label.setText(text);
    }

    public GlowingLabel getLabel() {
        return label;
    }

    public void setSelected(boolean selected) {
        if (selected) {
            if (!getStyleClass().contains("glowing-label-border-selected"))
                getStyleClass().add("glowing-label-border-selected");
            label.setGlowOrange(true);
        } else {
            getStyleClass().remove("glowing-label-border-selected");
            label.setGlowOrange(false);
        }
    }
}

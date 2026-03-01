package lv.lenc;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

public class LabeledCheckRow extends HBox {
    private final GlowingLabel label;
    private final CheckBox checkBox;

    public LabeledCheckRow(String text, boolean initialChecked) {
        super(UiScaleHelper.scale(8)); // расстояние между label и галочкой

        this.label = new GlowingLabel(text);
        this.label.setFont(Font.font("Arial", FontWeight.BOLD, UiScaleHelper.scaleX(16))); // масштабируемый шрифт

        this.checkBox = new CheckBox();
        this.checkBox.setSelected(initialChecked);


        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        setAlignment(Pos.CENTER_LEFT);
        setSpacing(UiScaleHelper.scaleX(8));
        setPadding(new Insets(0, UiScaleHelper.scaleX(12), 0, UiScaleHelper.scaleX(12)));
        setMaxWidth(Double.MAX_VALUE);
        double baseFont = 16; // база под FullHD
        double scaledFont = UiScaleHelper.scaleY(baseFont);

        checkBox.setStyle("-fx-font-size: " + scaledFont + "px;");
        getChildren().addAll(label, spacer, checkBox);
        applyStyle();
    }

    public CheckBox getCheckBox() {
        return checkBox;
    }

    public Label getLabel() {
        return label;
    }

    public void setLabel(String text) {
        label.setText(text);
    }

    public void applyStyle() {
        checkBox.getStyleClass().add("custom-checkbox");
    }

    public void setOnCheckedChange(Runnable action) {
        checkBox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (action != null) action.run();
        });
    }
}

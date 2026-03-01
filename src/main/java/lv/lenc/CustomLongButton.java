package lv.lenc;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.effect.Glow;
import javafx.scene.text.Font;

public class CustomLongButton extends MyButton {

    private static final PseudoClass PSEUDO_SELECTED = PseudoClass.getPseudoClass("selected");

    private final boolean isGreen;
    private final double strengthGlow;
    private final double strengthGlowMAX;

    private final BooleanProperty selected = new SimpleBooleanProperty(false);
    private final Glow glow = new Glow(0);

    public CustomLongButton(String text, boolean isGreen,
                            double widthFullHD, double heightFullHD, double fontSizeFullHD,
                            double strengthGlow, double strengthGlowMAX) {
        super(text);

        this.isGreen = isGreen;
        this.strengthGlow = strengthGlow;
        this.strengthGlowMAX = strengthGlowMAX;

        double width = UiScaleHelper.scaleX(widthFullHD);
        double height = UiScaleHelper.scaleY(heightFullHD);
        double fontSize = UiScaleHelper.scaleY(fontSizeFullHD);

        setPrefSize(width, height);
        setMaxSize(width, height);
        setAlignment(Pos.CENTER);
        setEffect(glow);

        setFont(Font.font("Arial Black", fontSize));

        double paddingTopBottom = UiScaleHelper.scaleY(4);
        double paddingLeftRight = UiScaleHelper.scaleX(10);
        setPadding(new Insets(paddingTopBottom, paddingLeftRight, paddingTopBottom, paddingLeftRight));

        // классы для CSS
        getStyleClass().add("long-btn");
        getStyleClass().add(isGreen ? "green" : "red");

        // selected -> включаем псевдокласс :selected (CSS будет менять картинку)
        selected.addListener((obs, oldVal, newVal) ->
                pseudoClassStateChanged(PSEUDO_SELECTED, newVal)
        );

        setupHandlers();
        glow.setLevel(strengthGlow);
    }

    private void setupHandlers() {
        setOnMouseEntered(e -> {
            if (!isSelected() && !isDisabled()) {
                glow.setLevel(strengthGlowMAX);
            }
        });

        setOnMouseExited(e -> {
            if (isSelected()) {
                glow.setLevel(0);
                return;
            }
            if (!isDisabled()) {
                glow.setLevel(strengthGlow);
            }
        });

        setOnMousePressed(e -> {
            if (isSelected()) return;
            // стиль не трогаем — :pressed отработает в CSS
        });

        setOnMouseReleased(e -> {
            if (isSelected()) return;
            // стиль не трогаем — вернётся normal/hover сам
        });

        setOnMouseClicked(e -> {
            if (isSelected()) {
                e.consume(); // как у тебя: если выбран — отменяем клики
            }
        });

        disabledProperty().addListener((obs, oldVal, disabled) -> {
            glow.setLevel(disabled ? 0.0 : strengthGlow);
        });
    }

    // ===== PUBLIC (как у тебя) =====

    public void select() {
        setSelected(true);
        glow.setLevel(strengthGlow);
        setMouseTransparent(true);
        javafx.application.Platform.runLater(() -> setMouseTransparent(false));
    }

    public void deselect() {
        setSelected(false);
        glow.setLevel(strengthGlow);
    }

    public boolean isSelected() {
        return selected.get();
    }

    public void setSelected(boolean value) {
        selected.set(value);
    }

    public BooleanProperty selectedProperty() {
        return selected;
    }
}
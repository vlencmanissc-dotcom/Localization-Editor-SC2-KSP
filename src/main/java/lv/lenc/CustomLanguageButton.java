package lv.lenc;

import javafx.beans.value.ChangeListener;
import javafx.scene.control.Button;
import javafx.scene.Scene;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.text.Font;

public class CustomLanguageButton extends Button implements Disabable {
    private final double widthFullHD;
    private final double heightFullHD;
    private final double fontSizeFullHD;
    private final ChangeListener<Number> sceneScaleListener = (obs, oldV, newV) -> refreshScaledSizing();

    public CustomLanguageButton(String text, double widthFullHD, double heightFullHD, double fontSizeFullHD) {
        super(text);

        this.widthFullHD = widthFullHD;
        this.heightFullHD = heightFullHD;
        this.fontSizeFullHD = fontSizeFullHD;

        // CSS class
        getStyleClass().add("lang-btn");
        applyScaledSizing();
        blockSecondaryClickVisualState();
        bindScaleRefresh();
        UiSoundManager.bindBnetButton(this);
    }

    private void applyScaledSizing() {
        double width = UiScaleHelper.SCREEN_WIDTH * (widthFullHD / 1920.0);
        double height = UiScaleHelper.SCREEN_HEIGHT * (heightFullHD / 1080.0);
        double fontSize = UiScaleHelper.scaleFont(fontSizeFullHD, 10.0);

        setMinSize(width, height);
        setPrefSize(width, height);
        setMaxSize(width, height);
        setFont(Font.font("Arial Black", fontSize));
        setStyle("-fx-font-size: " + fontSize + "px; -fx-font-weight: 900;");
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
    public void disable(Boolean bol) {
        setDisable(bol != null && bol);
        setOpacity(1); // 
    }
}

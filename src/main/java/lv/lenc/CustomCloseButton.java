package lv.lenc;

import javafx.scene.control.Button;

/**
 * Close button (X).
 *
 * Java responsibilities:
 *  - responsive sizing
 *  - providing a stable CSS hook
 *
 * CSS responsibilities:
 *  - textures for normal/hover/pressed states
 *  - transparent background, no borders
 */
public class CustomCloseButton extends Button {

    // FullHD reference: 26px at 1080p height
    private static final double SIZE_RATIO = 26.0 / 1080.0;

    public CustomCloseButton() {
        // CSS class hook (all visuals live in CSS)
        getStyleClass().add("close-button");

        // Responsive sizing
        double buttonSize = UiScaleHelper.SCREEN_HEIGHT * SIZE_RATIO;
        setPrefSize(buttonSize, buttonSize);
        setMinSize(buttonSize, buttonSize);
        setMaxSize(buttonSize, buttonSize);

        // Behavior-only settings
        setFocusTraversable(false);
        setPickOnBounds(true);
        UiSoundManager.bindBnetButton(this);
    }
}

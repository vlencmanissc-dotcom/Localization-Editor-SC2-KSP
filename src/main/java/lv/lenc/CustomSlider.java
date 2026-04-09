package lv.lenc;

import javafx.application.Platform;
import javafx.scene.input.MouseButton;
import javafx.scene.control.Slider;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

public class CustomSlider extends Slider {

    private final Rectangle fill = new Rectangle();
    private Region track;
    private Region thumb;
    private boolean inited = false;

    public CustomSlider(double min, double max, double value) {
        super(min, max, value);

        // Previously: setMaxWidth(scaleX(266)) — keep as base "current size"
        UiSoundManager.bindSlider(this);
        double width = UiScaleHelper.scaleX(304);
        setMaxWidth(width);
        setPrefWidth(width);
        setMinWidth(Region.USE_PREF_SIZE);

        // CSS hook
        getStyleClass().add("custom-slider");

        // Wait until skin is created, then init nodes
        skinProperty().addListener((obs, oldSkin, newSkin) -> {
            if (newSkin != null) {
                Platform.runLater(this::initFillAndScaleParts);
            }
        });
    }

    private void initFillAndScaleParts() {
        if (inited) return;

        track = (Region) lookup(".track");
        thumb = (Region) lookup(".thumb");
        if (track == null || thumb == null) return;



        track.setMinHeight(UiScaleHelper.scaleY(12));
        track.setPrefHeight(UiScaleHelper.scaleY(12));
        track.setMaxHeight(UiScaleHelper.scaleY(12));

        thumb.setMinSize(UiScaleHelper.scaleY(24), UiScaleHelper.scaleY(24));
        thumb.setPrefSize(UiScaleHelper.scaleY(24), UiScaleHelper.scaleY(24));
        thumb.setMaxSize(UiScaleHelper.scaleY(24), UiScaleHelper.scaleY(24));

        double controlHeight = UiScaleHelper.scaleY(44);
        setMinHeight(controlHeight);
        setPrefHeight(controlHeight);
        setMaxHeight(controlHeight);

        track.setMouseTransparent(true);
        thumb.setMouseTransparent(true);

        setPickOnBounds(true);
        // 2) Custom fill — fully scaled as well
        fill.setHeight(UiScaleHelper.scaleY(8));
        fill.setFill(Color.web("#ffb85c"));
        fill.setManaged(false);
        fill.setMouseTransparent(true);

        if (!getChildren().contains(fill)) {
            getChildren().add(fill);
        }

        // Update fill when geometry changes
        thumb.layoutXProperty().addListener((o, a, b) -> updateFill());
        track.layoutXProperty().addListener((o, a, b) -> updateFill());
        track.layoutYProperty().addListener((o, a, b) -> updateFill());
        track.heightProperty().addListener((o, a, b) -> updateFill());
        widthProperty().addListener((o, a, b) -> updateFill());
        setOnMousePressed(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                updateValueFromMouse(e.getX());
            }
        });
        setOnMouseDragged(e -> {
            if (e.isPrimaryButtonDown()) {
                updateValueFromMouse(e.getX());
            }
        });
        inited = true;
        updateFill();
    }

    private void updateFill() {
        if (track == null || thumb == null) return;

        double trackStart = track.getLayoutX();
        double thumbLeft  = thumb.getLayoutX();

        // Previously: offset = 2 (not scaled) — caused misalignment at different scales
        double offset = UiScaleHelper.scaleX(2);

        fill.setWidth(Math.max(0, thumbLeft - trackStart - offset));
        fill.setLayoutX(trackStart + offset);

        // Center vertically relative to track height
        fill.setLayoutY(track.getLayoutY() + (track.getHeight() - fill.getHeight()) / 2.0);
    }

    @Override
    protected void layoutChildren() {
        super.layoutChildren();

        // If skin/layout recalculated geometry — ensure fill stays aligned
        updateFill();
    }
    @Override
    public boolean contains(double localX, double localY) {
        double extra = UiScaleHelper.scaleY(10); //
        return localX >= 0 && localX <= getWidth()
                && localY >= -extra && localY <= getHeight() + extra;
    }
    private void updateValueFromMouse(double mouseX) {
        double percent = mouseX / getWidth();
        percent = Math.max(0.0, Math.min(1.0, percent));
        setValue(getMin() + percent * (getMax() - getMin()));
    }
}

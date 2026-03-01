package lv.lenc;

import javafx.application.Platform;
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

        // Было: setMaxWidth(scaleX(266)) — оставляем как базовый “текущий размер”
        double width = UiScaleHelper.scaleX(266);
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


        double trackHeight = UiScaleHelper.scaleY(6);
        double thumbSize   = UiScaleHelper.scaleY(16);

        track.setMinHeight(trackHeight);
        track.setPrefHeight(trackHeight);
        track.setMaxHeight(trackHeight);

        thumb.setMinSize(thumbSize, thumbSize);
        thumb.setPrefSize(thumbSize, thumbSize);
        thumb.setMaxSize(thumbSize, thumbSize);

        // 2) Твой fill — но тоже полностью scaled
        fill.setHeight(UiScaleHelper.scaleY(4));
        fill.setFill(Color.web("#ffd98a"));
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

        inited = true;
        updateFill();
    }

    private void updateFill() {
        if (track == null || thumb == null) return;

        double trackStart = track.getLayoutX();
        double thumbLeft  = thumb.getLayoutX();

        // Было: offset = 2 (НЕ scaled) — из-за этого на других масштабах “едет”
        double offset = UiScaleHelper.scaleX(2);

        fill.setWidth(Math.max(0, thumbLeft - trackStart - offset));
        fill.setLayoutX(trackStart + offset);

        // Центрируем по высоте дорожки
        fill.setLayoutY(track.getLayoutY() + (track.getHeight() - fill.getHeight()) / 2.0);
    }

    @Override
    protected void layoutChildren() {
        super.layoutChildren();

        // Если скин/лейаут что-то пересчитал — fill должен быть на месте
        updateFill();
    }
}
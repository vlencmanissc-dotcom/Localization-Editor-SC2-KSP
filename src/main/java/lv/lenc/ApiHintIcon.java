package lv.lenc;

import javafx.animation.PauseTransition;
import javafx.geometry.Bounds;
import javafx.geometry.Rectangle2D;
import javafx.geometry.Pos;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

public final class ApiHintIcon extends StackPane {
    private static final String ICON_RESOURCE = "/Assets/Textures/sc2_ui_glues_icons_purchasewarning.png";
    private static Image sharedIconImage;

    private final Tooltip tooltip;
    private final PauseTransition showDelay = new PauseTransition(Duration.millis(160));

    public ApiHintIcon(String hintText, double iconSizePx, double tooltipFontPx) {
        double iconSize = Math.max(12.0, iconSizePx);

        setAlignment(Pos.CENTER);
        CustomCursorManager.applyHyperlinkCursor(this);
        setPickOnBounds(true);
        setPrefSize(iconSize, iconSize);
        setMinSize(iconSize, iconSize);
        setMaxSize(iconSize, iconSize);

        Image iconImage = loadSharedIconImage();
        if (iconImage != null && !iconImage.isError()) {
            ImageView imageView = new ImageView(iconImage);
            imageView.setFitWidth(iconSize);
            imageView.setFitHeight(iconSize);
            imageView.setPreserveRatio(true);
            imageView.setMouseTransparent(true);
            getChildren().add(imageView);
        } else {
            Region fallback = new Region();
            fallback.setPrefSize(iconSize, iconSize);
            fallback.setMinSize(iconSize, iconSize);
            fallback.setMaxSize(iconSize, iconSize);
            fallback.setStyle(
                    "-fx-background-color: rgba(255, 195, 0, 0.92);"
                            + "-fx-border-color: rgba(20, 20, 20, 0.9);"
                            + "-fx-border-width: 1;"
                            + "-fx-background-radius: 3;"
                            + "-fx-border-radius: 3;"
            );
            fallback.setMouseTransparent(true);
            getChildren().add(fallback);
        }

        tooltip = new Tooltip(hintText == null ? "" : hintText);
        tooltip.setWrapText(true);
        tooltip.setShowDelay(Duration.millis(160));
        tooltip.setHideDelay(Duration.millis(60));
        tooltip.setMaxWidth(Math.max(160.0, iconSize * 6.0));
        tooltip.setStyle(
                "-fx-background-color: rgba(52, 24, 8, 0.96);"
                        + "-fx-text-fill: #ffe27a;"
                        + "-fx-border-color: rgba(255, 168, 52, 0.95);"
                        + "-fx-border-width: 1.5;"
                        + "-fx-font-size: " + Math.max(10.0, tooltipFontPx) + "px;"
                        + "-fx-padding: 7 9 7 9;"
                        + "-fx-background-radius: 6;"
                        + "-fx-border-radius: 6;"
        );

        showDelay.setOnFinished(e -> showTooltipAbove());

        setOnMouseEntered(e -> showDelay.playFromStart());
        setOnMouseExited(e -> {
            showDelay.stop();
            tooltip.hide();
        });
        setOnMouseMoved(e -> {
            if (tooltip.isShowing()) {
                relocateTooltipAbove();
            }
        });
    }

    public void setHintText(String hintText) {
        tooltip.setText(hintText == null ? "" : hintText);
    }

    private static Image loadSharedIconImage() {
        if (sharedIconImage == null) {
            java.net.URL iconUrl = ApiHintIcon.class.getResource(ICON_RESOURCE);
            if (iconUrl != null) {
                sharedIconImage = new Image(iconUrl.toExternalForm(), true);
            }
        }
        return sharedIconImage;
    }

    private void showTooltipAbove() {
        Bounds bounds = localToScreen(getBoundsInLocal());
        if (bounds == null || tooltip == null) {
            return;
        }
        // Show first, then place exactly above center.
        tooltip.show(this, bounds.getMinX(), bounds.getMinY());
        relocateTooltipAbove();
    }

    private void relocateTooltipAbove() {
        Bounds bounds = localToScreen(getBoundsInLocal());
        if (bounds == null || tooltip == null || !tooltip.isShowing()) {
            return;
        }

        Rectangle2D screen = javafx.stage.Screen.getPrimary().getVisualBounds();
        double margin = 8.0;

        // Preferred: to the RIGHT of icon, top-aligned.
        double preferredX = bounds.getMaxX() + 10.0;
        double preferredY = bounds.getMinY();

        double x = preferredX;
        if (x + tooltip.getWidth() > screen.getMaxX() - margin) {
            // Fallback only if it does not fit on the right.
            x = bounds.getMinX() - tooltip.getWidth() - 10.0;
        }

        double y = preferredY;
        if (y + tooltip.getHeight() > screen.getMaxY() - margin) {
            y = screen.getMaxY() - tooltip.getHeight() - margin;
        }
        if (y < screen.getMinY() + margin) {
            y = screen.getMinY() + margin;
        }

        tooltip.setX(Math.max(screen.getMinX() + margin, x));
        tooltip.setY(y);
    }
}

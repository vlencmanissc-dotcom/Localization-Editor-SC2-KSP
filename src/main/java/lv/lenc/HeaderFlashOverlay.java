package lv.lenc;

import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.geometry.Point2D;
import javafx.util.Duration;

public class HeaderFlashOverlay {
    private final Pane overlayPane = new Pane();
    private static final String HOOK_KEY = "lv.lenc.headerFlashHook";

    public HeaderFlashOverlay(CustomTableView tableView, Node root) {
        overlayPane.setPickOnBounds(false); // does not block mouse clicks
        overlayPane.setMouseTransparent(true);
        overlayPane.getStyleClass().add("header-flash-overlay");

        // dynamic value (cannot be calculated in CSS directly)
        double borderWidth = UiScaleHelper.scaleY(6);

        Runnable installHooks = () -> {
            tableView.lookupAll(".column-header").forEach(header -> {
                if (!(header instanceof Region region)) return;
                if (region.getProperties().putIfAbsent(HOOK_KEY, Boolean.TRUE) != null) {
                    return;
                }
                installHeaderHook(region, root, borderWidth);
            });
        };

        tableView.skinProperty().addListener((obs, oldSkin, newSkin) -> Platform.runLater(installHooks));
        tableView.widthProperty().addListener((obs, oldVal, newVal) -> Platform.runLater(installHooks));
        tableView.heightProperty().addListener((obs, oldVal, newVal) -> Platform.runLater(installHooks));
        tableView.getColumns().forEach(c ->
                c.visibleProperty().addListener((obs, oldV, newV) -> Platform.runLater(installHooks)));
        Platform.runLater(installHooks);
    }

    private void installHeaderHook(Region region, Node root, double borderWidth) {
        Region flash = new Region();
        flash.getStyleClass().add("header-flash-border");
        flash.setOpacity(0.0);
        flash.setMouseTransparent(true);
        flash.setStyle("-fx-border-image-width: " + borderWidth + ";");
        overlayPane.getChildren().add(flash);

        Runnable sync = () -> {
            Point2D scenePos = region.localToScene(0, 0);
            if (scenePos == null || root.getScene() == null) {
                return;
            }
            Point2D rootLocal = root.sceneToLocal(scenePos);
            flash.setLayoutX(rootLocal.getX());
            flash.setLayoutY(rootLocal.getY());
            flash.setPrefWidth(region.getWidth());
            flash.setPrefHeight(region.getHeight());
        };

        region.layoutBoundsProperty().addListener((obs, oldBounds, newBounds) -> sync.run());
        region.localToSceneTransformProperty().addListener((obs, oldV, newV) -> sync.run());
        Platform.runLater(sync);

        region.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> {
            if (e.getButton() != MouseButton.PRIMARY || e.getClickCount() != 1) {
                return;
            }
            Platform.runLater(() -> playFlash(region, root, flash, borderWidth));
        });
    }

    private void playFlash(Region region, Node root, Region flash, double borderWidth) {
        Point2D scenePos = region.localToScene(0, 0);
        if (scenePos == null || root.getScene() == null) {
            return;
        }
        Point2D rootLocal = root.sceneToLocal(scenePos);

        double w = region.getWidth();
        double h = region.getHeight();

        flash.setLayoutX(rootLocal.getX());
        flash.setLayoutY(rootLocal.getY());
        flash.setPrefWidth(w);
        flash.setPrefHeight(h);

        flash.setOpacity(1.0);
        FadeTransition flashOut = new FadeTransition(Duration.seconds(0.88), flash);
        flashOut.setFromValue(1.0);
        flashOut.setToValue(0.0);
        flashOut.play();

        Region borderFade = new Region();
        borderFade.getStyleClass().add("header-flash-border");
        borderFade.setStyle("-fx-border-image-width: " + borderWidth + ";");
        borderFade.setLayoutX(rootLocal.getX());
        borderFade.setLayoutY(rootLocal.getY());
        borderFade.setPrefWidth(w);
        borderFade.setPrefHeight(h);
        borderFade.setOpacity(1.0);
        borderFade.setMouseTransparent(true);

        overlayPane.getChildren().add(borderFade);

        FadeTransition borderFadeOut = new FadeTransition(Duration.seconds(0.88), borderFade);
        borderFadeOut.setFromValue(1.0);
        borderFadeOut.setToValue(0.0);
        borderFadeOut.setOnFinished(ev -> overlayPane.getChildren().remove(borderFade));
        borderFadeOut.play();
    }

    public Pane getOverlayPane() {
        return overlayPane;
    }
}

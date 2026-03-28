package lv.lenc;

import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.geometry.Point2D;
import javafx.util.Duration;

public class HeaderFlashOverlay {
    private final Pane overlayPane = new Pane();

    public HeaderFlashOverlay(CustomTableView tableView, Node root) {
        overlayPane.setPickOnBounds(false); // does not block mouse clicks
        overlayPane.getStyleClass().add("header-flash-overlay");

        // dynamic value (cannot be calculated in CSS directly)
        double borderWidth = UiScaleHelper.scaleY(6);

        Platform.runLater(() -> {
            tableView.lookupAll(".column-header").forEach(header -> {
                if (!(header instanceof Region)) return;
                Region region = (Region) header;

                Region flash = new Region();
                flash.getStyleClass().add("header-flash-border");
                flash.setOpacity(0.0);
                flash.setMouseTransparent(true);

                //only dynamic properties here:
                flash.setStyle("-fx-border-image-width: " + borderWidth + ";");

                overlayPane.getChildren().add(flash);

                // track header size and position
                region.layoutBoundsProperty().addListener((obs, oldBounds, newBounds) -> {
                    Point2D scenePos = region.localToScene(0, 0);
                    Point2D rootLocal = root.sceneToLocal(scenePos);

                    flash.setLayoutX(rootLocal.getX());
                    flash.setLayoutY(rootLocal.getY());
                    flash.setPrefWidth(newBounds.getWidth());
                    flash.setPrefHeight(newBounds.getHeight());
                });

                region.setOnMouseClicked(e -> Platform.runLater(() -> {
                    Point2D scenePos = region.localToScene(0, 0);
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
                    borderFade.setStyle("-fx-border-image-width: " + borderWidth + ";"); // 
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
                }));
            });
        });
    }

    public Pane getOverlayPane() {
        return overlayPane;
    }
}

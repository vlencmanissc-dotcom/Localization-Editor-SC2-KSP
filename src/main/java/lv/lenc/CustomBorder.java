package lv.lenc;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

public class CustomBorder extends StackPane {
    ImageView bg;
    ImageView bgH;
    ImageView bgF;
    ImageView lB;
    ImageView lS;
    ImageView lSR;
    ImageView lT;
    public ImageView scan1, scan2, scan3;

    public CustomBorder(CustomTableView table) {
        double sw = UiScaleHelper.SCREEN_WIDTH;
        double sh = UiScaleHelper.SCREEN_HEIGHT;

        bg = BackgroundApply(UiScaleHelper.scaleX(800), UiScaleHelper.scaleY(600));
        bgH = BackgroundHighLightApply(UiScaleHelper.scaleX(800), UiScaleHelper.scaleY(600));
        bgF = HighLightBackgroundFlash(UiScaleHelper.scaleX(800), UiScaleHelper.scaleY(600));

        lT = LineHighLightTop(UiScaleHelper.scaleX(281), UiScaleHelper.scaleY(17));
        lB = LineHighLightBottom(UiScaleHelper.scaleX(546), UiScaleHelper.scaleY(14));
        lS = LinesHighLightsSide(UiScaleHelper.scaleX(20), UiScaleHelper.scaleY(75));
        lSR = LinesHighLightsSide(UiScaleHelper.scaleX(20), UiScaleHelper.scaleY(75));

        Platform.runLater(() -> {
            bg.fitWidthProperty().bind(table.widthProperty().multiply(1.08));
            bg.fitHeightProperty().bind(table.heightProperty().multiply(1.2));

            bgH.fitWidthProperty().bind(bg.fitWidthProperty());
            bgH.fitHeightProperty().bind(bg.fitHeightProperty());

            bgF.fitWidthProperty().bind(bg.fitWidthProperty());
            bgF.fitHeightProperty().bind(bg.fitHeightProperty());

        });

        bgH.setClip(bgF);

        setAlignment(lT, Pos.TOP_RIGHT);
        lT.setTranslateX(-UiScaleHelper.scaleX(348));
        lT.setTranslateY(-UiScaleHelper.scaleY(10));

        setAlignment(lB, Pos.BOTTOM_CENTER);
        lB.setTranslateX(-UiScaleHelper.scaleX(165));
        lB.setTranslateY(UiScaleHelper.scaleY(5));

        setAlignment(lS, Pos.CENTER_LEFT);
        lS.setTranslateY(-UiScaleHelper.scaleY(85));
        lS.setTranslateX(UiScaleHelper.scaleX(142));

        setAlignment(lSR, Pos.BOTTOM_RIGHT);
        lSR.setTranslateY(-UiScaleHelper.scaleY(46));
        lSR.setTranslateX(-UiScaleHelper.scaleX(140));

        scan1 = ApplyImageScanLines(
                UiScaleHelper.scaleX(440), UiScaleHelper.scaleY(172),
                new double[]{0, 2.35, 2.95, 3.85, 5.25, 13.5},
                new double[]{0, 0, 0.25, 0.25, 0, 0, 0}
        );
        setAlignment(scan1, Pos.TOP_LEFT);
        scan1.setTranslateX(-UiScaleHelper.scaleX(10));
        scan1.setTranslateY(-UiScaleHelper.scaleY(40));

        scan2 = ApplyImageScanLines(
                UiScaleHelper.scaleX(500), UiScaleHelper.scaleY(182),
                new double[]{0, 2, 3.4, 4.6, 9.0},
                new double[]{0, 0, 0.25, 0, 0}
        );
        setAlignment(scan2, Pos.BOTTOM_RIGHT);
        scan2.setTranslateX(UiScaleHelper.scaleX(15));
        scan2.setTranslateY(UiScaleHelper.scaleY(10));

        scan3 = ApplyImageScanLines(
                UiScaleHelper.scaleX(234), UiScaleHelper.scaleY(192),
                new double[]{0, 0.65, 2.55, 3.85, 8.0},
                new double[]{0, 0, 0.25, 0, 0}
        );
        setAlignment(scan3, Pos.TOP_RIGHT);
        scan3.setTranslateX(-UiScaleHelper.scaleX(40));
        scan3.setTranslateY(-UiScaleHelper.scaleY(79));

        this.getChildren().addAll(bg, table, scan1, scan2, scan3, bgH, lB, lS, lSR, lT);
        this.setMouseTransparent(true);
        this.getStylesheets().add(
                getClass().getResource("/Assets/Style/Custom-Border.css").toExternalForm()
        );

    }

    public ImageView BackgroundApply(double width, double height) {
        ImageView image = new ImageView(new Image(getClass().getResource("/Assets/Textures/ui_nova_archives_backgroundframe.png").toExternalForm()));
        image.setFitWidth(width);
        image.setFitHeight(height);
        image.getStyleClass().add("background-frame");
        return image;
    }

    public ImageView BackgroundHighLightApply(double width, double height) {
        ImageView image = new ImageView(new Image(getClass().getResource("/Assets/Textures/ui_nova_archives_backgroundframehighlight.png").toExternalForm()));
        image.setFitWidth(width);
        image.setFitHeight(height);
        image.getStyleClass().add("background-frame-highlight");
        return image;

    }

    public ImageView HighLightBackgroundFlash(double width, double height) {
        ImageView image = new ImageView(new Image(getClass().getResource("/Assets/Textures/ui_nova_archives_backgroundframehighlightmask.png").toExternalForm()));
        image.setFitWidth(width);
        image.setFitHeight(height);

        TranslateTransition anim = new TranslateTransition(Duration.seconds(8), image);
        anim.setFromY(-UiScaleHelper.scaleY(800));
        anim.setToY(UiScaleHelper.scaleY(800));
        anim.setInterpolator(Interpolator.EASE_OUT);
        anim.setCycleCount(Animation.INDEFINITE);
        anim.play();

        return image;
    }

    public ImageView LineHighLightBottom(double width, double height) {
        ImageView image = new ImageView(new Image(getClass().getResource("/Assets/Textures/ui_nova_archives_backgroundframe_lights_bottom.png").toExternalForm()));
        image.setFitWidth(width);
        image.setFitHeight(height);
        return image;
    }

    public ImageView LinesHighLightsSide(double width, double height) {
        ImageView image = new ImageView(new Image(getClass().getResource("/Assets/Textures/ui_nova_archives_backgroundframe_lights_side.png").toExternalForm()));
        image.setFitWidth(width);
        image.setFitHeight(height);
        return image;
    }

    public ImageView LineHighLightTop(double width, double height) {
        ImageView image = new ImageView(new Image(getClass().getResource("/Assets/Textures/ui_nova_archives_backgroundframe_lights_top.png").toExternalForm()));
        image.setFitWidth(width);
        image.setFitHeight(height);
        return image;
    }

    public ImageView ApplyImageScanLines(double width, double height, double[] keyTimes, double[] opacities) {
        ImageView scan = new ImageView(new Image(getClass().getResource("/Assets/Textures/ui_nova_archives_backgroundframe_scanlines.png").toExternalForm()));
        scan.setFitWidth(width);
        scan.setFitHeight(height);
        scan.setOpacity(0);

        Timeline timeline = new Timeline();
        for (int i = 0; i < keyTimes.length; i++) {
            timeline.getKeyFrames().add(
                    new KeyFrame(Duration.seconds(keyTimes[i]),
                            new KeyValue(scan.opacityProperty(), opacities[i], Interpolator.LINEAR))
            );
        }
        timeline.setCycleCount(Animation.INDEFINITE);
        timeline.play();

        return scan;
    }

    public void setTableLightingVisible(boolean visible) {
        scan1.setVisible(visible);
        scan2.setVisible(visible);
        scan3.setVisible(visible);
    }
}

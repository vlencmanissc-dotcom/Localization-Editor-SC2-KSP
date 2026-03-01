package lv.lenc;

import javafx.animation.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.util.Duration;
import javafx.scene.image.WritableImage;
import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;

import java.util.Arrays;

public class BackgroundGridLayer extends Pane {
    public final Pane gridLayer = new Pane();   // Слой для клеточек
    public final Pane pointLayer = new Pane();  // Слой для точек
    public final Pane shimmerContainer = new Pane();
    private ImageView swipe;
    public ImageView blurredLights; // Дымка-слой
    private final double TILE_WIDTH = 51;  // Тут размеры PNG, можно оставить
    private final double TILE_HEIGHT = 96;

    private final double sw = UiScaleHelper.SCREEN_WIDTH;
    private final double sh = UiScaleHelper.SCREEN_HEIGHT;
    public BackgroundGridLayer() {
        setMouseTransparent(true);

        widthProperty().addListener((obs, oldVal, newVal) -> {
            gridLayer.setPrefWidth(newVal.doubleValue());
            pointLayer.setPrefWidth(newVal.doubleValue());
            if (blurredLights != null) blurredLights.setFitWidth(newVal.doubleValue());
        });
        heightProperty().addListener((obs, oldVal, newVal) -> {
            gridLayer.setPrefHeight(newVal.doubleValue());
            pointLayer.setPrefHeight(newVal.doubleValue());
            if (blurredLights != null) blurredLights.setFitHeight(newVal.doubleValue());
        });

        gridLayer.setPrefWidth(getWidth());
        gridLayer.setPrefHeight(getHeight());
        pointLayer.setPrefWidth(getWidth());
        pointLayer.setPrefHeight(getHeight());

        showShimmers();
        showBlurredLights();
        showGrid();
        showPoints();
        showSwipeLine();

        getChildren().addAll(blurredLights, gridLayer, pointLayer, shimmerContainer, swipe);
        playBlurredLightsAnimation();
    }

    // ====== BLURRED LIGHTS (SC2 breathing style) ======
    private void showBlurredLights() {
        Image img = new Image(getClass().getResource("/Assets/Textures/ui_nova_login_backgroundlights.png").toExternalForm());
        blurredLights = new ImageView(img);
        blurredLights.setPreserveRatio(false);
        blurredLights.setFitWidth(UiScaleHelper.scaleX(1920));
        blurredLights.setFitHeight(UiScaleHelper.scaleY(1200)); // 1200 = 1.111 * 1080
        blurredLights.setOpacity(38.0 / 255.0);
        blurredLights.setMouseTransparent(true);
    }

    public void playBlurredLightsAnimation() {
        Timeline alphaTimeline = new Timeline(
                new KeyFrame(Duration.seconds(0),   new KeyValue(blurredLights.opacityProperty(), 38.0/255.0, Interpolator.EASE_OUT)),
                new KeyFrame(Duration.seconds(2.5), new KeyValue(blurredLights.opacityProperty(), 0.0, Interpolator.EASE_IN)),
                new KeyFrame(Duration.seconds(4.5), new KeyValue(blurredLights.opacityProperty(), 38.0/255.0, Interpolator.EASE_OUT))
        );
        alphaTimeline.setCycleCount(Animation.INDEFINITE);

        Timeline xTimeline = new Timeline(
                new KeyFrame(Duration.seconds(0),   new KeyValue(blurredLights.translateXProperty(), 0, Interpolator.EASE_OUT)),
                new KeyFrame(Duration.seconds(2.0), new KeyValue(blurredLights.translateXProperty(), -sw * 0.0781, Interpolator.EASE_IN)), // -150/1920=0.0781
                new KeyFrame(Duration.seconds(4.5), new KeyValue(blurredLights.translateXProperty(), 0, Interpolator.EASE_OUT))
        );
        xTimeline.setCycleCount(Animation.INDEFINITE);

        Timeline yTimeline = new Timeline(
                new KeyFrame(Duration.seconds(0),   new KeyValue(blurredLights.translateYProperty(), 0, Interpolator.EASE_OUT)),
                new KeyFrame(Duration.seconds(2.0), new KeyValue(blurredLights.translateYProperty(), UiScaleHelper.scaleY(150), Interpolator.EASE_IN)),
                new KeyFrame(Duration.seconds(4.5), new KeyValue(blurredLights.translateYProperty(), 0, Interpolator.EASE_OUT))
        );

        yTimeline.setCycleCount(Animation.INDEFINITE);

        alphaTimeline.play();
        xTimeline.play();
        yTimeline.play();
    }

    private void showShimmers() {
        shimmerContainer.getChildren().clear();

        // --- Vertical shimmers ---
        double[] verticalOffsets = Arrays.stream(new int[] {
                52, 154, 256, 358, 460, 562, 664, 766, 868, 970, 1072, 1174,
                -50, -152, -254, -356, -458, -560, -662, -764, -866, -968, -1070, -1172
        }).mapToDouble(UiScaleHelper::scaleX).toArray();
        double[] verticalDelays = {
                5.0, 21.0, 9.0, 16.0, 4.0, 14.0, 22.0, 1.0, 6.0, 13.0, 20.0, 9.5,
                2.4, 17.5, 11.2, 0.1, 13.2, 21.6, 7.5, 15.7, 3.0, 18.0, 8.5, 23.0
        };

        boolean[] verticalUp = {
                false, true,  false, true,  false, true,
                false, true,  false, true,  false, true,
                true,  false, true,  false, true,  false,
                true,  false, true,  false, true,  false
        };

        // --- Horizontal shimmers ---
        double[] horizontalOffsets = Arrays.stream(new int[] {
                -52, -154, -256, -359, -460, -562,
                50,  151,  253,  355,  457,  559
        }).mapToDouble(UiScaleHelper::scaleY).toArray();

        double[] horizontalDelays = {
                6.5, 17.4, 21.0,  3.7, 14.1, 19.6,
                7.1, 12.7, 22.1, 10.6, 18.9,  5.1
        };

        boolean[] horizontalReverse = {
                false, true,  false, true,  false, true,
                true,  false, true,  false, true,  false
        };

        String maskPath = getClass().getResource("/Assets/Textures/ui_nova_storymode_bggrid.png").toExternalForm();
        String shimmerPathVertical = getClass().getResource("/Assets/Textures/ui_nova_storymode_bggrid_shimmer.png").toExternalForm();
        String shimmerPathHorizontal = getClass().getResource("/Assets/Textures/ui_nova_storymode_bggrid_shimmer_sideways.png").toExternalForm();

        double centerX = sw / 2;
        double centerY = sh / 2;
        double shimmerHeight = UiScaleHelper.scaleY(2700); // 2.5 * 1080
        double shimmerWidth = UiScaleHelper.scaleX(3000);  // 1.5625 * 1920
        // Вертикальные shimmer
        for (int i = 0; i < verticalOffsets.length; i++) {
            ShimmerStrip strip = new ShimmerStrip(
                    maskPath, shimmerPathVertical,
                    TILE_WIDTH,         // 51px
                    shimmerHeight,           // 2700/1080 = 2.5 (если хочешь более адаптивно - умножай)
                    ShimmerStrip.Direction.VERTICAL,
                    verticalUp[i],
                    verticalDelays[i],
                    10.5,
                    0, 0
            );

            strip.setLayoutX(centerX + verticalOffsets[i] - TILE_WIDTH / 2.0);
            strip.setLayoutY(0);
            shimmerContainer.getChildren().add(strip);
        }

        // Горизонтальные shimmer
        for (int i = 0; i < horizontalOffsets.length; i++) {

            ShimmerStrip strip = new ShimmerStrip(
                    maskPath, shimmerPathHorizontal,
                    shimmerWidth,    // 3000 / 1920 = 1.5625
                    TILE_WIDTH,     // 51px
                    ShimmerStrip.Direction.HORIZONTAL,
                    horizontalReverse[i],
                    horizontalDelays[i],
                    10.5,
                    0, 0
            );
            strip.setLayoutX(0);
            strip.setLayoutY(centerY + horizontalOffsets[i] - TILE_WIDTH / 2.0);
            shimmerContainer.getChildren().add(strip);
        }

        shimmerContainer.prefWidthProperty().bind(widthProperty());
        shimmerContainer.prefHeightProperty().bind(heightProperty());
    }

    // =========== COLORIZE PNG ============
    public static Image colorizeTexture(Image src, Color targetColor) {
        int w = (int) src.getWidth();
        int h = (int) src.getHeight();
        WritableImage dst = new WritableImage(w, h);
        PixelReader reader = src.getPixelReader();
        PixelWriter writer = dst.getPixelWriter();

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                Color px = reader.getColor(x, y);
                if (px.getOpacity() > 0) {
                    Color newColor = new Color(
                            targetColor.getRed(),
                            targetColor.getGreen(),
                            targetColor.getBlue(),
                            px.getOpacity()
                    );
                    writer.setColor(x, y, newColor);
                } else {
                    writer.setColor(x, y, px);
                }
            }
        }
        return dst;
    }

    // ======= GRID & POINTS LOGIC =======
    public void showGrid() {
        fillLayer(
                gridLayer,
                "/Assets/Textures/ui_nova_storymode_bggrid.png",
                Color.rgb(0, 255, 168), // #00FFA8
                00.25 // Альфа = 0.066
        );
    }
    public void showPoints() {
        fillLayer(
                pointLayer,
                "/Assets/Textures/ui_nova_storymode_bgpointgrid.png",
                Color.rgb(40, 101, 103), // #286567
                0.25 // Альфа = 1.0, как в SC2
        );
    }
    private void fillLayer(Pane targetLayer, String texturePath, Color baseColor, double alpha) {
        targetLayer.getChildren().clear();

        Image src = new Image(getClass().getResource(texturePath).toExternalForm());
        Image colorized = colorizeTexture(src, baseColor);

        double screenW = sw;
        double screenH = sh;

        int cols = (int) Math.ceil(screenW / colorized.getWidth()) + 1;
        int rows = (int) Math.ceil(screenH / colorized.getHeight()) + 1;

        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                ImageView img = new ImageView(colorized);
                img.setX(x * colorized.getWidth());
                img.setY(y * colorized.getHeight());
                img.setOpacity(alpha);
                img.setSmooth(false);
                targetLayer.getChildren().add(img);
            }
        }
    }

    // ======= ALPHA CONTROL =======
    public void setGridAlpha(double alpha) { updateAlpha(gridLayer, alpha); }
    public void setPointAlpha(double alpha) { updateAlpha(pointLayer, alpha); }
    public void setFlashAlpha(double alpha) { if (swipe != null) swipe.setOpacity(clampAlpha(alpha)); }
    public double getGridAlpha() { return getAlpha(gridLayer); }
    public double getPointAlpha() { return getAlpha(pointLayer); }
    public double getFlashAlpha() { return (swipe != null) ? swipe.getOpacity() : 0; }
    private double clampAlpha(double a) { return Math.max(0, Math.min(1, a)); }

    private void updateAlpha(Pane layer, double alpha) {
        layer.getChildren().forEach(node -> {
            if (node instanceof ImageView) ((ImageView) node).setOpacity(clampAlpha(alpha));
        });
    }
    private double getAlpha(Pane layer) {
        return layer.getChildren().stream()
                .filter(n -> n instanceof ImageView)
                .map(n -> (ImageView) n)
                .map(ImageView::getOpacity)
                .findFirst().orElse(0.0);
    }

    // ======= SWIPE LINE =======
    private void showSwipeLine() {
        String texturePath = getClass().getResource("/Assets/Textures/ui_nova_equipmentupgrades_novapaperdoll_boxswipe.png").toExternalForm();
        Image image = new Image(texturePath);

        swipe = new ImageView(image);
        swipe.setFitWidth(UiScaleHelper.scaleX(1970)); // 1.025 * 1920
        swipe.setPreserveRatio(false);
        swipe.setOpacity(1);
        swipe.setTranslateX(UiScaleHelper.scaleX(-860)); // -0.45 * 1920
        swipe.setMouseTransparent(true);

        TranslateTransition move = new TranslateTransition(Duration.seconds(13), swipe);
        move.setFromX(UiScaleHelper.scaleX(-23));  // -23 / 1920
        move.setFromY(UiScaleHelper.scaleY(-750)); // -750 / 1080
        move.setToY(UiScaleHelper.scaleY(1730));   // 1080 + 650
        move.setInterpolator(Interpolator.LINEAR);
        move.setCycleCount(Animation.INDEFINITE);
        move.play();
    }
}

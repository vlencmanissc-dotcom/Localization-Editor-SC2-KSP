package lv.lenc;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.geometry.Pos;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

public class SquareDiscordURL extends StackPane {

    private static final String DISCORD_URL = "https://discord.com/invite/UKYgsB6Zrx";

    // Design-time (HD) margins. They will be scaled via UiScaleHelper.
    private static final double WIPE_MARGIN_X_HD = 9.0;
    private static final double WIPE_MARGIN_Y_HD = 16.0;

    private static final Interpolator FAST_OUT =
            Interpolator.SPLINE(0.0, 0.0, 0.15, 1.0);
    private static final Interpolator FAST_IN =
            Interpolator.SPLINE(0.85, 0.0, 1.0, 1.0);

    private final StackPane wipeHolder = new StackPane();
    private final Region wipe = new Region();
    private final double size;
    private Timeline wipeAnim;

    public SquareDiscordURL() {

        getStyleClass().add("square-discord-url");
        CustomCursorManager.applyHyperlinkCursor(this);
        setPickOnBounds(true);

        double sizeW = UiScaleHelper.SCREEN_WIDTH  * (76.0 / 1920.0);
        double sizeH = UiScaleHelper.SCREEN_HEIGHT * (76.0 / 1080.0);
        this.size = Math.min(sizeW, sizeH);
        double size = this.size;
        // Icon layer (CSS-driven)
        StackPane icon = new StackPane();
        icon.getStyleClass().add("discord-icon");
        icon.setMouseTransparent(true);
        // --- ICON SIZE (36px at 80px design => 0.45) ---
        double iconSize = size * (36.0 / 80.0); // = size * 0.45
        icon.setMinSize(iconSize, iconSize);
        icon.setPrefSize(iconSize, iconSize);
        icon.setMaxSize(iconSize, iconSize);

        // IMPORTANT: make CSS background scale with the region
        // This sets only the background-size, images from CSS (normal/hover/pressed) remain.
        icon.setStyle("-fx-background-size: " + iconSize + "px " + iconSize + "px;");
        // === SIZE: design = 80x80 at 1920x1080 ===

        setPrefSize(size, size);
        setMinSize(size, size);
        setMaxSize(size, size);
        // Wipe layer (CSS-driven)
        wipe.getStyleClass().add("wipe-layer");
        wipe.setMouseTransparent(true);
        wipe.setOpacity(0);

        // --- WIPE SIZE (design: 70px height at 80px button) ---
        double wipeH = size * (75.0 / 80.0);     // 0.875
        wipe.setMinHeight(wipeH);
        wipe.setPrefHeight(wipeH);
        wipe.setMaxHeight(wipeH);
    //
        setStyle("-fx-background-size: " + size + "px " + size + "px;");
    // --- WIPE BACKGROUND SIZE (design: 104px bg-height at 80px button) ---
        double wipeBgH = size * (112.0 / 80.0);  // 1.30
        wipe.setStyle("-fx-background-size: 100% " + wipeBgH + "px;");
        wipeHolder.setMouseTransparent(true);
        wipeHolder.getChildren().add(wipe);
        wipeHolder.setMinSize(size, size);
        wipeHolder.setPrefSize(size, size);
        wipeHolder.setMaxSize(size, size);
        // Center layers
        StackPane.setAlignment(icon, Pos.CENTER);
        StackPane.setAlignment(wipeHolder, Pos.CENTER);

        // Clip ONLY the wipe area with margins (does not shrink the wipe itself)
        Rectangle wipeClip = new Rectangle();
        wipeHolder.setClip(wipeClip);

        // Order: icon first, wipe on top
        getChildren().addAll(icon, wipeHolder);
        wipeHolder.toFront();

        // The wipe should stretch to the full available width
        wipe.minWidthProperty().bind(wipeHolder.widthProperty());
        wipe.prefWidthProperty().bind(wipeHolder.widthProperty());
        wipe.maxWidthProperty().bind(wipeHolder.widthProperty());

        // IMPORTANT: do not bind wipe height (it is defined by CSS, e.g. 12px)
        widthProperty().addListener((o, a, b) -> rebuild());
        heightProperty().addListener((o, a, b) -> rebuild());
        sceneProperty().addListener((o, a, b) -> rebuild());

        hoverProperty().addListener((obs, was, isNow) -> {
            if (wipeAnim == null) rebuild();
            if (wipeAnim == null) return;
            if (isNow) playForward();
            else playReverse();
        });
        addEventHandler(MouseEvent.MOUSE_ENTERED, e -> {
            if (!isDisabled()) {
                UiSoundManager.playNovaHover();
            }
        });
        addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
            if (!isDisabled() && e.getButton() == MouseButton.PRIMARY) {
                UiSoundManager.playNovaClick();
            }
        });

        setOnMouseClicked(e -> {
            if (isDisabled()) return;
            if (e.getButton() != MouseButton.PRIMARY) return;

            try {
                java.awt.Desktop.getDesktop().browse(new java.net.URI(DISCORD_URL));
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });

        // Also rebuild once CSS is ready
        rebuild();
    }

    private void rebuild() {
        applyCss();
        layout();

        double holderW = wipeHolder.getWidth();
        double holderH = wipeHolder.getHeight();
        if (holderW <= 1 || holderH <= 1) return;

        // Apply scaled clip margins
        double marginX = size * (WIPE_MARGIN_X_HD / 80.0);
        double marginY = size * (WIPE_MARGIN_Y_HD / 80.0);

        Rectangle clip = (Rectangle) wipeHolder.getClip();
        clip.setX(marginX);
        clip.setY(marginY);
        clip.setWidth(Math.max(0, holderW - marginX * 2.0));
        clip.setHeight(Math.max(0, holderH - marginY * 2.0));

        // Read wipe height from CSS layout bounds
        double wipeH = wipe.getBoundsInParent().getHeight();
        if (wipeH <= 1) wipeH = holderH * 0.2; // proportional fallback (not a fixed pixel fallback)

        // Vertical insets for the wipe animation path (proportional)
        double insetY = holderH * 0.20;

        // Compute animation start/end so it fully travels through the control
        double startY = -(holderH / 2.0) - (wipeH / 2.0) + insetY;
        double endY   =  (holderH / 2.0) + (wipeH / 2.0) - insetY;

        double T = 1.0;

        wipeAnim = new Timeline(
                new KeyFrame(Duration.seconds(0.00),
                        new KeyValue(wipe.translateYProperty(), startY, FAST_OUT),
                        new KeyValue(wipe.opacityProperty(), 0.0, FAST_OUT)
                ),

                // Quick visible start removes perceived delay
                new KeyFrame(Duration.seconds(0.03),
                        new KeyValue(wipe.opacityProperty(), 10 / 255.0, FAST_OUT)
                ),

                // SC2-like opacity curve (same proportions)
                new KeyFrame(Duration.seconds(T * 0.30),
                        new KeyValue(wipe.opacityProperty(), 100.0 / 255.0, FAST_OUT)
                ),
                new KeyFrame(Duration.seconds(T * 0.55),
                        new KeyValue(wipe.opacityProperty(), 240.0 / 255.0, FAST_OUT)
                ),

                new KeyFrame(Duration.seconds(T),
                        new KeyValue(wipe.translateYProperty(), endY, FAST_IN),
                        new KeyValue(wipe.opacityProperty(), 0.0, FAST_IN)
                )
        );
    }

    private void playForward() {
        if (wipeAnim == null) return;

        wipeAnim.setRate(1);
        wipeAnim.play();
    }

    private void playReverse() {
        if (wipeAnim == null) return;

        wipeAnim.setRate(-1);
        wipeAnim.play();
    }
}

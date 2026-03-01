package lv.lenc;

import javafx.animation.*;
import javafx.scene.image.*;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.util.Duration;

public class ShimmerStrip extends StackPane {

    public enum Direction {
        HORIZONTAL,
        VERTICAL
    }

    public ShimmerStrip(
            String alphaGridPath,
            String shimmerPath,
            double width,
            double height,
            Direction direction,
            boolean reverse,
            double shimmerDelay,
            double durationSeconds,
            double offsetX,
            double offsetY
    ) {

        // Маска с wrap по всей высоте shimmer
        Image maskImg = new Image(alphaGridPath);
        final int tileW = (int) Math.round(maskImg.getWidth());
        final int tileH = (int) Math.round(maskImg.getHeight());
        final int iw = (int) Math.floor(width);
        final int ih = (int) Math.floor(height);

        WritableImage tiledMask = new WritableImage(iw, ih);
        PixelWriter pw = tiledMask.getPixelWriter();
        PixelReader pr = maskImg.getPixelReader();

        if (direction == Direction.VERTICAL) {
            final int uvOffsetX = -(tileW / 2); // ~= -0.5*tileW
            for (int y = 0; y < ih; y++) {                    // <-- ih
                final int yy = y % tileH;                     // wrap Y
                for (int x = 0; x < iw; x++) {                // <-- iw
                    int xx = (x + uvOffsetX) % tileW;         // wrap X со сдвигом
                    if (xx < 0) xx += tileW;                  // нормализуем отрицательный модуль
                    pw.setColor(x, y, pr.getColor(xx, yy));
                }
            }
        } else {
            final int uvOffsetY = +(tileH / 2);               // ~= +0.5*tileH
            for (int y = 0; y < ih; y++) {                    // <-- ih
                int yy = (y + uvOffsetY) % tileH;             // wrap Y со сдвигом
                if (yy < 0) yy += tileH;
                for (int x = 0; x < iw; x++) {                // <-- iw
                    int xx = x % tileW;                       // wrap X
                    pw.setColor(x, y, pr.getColor(xx, yy));
                }
            }
        }
        ImageView mask = new ImageView(tiledMask);

        Image shimmerImg = new Image(shimmerPath); // Horizontal or vertical


        ImageView shimmer = new ImageView(recolorShimmer(shimmerImg));
        shimmer.setFitWidth(width);
        shimmer.setFitHeight(height);
        shimmer.setVisible(false);
        shimmer.setClip(mask);
     //   shimmer.setOpacity(0.9);
       shimmer.setOpacity(0.325);

        getChildren().add(shimmer);

        shimmer.setTranslateX(offsetX);
        shimmer.setTranslateY(offsetY);

        shimmer.getParent().applyCss();
        shimmer.getParent().layout();

        PauseTransition pause = new PauseTransition(Duration.seconds(shimmerDelay));
        pause.setOnFinished(e -> shimmer.setVisible(true));

        TranslateTransition move = new TranslateTransition(Duration.seconds(durationSeconds), shimmer);

        if (direction == Direction.VERTICAL) {
            double startY = reverse ? height : -height;
            double endY = reverse ? -height : height;
            move.setFromY(startY);
            move.setToY(endY);
        } else if (direction == Direction.HORIZONTAL) {
            double startX = reverse ? width : -width;
            double endX = reverse ? -width : width;
            move.setFromX(startX);
            move.setToX(endX);
        }

        move.setInterpolator(Interpolator.LINEAR);

        SequentialTransition seq = new SequentialTransition(shimmer, pause, move);
        seq.setCycleCount(Animation.INDEFINITE);
        seq.play();
    }

    private Image recolorShimmer(Image src) {
        int w = (int) src.getWidth();
        int h = (int) src.getHeight();
        WritableImage out = new WritableImage(w, h);

        PixelReader reader = src.getPixelReader();
        PixelWriter writer = out.getPixelWriter();

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                Color px = reader.getColor(x, y);
                if (px.getOpacity() > 0) {
                    writer.setColor(x, y, Color.rgb(0, 255, 168, px.getOpacity()));
                }
            }
        }
        return out;
    }
}

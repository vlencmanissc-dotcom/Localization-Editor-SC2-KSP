package lv.lenc;

import javafx.stage.Screen;

public class UiScaleHelper {
    public static final double SCREEN_WIDTH;
    public static final double SCREEN_HEIGHT;
    public static final double BASE_W = 1920.0;
    public static final double BASE_H = 1080.0;

    public static double sx(double v, double w) { return v * (w / BASE_W); }
    public static double sy(double v, double h) { return v * (h / BASE_H); }
    static {
        SCREEN_WIDTH = Screen.getPrimary().getBounds().getWidth();
        SCREEN_HEIGHT = Screen.getPrimary().getBounds().getHeight();
    }

    // масштабирует по ширине (относительно fullhd)
    public static double scaleX(double pxFullHD) {
        return SCREEN_WIDTH * pxFullHD / 1920.0;
    }
    // по высоте
    public static double scaleY(double pxFullHD) {
        return SCREEN_HEIGHT * pxFullHD / 1080.0;
    }
    public static double scale(double pxFullHD) {
        double scaleX = Math.round(SCREEN_WIDTH / 1920.0);
        double scaleY = Math.round(SCREEN_HEIGHT / 1080.0);
        return pxFullHD * Math.round(Math.sqrt((scaleX * scaleX + scaleY * scaleY) / 2));
    }
    public static double s(double v, double w, double h) {
        double k = Math.min(w / BASE_W, h / BASE_H);
        return v * k;
    }

    public static double SCREEN_WIDTH(int i) {
        return 0;
    }
}
//import javafx.geometry.Rectangle2D;
//import javafx.stage.Screen;
//
//public final class UiScaleHelper {
//
//    private static final double BASE_W = 1920.0;
//    private static final double BASE_H = 1080.0;
//
//    // ВАЖНО: лучше брать VisualBounds (без панели задач), чтобы не было "сдвигов"
//    private static final Rectangle2D VB = Screen.getPrimary().getVisualBounds();
//    private static final double W = VB.getWidth();
//    private static final double H = VB.getHeight();
//
//    // Стратегия: чаще всего для UI лучше MIN (влезть целиком без обрезки)
//    private static final double SCALE = computeScaleMin(W, H);
//
//    private UiScaleHelper() {}
//
//    private static double computeScaleMin(double w, double h) {
//        return Math.min(w / BASE_W, h / BASE_H);
//    }
//
//    // Альтернатива: диагональ (иногда приятнее для “игрового” UI)
//    private static double computeScaleDiag(double w, double h) {
//        double bw = BASE_W, bh = BASE_H;
//        double d1 = Math.sqrt(w*w + h*h);
//        double d0 = Math.sqrt(bw*bw + bh*bh);
//        return d1 / d0;
//    }
//
//    /** Базовый коэффициент масштаба */
//    public static double scaleFactor() {
//        return SCALE;
//    }
//
//    /** Масштабирование значения из FullHD */
//    public static double scale(double pxFullHD) {
//        return pxFullHD * SCALE;
//    }
//
//    /**
//     * Snap к физическому пикселю, чтобы НЕ было “пляски” картинок.
//     * В JavaFX 1 logical px может быть != 1 physical px (HiDPI).
//     */
//    public static double snap(double value) {
//        double outScale = Screen.getPrimary().getOutputScaleX(); // обычно X==Y
//        return Math.round(value * outScale) / outScale;
//    }
//
//    /** scale + snap сразу */
//    public static double s(double pxFullHD) {
//        return snap(scale(pxFullHD));
//    }
//}

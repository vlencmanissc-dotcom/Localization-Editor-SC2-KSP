package lv.lenc;

import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.stage.PopupWindow;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.stage.Screen;

public class UiScaleHelper {
    public static double REAL_SCREEN_WIDTH;
    public static double REAL_SCREEN_HEIGHT;
    public static double SCREEN_WIDTH;
    public static double SCREEN_HEIGHT;
    public static final double BASE_W = 1920.0;
    public static final double BASE_H = 1080.0;
    public static final double FONT_BOOST = 1.30;
    public static double UNIFORM_SCALE;
    public static double CONTENT_OFFSET_X;
    public static double CONTENT_OFFSET_Y;

    static {
        refreshFromPrimaryScreen();
    }

    public static synchronized void refreshFromPrimaryScreen() {
        Rectangle2D bounds = Screen.getPrimary().getBounds();
        applyScale(bounds.getWidth(), bounds.getHeight());
    }

    public static synchronized void refreshFromScene(Scene scene) {
        if (scene == null) {
            refreshFromPrimaryScreen();
            return;
        }
        Window window = scene.getWindow();
        // Ignore detached scenes: controls inside popups can get a Scene before
        // the window is attached/shown. Using that temporary size shrinks global scale.
        if (window == null || !window.isShowing()) {
            return;
        }
        // Ignore popup scenes (e.g. search popup/combo popup), they must not
        // redefine global app scale.
        if (window instanceof PopupWindow) {
            return;
        }
        // Only main stage should drive global scaling.
        if (!(window instanceof Stage)) {
            return;
        }
        double w = scene.getWidth();
        double h = scene.getHeight();
        if (w <= 1.0 || h <= 1.0) {
            // Keep last valid scale if stage is not yet laid out.
            return;
        }
        applyScale(w, h);
    }

    private static void applyScale(double w, double h) {
        REAL_SCREEN_WIDTH = w;
        REAL_SCREEN_HEIGHT = h;
        UNIFORM_SCALE = Math.min(REAL_SCREEN_WIDTH / BASE_W, REAL_SCREEN_HEIGHT / BASE_H);
        SCREEN_WIDTH = BASE_W * UNIFORM_SCALE;
        SCREEN_HEIGHT = BASE_H * UNIFORM_SCALE;
        CONTENT_OFFSET_X = (REAL_SCREEN_WIDTH - SCREEN_WIDTH) * 0.5;
        CONTENT_OFFSET_Y = (REAL_SCREEN_HEIGHT - SCREEN_HEIGHT) * 0.5;
    }

    public static double sx(double v, double w) { return v * (w / BASE_W); }
    public static double sy(double v, double h) { return v * (h / BASE_H); }

    // Keep Full-HD proportions on any aspect ratio (no X/Y stretching).
    public static double scaleX(double pxFullHD) {
        return pxFullHD * UNIFORM_SCALE;
    }

    public static double scaleY(double pxFullHD) {
        return pxFullHD * UNIFORM_SCALE;
    }

    public static double scale(double pxFullHD) {
        return pxFullHD * UNIFORM_SCALE;
    }

    public static double scaleFont(double pxFullHD) {
        return pxFullHD * UNIFORM_SCALE * FONT_BOOST;
    }

    public static double scaleFont(double pxFullHD, double minPx) {
        return Math.max(scaleFont(pxFullHD), minPx);
    }

    public static double s(double v, double w, double h) {
        double k = Math.min(w / BASE_W, h / BASE_H);
        return v * k;
    }

    public static double SCREEN_WIDTH(int i) {
        return SCREEN_WIDTH;
    }
}

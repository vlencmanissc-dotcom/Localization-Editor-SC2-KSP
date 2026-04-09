package lv.lenc;

import java.net.URL;
import java.util.EnumMap;
import java.util.Map;

import javafx.scene.Cursor;
import javafx.scene.ImageCursor;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;

public final class CustomCursorManager {
    private static final double EDGE_THRESHOLD = 24.0;
    private static final String INSTALL_KEY = "lv.lenc.customCursorManager.installed";

    private enum CursorKind {
        DEFAULT("/Assets/Cursors/cursor.png", 2, 2),
        HYPERLINK("/Assets/Cursors/cursor-hyperlink.png", 4, 2),
        GRIP_OPEN("/Assets/Cursors/cursor-grip-open.png", 16, 16),
        GRIP_CLOSED("/Assets/Cursors/cursor-grip-closed.png", 16, 16),
        DRAGFRAME("/Assets/Cursors/cursor-dragframe-nova.png", 16, 16),
        EDGE_TOP("/Assets/Cursors/cursor-edgescroll-top.png", 16, 16),
        EDGE_TOP_RIGHT("/Assets/Cursors/cursor-edgescroll-top-right.png", 16, 16),
        EDGE_RIGHT("/Assets/Cursors/cursor-edgescroll-right.png", 16, 16),
        EDGE_BOTTOM_RIGHT("/Assets/Cursors/cursor-edgescroll-bottom-right.png", 16, 16),
        EDGE_BOTTOM("/Assets/Cursors/cursor-edgescroll-bottom.png", 16, 16),
        EDGE_BOTTOM_LEFT("/Assets/Cursors/cursor-edgescroll-bottom-left.png", 16, 16),
        EDGE_LEFT("/Assets/Cursors/cursor-edgescroll-left.png", 16, 16),
        EDGE_TOP_LEFT("/Assets/Cursors/cursor-edgescroll-top-left.png", 16, 16);

        final String resourcePath;
        final double hotspotX;
        final double hotspotY;

        CursorKind(String resourcePath, double hotspotX, double hotspotY) {
            this.resourcePath = resourcePath;
            this.hotspotX = hotspotX;
            this.hotspotY = hotspotY;
        }
    }

    private static final Map<CursorKind, Cursor> CURSORS = new EnumMap<>(CursorKind.class);

    private CustomCursorManager() {}

    public static Cursor defaultCursor() {
        return cursor(CursorKind.DEFAULT);
    }

    public static Cursor hyperlinkCursor() {
        return cursor(CursorKind.HYPERLINK);
    }

    public static Cursor gripOpenCursor() {
        return cursor(CursorKind.GRIP_OPEN);
    }

    public static Cursor gripClosedCursor() {
        return cursor(CursorKind.GRIP_CLOSED);
    }

    public static Cursor dragFrameCursor() {
        return cursor(CursorKind.DRAGFRAME);
    }

    public static void applyHyperlinkCursor(Node node) {
        if (node != null) {
            node.setCursor(hyperlinkCursor());
        }
    }

    public static void applyDragGripCursor(Node node) {
        if (node == null) {
            return;
        }

        final boolean[] pressed = {false};
        node.setCursor(gripOpenCursor());

        node.addEventHandler(MouseEvent.MOUSE_ENTERED, event -> {
            if (!pressed[0]) {
                node.setCursor(gripOpenCursor());
            }
        });
        node.addEventHandler(MouseEvent.MOUSE_EXITED, event -> {
            if (!pressed[0]) {
                node.setCursor(defaultCursor());
            }
        });
        node.addEventHandler(MouseEvent.MOUSE_PRESSED, event -> {
            if (event.getButton() == MouseButton.PRIMARY) {
                pressed[0] = true;
                node.setCursor(gripClosedCursor());
            }
        });
        node.addEventHandler(MouseEvent.MOUSE_RELEASED, event -> {
            pressed[0] = false;
            node.setCursor(node.isHover() ? gripOpenCursor() : defaultCursor());
        });
    }

    public static void applyResizeCursor(Node node) {
        if (node != null) {
            node.setCursor(dragFrameCursor());
        }
    }

    public static void applyDefaultCursor(Node node) {
        if (node != null) {
            node.setCursor(defaultCursor());
        }
    }

    public static void install(Scene scene) {
        if (scene == null || scene.getProperties().putIfAbsent(INSTALL_KEY, Boolean.TRUE) != null) {
            return;
        }

        scene.setCursor(defaultCursor());
        scene.addEventFilter(MouseEvent.MOUSE_MOVED, event -> updateSceneCursor(scene, event));
        scene.addEventFilter(MouseEvent.MOUSE_DRAGGED, event -> updateSceneCursor(scene, event));
        scene.addEventFilter(MouseEvent.MOUSE_EXITED_TARGET, event -> scene.setCursor(defaultCursor()));
    }

    private static void updateSceneCursor(Scene scene, MouseEvent event) {
        if (scene == null) {
            return;
        }

        Node target = event.getPickResult() == null ? null : event.getPickResult().getIntersectedNode();
        remapSystemCursor(target);
        if (shouldKeepNodeCursor(target)) {
            scene.setCursor(defaultCursor());
            return;
        }

        Cursor edgeCursor = edgeCursorFor(event.getSceneX(), event.getSceneY(), scene.getWidth(), scene.getHeight());
        scene.setCursor(edgeCursor != null ? edgeCursor : defaultCursor());
    }

    private static boolean shouldKeepNodeCursor(Node node) {
        Node current = node;
        while (current != null) {
            if (current.getCursor() != null) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    private static void remapSystemCursor(Node node) {
        Node current = node;
        while (current != null) {
            Cursor cursor = current.getCursor();
            if (cursor == Cursor.TEXT || cursor == Cursor.DEFAULT) {
                current.setCursor(defaultCursor());
            } else if (cursor == Cursor.HAND) {
                current.setCursor(hyperlinkCursor());
            } else if (cursor == Cursor.MOVE) {
                current.setCursor(gripOpenCursor());
            } else if (cursor == Cursor.SE_RESIZE) {
                current.setCursor(dragFrameCursor());
            }
            current = current.getParent();
        }
    }

    private static Cursor edgeCursorFor(double x, double y, double width, double height) {
        boolean top = y <= EDGE_THRESHOLD;
        boolean bottom = y >= height - EDGE_THRESHOLD;
        boolean left = x <= EDGE_THRESHOLD;
        boolean right = x >= width - EDGE_THRESHOLD;

        if (top && left) return cursor(CursorKind.EDGE_TOP_LEFT);
        if (top && right) return cursor(CursorKind.EDGE_TOP_RIGHT);
        if (bottom && left) return cursor(CursorKind.EDGE_BOTTOM_LEFT);
        if (bottom && right) return cursor(CursorKind.EDGE_BOTTOM_RIGHT);
        if (top) return cursor(CursorKind.EDGE_TOP);
        if (bottom) return cursor(CursorKind.EDGE_BOTTOM);
        if (left) return cursor(CursorKind.EDGE_LEFT);
        if (right) return cursor(CursorKind.EDGE_RIGHT);
        return null;
    }

    private static Cursor cursor(CursorKind kind) {
        return CURSORS.computeIfAbsent(kind, CustomCursorManager::loadCursor);
    }

    private static Cursor loadCursor(CursorKind kind) {
        URL url = CustomCursorManager.class.getResource(kind.resourcePath);
        if (url == null) {
            return Cursor.DEFAULT;
        }
        Image image = new Image(url.toExternalForm(), false);
        if (image.isError()) {
            return Cursor.DEFAULT;
        }
        return new ImageCursor(image, kind.hotspotX, kind.hotspotY);
    }
}

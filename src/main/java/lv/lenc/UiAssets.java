package lv.lenc;

import java.net.URL;

final class UiAssets {
    private static final String TEXTURE_ROOT = externalForm("/Assets/Textures/");
    private static final String STYLE_ROOT = "/Assets/Style/";

    private UiAssets() {
    }

    static String textureRoot() {
        return TEXTURE_ROOT;
    }

    static String css(String fileName) {
        return externalForm(STYLE_ROOT + fileName);
    }

    static URL resource(String resourcePath) {
        URL url = UiAssets.class.getResource(resourcePath);
        if (url == null) {
            throw new IllegalStateException("Resource not found: " + resourcePath);
        }
        return url;
    }

    private static String externalForm(String resourcePath) {
        return resource(resourcePath).toExternalForm();
    }
}

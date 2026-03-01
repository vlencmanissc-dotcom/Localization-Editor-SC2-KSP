package lv.lenc;

import javafx.beans.binding.Bindings;
import javafx.scene.Node;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.transform.Scale;

public class ResolutionScaler extends StackPane {

    private final double designWidth;
    private final double designHeight;
    private final Scale scale = new Scale();

    public ResolutionScaler(Node content, double designWidth, double designHeight) {
        this.designWidth = designWidth;
        this.designHeight = designHeight;

        getChildren().add(content);
        content.getTransforms().add(scale);

        // Если это Region — фиксируем логический размер
        if (content instanceof Region region) {
            region.setMinSize(designWidth, designHeight);
            region.setPrefSize(designWidth, designHeight);
            region.setMaxSize(designWidth, designHeight);
        }

        scale.xProperty().bind(Bindings.createDoubleBinding(() -> {
            double scaleX = getWidth() / designWidth;
            double scaleY = getHeight() / designHeight;
            return Math.min(scaleX, scaleY); // FIT режим
        }, widthProperty(), heightProperty()));

        scale.yProperty().bind(scale.xProperty());
    }
}
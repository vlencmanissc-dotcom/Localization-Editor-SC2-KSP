package lv.lenc;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.layout.Region;

public class CustomComboBoxClassic<T> extends ComboBox<T> implements Disabable {

    private final String texturePath;
    private final boolean isGreen;
    private final double fontSize;
    private final double dropdownFontSize;
    private final double paddingTop;
    private final double paddingLeft;
    private final double arrowRight;
    private final double borderRadius;
    private final double cellHeight;
    private final double cellRadius;
    private final double cellFontSize;
    private final int visibleRows;

    public CustomComboBoxClassic(String texturePath, boolean isGreen,
                                 double widthFullHD, double heightFullHD,
                                 double fontSizeFullHD, double dropdownFontSizeFullHD) {
        this(texturePath, isGreen, widthFullHD, heightFullHD, fontSizeFullHD, dropdownFontSizeFullHD, 20, 6);
    }

    public CustomComboBoxClassic(String texturePath, boolean isGreen,
                                 double widthFullHD, double heightFullHD,
                                 double fontSizeFullHD, double dropdownFontSizeFullHD,
                                 double cellHeightFullHD, int visibleRows) {
        super();
        this.texturePath = texturePath;
        this.isGreen = isGreen;

        double width = UiScaleHelper.scaleX(widthFullHD);
        double height = UiScaleHelper.scaleY(heightFullHD);
        this.fontSize = UiScaleHelper.scaleFont(fontSizeFullHD, 10.0);
        this.dropdownFontSize = UiScaleHelper.scaleFont(dropdownFontSizeFullHD, 9.0);

        this.paddingTop = UiScaleHelper.scaleY(-4);
        this.paddingLeft = UiScaleHelper.scaleX(25);
        this.arrowRight = UiScaleHelper.scaleX(20);
        this.borderRadius = UiScaleHelper.scaleY(8);

        this.cellHeight = UiScaleHelper.scaleY(cellHeightFullHD);
        this.cellRadius = UiScaleHelper.scaleY(3);
        this.cellFontSize = this.dropdownFontSize;
        this.visibleRows = Math.max(4, visibleRows);

        setPrefSize(width, height);
        setMaxSize(width, height);
        setVisibleRowCount(this.visibleRows);

        hideScrollBar();
        applyTextCells();
        applyBaseStyles();
        this.disabledProperty().addListener((obs, wasDisabled, isNowDisabled) -> updateStyle());
        updateStyle();

        getStyleClass().add("classic-combo");
        getStyleClass().add(isGreen ? "theme-green" : "theme-yellow");
    }

    public void disable(Boolean bol) {
        setDisable(bol);
        updateStyle();
    }

    private void updateStyle() {
        applyControlStyle(isHover(), isPressed());
    }

    private void applyBaseStyles() {
        applyControlStyle(false, false);

        skinProperty().addListener((obs, oldSkin, newSkin) -> {
            Platform.runLater(() -> {
                Node displayNode = lookup(".text");
                if (displayNode != null) {
                    displayNode.setStyle(
                            "-fx-font-size: " + fontSize + "px; " +
                                    "-fx-font-family: 'Arial Black'; " +
                                    "-fx-fill: " + textGradNormal() + ";"
                    );
                }
            });
        });

        Platform.runLater(() -> {
            Node listView = lookup(".list-view");
            applyPopupListStyle(listView);
        });

        Platform.runLater(() -> {
            Node arrowButton = lookup(".arrow-button");
            if (arrowButton != null) {
                arrowButton.setOpacity(0);
                arrowButton.setManaged(false);
            }
        });

        hoverProperty().addListener((obs, wasHover, isHover) -> applyControlStyle(isHover, isPressed()));
        pressedProperty().addListener((obs, wasPressed, isPressed) -> applyControlStyle(isHover(), isPressed));
        showingProperty().addListener((obs, wasShowing, isShowing) -> applyControlStyle(isHover(), isPressed()));
    }

    private void applyPopupListStyle(Node listView) {
        if (!(listView instanceof Region region)) {
            return;
        }

        boolean popupGreenTheme = isGreen;
        String borderColor = popupGreenTheme
                ? "limegreen"
                : "linear-gradient(from 0% 0% to 100% 100%, #FFF1A8, #E6B800)";
        String backgroundColor = popupGreenTheme ? "#001000" : "#201400";

        region.setStyle(
                "-fx-background-color: " + backgroundColor + ";" +
                        "-fx-border-color: " + borderColor + ";" +
                        "-fx-font-size: " + dropdownFontSize + "px;" +
                        "-fx-border-width: " + UiScaleHelper.scaleX(2) + "px;" +
                        "-fx-border-radius: " + borderRadius + "px;" +
                        "-fx-background-radius: " + borderRadius + "px;" +
                        "-fx-padding: " + UiScaleHelper.scaleY(1) + "px;"
        );
    }

    private void applyControlStyle(boolean hover, boolean pressed) {
        String normalTexture = isGreen ? "ui_glue_dropdownbutton_normal_terran.png" : "ui_glue_dropdownbutton_Yellow_normal_terran.png";
        String overTexture = isGreen ? "ui_glue_dropdownbutton_over_terran.png" : "ui_glue_dropdownbutton_Yellow_over_terran.png";
        String downTexture = isGreen ? "ui_glue_dropdownbutton_Pressed_terran.png" : "ui_glue_dropdownbutton_Yellow_Pressed_terran.png";
        String disabledTexture = isGreen ? "ui_glue_dropdownbutton_disabled_terran.png" : "ui_glue_dropdownbutton_Yellow_disabled_terran.png";
        String arrowNormal = isGreen ? "ui_glue_dropdownarrow_normal_terran.png" : "ui_glue_dropdownarrow_Yellow_normal_terran.png";
        String arrowOver = isGreen ? "ui_glue_dropdownarrow_over_terran.png" : "ui_glue_dropdownarrow_Yellow_normal_terran.png";
        String arrowPressed = isGreen ? "ui_glue_dropdownarrow_pressed_terran.png" : "ui_glue_dropdownarrow_Yellow_normal_terran.png";
        String arrowDisabled = "ui_glue_dropdownarrow_normal_terran.png";

        boolean disabled = isDisabled();
        String baseTexture = disabled
                ? disabledTexture
                : pressed
                ? downTexture
                : hover
                ? overTexture
                : normalTexture;
        String arrowTexture = disabled
                ? arrowDisabled
                : pressed
                ? arrowPressed
                : hover
                ? arrowOver
                : arrowNormal;
        String textFill = disabled ? textGradNormal() : (hover ? textGradHover() : textGradNormal());

        setStyle(
                "-fx-background-color: transparent; " +
                        "-fx-background-image: url('" + texturePath + baseTexture + "'), url('" + texturePath + arrowTexture + "'); " +
                        "-fx-background-size: 100% 100%, " + UiScaleHelper.scaleY(16) + "px " + UiScaleHelper.scaleY(16) + "px; " +
                        "-fx-background-repeat: no-repeat, no-repeat; " +
                        "-fx-background-position: center center, right " + arrowRight + "px center; " +
                        "-fx-background-insets: 0, 0; " +
                        "-fx-opacity: 1.0; " +
                        "-fx-padding: " + paddingTop + " 0 0 " + paddingLeft + "; " +
                        "-fx-alignment: center; " +
                        "-fx-font-size: " + fontSize + "px; " +
                        "-fx-font-family: 'Arial Black'; " +
                        "-fx-text-fill: " + textFill + ";"
        );
    }

    private void applyTextCells() {
        setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                    return;
                }

                setText(item.toString());
                setStyle(
                        "-fx-background-color: transparent;" +
                                "-fx-font-family: 'Arial Black';" +
                                "-fx-font-size: " + fontSize + "px;" +
                                "-fx-text-fill: " + textGradNormal() + ";" +
                                "-fx-alignment: center;" +
                                "-fx-padding: 0 " + (arrowRight + UiScaleHelper.scaleX(26)) + " 0 0;" +
                                "-fx-background-insets: 0;" +
                                "-fx-border-insets: 0;" +
                                "-fx-text-overrun: clip;"
                );
            }
        });

        setCellFactory(lv -> new ListCell<>() {
            {
                hoverProperty().addListener((obs, wasHover, isHover) -> refreshPopupCellStyle(this));
                selectedProperty().addListener((obs, wasSelected, isSelected) -> refreshPopupCellStyle(this));
            }

            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setStyle("");
                    return;
                }

                setText(item.toString());
                refreshPopupCellStyle(this);
            }
        });
    }

    private void refreshPopupCellStyle(ListCell<T> cell) {
        if (cell == null || cell.isEmpty() || cell.getItem() == null) {
            return;
        }

        boolean popupGreenTheme = isGreen;

        String baseStyle =
                "-fx-background-radius: " + cellRadius + "px;" +
                        "-fx-border-radius: " + cellRadius + "px;" +
                        "-fx-background-insets: 0 2 0 2;" +
                        "-fx-border-insets: 0 2 0 2;" +
                        "-fx-background-size: 100% 100%; " +
                        "-fx-background-repeat: no-repeat; " +
                        "-fx-font-family: 'Arial Black';" +
                        "-fx-font-size: " + cellFontSize + "px;" +
                        "-fx-pref-height: " + cellHeight + "px;" +
                        "-fx-min-height: " + cellHeight + "px;" +
                        "-fx-max-height: " + cellHeight + "px;" +
                        "-fx-padding: 0 " + UiScaleHelper.scaleX(10) + " 0 " + UiScaleHelper.scaleX(10) + ";" +
                        "-fx-alignment: center;" +
                        "-fx-text-overrun: clip;";

        if (cell.isSelected()) {
            cell.setStyle(
                    "-fx-border-color: " + (popupGreenTheme ? "darkgreen" : "#d6b100") + ";" +
                            "-fx-background-color: " + (popupGreenTheme ? "rgba(30, 80, 40, 0.6);" : "rgba(120, 90, 20, 0.6);") +
                            baseStyle +
                            "-fx-text-fill: " + textGradSelected() + ";"
            );
            return;
        }

        String background = cell.isHover()
                ? (popupGreenTheme ? "rgba(38, 96, 52, 0.32);" : "rgba(160, 110, 30, 0.3);")
                : (popupGreenTheme ? "#001000;" : "#201400;");
        String textFill = cell.isHover()
                ? textGradHover()
                : textGradNormal();

        cell.setStyle(
                "-fx-background-color: " + background +
                        baseStyle +
                        "-fx-text-fill: " + textFill + ";"
        );
    }

    private String textGradNormal() {
        return isGreen
                ? "linear-gradient(from 0% 0% to 100% 100%, #F4FFF4, #7CFF9A)"
                : "linear-gradient(from 0% 0% to 100% 100%, #FFFBE7, #F0C040)";
    }

    private String textGradHover() {
        return isGreen
                ? "linear-gradient(from 0% 0% to 100% 100%, #E8FFED, #4BFF7C)"
                : "linear-gradient(from 0% 0% to 100% 100%, #FFFFFF, #FFE066)";
    }

    private String textGradSelected() {
        return isGreen
                ? "linear-gradient(from 0% 0% to 100% 100%, #FFFFFF, #BFFFD0)"
                : "linear-gradient(from 0% 0% to 100% 100%, #FFFDEB, #FFE27A)";
    }

    private void hideScrollBar() {
        skinProperty().addListener((obs, oldSkin, newSkin) -> {
            if (newSkin != null) {
                Node scrollBar = lookup(".scroll-bar");
                if (scrollBar != null) {
                    scrollBar.setVisible(false);
                    scrollBar.setManaged(false);
                }
            }
            Platform.runLater(() -> {
                show();
                hide();
                Node listView = lookup(".list-view");
                applyPopupListStyle(listView);
            });
        });
        showingProperty().addListener((obs, wasShowing, isNowShowing) -> {
            if (isNowShowing) {
                Node scrollBar = lookup(".scroll-bar");
                if (scrollBar != null) {
                    scrollBar.setVisible(false);
                    scrollBar.setManaged(false);
                }
            }
        });
    }
}

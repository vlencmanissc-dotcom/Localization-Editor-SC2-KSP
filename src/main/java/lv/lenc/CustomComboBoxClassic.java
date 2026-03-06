package lv.lenc;

import javafx.application.Platform;
import javafx.geometry.Insets;
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

    // --- Constructor with relative fontSize support ---
    public CustomComboBoxClassic(String texturePath, boolean isGreen,
                                 double widthFullHD, double heightFullHD,
                                 double fontSizeFullHD, double dropdownFontSizeFullHD) {
        super();
        this.texturePath = texturePath;
        this.isGreen = isGreen;

        // 
        double width = UiScaleHelper.scaleX(widthFullHD);
        double height = UiScaleHelper.scaleY(heightFullHD);
        this.fontSize = UiScaleHelper.scale(fontSizeFullHD);
        this.dropdownFontSize = UiScaleHelper.scale(dropdownFontSizeFullHD);

        this.paddingTop = UiScaleHelper.scaleY(-4);
        this.paddingLeft = UiScaleHelper.scaleX(25);
        this.arrowRight = UiScaleHelper.scaleX(20);
        this.borderRadius = UiScaleHelper.scaleY(8);

        this.cellHeight = UiScaleHelper.scaleY(20);
        this.cellRadius = UiScaleHelper.scaleY(3);
        this.cellFontSize = this.dropdownFontSize;

        setPrefSize(width, height);
        setMaxSize(width, height);

        hideScrollBar();
        applyListItemStyles();
        this.disabledProperty().addListener((obs, wasDisabled, isNowDisabled) -> updateStyle());
        updateStyle();
        applyBaseStyles();
        setVisibleRowCount(6);
        hideScrollBar();
        getStyleClass().add("classic-combo");
        getStyleClass().add(isGreen ? "theme-green" : "theme-yellow");
        //setupPopupDownAndHideScroll();   // 
    }

    public void disable(Boolean bol) {
        this.setDisable(bol);
        updateStyle();
    }

    private void updateStyle() {
        String normalTexture = isGreen ? "ui_glue_dropdownbutton_normal_terran.png" : "ui_glue_dropdownbutton_Yellow_normal_terran.png";
        String disabledTexture = isGreen ? "ui_glue_dropdownbutton_disabled_terran.png" : "ui_glue_dropdownbutton_Yellow_disabled_terran.png";
        String arrowNormal = isGreen ? "ui_glue_dropdownarrow_normal_terran.png" : "ui_glue_dropdownarrow_Yellow_normal_terran.png";
        String arrowDisabled = "ui_glue_dropdownarrow_normal_terran.png";
        applyTextCells();
        boolean isDisabled = this.isDisabled();

        String baseTexture = isDisabled ? disabledTexture : normalTexture;
        String arrowTexture = isDisabled ? arrowDisabled : arrowNormal;
        String textColor = isGreen
                ? "linear-gradient(from 0% 0% to 100% 100%, white, #f0c040)"
                : "linear-gradient(from 0% 0% to 100% 100%, #fffbe7, #f0c040)";
        String border = isGreen
                ? "linear-gradient(from 0% 0% to 100% 100%, #BFFFD0, #00D94A)"
                : "linear-gradient(from 0% 0% to 100% 100%, #FFF1A8, #E6B800)";

        this.setStyle(
                "-fx-background-color: transparent; " +
                        "-fx-background-image: url('" + texturePath + baseTexture + "'), url('" + texturePath + arrowTexture + "'); " +
                        "-fx-background-size: stretch, " + UiScaleHelper.scaleY(16) + "px " + UiScaleHelper.scaleY(16) + "px; " +
                        "-fx-background-repeat: no-repeat, no-repeat; " +
                        "-fx-background-position: center, right " + arrowRight + "px center; " +
                        "-fx-opacity: 1.0; " +
                        "-fx-padding: " + paddingTop + " 0 0 " + paddingLeft + "; " +
                        "-fx-alignment: center; " +
                        "-fx-font-size: " + fontSize + "px;" +
                        "-fx-text-fill: " + textColor + ";"
        );
    }

    private void applyBaseStyles() {
        String normalTexture = isGreen ? "ui_glue_dropdownbutton_normal_terran.png" : "ui_glue_dropdownbutton_Yellow_normal_terran.png";
        String overTexture = isGreen ? "ui_glue_dropdownbutton_over_terran.png" : "ui_glue_dropdownbutton_Yellow_over_terran.png";
        String downTexture = isGreen ? "ui_glue_dropdownbutton_Pressed_terran.png" : "ui_glue_dropdownbutton_Yellow_Pressed_terran.png";
        String arrowNormal = isGreen ? "ui_glue_dropdownarrow_normal_terran.png" : "ui_glue_dropdownarrow_Yellow_normal_terran.png";
        String arrowOver = isGreen ? "ui_glue_dropdownarrow_over_terran.png" : "ui_glue_dropdownarrow_Yellow_normal_terran.png";
        String arrowPressed = isGreen ? "ui_glue_dropdownarrow_pressed_terran.png" : "ui_glue_dropdownarrow_Yellow_normal_terran.png";

        // Base style for all states
        String baseStyle =
                "-fx-background-color: transparent; " +
                        "-fx-background-image: url('" + texturePath + normalTexture + "'), url('" + texturePath + arrowNormal + "'); " +
                        "-fx-background-size: stretch, " + UiScaleHelper.scaleY(16) + "px " + UiScaleHelper.scaleY(16) + "px; " +
                        "-fx-background-repeat: no-repeat, no-repeat; " +
                        "-fx-background-position: center, right " + arrowRight + "px center; " +
                        "-fx-padding: " + paddingTop + " 0 0 " + paddingLeft + "; " +
                        "-fx-alignment: center; " +
                        "-fx-font-size: " + fontSize + "px; " +
                        "-fx-font-family: 'Arial Black'; " +
                        "-fx-text-fill: " + textGradNormal() + ";";

        this.setStyle(baseStyle);

        this.skinProperty().addListener((obs, oldSkin, newSkin) -> {
            Platform.runLater(() -> {
                Node displayNode = this.lookup(".text");
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
            Node listView = this.lookup(".list-view");
            String borderColor = isGreen
                    ? "linear-gradient(from 0% 0% to 100% 100%, #BFFFD0, #00D94A)"
                    : "linear-gradient(from 0% 0% to 100% 100%, #FFF1A8, #E6B800)";
            String backgroundColor = "#001000";
            if (listView instanceof Region) {
                listView.setStyle(
                        "-fx-background-color: " + backgroundColor + ";" +
                                "-fx-border-color: " + borderColor + ";" +
                                "-fx-font-size: " + dropdownFontSize + "px;" +
                                "-fx-border-width: " + UiScaleHelper.scaleX(2) + "px;" +
                                "-fx-border-radius: " + borderRadius + "px;" +
                                "-fx-background-radius: " + borderRadius + "px;" +
                                "-fx-padding: " + UiScaleHelper.scaleY(1) + "px;"
                );
            }
        });

        Platform.runLater(() -> {
            Node arrowButton = this.lookup(".arrow-button");
            if (arrowButton != null) {
                arrowButton.setOpacity(0);
                arrowButton.setManaged(false);
            }
        });

        // Hover
        this.setOnMouseEntered(e -> this.setStyle(
                "-fx-background-color: transparent; " +
                        "-fx-background-image: url('" + texturePath + overTexture + "'), url('" + texturePath + arrowOver + "'); " +
                        "-fx-background-size: stretch, " + UiScaleHelper.scaleY(16) + "px " + UiScaleHelper.scaleY(16) + "px; " +
                        "-fx-background-repeat: no-repeat, no-repeat; " +
                        "-fx-background-position: center, right " + arrowRight + "px center; " +
                        "-fx-padding: " + paddingTop + " 0 0 " + paddingLeft + "; " +
                        "-fx-alignment: center; " +
                        "-fx-font-size: " + fontSize + "px; " +
                        "-fx-font-family: 'Arial Black'; " +
                        "-fx-text-fill: " + textGradHover() + ";"
        ));

        // Normal
        this.setOnMouseExited(e -> this.setStyle(baseStyle));

        // Pressed
        this.setOnMousePressed(e -> this.setStyle(
                "-fx-background-color: transparent; " +
                        "-fx-background-image: url('" + texturePath + downTexture + "'), url('" + texturePath + arrowPressed + "'); " +
                        "-fx-background-size: stretch, " + UiScaleHelper.scaleY(16) + "px " + UiScaleHelper.scaleY(16) + "px; " +
                        "-fx-background-repeat: no-repeat, no-repeat; " +
                        "-fx-background-position: center, right " + arrowRight + "px center; " +
                        "-fx-padding: " + paddingTop + " 0 0 " + paddingLeft + "; " +
                        "-fx-alignment: center; " +
                        "-fx-font-family: 'Arial Black';" +
                        "-fx-font-size: " + fontSize + "px;"
        ));

        // Released
        this.setOnMouseReleased(e -> this.setStyle(baseStyle));

        // For dropdown list
//        this.setOnShown(e -> Platform.runLater(() -> {
//            this.lookupAll(".list-cell").forEach(cell -> {
//                cell.setStyle(
//                        "-fx-background-color: transparent;" +
//                                "-fx-background-size: stretch;" +
//                                "-fx-background-repeat: no-repeat;" +
//                                "-fx-background-position: center;" +
//                                "-fx-font-family: 'Arial Black';" +
//                                "-fx-font-size: " + dropdownFontSize + "px;" +
//                                "-fx-text-fill:" + (isGreen
//                                ? "linear-gradient(from 0% 0% to 100% 100%, white, #f0c040);"
//                                : "linear-gradient(from 0% 0% to 100% 100%, white, yellow);") +
//                                "-fx-alignment: center;"
//                );
//            });
//        }));

        this.show();
        this.hide();
    }
    private void applyTextCells() {


        setButtonCell(new ListCell<T>() {
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
                                // 
                                "-fx-padding: 0 " + (arrowRight + UiScaleHelper.scaleX(26)) + " 0 0;"
                );
            }
        });

        // ensures buttonCell is applied correctly
        setCellFactory(lv -> new ListCell<T>() {

            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);

                String base =
                        "-fx-font-family: 'Arial Black';" +
                                "-fx-font-size: " + dropdownFontSize + "px;" +
                                "-fx-alignment: center;" +
                                "-fx-background-radius: " + cellRadius + "px;" +
                                "-fx-border-radius: " + cellRadius + "px;" +
                                "-fx-pref-height: " + cellHeight + "px;";

                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                    return;
                }

                setText(item.toString());

                // normal
                setStyle(
                        "-fx-background-color: " + (isGreen ? "#001000;" : "#201400;") +
                                base +
                                "-fx-text-fill: " + textGradNormal() + ";"
                );

                // hover
                setOnMouseEntered(e -> {
                    if (!isSelected()) {
                        setStyle(
                                "-fx-background-color: " + (isGreen ? "rgba(30,80,40,0.35);" : "rgba(160,110,30,0.35);") +
                                        base +
                                        "-fx-text-fill: " + textGradHover() + ";"
                        );
                    }
                });

                setOnMouseExited(e -> {
                    if (!isSelected()) {
                        setStyle(
                                "-fx-background-color: " + (isGreen ? "#001000;" : "#201400;") +
                                        base +
                                        "-fx-text-fill: " + textGradNormal() + ";"
                        );
                    }
                });

                // selected (
                if (isSelected()) {
                    setStyle(
                            "-fx-background-color: " + (isGreen ? "rgba(30,80,40,0.65);" : "rgba(120,90,20,0.65);") +
                                    "-fx-border-color: " + (isGreen ? "#00D94A;" : "#d6b100;") +
                                    base +
                                    "-fx-text-fill: " + textGradSelected() + ";"
                    );
                }
            }
        });
    }

    private String textGradNormal() {
        return isGreen
                ? "linear-gradient(from 0% 0% to 100% 100%, #F4FFF4, #7CFF9A)" // 
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
    private void applyListItemStyles() {
        Platform.runLater(() -> {
            this.setCellFactory(param -> new ListCell<T>() {
                @Override
                protected void updateItem(T item, boolean empty) {
                    super.updateItem(item, empty);

                    String baseStyle = "-fx-background-image: none;" +
                            "-fx-background-radius: " + cellRadius + "px;" +
                            "-fx-border-radius: " + cellRadius + "px;" +
                            "-fx-background-insets: 0 2 0 2;" +
                            "-fx-border-insets: 0 2 0 2;" +
                            "-fx-background-size: 100% 100%; " +
                            "-fx-background-repeat: no-repeat; " +
                            "-fx-font-family: 'Arial Black';" +
                            "-fx-font-size: " + cellFontSize + "px;" +
                            "-fx-pref-height: " + cellHeight + "px;" +
                            "-fx-alignment: center;";

                    if (empty || item == null) {
                        setText(null);
                        setGraphic(null);
                        setStyle("");
                    } else {
                        setText(item.toString());

                        setStyle(
                                "-fx-background-color: " + (isGreen ? "#001000" : "#201400") + ";" +
                                        baseStyle +
                                        "-fx-text-fill: " + (isGreen
                                        ? "linear-gradient(from 0% 0% to 100% 100%, white, #f0c040);"
                                        : "linear-gradient(from 0% 0% to 100% 100%, white, yellow);")
                        );

                        setOnMouseEntered(e -> {
                            if (!isSelected()) {
                                setStyle(
                                        "-fx-background-color: " +
                                                (isGreen ? "rgba(30, 80, 40, 0.2);" : "rgba(160, 110, 30, 0.3);") +
                                                baseStyle +
                                                "-fx-text-fill: " + (isGreen
                                                ? "linear-gradient(from 0% 0% to 100% 100%, yellow, red);"
                                                : "linear-gradient(from 0% 0% to 100% 100%, yellow, gold);")
                                );
                            }
                        });

                        setOnMouseExited(e -> {
                            if (!isSelected()) {
                                setStyle(
                                        "-fx-background-color: " + (isGreen ? "#001000" : "#201400") + ";" +
                                                baseStyle +
                                                "-fx-text-fill: " + (isGreen
                                                ? "linear-gradient(from 0% 0% to 100% 100%, white, #f0c040);"
                                                : "linear-gradient(from 0% 0% to 100% 100%, white, yellow);")
                                );
                            }
                        });
                    }

                    if (isSelected()) {
                        setStyle(
                                "-fx-border-color: " + (isGreen ? "darkgreen" : "#d6b100") + ";" +
                                        "-fx-background-color: " + (isGreen ? "rgba(30, 80, 40, 0.6);" : "rgba(120, 90, 20, 0.6);") +
                                        baseStyle +
                                        "-fx-text-fill: " + (isGreen ? "#C7F0C7;" : "#FDFDB3;")
                        );
                    }
                }
            });
        });

        Node listView = this.lookup(".list-view");
        if (listView instanceof Region) {
            ((Region) listView).setPadding(new Insets(0));
            listView.setStyle(
                    "-fx-background-color: transparent;" +
                            "-fx-border-width: 0;"
            );
        }
    }

    private void hideScrollBar() {
        this.skinProperty().addListener((obs, oldSkin, newSkin) -> {
            if (newSkin != null) {
                Node scrollBar = this.lookup(".scroll-bar");
                if (scrollBar != null) {
                    scrollBar.setVisible(false);
                    scrollBar.setManaged(false);
                }
            }
            Platform.runLater(() -> {
                this.show();
                this.hide();
            });
        });
        this.showingProperty().addListener((obs, wasShowing, isNowShowing) -> {
            if (isNowShowing) {
                Node scrollBar = this.lookup(".scroll-bar");
                if (scrollBar != null) {
                    scrollBar.setVisible(false);
                    scrollBar.setManaged(false);
                }
            }
        });
    }
}

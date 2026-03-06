package lv.lenc;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.layout.Region;
import javafx.scene.text.Text;

    public class CustomComboBoxTexture<T> extends ComboBox<T> implements Disabable {

        private final String texturePath;
        private final double realWidth;
        private final double realHeight;
        public CustomComboBoxTexture(String texturePath, double widthFullHD, double heightFullHD) {
            super();
            this.texturePath = texturePath;
            this.realWidth  = UiScaleHelper.SCREEN_WIDTH  * (widthFullHD  / 1920.0);
            this.realHeight = UiScaleHelper.SCREEN_HEIGHT * (heightFullHD / 1080.0);
            // Make sizes adaptive relative to FullHD
            setMinSize(realWidth, realHeight);
            setPrefSize(realWidth, realHeight);
            setMaxSize(realWidth, realHeight);


            applyBaseStyles();
            applyTrackAndThumbStyles();
            applyListItemStyles();
            this.disabledProperty().addListener((obs, wasDisabled, isNowDisabled) -> updateStyle());
            updateStyle();
        }


        public void disable(Boolean bol) {
            this.setDisable(bol);
            updateStyle();
        }

        private void updateStyle() {
            String normalTexture = "ui_glue_dropdownbutton_normal_terran.png";
            String disabledTexture = "ui_glue_dropdownbutton_disabled_terran.png";
            String arrowNormal = "ui_glue_dropdownarrow_normal_terran.png";
            String arrowDisabled = "ui_glue_dropdownarrow_normal_terran.png";
            boolean isDisabled = this.isDisabled();

            String baseTexture = isDisabled ? disabledTexture : normalTexture;
            String arrowTexture = isDisabled ? arrowDisabled : arrowNormal;
            String textColor = isDisabled ? "gray" : "linear-gradient(from 0% 0% to 100% 100%, white, limegreen)";
            double opacity = 1.0;

            // Arrow size and padding calculated relative to width/height
            double arrowSize   = realHeight * (16.0 / 40.0);
            double arrowRight  = realWidth  * (20.0 / 220.0);
            double leftPadding = realWidth  * (15.0 / 220.0);
            double paddingTop  = realHeight * (-4.0 / 40.0);

            this.setStyle(
                    "-fx-background-color: transparent; " +
                            "-fx-background-image: url('" + texturePath + baseTexture + "'), url('" + texturePath + arrowTexture + "'); " +
                            "-fx-background-size: stretch, " + arrowSize + "px " + arrowSize + "px; " +
                            "-fx-background-repeat: no-repeat, no-repeat; " +
                            "-fx-background-position: center, right " + arrowRight + "px center; " +
                            "-fx-opacity: " + opacity + "; " +
                            "-fx-padding: -4 0 0 " + leftPadding + "; " +
                            "-fx-alignment: center-left; " +
                            "-fx-text-fill: " + textColor + ";"
            );
        }

        private void applyBaseStyles() {
            String normalTexture = "ui_glue_dropdownbutton_normal_terran.png";
            String overTexture = "ui_glue_dropdownbutton_over_terran.png";
            String downTexture = "ui_glue_dropdownbutton_Pressed_terran.png";
            String arrowNormal = "ui_glue_dropdownarrow_normal_terran.png";
            String arrowOver = "ui_glue_dropdownarrow_over_terran.png";
            String arrowPressed = "ui_glue_dropdownarrow_pressed_terran.png";

            double arrowSize = UiScaleHelper.SCREEN_HEIGHT * (16.0 / 1080.0);
            double arrowRight = UiScaleHelper.SCREEN_WIDTH * (20.0 / 1920.0);
            double leftPadding = UiScaleHelper.SCREEN_WIDTH * (15.0 / 1920.0);
            double paddingTop = UiScaleHelper.scaleY(-4);
            double borderRadius = UiScaleHelper.scaleY(8);

            double pading = UiScaleHelper.scaleY(2);

            this.setStyle(
                    "-fx-background-color: transparent; " +
                            "-fx-background-image: url('" + texturePath + normalTexture + "'), url('" + texturePath + arrowNormal + "'); " +
                            "-fx-background-size: stretch, " + arrowSize + "px " + arrowSize + "px; " +
                            "-fx-background-repeat: no-repeat, no-repeat; " +
                            "-fx-background-position: center, right " + arrowRight + "px center; " +
                            "-fx-padding: " + paddingTop +" 0 0 " + leftPadding + "; " +
                            "-fx-alignment: center-left;"
            );

            Platform.runLater(() -> {
                Node listView = this.lookup(".list-view");
                if (listView instanceof Region) {
                    double borderWidth = UiScaleHelper.SCREEN_WIDTH * (2.0 / 1920.0);
                    ((Region) listView).setPrefHeight(UiScaleHelper.SCREEN_HEIGHT * (180.0 / 1080.0));
                    ((Region) listView).setMaxHeight(UiScaleHelper.SCREEN_HEIGHT * (180.0 / 1080.0));
                    listView.setStyle(
                            "-fx-background-color: #001000;" +
                                    "-fx-border-color: limegreen;" +
                                    "-fx-border-width: " + borderWidth + "px;" +
                                    "-fx-border-radius:"+ borderRadius + "px;" +
                                    "-fx-background-radius:"+ borderRadius + "px;" +
                                    "-fx-padding:"+ pading + "px;"
                    );
                }
            });

            Platform.runLater(() -> {
                Text textNode = (Text) this.lookup(".text");
                if (textNode != null) {
                    textNode.setStyle(
                            "-fx-fill: linear-gradient(from 0% 0% to 100% 100%, white, limegreen);"
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

            this.setOnMouseEntered(e -> this.setStyle(
                    "-fx-background-color: transparent; " +
                            "-fx-background-image: url('" + texturePath + overTexture + "'), url('" + texturePath + arrowOver + "'); " +
                            "-fx-background-size: stretch, " + arrowSize + "px " + arrowSize + "px; " +
                            "-fx-background-repeat: no-repeat, no-repeat; " +
                            "-fx-background-position: center, right " + arrowRight + "px center; " +
                            "-fx-padding:" + paddingTop + " 0 0 " + leftPadding + "; " +
                            "-fx-alignment: center-left;"
            ));

            this.setOnMouseExited(e -> this.setStyle(
                    "-fx-background-color: transparent; " +
                            "-fx-background-image: url('" + texturePath + normalTexture + "'), url('" + texturePath + arrowNormal + "'); " +
                            "-fx-background-size: stretch, " + arrowSize + "px " + arrowSize + "px; " +
                            "-fx-background-repeat: no-repeat, no-repeat; " +
                            "-fx-background-position: center, right " + arrowRight + "px center; " +
                            "-fx-padding:" + paddingTop + " 0 0 " + leftPadding + "; " +
                            "-fx-alignment: center-left;"
            ));

            this.setOnMousePressed(e -> this.setStyle(
                    "-fx-background-color: transparent; " +
                            "-fx-background-image: url('" + texturePath + downTexture + "'), url('" + texturePath + arrowPressed + "'); " +
                            "-fx-background-size: stretch, " + arrowSize + "px " + arrowSize + "px; " +
                            "-fx-background-repeat: no-repeat, no-repeat; " +
                            "-fx-background-position: center, right " + arrowRight + "px center; " +
                            "-fx-padding:" + paddingTop + " 0 0 " + leftPadding + "; " +
                            "-fx-alignment: center-left;"
            ));

            this.setOnMouseReleased(e -> this.setStyle(
                    "-fx-background-color: transparent; " +
                            "-fx-background-image: url('" + texturePath + normalTexture + "'), url('" + texturePath + arrowNormal + "'); " +
                            "-fx-background-size: stretch, " + arrowSize + "px " + arrowSize + "px; " +
                            "-fx-background-repeat: no-repeat, no-repeat; " +
                            "-fx-background-position: center, right " + arrowRight + "px center; " +
                            "-fx-padding:" + paddingTop + " 0 0 " + leftPadding + "; " +
                            "-fx-alignment: center-left;"
            ));

            this.setOnShown(e -> Platform.runLater(() -> {
                this.lookupAll(".list-cell").forEach(cell -> {
                    cell.setStyle(
                            "-fx-background-color: transparent;" +
                                    "-fx-background-size: stretch;" +
                                    "-fx-background-repeat: no-repeat;" +
                                    "-fx-background-position: center;" +
                                    "-fx-font-family: 'Arial Black';" +
                                    "-fx-font-size: " + (UiScaleHelper.SCREEN_HEIGHT * (14.0 / 1080.0)) + "px;" +
                                    "-fx-text-fill: linear-gradient(from 0% 0% to 100% 100%, white, limegreen);" +
                                    "-fx-alignment: center;"
                    );
                });
            }));
            this.show();
            this.hide();
        }

        // 
        private void applyTrackAndThumbStyles() {
            String arrowNormal = "ui_glue_dropdownarrow_normal_terran.png";
            double borderRadius = UiScaleHelper.scaleY(5);
            double borderRadiusHigh = UiScaleHelper.scaleY(25);
            double borderWidth = UiScaleHelper.scaleY(5);
            double borderWidthBar = UiScaleHelper.scaleY(3);
        /* .thumb —
           .track — cell for  thumb. */
            Platform.runLater(() -> {
        // Configure decrement button (bottom arrow) with border
                Node decrementButton = this.lookup(".decrement-button");
                if (decrementButton != null) {
                // Hide internal arrow
                    Node decrementArrow = decrementButton.lookup(".decrement-arrow");
                    if (decrementArrow != null) {
                        String currentStyle = decrementArrow.getStyle();
                        decrementArrow.setStyle(
                                currentStyle + "; " +
                                        "-fx-opacity: 0; -fx-background-color: white;"
                        );
                    }

                    // Apply custom texture and border to decrement button
                    String currentStyle = decrementButton.getStyle();

                    decrementButton.setStyle(
                            currentStyle + "; " +
                                    "-fx-background-color: transparent; " +
                                    "-fx-background-image: url('" + texturePath + arrowNormal + "'); " +
                                    "-fx-background-size: 16px 16px; " +
                                    "-fx-background-repeat: no-repeat; " +
                                    "-fx-background-position: center;" +
                                    "-fx-rotate: 180;" +
                                    "-fx-border-color: limegreen; " +
                                    "-fx-border-width:" + borderWidth + "px;"  +
                                    "-fx-border-radius:" + borderRadius + "px;" +
                                    "-fx-effect: dropshadow(gaussian, black, 5, 0.5, 0, 0);"
                    );
                }

                // Configure increment button (top arrow) with border
                Node incrementButton = this.lookup(".increment-button");
                if (incrementButton != null) {
                    // 
                    Node incrementArrow = incrementButton.lookup(".increment-arrow");
                    if (incrementArrow != null) {
                        String currentStyle = incrementArrow.getStyle();
                        incrementArrow.setStyle(
                                currentStyle + "; " +
                                        "-fx-opacity: 0; -fx-background-color: transparent;"
                        );
                    }

                    // 
                    String currentStyle = incrementButton.getStyle();
                    incrementButton.setStyle(
                            currentStyle + "; " +
                                    "-fx-background-color: transparent; " + // 
                                    "-fx-background-image: url('" + texturePath + arrowNormal + "'); " + // 
                                    "-fx-background-size: 16px 16px; " + // 
                                    "-fx-background-repeat: no-repeat; " +
                                    "-fx-background-position: center;" + // 
                                    "-fx-border-color: limegreen; " + // 
                                    "-fx-border-width:" + borderWidth + "px;"  + // 
                                    "-fx-border-radius:" + borderRadius + "px;" + // 
                                    "-fx-effect: dropshadow(gaussian, black, 5, 0.5, 0, 0);" // 
                    );
                }

                // Configure scrollbar track
                Node track = this.lookup(".track");
                if (track != null) {
                    String currentStyle = track.getStyle();
                    track.setStyle(
                            currentStyle + "; " +
                                    "-fx-background-color: transparent; " +
                                    "-fx-border-color: limegreen; " +
                                    "-fx-border-width:" + borderWidth + "px;"  +
                                    "-fx-border-radius:" + borderRadiusHigh + "px;"
                    );
                }

                // Configure track background)
                Node trackBackground = this.lookup(".track-background");
                if (trackBackground != null) {
                    String currentStyle = trackBackground.getStyle();
                    trackBackground.setStyle(
                            currentStyle + "; " +
                                    "-fx-background-color: #001000; "
                    );
                }

                // Configure scrollbar thumb
                Node thumb = this.lookup(".thumb");
                if (thumb != null) {
                    String currentStyle = thumb.getStyle();
                    thumb.setStyle(
                            currentStyle + "; " +
                                    "-fx-background-color: limegreen; " +
                                    "-fx-border-color: black; " +
                                    "-fx-border-width:" + borderWidthBar + "px;"  +
                                    "-fx-border-radius:" + borderRadiusHigh + "px;"
                    );
                }
            });

        }

        // Apply styles for list items
        private void applyListItemStyles() {
            String frameTexture = "ui_glue_dropdownmenuframe_terran.png";
            String frameSelectedTexture = "ui_glue_dropdownmenubutton_selected_terran.png";
            double itemFontSize = UiScaleHelper.scaleY(14.0);
            this.setCellFactory(param -> new ListCell<T>() {
                @Override
                protected void updateItem(T item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setGraphic(null);
                        setStyle(""); // Reset style for empty cells
                    } else {
                        // Reduce cell height by 40%
                        setText(item.toString()); // Set the text of the item
                        String currentStyle = getStyle(); // Retrieve the current style
                        setStyle(
                                currentStyle + "; " +
                                        "-fx-background-color: black;" +
                                        "-fx-background-image: url('" + texturePath + frameTexture + "');" +
                                        "-fx-background-size: 100% 100%; " +
                                        "-fx-background-repeat: no-repeat; " +
                                        "-fx-font-family: 'Arial Black';" +
                                        "-fx-font-size: " + itemFontSize + "px;" +
                                        "-fx-text-fill: linear-gradient(from 0% 0% to 100% 100%, white, limegreen);" +
                                        "-fx-alignment: center;"
                        );

                        // Hover style
                        setOnMouseEntered(e -> {
                            if (!isSelected()) {
                                String hoverStyle = getStyle();
                                setStyle(
                                        hoverStyle + "; " +
                                                "-fx-background-color: black;" +
                                                "-fx-background-image: url('" + texturePath + frameTexture + "');" +
                                                "-fx-background-size: 100% 100%; " +
                                                "-fx-background-repeat: no-repeat; " +
                                                "-fx-font-family: 'Arial Black';" +
                                                "-fx-font-size: " + itemFontSize + "px;" +
                                                "-fx-text-fill: linear-gradient(from 0% 0% to 100% 100%, yellow, red);" +
                                                "-fx-alignment: center;"
                                );
                            }
                        });

                        // Restore style on mouse exit
                        setOnMouseExited(e -> {
                            if (!isSelected()) {
                                String exitStyle = getStyle();
                                setStyle(
                                        exitStyle + "; " +
                                                "-fx-background-color: black;" +
                                                "-fx-background-image: url('" + texturePath + frameTexture + "');" +
                                                "-fx-background-size: 100% 100%; " +
                                                "-fx-background-repeat: no-repeat; " +
                                                "-fx-font-family: 'Arial Black';" +
                                                "-fx-font-size: " + itemFontSize + "px;" +
                                                "-fx-text-fill: linear-gradient(from 0% 0% to 100% 100%, white, limegreen);" +
                                                "-fx-alignment: center;"
                                );
                            }
                        });
                    }

                    if (isSelected()) {
                        String selectedStyle = getStyle();
                        setStyle(
                                selectedStyle + "; " +
                                        "-fx-background-color: rgba(0, 0, 0, 0.5);" +
                                        "-fx-background-image: url('" + texturePath + frameSelectedTexture + "');" +
                                        "-fx-background-size: 100% 100%; " +
                                        "-fx-background-repeat: no-repeat; " +
                                        "-fx-font-family: 'Arial Black';" +
                                        "-fx-font-size: " + itemFontSize + "px;" +
                                        "-fx-text-fill: white;" +
                                        "-fx-alignment: center;"
                        );
                    }
                }
            });
        }
    }

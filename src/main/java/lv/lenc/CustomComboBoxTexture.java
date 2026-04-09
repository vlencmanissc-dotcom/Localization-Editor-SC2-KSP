package lv.lenc;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Text;

    public class CustomComboBoxTexture<T> extends ComboBox<T> implements Disabable {

        private final String texturePath;
        private final double realWidth;
        private final double realHeight;

        private String buildControlStyle(String buttonTexture,
                                         String arrowTexture,
                                         double arrowSize,
                                         double arrowRight,
                                         double leftPadding,
                                         double paddingTop) {
            double controlFontSize = UiScaleHelper.scaleFont(11.2, 9.0);
            return "-fx-background-color: transparent; " +
                    "-fx-background-image: url('" + texturePath + buttonTexture + "'), url('" + texturePath + arrowTexture + "'); " +
                    "-fx-background-size: stretch, " + arrowSize + "px " + arrowSize + "px; " +
                    "-fx-background-repeat: no-repeat, no-repeat; " +
                    "-fx-background-position: center, right " + arrowRight + "px center; " +
                    "-fx-background-insets: 0; " +
                    "-fx-border-insets: 0; " +
                    "-fx-border-color: transparent; " +
                    "-fx-border-width: 0; " +
                    "-fx-focus-color: transparent; " +
                    "-fx-faint-focus-color: transparent; " +
                    // Keep label visually centered in all states (normal/hover/pressed).
                    "-fx-padding:" + paddingTop + " 0 0 " + leftPadding + "; " +
                    "-fx-alignment: center; " +
                    "-fx-font-family: 'Arial Black'; " +
                    "-fx-font-size: " + controlFontSize + "px; " +
                    "-fx-text-fill: linear-gradient(from 0% 0% to 100% 100%, white, limegreen);";
        }

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
            applyButtonCellStyle();
            UiSoundManager.bindBnetDropdown(this);
            CustomCursorManager.applyDefaultCursor(this);
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
                    buildControlStyle(
                            baseTexture,
                            arrowTexture,
                            arrowSize,
                            arrowRight,
                            leftPadding,
                            paddingTop
                    ) +
                            "-fx-opacity: " + opacity + "; " +
                            "-fx-text-fill: " + textColor + ";"
            );
        }

        private void applyBaseStyles() {
            String normalTexture = "ui_glue_dropdownbutton_normal_terran.png";
            String overTexture = "ui_glue_dropdownbutton_over_terran.png";
            String arrowNormal = "ui_glue_dropdownarrow_normal_terran.png";
            String arrowOver = "ui_glue_dropdownarrow_over_terran.png";

            // Keep geometry identical across normal/hover/pressed to avoid text jitter.
            double arrowSize = realHeight * (16.0 / 40.0);
            double arrowRight = realWidth * (20.0 / 220.0);
            double leftPadding = realWidth * (15.0 / 220.0);
            double paddingTop = realHeight * (-4.0 / 40.0);
            double borderRadius = UiScaleHelper.scaleY(8);

            double pading = UiScaleHelper.scaleY(2);

            this.setStyle(buildControlStyle(normalTexture, arrowNormal, arrowSize, arrowRight, leftPadding, paddingTop));

            Platform.runLater(() -> {
                Node listView = this.lookup(".list-view");
                if (listView instanceof Region) {
                    CustomCursorManager.applyDefaultCursor(listView);
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
                            "-fx-fill: linear-gradient(from 0% 0% to 100% 100%, white, limegreen);" +
                                    "-fx-font-family: 'Arial Black';" +
                                    "-fx-font-size: " + UiScaleHelper.scaleFont(11.2, 9.0) + "px;"
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
                    buildControlStyle(overTexture, arrowOver, arrowSize, arrowRight, leftPadding, paddingTop)
            ));

            this.setOnMouseExited(e -> this.setStyle(
                    buildControlStyle(normalTexture, arrowNormal, arrowSize, arrowRight, leftPadding, paddingTop)
            ));

            // Keep text position stable on click (pressed texture can visually drop baseline).
            this.setOnMousePressed(e -> this.setStyle(
                    buildControlStyle(overTexture, arrowOver, arrowSize, arrowRight, leftPadding, paddingTop)
            ));

            this.setOnMouseReleased(e -> this.setStyle(
                    buildControlStyle(
                            this.isHover() ? overTexture : normalTexture,
                            this.isHover() ? arrowOver : arrowNormal,
                            arrowSize,
                            arrowRight,
                            leftPadding,
                            paddingTop
                    )
            ));

            this.focusedProperty().addListener((obs, was, isNow) -> this.setStyle(
                    buildControlStyle(
                            this.isHover() ? overTexture : normalTexture,
                            this.isHover() ? arrowOver : arrowNormal,
                            arrowSize,
                            arrowRight,
                            leftPadding,
                            paddingTop
                    )
            ));

            this.showingProperty().addListener((obs, wasShowing, isShowing) -> this.setStyle(
                    buildControlStyle(
                            isShowing || this.isHover() ? overTexture : normalTexture,
                            isShowing || this.isHover() ? arrowOver : arrowNormal,
                            arrowSize,
                            arrowRight,
                            leftPadding,
                            paddingTop
                    )
            ));

            this.setOnShown(e -> Platform.runLater(() -> {
                this.lookupAll(".list-cell").forEach(cell -> {
                    CustomCursorManager.applyDefaultCursor(cell);
                    cell.setStyle(
                            "-fx-background-color: transparent;" +
                                    "-fx-background-size: stretch;" +
                                    "-fx-background-repeat: no-repeat;" +
                                    "-fx-background-position: center;" +
                                    "-fx-font-family: 'Arial Black';" +
                                    "-fx-font-size: " + UiScaleHelper.scaleFont(9.8, 8.0) + "px;" +
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
            double itemFontSize = UiScaleHelper.scaleFont(11.2, 9.0);
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
                        CustomCursorManager.applyDefaultCursor(this);
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

        private void applyButtonCellStyle() {
            final double buttonFontSize = UiScaleHelper.scaleFont(11.2, 9.0);
            final double rightReserved = UiScaleHelper.SCREEN_WIDTH * (30.0 / 1920.0);
            final double leftReserved = UiScaleHelper.SCREEN_WIDTH * (40.0 / 1920.0);
            final double visualCenterShift = (leftReserved - rightReserved) / 2.0;

            setButtonCell(new ListCell<>() {
                private final Label stableLabel = new Label();
                private final StackPane labelHolder = new StackPane(stableLabel);

                {
                    setMaxWidth(Double.MAX_VALUE);
                    prefWidthProperty().bind(CustomComboBoxTexture.this.widthProperty());

                    stableLabel.setMouseTransparent(true);
                    stableLabel.setStyle(
                            "-fx-font-family: 'Arial Black';" +
                                    "-fx-font-size: " + buttonFontSize + "px;" +
                                    "-fx-text-fill: linear-gradient(from 0% 0% to 100% 100%, white, limegreen);"
                    );
                    stableLabel.setTranslateX(visualCenterShift);

                    labelHolder.setMouseTransparent(true);
                    labelHolder.setAlignment(Pos.CENTER);
                    labelHolder.setPrefHeight(realHeight);
                    labelHolder.setMinHeight(realHeight);
                    labelHolder.setMaxHeight(realHeight);
                    labelHolder.setMaxWidth(Double.MAX_VALUE);
                    labelHolder.prefWidthProperty().bind(CustomComboBoxTexture.this.widthProperty());

                    setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                    setText(null);
                    setGraphic(labelHolder);
                    setStyle(
                            "-fx-background-color: transparent;" +
                                    "-fx-padding: 0;" +
                                    "-fx-alignment: center;" +
                                    "-fx-background-insets: 0;" +
                                    "-fx-border-insets: 0;" +
                                    "-fx-text-overrun: clip;"
                    );
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

                    stableLabel.setText(item.toString());
                    setText(null);
                    setGraphic(labelHolder);
                    CustomCursorManager.applyDefaultCursor(this);
                    setStyle(
                            "-fx-background-color: transparent;" +
                                    "-fx-padding: 0;" +
                                    "-fx-alignment: center;" +
                                    "-fx-background-insets: 0;" +
                                    "-fx-border-insets: 0;" +
                                    "-fx-text-overrun: clip;"
                    );
                }
            });
        }
    }

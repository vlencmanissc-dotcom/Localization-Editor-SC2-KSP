package lv.lenc;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.Glow;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;


public class UIElementFactory {

    /**
     * Creates a standard button with specified text, size, and font.
     *
     * @param text      Button text
     * @param width     Button width
     * @param height    Button height
     * @param fontSize  Font size
     * @return Created button
     */
    public static Button createButton(String text, double width, double height, double fontSize) {
        Button button = new Button(text);
        button.setPrefSize(width, height);
        button.setMaxSize(width, height);
        button.setFont(Font.font(fontSize));
        return button;
    }

    /**
     * Creates a customizable textured button with text gradient and glow effect.
     *
     * @param text            Button text
     * @param texturePath     Path to texture folder
     * @param isGreen         Determines button color scheme
     * @param strengthGlow    Initial glow level
     * @param strengthGlowMAX Maximum glow level
     * @return Styled button instance
     */
    public static MyButton createCustomLongButton(String text, String texturePath, boolean isGreen, double strengthGlow, double strengthGlowMAX) {
        MyButton button = new MyButton(text);
        final boolean[] isPressed = {false};

        double btnWidth = UiScaleHelper.scaleX(330);
        double btnHeight = UiScaleHelper.scaleY(58);
        double fontSize = UiScaleHelper.scaleY(16);
        double paddingY = UiScaleHelper.scaleY(4);
        double paddingX = UiScaleHelper.scaleX(10);

        button.setPrefSize(btnWidth, btnHeight);
        button.setMinSize(btnWidth, btnHeight);
        button.setMaxSize(btnWidth, btnHeight);
        button.setPickOnBounds(true);

        final Glow buttonGlow = new Glow(strengthGlow);
        button.setEffect(buttonGlow);

        String upTexture = isGreen ? "ui_battlenet_glues_greenbuttons_alternate_largeup_ONE_UPSCALE_APS.png" : "ui_battlenet_glues_redbuttons_alternate_largeup_ONE_UPSCALE_APS.png";
        String hoverTexture = isGreen ? "ui_battlenet_glues_greenbuttons_alternate_largeover_ONE_UPSCALE_APS.png" : "ui_battlenet_glues_redbuttons_alternate_largeover_ONE_UPSCALE_APS.png";
        String downTexture = isGreen ? "ui_battlenet_glues_greenbuttons_alternate_largedown_ONE_UPSCALE_APS.png" : "ui_battlenet_glues_redbuttons_alternate_largedown_ONE_UPSCALE_APS.png";
        String disabledTexture = isGreen ? "ui_battlenet_glues_greenbuttons_alternate_largedisabled_ONE_UPSCALE_APS.png" : "ui_battlenet_glues_redbuttons_alternate_largedisabled_ONE_UPSCALE_APS.png";
        String textColor = isGreen
                ? "linear-gradient(from 0% 0% to 100% 100%, #e0ffe0, #55aa55)"
                : "linear-gradient(from 0% 0% to 100% 100%, #ffe0e0, #aa5555)";

        button.setAlignment(Pos.CENTER);

        Runnable updateStyle = () -> {
            String currentTexture = button.isDisabled() ? disabledTexture : upTexture;
            String currentTextColor = button.isDisabled() ? "gray" : textColor;
            double opacity = 1.0;

            button.setStyle(
                    "-fx-background-image: url('" + texturePath + currentTexture + "'); " +
                            "-fx-background-size: 100% 100%; " +
                            "-fx-background-repeat: no-repeat; " +
                            "-fx-background-color: transparent; " +
                            "-fx-font-family: 'Arial Black'; " +
                            "-fx-font-size: " + fontSize + "px; " +
                            "-fx-text-fill: " + currentTextColor + "; " +
                            "-fx-opacity: " + opacity + "; " +
                            "-fx-padding: " + paddingY + " " + paddingX + " " + paddingY + " " + paddingX + ";"
            );
            buttonGlow.setLevel(button.isDisabled() ? 0.0 : strengthGlow);
        };

        updateStyle.run();

        // 
        button.setOnMouseEntered(e -> {
            if (!isPressed[0]) {
                button.setStyle(
                        "-fx-background-image: url('" + texturePath + hoverTexture + "'); " +
                                "-fx-background-size: 100% 100%; " +
                                "-fx-background-repeat: no-repeat; " +
                                "-fx-background-color: transparent; " +
                                "-fx-font-family: 'Arial Black'; " +
                                "-fx-font-size: " + fontSize + "px; " +
                                "-fx-text-fill: white; " +
                                "-fx-padding: 0;"
                );
            }
            buttonGlow.setLevel(strengthGlowMAX);
        });

        button.setOnMouseExited(e -> {
            isPressed[0] = false;
            updateStyle.run();
        });

        button.setOnMousePressed(e -> {
            isPressed[0] = true;
            button.setStyle(
                    "-fx-background-image: url('" + texturePath + downTexture + "'); " +
                            "-fx-background-size: 100% 100%; " +
                            "-fx-background-repeat: no-repeat; " +
                            "-fx-background-color: transparent; " +
                            "-fx-font-family: 'Arial Black'; " +
                            "-fx-font-size: " + fontSize + "px; " +
                            "-fx-text-fill: white; " +
                            "-fx-padding: 0;"
            );
        });

        button.setOnMouseReleased(e -> {
            isPressed[0] = false;
            updateStyle.run();
        });

        button.disabledProperty().addListener((obs, oldVal, newVal) -> updateStyle.run());

        return button;
    }


    /**
     * Creates a customizable language button with texture and glow effect.
     *
     * @param text        Button text
     * @param texturePath Path to texture folder
     * @return Styled button instance
     */
    public static MyButton createCustomLanguageButton(String text, String texturePath) {
        MyButton button = new MyButton(text);
        final boolean[] isPressed = {false};
        button.setPrefSize(75, 54);
        button.setMaxSize(75, 54);
        // 
        String normalTexture = "ui_glues_greenbuttons_squarebackbuttonnormal.png";
        String overTexture = "ui_glues_greenbuttons_squarebackbuttonover.png";
        String downTexture = "ui_glues_greenbuttons_squarebackbuttondown.png";
        String disabledTexture = "ui_glues_greenbuttons_squarebackbuttondisabled.png";

        // 
        Glow buttonGlow = new Glow(0.15);
        button.setEffect(buttonGlow);

        // 
        DropShadow textGlow = new DropShadow();
        textGlow.setColor(Color.LIMEGREEN);
        textGlow.setRadius(5);

        // 
        Runnable updateStyle = () -> {
            String currentTexture = button.isDisabled() ? disabledTexture : normalTexture;
            double glowLevel = button.isDisabled() ? 0.0 : 0.15;
            button.setStyle(
                    "-fx-background-image: url('" + texturePath + currentTexture + "');" +
                            "-fx-background-size: 100% 100%;" +
                            "-fx-background-repeat: no-repeat;" +
                            "-fx-background-color: transparent;" +
                            "-fx-font-family: 'Arial Black';" +
                            "-fx-font-size: 16px;" +
                            "-fx-text-fill: linear-gradient(from 0% 0% to 0% 100%, white, limegreen);" +
                            "-fx-padding: 0;"
            );
            buttonGlow.setLevel(glowLevel);
        };

        // 
        updateStyle.run();

        // 
        button.disabledProperty().addListener((obs, oldVal, newVal) -> updateStyle.run());

        // 
        button.setOnMouseEntered(e -> {
            if (!isPressed[0] && !button.isDisabled()) {
                button.setStyle(
                        "-fx-background-image: url('" + texturePath + overTexture + "'); " +
                                "-fx-background-size: 100% 100%; " +
                                "-fx-background-repeat: no-repeat; " +
                                "-fx-background-color: transparent; " +
                                "-fx-font-family: 'Arial Black'; " +
                                "-fx-font-size: 16px; " +
                                "-fx-text-fill: linear-gradient(from 0% 0% to 100% 100%, #E6FFE6, #2E8B57);" +
                                "-fx-padding: 0;"
                );
                buttonGlow.setLevel(0.25);
            }
        });

        // 
        button.setOnMouseExited(e -> {
            if (!isPressed[0]) {
                updateStyle.run();
            }
        });

        // 
        button.setOnMousePressed(e -> {
            if (!button.isDisabled()) {
                isPressed[0] = true;
                button.setStyle(
                        "-fx-background-image: url('" + texturePath + downTexture + "'); " +
                                "-fx-background-size: 100% 100%; " +
                                "-fx-background-repeat: no-repeat; " +
                                "-fx-background-color: transparent; " +
                                "-fx-font-family: 'Arial Black'; " +
                                "-fx-font-size: 16px; " +
                                "-fx-text-fill: linear-gradient(from 0% 0% to 0% 100%, darkgreen, limegreen);" +
                                "-fx-padding: 0;"
                );
            }
        });

        // 
        button.setOnMouseReleased(e -> {
            if (!button.isDisabled()) {
                isPressed[0] = false;
                updateStyle.run();
            }
        });

        return button;
    }


    /**
     * Creates a custom quit button with texture, text gradient and glow effect.
     *
     * @param text        Button text
     * @param texturePath Path to texture folder
     * @return Styled quit button
     */
    public static MyButton createCustomQuitButton(String text, String texturePath) {
        MyButton button = new MyButton(text);
        final boolean[] isPressed = {false};

        double btnWidth = UiScaleHelper.scaleX(270);
        double btnHeight = UiScaleHelper.scaleY(78);
        double fontSize = UiScaleHelper.scaleY(19);
        double glowRadius = UiScaleHelper.scaleY(8);

        button.setPrefSize(btnWidth, btnHeight);
        button.setMaxSize(btnWidth, btnHeight);

        String normalTexture = "ui_nova_global_largebuttongreen_normal.png";
        String overTexture = "ui_nova_global_largebuttongreen_over.png";
        String downTexture = "ui_nova_global_largebuttongreen_down.png";
        String disabledTexture = "ui_nova_global_largebuttongreen_disabled.png";

        Glow buttonGlow = new Glow(0.2);
        button.setEffect(buttonGlow);

        DropShadow textGlow = new DropShadow();
        textGlow.setColor(Color.LIMEGREEN);
        textGlow.setRadius(glowRadius); // 

        Runnable updateStyle = () -> {
            String currentTexture = button.isDisabled() ? disabledTexture : normalTexture;
            double glowLevel = button.isDisabled() ? 0.0 : 0.2;

            button.setStyle(
                    "-fx-background-image: url('" + texturePath + currentTexture + "');" +
                            "-fx-background-size: 100% 100%;" +
                            "-fx-background-repeat: no-repeat;" +
                            "-fx-background-color: transparent;" +
                            "-fx-font-family: 'Arial Black';" +
                            "-fx-font-size: " + fontSize + "px;" +
                            "-fx-text-fill: linear-gradient(from 0% 0% to 0% 100%, white, limegreen);" +
                            "-fx-padding: 0;"
            );
            buttonGlow.setLevel(glowLevel);
        };

        updateStyle.run();
        button.disabledProperty().addListener((obs, oldVal, newVal) -> updateStyle.run());

        button.setOnMouseEntered(e -> {
            if (!isPressed[0] && !button.isDisabled()) {
                button.setStyle(
                        "-fx-background-image: url('" + texturePath + overTexture + "'); " +
                                "-fx-background-size: 100% 100%; " +
                                "-fx-background-repeat: no-repeat; " +
                                "-fx-background-color: transparent; " +
                                "-fx-font-family: 'Arial Black'; " +
                                "-fx-font-size: " + fontSize + "px; " +
                                "-fx-text-fill: linear-gradient(from 0% 0% to 100% 100%, #E6FFE6, #2E8B57);" +
                                "-fx-padding: 0;"
                );
                buttonGlow.setLevel(0.35);
            }
        });

        button.setOnMouseExited(e -> {
            if (!isPressed[0]) {
                updateStyle.run();
            }
        });

        button.setOnMousePressed(e -> {
            if (!button.isDisabled()) {
                isPressed[0] = true;
                button.setStyle(
                        "-fx-background-image: url('" + texturePath + downTexture + "'); " +
                                "-fx-background-size: 100% 100%; " +
                                "-fx-background-repeat: no-repeat; " +
                                "-fx-background-color: transparent; " +
                                "-fx-font-family: 'Arial Black'; " +
                                "-fx-font-size: " + fontSize + "px; " +
                                "-fx-text-fill: linear-gradient(from 0% 0% to 0% 100%, darkgreen, limegreen);" +
                                "-fx-padding: 0;"
                );
            }
        });

        button.setOnMouseReleased(e -> {
            if (!button.isDisabled()) {
                isPressed[0] = false;
                updateStyle.run();
            }
        });

        return button;
    }

    public static MyButton createCustomLongAlternativeButton(String text, String texturePath, double strengthGlow, double strengthGlowMAX) {
        MyButton button = new MyButton(text);
        final boolean[] isPressed = {false};

        // 
        double btnWidth = UiScaleHelper.scaleX(170);
        double btnHeight = UiScaleHelper.scaleY(54);
        double fontSize = UiScaleHelper.scaleY(14);

        button.setPrefSize(btnWidth, btnHeight);
        button.setMaxSize(btnWidth, btnHeight);

        Glow buttonGlow = new Glow(strengthGlow);
        button.setEffect(buttonGlow);

        String upTexture = "ui_nova_global_mediumbutton_green_normal.png";
        String hoverTexture = "ui_nova_global_mediumbutton_green_over.png";
        String downTexture = "ui_nova_global_mediumbutton_green_down.png";
        String disabledTexture = "ui_nova_global_mediumbutton_green_disabled.png";

        Runnable updateStyle = () -> {
            String currentTexture = button.isDisabled() ? disabledTexture : upTexture;
            double glowLevel = button.isDisabled() ? 0.0 : strengthGlow;
            double opacity = 1.0;

            button.setStyle(
                    "-fx-background-image: url('" + texturePath + currentTexture + "'); " +
                            "-fx-background-size: 100% 100%; " +
                            "-fx-background-repeat: no-repeat; " +
                            "-fx-background-color: transparent; " +
                            "-fx-font-family: 'Arial Black'; " +
                            "-fx-font-size: " + fontSize + "px; " +
                            "-fx-text-fill: " + (button.isDisabled()
                            ? "linear-gradient(from 0% 0% to 20% 100%, rgba(255,255,255,0.5), rgba(144,238,144,0.5))"
                            : "linear-gradient(from 0% 0% to 20% 100%, white, #90EE90)") + "; " +
                            "-fx-opacity: " + opacity + "; " +
                            "-fx-padding: 0;"
            );
            buttonGlow.setLevel(glowLevel);
        };

        updateStyle.run();

        button.disabledProperty().addListener((obs, oldVal, newVal) -> updateStyle.run());

        button.setOnMouseEntered(e -> {
            if (!isPressed[0] && !button.isDisabled()) {
                button.setStyle(
                        "-fx-background-image: url('" + texturePath + hoverTexture + "'); " +
                                "-fx-background-size: 100% 100%; " +
                                "-fx-background-repeat: no-repeat; " +
                                "-fx-background-color: transparent; " +
                                "-fx-font-family: 'Arial Black'; " +
                                "-fx-font-size: " + fontSize + "px; " +
                                "-fx-text-fill: linear-gradient(from 0% 0% to 100% 100%, white, #F0FFF0);" +
                                "-fx-padding: 0;"
                );
                buttonGlow.setLevel(strengthGlowMAX);
            }
        });

        button.setOnMouseExited(e -> {
            if (!isPressed[0]) {
                updateStyle.run();
            }
        });

        button.setOnMousePressed(e -> {
            if (!button.isDisabled()) {
                isPressed[0] = true;
                button.setStyle(
                        "-fx-background-image: url('" + texturePath + downTexture + "'); " +
                                "-fx-background-size: 100% 100%; " +
                                "-fx-background-repeat: no-repeat; " +
                                "-fx-background-color: transparent; " +
                                "-fx-font-family: 'Arial Black'; " +
                                "-fx-font-size: " + fontSize + "px; " +
                                "-fx-text-fill: linear-gradient(from 0% 0% to 100% 100%, white, #F0FFF0);" +
                                "-fx-padding: 0;"
                );
            }
        });

        button.setOnMouseReleased(e -> {
            if (!button.isDisabled()) {
                isPressed[0] = false;
                updateStyle.run();
            }
        });

        return button;
    }

    // public static ComboBox<String> createCustomLanguageComboBox(String texturePath) {
    //     ComboBox<String> comboBox = new ComboBox<>();
    //     comboBox.setPrefSize(180, 45);

    //     // Define textures for button and arrow
    //     String normalTexture = "ui_glue_dropdownbutton_normal_terran.png";
    //     String overTexture = "ui_glue_dropdownbutton_over_terran.png";
    //     String downTexture = "ui_glue_dropdownbutton_Pressed_terran.png";
    //     String arrowNormal = "ui_glue_dropdownarrow_normal_terran.png";
    //     String arrowOver = "ui_glue_dropdownarrow_over_terran.png";
    //     String arrowPressed = "ui_glue_dropdownarrow_pressed_terran.png";

    //     // Apply initial styling for the ComboBox
    //     comboBox.setStyle(
    //         "-fx-background-color: transparent; " +
    //         "-fx-background-image: url('" + texturePath + normalTexture + "'), url('" + texturePath + arrowNormal + "'); " +
    //         "-fx-background-size: stretch, 16px 16px; " +
    //         "-fx-background-repeat: no-repeat, no-repeat; " +
    //         "-fx-background-position: center, right 20px center; " +
    //         "-fx-padding: -4 0 0 15; " +
    //         "-fx-alignment: center-left;"
    //     );

    //     Platform.runLater(() -> {
    //         // 
    //         Node listView = comboBox.lookup(".list-view");
    //         if (listView != null && listView instanceof Region) {
    //             ((Region) listView).setStyle(
    //                 "-fx-background-color: #001000;" + // 
    //                 "-fx-border-color: limegreen;" + // 
    //                 "-fx-border-width: 2px;" + // 
    //                 "-fx-border-radius: 8px;" + // 
    //                 "-fx-background-radius: 8px;" + // 
    //                 "-fx-padding: 2px;" // 
    //             );
    //         }
    //     });




    //     // Restore text styling for the ComboBox
    //     Platform.runLater(() -> {
    //         Text textNode = (Text) comboBox.lookup(".text");
    //         if (textNode != null) {
    //             textNode.setStyle(
    //                 "-fx-fill: linear-gradient(from 0% 0% to 100% 100%, white, limegreen);"
    //             );
    //           /*   textNode.setTranslateY(-4); // Shift text slightly upward
    //             textNode.setTranslateX(15); // Shift text slightly to the right*/
    //         }
    //     });

    //     // Ensure the default arrow is hidden
    //     Platform.runLater(() -> {
    //         Node arrowButton = comboBox.lookup(".arrow-button");
    //         if (arrowButton != null) {
    //             arrowButton.setOpacity(0); // Completely hide the default arrow
    //             arrowButton.setManaged(false); // Remove layout reservation
    //         }
    //     });
    //     System.out.println("onShown");
    //     comboBox.setOnMouseEntered(e -> comboBox.setStyle(
    //         "-fx-background-color: transparent; " +
    //         "-fx-background-image: url('" + texturePath + overTexture + "'), url('" + texturePath + arrowOver + "'); " +
    //         "-fx-background-size: stretch, 16px 16px; " +
    //         "-fx-background-repeat: no-repeat, no-repeat; " +
    //         "-fx-background-position: center, right 20px center; " +
    //         "-fx-padding: -4 0 0 15; " +
    //         "-fx-alignment: center-left;"
    //     ));

    //     comboBox.setOnMouseExited(e -> comboBox.setStyle(
    //         "-fx-background-color: transparent; " +
    //         "-fx-background-image: url('" + texturePath + normalTexture + "'), url('" + texturePath + arrowNormal + "'); " +
    //         "-fx-background-size: stretch, 16px 16px; " +
    //         "-fx-background-repeat: no-repeat, no-repeat; " +
    //         "-fx-background-position: center, right 20px center; " +
    //         "-fx-padding: -4 0 0 15; " +
    //         "-fx-alignment: center-left;"
    //     ));

    //     comboBox.setOnMousePressed(e -> comboBox.setStyle(
    //         "-fx-background-color: transparent; " +
    //         "-fx-background-image: url('" + texturePath + downTexture + "'), url('" + texturePath + arrowPressed + "'); " +
    //         "-fx-background-size: stretch, 16px 16px; " +
    //         "-fx-background-repeat: no-repeat, no-repeat; " +
    //         "-fx-background-position: center, right 20px center; " +
    //         "-fx-padding: -4 0 0 15; " +
    //         "-fx-alignment: center-left;"
    //     ));

    //     comboBox.setOnMouseReleased(e -> comboBox.setStyle(
    //         "-fx-background-color: transparent; " +
    //         "-fx-background-image: url('" + texturePath + normalTexture + "'), url('" + texturePath + arrowNormal + "'); " +
    //         "-fx-background-size: stretch, 16px 16px; " +
    //         "-fx-background-repeat: no-repeat, no-repeat; " +
    //         "-fx-background-position: center, right 20px center; " +
    //         "-fx-padding: -4 0 0 15; " +
    //         "-fx-alignment: center-left;"
    //     ));
    //     comboBox.setOnShown(e -> Platform.runLater(() -> { // 
    //         comboBox.lookupAll(".list-cell").forEach(cell -> {
    //             cell.setStyle(
    //                 "-fx-background-color: transparent;" + /* 
    //            //     "-fx-background-image: url('" + texturePath + frameTexture + "');" + /* 
    //                 "-fx-background-size: stretch;" + /* 
    //                 "-fx-background-repeat: no-repeat;" + /* 
    //                 "-fx-background-position: center;" + /* 
    //                 "-fx-font-family: 'Arial Black';" +
    //                 "-fx-font-size: 14px;" +
    //                 "-fx-text-fill: linear-gradient(from 0% 0% to 100% 100%, white, limegreen);" +
    //                 "-fx-alignment: center;"

    //             );
    //         });
    //     }));


    //  comboBox.show();// 
    //  comboBox.hide();// 

    //     return comboBox;
    // }
    // public static ComboBox<String> addTrackTrumb(ComboBox<String> comboBox, String texturePath) {
    //     String arrowNormal = "ui_glue_dropdownarrow_normal_terran.png";
    //     /* .thumb — 
    //        .track — 
    //     Platform.runLater(() -> {
    //         // 
    //         Node decrementButton = comboBox.lookup(".decrement-button");
    //         if (decrementButton != null) {
    //             // 
    //             Node decrementArrow = decrementButton.lookup(".decrement-arrow");
    //             if (decrementArrow != null) {
    //                 String currentStyle = decrementArrow.getStyle();
    //                 decrementArrow.setStyle(
    //                     currentStyle + "; " +
    //                     "-fx-opacity: 0; -fx-background-color: white;"
    //                 );
    //             }

    //             // 
    //             String currentStyle = decrementButton.getStyle();
    //             decrementButton.setStyle(
    //                 currentStyle + "; " +
    //                 "-fx-background-color: transparent; " + // 
    //                 "-fx-background-image: url('" + texturePath + arrowNormal + "'); " + // 
    //                 "-fx-background-size: 16px 16px; " + // 
    //                 "-fx-background-repeat: no-repeat; " +
    //                 "-fx-background-position: center;" + // 
    //                 "-fx-rotate: 180;" + // 
    //                 "-fx-border-color: limegreen; " + // 
    //                 "-fx-border-width: 2px; " + // 
    //                 "-fx-border-radius: 5px;" + // 
    //                 "-fx-effect: dropshadow(gaussian, black, 5, 0.5, 0, 0);" // 
    //             );
    //         }

    //         // 
    //         Node incrementButton = comboBox.lookup(".increment-button");
    //         if (incrementButton != null) {
    //             // 
    //             Node incrementArrow = incrementButton.lookup(".increment-arrow");
    //             if (incrementArrow != null) {
    //                 String currentStyle = incrementArrow.getStyle();
    //                 incrementArrow.setStyle(
    //                     currentStyle + "; " +
    //                     "-fx-opacity: 0; -fx-background-color: transparent;"
    //                 );
    //             }

    //             // 
    //             String currentStyle = incrementButton.getStyle();
    //             incrementButton.setStyle(
    //                 currentStyle + "; " +
    //                 "-fx-background-color: transparent; " + // 
    //                 "-fx-background-image: url('" + texturePath + arrowNormal + "'); " + // 
    //                 "-fx-background-size: 16px 16px; " + // 
    //                 "-fx-background-repeat: no-repeat; " +
    //                 "-fx-background-position: center;" + // 
    //                 "-fx-border-color: limegreen; " + // 
    //                 "-fx-border-width: 2px; " + // 
    //                 "-fx-border-radius: 5px;" + // 
    //                 "-fx-effect: dropshadow(gaussian, black, 5, 0.5, 0, 0);" // 
    //             );
    //         }

    //         // 
    //         Node track = comboBox.lookup(".track");
    //         if (track != null) {
    //             String currentStyle = track.getStyle();
    //             track.setStyle(
    //                 currentStyle + "; " +
    //                 "-fx-background-color: transparent; " +
    //                 "-fx-border-color: limegreen; " + // 
    //                 "-fx-border-width: 2px; " +
    //                 "-fx-border-radius: 25px;" // 
    //             );
    //         }

    //         // 
    //         Node trackBackground = comboBox.lookup(".track-background");
    //         if (trackBackground != null) {
    //             String currentStyle = trackBackground.getStyle();
    //             trackBackground.setStyle(
    //                 currentStyle + "; " +
    //                 "-fx-background-color: #001000; " // 
    //             );
    //         }

    //         // 
    //         Node thumb = comboBox.lookup(".thumb");
    //         if (thumb != null) {
    //             String currentStyle = thumb.getStyle();
    //             thumb.setStyle(
    //                 currentStyle + "; " +
    //                 "-fx-background-color: limegreen; " +
    //                 "-fx-border-color: black; " + // 
    //                 "-fx-border-width: 3px;" +
    //                 "-fx-border-radius: 25px;" // 
    //             );
    //         }
    //     });

    //     return comboBox;
    // }


    // public static ComboBox<String> addListItemTexture(ComboBox<String> comboBox, String texturePath) {
    //     String frameTexture = "ui_glue_dropdownmenuframe_terran.png";
    //     String frameSelectedTexture = "ui_glue_dropdownmenubutton_selected_terran.png";

    //     // Customize list cells
    //     comboBox.setCellFactory(param -> new ListCell<String>() {
    //         @Override
    //         protected void updateItem(String item, boolean empty) {
    //             super.updateItem(item, empty);

    //             if (empty || item == null) {
    //                 setText(null);
    //                 setGraphic(null);
    //                 setStyle(""); // Reset style for empty cells
    //             } else {
    //                 setText(item); // Set the text of the item
    //                 String currentStyle = getStyle(); // Retrieve the current style
    //                 setStyle(
    //                     currentStyle + "; " +
    //                     "-fx-background-color: black;" + // Transparent background
    //                     "-fx-background-image: url('" + texturePath + frameTexture + "');" +
    //                     "-fx-background-size: 100% 100%; " +
    //                     "-fx-background-repeat: no-repeat; " +
    //                     "-fx-font-family: 'Arial Black';" +
    //                     "-fx-font-size: 14;" +
    //                     "-fx-text-fill: linear-gradient(from 0% 0% to 100% 100%, white, limegreen);" +
    //                     "-fx-alignment: center;" // 
    //                 );

    //                 // 
    //                 setOnMouseEntered(e -> {
    //                     if (!isSelected()) { // 
    //                         String hoverStyle = getStyle(); // 
    //                         setStyle(
    //                             hoverStyle + "; " +
    //                             "-fx-background-color: black;" +
    //                             "-fx-background-image: url('" + texturePath + frameTexture + "');" +
    //                             "-fx-background-size: 100% 100%; " +
    //                             "-fx-background-repeat: no-repeat; " +
    //                             "-fx-font-family: 'Arial Black';" +
    //                             "-fx-font-size: 14;" +
    //                             "-fx-text-fill: linear-gradient(from 0% 0% to 100% 100%, yellow, red);" + // 
    //                             "-fx-alignment: center;"
    //                         );
    //                     }
    //                 });

    //                 // 
    //                 setOnMouseExited(e -> {
    //                     if (!isSelected()) { // 
    //                         String exitStyle = getStyle(); // 
    //                         setStyle(
    //                             exitStyle + "; " +
    //                             "-fx-background-color: black;" +
    //                             "-fx-background-image: url('" + texturePath + frameTexture + "');" +
    //                             "-fx-background-size: 100% 100%; " +
    //                             "-fx-background-repeat: no-repeat; " +
    //                             "-fx-font-family: 'Arial Black';" +
    //                             "-fx-font-size: 14;" +
    //                             "-fx-text-fill: linear-gradient(from 0% 0% to 100% 100%, white, limegreen);" + // 
    //                             "-fx-alignment: center;"
    //                         );
    //                     }
    //                 });
    //             }

    //             // 
    //             if (isSelected()) {
    //                 String selectedStyle = getStyle(); // 
    //                 setStyle(
    //                     selectedStyle + "; " +
    //                     "-fx-background-color: rgba(0, 0, 0, 0.5);" + // 
    //                     "-fx-background-image: url('" + texturePath + frameSelectedTexture + "');" +
    //                     "-fx-background-size: 100% 100%; " +
    //                     "-fx-background-repeat: no-repeat; " +
    //                     "-fx-font-family: 'Arial Black';" +
    //                     "-fx-font-size: 14px;" +
    //                     "-fx-text-fill: white;" + // 
    //                     "-fx-alignment: center;" // 
    //                 );
    //             }
    //         }
    //     });
    //     return comboBox;
    // }

    // public static ComboBox<String> setCustomClassicListItem(ComboBox<String> comboBox) {
    //     // 
    //     Platform.runLater(() -> {
    //         comboBox.setCellFactory(param -> new ListCell<String>() {
    //             @Override
    //             protected void updateItem(String item, boolean empty) {
    //                 super.updateItem(item, empty);

    //                 if (empty || item == null) {
    //                     setText(null);
    //                     setGraphic(null);
    //                     setStyle(""); // Reset style for empty cells
    //                 } else {
    //                     setText(item); // Set the text of the item
    //                     setStyle(
    //                         "-fx-background-color: #001000;" + // Transparent background
    //                         "-fx-background-image: none;" +
    //                         "-fx-background-radius: 3px;" +
    //                         "-fx-border-radius: 3px;" +
    //                         "-fx-background-insets: 0 2 0 2;" +
    //                         "-fx-border-insets: 0 2 0 2;" +
    //                         "-fx-background-size: 100% 100%; " +
    //                         "-fx-background-repeat: no-repeat; " +
    //                         "-fx-font-family: 'Arial Black';" +
    //                         "-fx-font-size: 12px;" +
    //                         "-fx-pref-height: 20px;" +
    //                         "-fx-text-fill: linear-gradient(from 0% 0% to 100% 100%, white, limegreen);" +
    //                         "-fx-alignment: center;" // 
    //                     );

    //                     // 
    //                     setOnMouseEntered(e -> {
    //                         if (!isSelected()) { // 
    //                             setStyle(
    //                           //      "-fx-border-color: darkgreen;" +
    //                                 "-fx-background-color: rgba(30, 80, 40, 0.2);" +
    //                                 "-fx-background-image: none;" +
    //                                 "-fx-background-insets: 0 2 0 2;" +
    //                                 "-fx-border-insets: 0 2 0 2;" +
    //                                // "-fx-border-radius: 2px;" +
    //                                 "-fx-background-radius: 3px;" +
    //                                 "-fx-border-radius: 3px;" +
    //                                 "-fx-background-size: 100% 100%; " +
    //                                 "-fx-background-repeat: no-repeat; " +
    //                                 "-fx-font-family: 'Arial Black';" +
    //                                 "-fx-font-size: 12px;" +
    //                                 "-fx-pref-height: 20px;" +
    //                                 "-fx-text-fill: linear-gradient(from 0% 0% to 100% 100%, yellow, red);" + // 
    //                                 "-fx-alignment: center;"
    //                             );
    //                         }
    //                     });

    //                     // 
    //                     setOnMouseExited(e -> {
    //                         if (!isSelected()) { // 
    //                             setStyle(
    //                                 "-fx-background-color: #001000;" +
    //                                 "-fx-background-image: none;"  +
    //                                 "-fx-background-insets: 0 2 0 2;" +
    //                                 "-fx-border-insets: 0 2 0 2;" +
    //                                 "-fx-background-radius: 3px;" +
    //                                 "-fx-border-radius: 3px;" +
    //                                 "-fx-background-size: 100% 100%; " +
    //                                 "-fx-background-repeat: no-repeat; " +
    //                                 "-fx-font-family: 'Arial Black';" +
    //                                 "-fx-font-size: 12px;" +
    //                                 "-fx-pref-height: 20px;" +
    //                                 "-fx-text-fill: linear-gradient(from 0% 0% to 100% 100%, white, limegreen);" + // 
    //                                 "-fx-alignment: center;"
    //                             );
    //                         }
    //                     });
    //                 }

    //                 // Apply different style for the selected cell
    //                 if (isSelected()) {
    //                     setStyle(
    //                         "-fx-border-color: darkgreen;" + // 
    //                         "-fx-background-color: rgba(30, 80, 40, 0.6);" + // 
    //                         "-fx-background-radius: 3px;" +
    //                         "-fx-border-radius: 3px;" +
    //                         "-fx-background-insets: 0 2 0 2;" +
    //                         "-fx-border-insets: 0 2 0 2;" +
    //                         "-fx-font-family: 'Arial Black';" +
    //                         "-fx-font-size: 12px;" +
    //                         "-fx-pref-height: 20px;" +
    //                         "-fx-text-fill: #C7F0C7;" + // 
    //                         "-fx-alignment: center;" // 
    //                     );
    //                 }
    //             }
    //         });
    //     });
    //     Node listView = comboBox.lookup(".list-view");
    //     if (listView instanceof Region) {
    //         ((Region) listView).setPadding(new Insets(0)); // 
    //         ((Region) listView).setStyle(
    //             "-fx-background-color: transparent;" +
    //             "-fx-border-width: 0;" // 
    //         );
    //     }

    //     return comboBox;
    // }
    // public static ComboBox<String> hideScrollBar(ComboBox<String> comboBox) {
    //     Platform.runLater(() -> {
    //         Node scrollBar = comboBox.lookup(".scroll-bar");
    //         if (scrollBar != null) {
    //             String currentStyle = scrollBar.getStyle();
    //             scrollBar.setStyle(
    //                 currentStyle + "; " +
    //                 "-fx-opacity: 0; -fx-background-color: transparent;"
    //             );
    //             scrollBar.setManaged(false);
    //         }

    //         Node listView = comboBox.lookup(".list-view");
    //         if (listView instanceof Region) {
    //  //           String currentStyle = ((Region) listView).getStyle();
    //             ((Region) listView).setPadding(new Insets(0));
    //         }
    //     });
    //     return comboBox;
    // }

}

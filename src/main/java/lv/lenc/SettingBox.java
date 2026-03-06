package lv.lenc;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.util.Duration;

import java.util.HashMap;
import java.util.Map;

public class SettingBox {

    // --- UI refs for localization updates ---
    private static final GaussianBlur blurEffect = new GaussianBlur(0);
    private static Node blurredTarget; // что размываем (обычно appRoot)
    private static GlowingLabel languageLabel;
    private static GlowingLabel uiLabel;
    private static GlowingLabel uilabelFLASH;
    private static GlowingLabel uilabelPOINT;
    private static GlowingLabel uilabelGRIDE;
    private static GlowingLabel otherDescrption;

    private static CustomAlternativeButton uiDEFAUTBUTTON;
    private static CustomAlternativeButton saveButton;
    private static CustomAlternativeButton discordURL;

    private static CustomLanguageButton[] menuButtons;

    private static LabeledCheckRow tableLightCheckBox;
    private static LabeledCheckRow shimmerRow;
    private static LabeledCheckRow backgroundLightCheckBox;

    // --- overlay instance (kept between openings) ---
    private static StackPane overlayRoot;     // full-screen dim layer
    private static StackPane windowHolder;    // centers the window
    private static Pane windowContent;        // actual settings window (your old root)

    // we keep last references for updateTexts() calls
    private static LocalizationManager lastLocalization;
    private static CustomTableView lastTableView;

    private SettingBox() {}

    /**
     * Show settings as an in-app overlay ("window in window") inside the provided appRoot StackPane.
     */
    public static void show(
            StackPane appRoot,
            LocalizationManager localization,
            BackgroundGridLayer background,
            CustomLongButton longButton,
            Main main,
            CustomBorder borderTable,
            CustomTableView tableView
    ) {
        lastLocalization = localization;
        lastTableView = tableView;

        if (overlayRoot == null) {
            overlayRoot = buildOverlay(appRoot, localization, background, longButton, main, borderTable, tableView);
            appRoot.getChildren().add(overlayRoot);
        } else {
            // rebuild content each open (safe way to refresh UI sizes/textures/state)
            windowHolder.getChildren().clear();
            windowContent = buildWindowContent(localization, background, longButton, main, borderTable, tableView);
            windowHolder.getChildren().add(windowContent);
        }

        ensureStylesheets(appRoot.getScene());

        ensureStylesheets(appRoot.getScene());

// blur ONLY background layer (not the overlay itself)
        blurredTarget = (!appRoot.getChildren().isEmpty()) ? appRoot.getChildren().get(0) : appRoot;

        if (blurredTarget.getEffect() == null) {
            blurEffect.setRadius(0);
            blurredTarget.setEffect(blurEffect);
        }

        overlayRoot.setVisible(true);
        overlayRoot.setOpacity(0);
        overlayRoot.setMouseTransparent(false);

        Platform.runLater(() -> {
            overlayRoot.applyCss();
            overlayRoot.layout();

            overlayRoot.setOpacity(1);
            overlayRoot.requestFocus();

            playOpenAnim(windowContent);

            // animate blur in (only after effect is set)
            javafx.animation.Timeline blurIn = new javafx.animation.Timeline(
                    new javafx.animation.KeyFrame(Duration.millis(0),
                            new javafx.animation.KeyValue(blurEffect.radiusProperty(), 0)),
                    new javafx.animation.KeyFrame(Duration.millis(220),
                            new javafx.animation.KeyValue(
                                    blurEffect.radiusProperty(),
                                    UiScaleHelper.scaleY(10),
                                    Interpolator.EASE_OUT
                            ))
            );
            blurIn.play();
        });

        if (blurredTarget.getEffect() == null) {
            blurEffect.setRadius(0);
            blurredTarget.setEffect(blurEffect);
        }
    }

    /**
     * Close overlay (and deselect button).
     */
    private static void close(CustomLongButton longButton) {
        if (overlayRoot != null) {
            overlayRoot.setVisible(false);
            overlayRoot.setMouseTransparent(true);
        }
        if (blurredTarget != null && blurredTarget.getEffect() == blurEffect) {
            javafx.animation.Timeline blurOut = new javafx.animation.Timeline(
                    new javafx.animation.KeyFrame(Duration.millis(0),
                            new javafx.animation.KeyValue(blurEffect.radiusProperty(), blurEffect.getRadius())),
                    new javafx.animation.KeyFrame(Duration.millis(160),
                            new javafx.animation.KeyValue(blurEffect.radiusProperty(), 0, Interpolator.EASE_IN))
            );
            blurOut.setOnFinished(ev -> blurredTarget.setEffect(null));
            blurOut.play();
        }
        if (longButton != null) longButton.deselect();
    }

    private static StackPane buildOverlay(
            StackPane appRoot,
            LocalizationManager localization,
            BackgroundGridLayer background,
            CustomLongButton longButton,
            Main main,
            CustomBorder borderTable,
            CustomTableView tableView
    ) {
        // Dim background (clicking outside closes)
        Region dim = new Region();
        dim.setStyle("-fx-background-color: rgba(0,0,0,0.55);");
        dim.setPrefSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
        dim.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        // holder to center the window
        windowHolder = new StackPane();
        windowHolder.setPickOnBounds(false);
        StackPane.setAlignment(windowHolder, Pos.CENTER);

        windowContent = buildWindowContent(localization, background, longButton, main, borderTable, tableView);
        windowHolder.getChildren().add(windowContent);

        StackPane overlay = new StackPane(dim, windowHolder);
        overlay.setVisible(false);
        overlay.setMouseTransparent(true);
        overlay.setFocusTraversable(true);

        dim.setOnMouseClicked(e -> e.consume());

        // ESC closes
        overlay.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                close(longButton);
                e.consume();
            }
        });

            // keep overlay sized
        overlay.prefWidthProperty().bind(appRoot.widthProperty());
        overlay.prefHeightProperty().bind(appRoot.heightProperty());

        return overlay;
    }

    private static void ensureStylesheets(Scene scene) {
        if (scene == null) return;

        String[] sheets = new String[]{
                SettingBox.class.getResource("/Assets/Style/custom-checkbox.css").toExternalForm(),
                SettingBox.class.getResource("/Assets/Style/CustomAlternativeButton.css").toExternalForm(),
                SettingBox.class.getResource("/Assets/Style/CustomCloseButton.css").toExternalForm(),
                SettingBox.class.getResource("/Assets/Style/CustomComboBoxClassic.css").toExternalForm(),
                SettingBox.class.getResource("/Assets/Style/CustomLanguageButton.css").toExternalForm(),
                SettingBox.class.getResource("/Assets/Style/CustomSlider.css").toExternalForm(),
                SettingBox.class.getResource("/Assets/Style/GlowingLabel.css").toExternalForm()
        };

        for (String s : sheets) {
            if (!scene.getStylesheets().contains(s)) {
                scene.getStylesheets().add(s);
            }
        }
    }

    private static void playOpenAnim(Pane content) {
        if (content == null) return;

        double fromY = UiScaleHelper.scaleY(-26);

        content.setOpacity(0);
        content.setTranslateY(fromY);

        FadeTransition fade = new FadeTransition(Duration.millis(220), content);
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.setInterpolator(Interpolator.EASE_OUT);

        TranslateTransition move = new TranslateTransition(Duration.millis(220), content);
        move.setFromY(fromY);
        move.setToY(0);
        move.setInterpolator(Interpolator.EASE_OUT);

        new ParallelTransition(fade, move).play();
    }

    /**
     * Builds the actual settings window content (old Stage root) but as a simple Pane.
     */
    private static Pane buildWindowContent(
            LocalizationManager localization,
            BackgroundGridLayer background,
            CustomLongButton longButton,
            Main main,
            CustomBorder borderTable,
            CustomTableView tableView
    ) {
        final double WIDTH  = UiScaleHelper.scaleX(473);
        final double HEIGHT = UiScaleHelper.scaleY(509);

        String texturePath = SettingBox.class.getResource("/Assets/Textures/").toExternalForm();

        Image borderImage = new Image(texturePath + "ui_nova_archives_listitem_normal_cropped_52_boosted.png");
        Image highlightImage = new Image(texturePath + "ui_nova_archives_listitem_selected.png");

        BorderImage border = new BorderImage(
                borderImage,
                new BorderWidths(1),
                Insets.EMPTY,
                new BorderWidths(1),
                true,
                BorderRepeat.STRETCH,
                BorderRepeat.STRETCH
        );

        double highlightThickness = UiScaleHelper.scale(8);
        BorderImage highlightBorder = new BorderImage(
                highlightImage,
                new BorderWidths(highlightThickness),
                Insets.EMPTY,
                new BorderWidths(highlightThickness),
                true,
                BorderRepeat.STRETCH,
                BorderRepeat.STRETCH
        );

        Region frame = new Region();
        frame.setPrefSize(WIDTH, HEIGHT);
        frame.setStyle("-fx-background-color: black;");
        frame.setBorder(new Border(border));

        // Left panel with buttons
        VBox buttonBox = new VBox(UiScaleHelper.scaleY(20));
        buttonBox.setPadding(new Insets(
                UiScaleHelper.scaleY(20),
                UiScaleHelper.scaleX(10),
                UiScaleHelper.scaleY(20),
                UiScaleHelper.scaleX(20)
        ));

        Region lefthighlightRegion = new Region();
        lefthighlightRegion.setMinWidth(UiScaleHelper.scaleX(140));
        lefthighlightRegion.setMinHeight(UiScaleHelper.scaleY(470));
        StackPane.setAlignment(lefthighlightRegion, Pos.TOP_LEFT);
        lefthighlightRegion.setBorder(new Border(highlightBorder));

        Image selectionImage = new Image(texturePath + "ui_nova_equipmentupgrades_carditemiconframe_orange_selectedbartop.png");
        ImageView selectionMarkImage = new ImageView(selectionImage);
        selectionMarkImage.setManaged(false);
        selectionMarkImage.setRotate(270);
        selectionMarkImage.setFitWidth(UiScaleHelper.scaleX(75));
        selectionMarkImage.setPreserveRatio(true);
        selectionMarkImage.setVisible(false);

        Pane leftPanel = new Pane();
        lefthighlightRegion.setLayoutX(0);
        lefthighlightRegion.setLayoutY(0);
        buttonBox.setLayoutX(0);
        buttonBox.setLayoutY(0);
        leftPanel.getChildren().addAll(lefthighlightRegion, buttonBox, selectionMarkImage);

        // Close button (in-app close)
        CustomCloseButton closeButton = new CustomCloseButton();
        closeButton.setOnAction(e -> close(longButton));

        // -------------------------
        // Language Panel
        // -------------------------
        languageLabel = new GlowingLabel(localization.get("settingbox.language.choose"));

        CustomComboBoxClassic<String> languageComboBox =
                new CustomComboBoxClassic<>(texturePath, false, 200, 45, 15, 13);

        languageComboBox.getItems().addAll(
                localization.get("settingbox.language.ru"),
                localization.get("settingbox.language.en")
        );

        languageComboBox.setOnAction(e -> {
            int selectedIndex = languageComboBox.getSelectionModel().getSelectedIndex();
            String lang = (selectedIndex == 0) ? "ru" : "en";

            SettingsManager.saveLanguage(lang);
            localization.changeLanguage(lang);

            background.getScene().getRoot().applyCss();

            Platform.runLater(() -> {
                main.updateTexts();
                SettingBox.updateTexts(localization, tableView);
            });
        });

        String currentLang = SettingsManager.loadLanguage();
        if ("ru".equals(currentLang)) {
            languageComboBox.setValue(localization.get("settingbox.language.ru"));
            languageComboBox.getSelectionModel().select(0);
        } else {
            languageComboBox.setValue(localization.get("settingbox.language.en"));
            languageComboBox.getSelectionModel().select(1);
        }

        VBox languageBox = new VBox(UiScaleHelper.scaleY(10), languageLabel, languageComboBox);
        languageBox.setAlignment(Pos.TOP_CENTER);
        Pane languageView = new StackPane(languageBox);

        // -------------------------
        // UI Settings Panel
        // -------------------------
        uiLabel = new GlowingLabel(localization.get("setting.box.ui.placeholder"));
        VBox.setMargin(uiLabel, new Insets(0, 0, UiScaleHelper.scaleY(10), 0));

        uilabelFLASH = new GlowingLabel(localization.get("setting.box.ui.flash"));
        CustomSlider sliderFlash = new CustomSlider(0, 100, background.getFlashAlpha() * 100);

        uilabelPOINT = new GlowingLabel(localization.get("setting.box.ui.point"));
        CustomSlider sliderPoint = new CustomSlider(0, 100, background.getPointAlpha() * 100);

        uilabelGRIDE = new GlowingLabel(localization.get("setting.box.ui.gride"));
        CustomSlider slideGRIDE = new CustomSlider(0, 12, background.getGridAlpha() * 100);

        sliderFlash.valueProperty().addListener((obs, oldVal, newVal) ->
                background.setFlashAlpha(newVal.doubleValue() / 100.0)
        );
        sliderPoint.valueProperty().addListener((obs, oldVal, newVal) ->
                background.setPointAlpha(newVal.doubleValue() / 100.0)
        );
        slideGRIDE.valueProperty().addListener((obs, oldVal, newVal) ->
                background.setGridAlpha(newVal.doubleValue() / 100.0)
        );

        tableLightCheckBox = new LabeledCheckRow(
                localization.get("setting.box.ui.tableLight"),
                SettingsManager.loadCheckboxState(SettingsManager.TABLE_LIGHTING_KEY, true)
        );
        shimmerRow = new LabeledCheckRow(
                localization.get("setting.box.ui.shimmers"),
                SettingsManager.loadCheckboxState(SettingsManager.SHIMMERS_KEY, true)
        );
        backgroundLightCheckBox = new LabeledCheckRow(
                localization.get("setting.box.ui.backgroundBlur"),
                SettingsManager.loadCheckboxState(SettingsManager.BLUR_KEY, true)
        );

        backgroundLightCheckBox.getCheckBox().selectedProperty().addListener((obs, oldVal, newVal) -> {
            background.blurredLights.setVisible(newVal);
        });
        shimmerRow.getCheckBox().selectedProperty().addListener((obs, oldVal, newVal) -> {
            background.shimmerContainer.setVisible(newVal);
        });
        tableLightCheckBox.getCheckBox().selectedProperty().addListener((obs, oldVal, newVal) -> {
            borderTable.setTableLightingVisible(newVal);
        });

        uiDEFAUTBUTTON = new CustomAlternativeButton(
                localization.get("setting.box.ui.defaut"),
                0.6, 0.8, 170.0, 54, 14
        );
        uiDEFAUTBUTTON.setOnAction(e -> {
            background.setFlashAlpha(SettingsManager.DEFAULT_FLASH_ALPHA);
            background.setGridAlpha(SettingsManager.DEFAULT_GRID_ALPHA);
            background.setPointAlpha(SettingsManager.DEFAULT_POINT_ALPHA);

            sliderFlash.setValue(33);
            slideGRIDE.setValue(1);
            sliderPoint.setValue(8);

            tableLightCheckBox.getCheckBox().setSelected(SettingsManager.DEFAULT_TABLE_LIGHTING);
            shimmerRow.getCheckBox().setSelected(SettingsManager.DEFAULT_SHIMMERS);
            backgroundLightCheckBox.getCheckBox().setSelected(SettingsManager.DEFAULT_BACKGROUND_BLUR);
        });

        saveButton = new CustomAlternativeButton(
                localization.get("button.save"),
                0.6, 0.8, 170.0, 54, 14
        );
        saveButton.setOnAction(e -> {
            SettingsManager.saveAllSettings(
                    background.getGridAlpha(),
                    background.getPointAlpha(),
                    background.getFlashAlpha(),
                    tableLightCheckBox.getCheckBox().isSelected(),
                    shimmerRow.getCheckBox().isSelected(),
                    backgroundLightCheckBox.getCheckBox().isSelected()
            );
        });

        VBox uiBox = new VBox(
                UiScaleHelper.scaleY(10),
                uiLabel,
                uilabelFLASH, sliderFlash,
                uilabelPOINT, sliderPoint,
                uilabelGRIDE, slideGRIDE,
                backgroundLightCheckBox,
                tableLightCheckBox,
                shimmerRow,
                uiDEFAUTBUTTON,
                saveButton
        );
        uiBox.setAlignment(Pos.TOP_CENTER);
        Pane uiView = new StackPane(uiBox);

        // -------------------------
        // Other Panel
        // -------------------------
        otherDescrption = new GlowingLabel(localization.get("setting.box.other.description"));
        VBox.setMargin(otherDescrption, new Insets(0, 0, UiScaleHelper.scaleY(10), 0));
        otherDescrption.setWrapText(true);
        otherDescrption.setPrefSize(UiScaleHelper.scaleX(260), UiScaleHelper.scaleY(100));

        discordURL = new CustomAlternativeButton(
                localization.get("setting.box.other.join"),
                0.6, 0.8, 200.0, 55.0, 14.0
        );
        discordURL.setOnAction(e -> {
            try {
                java.awt.Desktop.getDesktop().browse(new java.net.URI("https://discord.com/invite/UKYgsB6Zrx"));
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });

        VBox otherBox = new VBox(10, otherDescrption, discordURL);
        otherBox.setAlignment(Pos.TOP_CENTER);
        Pane otherView = new StackPane(otherBox);

        // Panels mapped by index
        Map<Integer, Pane> viewMap = new HashMap<>();
        viewMap.put(0, languageView);
        viewMap.put(1, uiView);
        viewMap.put(4, otherView);

        // Configure right panel
        languageView.setVisible(false);
        uiView.setVisible(false);
        otherView.setVisible(false);

        StackPane rightPanel = new StackPane(languageView, uiView, otherView);
        rightPanel.setMinSize(UiScaleHelper.scaleX(316), UiScaleHelper.scaleY(470));
        rightPanel.setMaxSize(UiScaleHelper.scaleX(316), UiScaleHelper.scaleY(470));
        rightPanel.setPrefSize(UiScaleHelper.scaleX(316), UiScaleHelper.scaleY(470));
        rightPanel.setBorder(new Border(highlightBorder));
        rightPanel.setAlignment(Pos.TOP_CENTER);
        rightPanel.setPadding(new Insets(10));

        // Menu buttons
        String[] keys = {
                localization.get("setting.box.language"),
                localization.get("setting.box.ui"),
                localization.get("setting.box.audio"),
                localization.get("setting.box.controls"),
                localization.get("setting.box.other"),
        };

        languageView.setMinSize(UiScaleHelper.scaleX(306), UiScaleHelper.scaleY(470));
        uiView.setMinSize(UiScaleHelper.scaleX(306), UiScaleHelper.scaleY(470));
        otherView.setMinSize(UiScaleHelper.scaleX(306), UiScaleHelper.scaleY(470));

        menuButtons = new CustomLanguageButton[5];

        for (int i = 0; i < keys.length; i++) {
            String key = keys[i];
            menuButtons[i] = new CustomLanguageButton(key, 104, 56, 16);

            VBox.setMargin(menuButtons[i], new Insets(0, 0, 0, -1));

            final int index = i;
            final CustomLanguageButton button = menuButtons[i];

            button.setOnAction(e -> {
                viewMap.values().forEach(p -> p.setVisible(false));

                Pane pane = viewMap.get(index);
                if (pane != null) pane.setVisible(true);

                int buttonIndex = buttonBox.getChildren().indexOf(button);
                double buttonHeight = button.getHeight();
                double spacing = UiScaleHelper.scaleY(20);
                double paddingTop = UiScaleHelper.scaleY(20);

                double centerY = paddingTop
                        + buttonIndex * (buttonHeight + spacing)
                        + buttonHeight / 2.0;

                double markerHeight = selectionMarkImage.getBoundsInParent().getHeight();
                double baseOffset = UiScaleHelper.scaleY(28);
                double scaleFactor = UiScaleHelper.scale(1);

                double hdLift = (scaleFactor < 1.0) ? UiScaleHelper.scaleY(6) : 0.0;
                double adjustedY = centerY - markerHeight / 2.0 + baseOffset - hdLift;

                selectionMarkImage.setLayoutY(adjustedY);
                selectionMarkImage.setLayoutX(-UiScaleHelper.scaleX(20));
                selectionMarkImage.setVisible(true);

                selectionMarkImage.setOpacity(0);

                double startTranslate = UiScaleHelper.scaleY(-6);
                double fromY = UiScaleHelper.scaleY(-9);
                double toY = UiScaleHelper.scaleY(3);

                double animLift = (scaleFactor < 1.0) ? UiScaleHelper.scaleY(2) : 0.0;

                selectionMarkImage.setTranslateY(startTranslate - animLift);

                FadeTransition fade = new FadeTransition(Duration.seconds(0.33), selectionMarkImage);
                fade.setFromValue(0);
                fade.setToValue(1);
                fade.setInterpolator(Interpolator.EASE_OUT);

                TranslateTransition moveDown = new TranslateTransition(Duration.seconds(0.33), selectionMarkImage);
                moveDown.setFromY(fromY - animLift);
                moveDown.setToY(toY - animLift);
                moveDown.setInterpolator(Interpolator.EASE_OUT);

                new ParallelTransition(fade, moveDown).play();
            });

            if (index >= 2 && index <= 3) {
                menuButtons[i].disable(true);
            }

            buttonBox.getChildren().add(menuButtons[i]);
        }

        // Layout
        HBox contentLayout = new HBox(0, leftPanel, rightPanel);
        contentLayout.setAlignment(Pos.TOP_LEFT);
        contentLayout.setPadding(new Insets(
                UiScaleHelper.scaleY(20),
                UiScaleHelper.scaleX(6),
                UiScaleHelper.scaleY(20),
                UiScaleHelper.scaleX(8)
        ));

        Pane root = new Pane();
        root.setPrefSize(WIDTH, HEIGHT);
        root.setMinSize(WIDTH, HEIGHT);
        root.setMaxSize(WIDTH, HEIGHT);

        root.getChildren().addAll(frame, contentLayout, closeButton);

        // place close button after layout
        root.layoutBoundsProperty().addListener((obs, oldVal, newVal) -> {
            double w = newVal.getWidth();
            closeButton.setLayoutX(w - closeButton.getWidth() + UiScaleHelper.scaleX(2));
            closeButton.setLayoutY(-UiScaleHelper.scaleY(3));
        });

        // default open first tab (language)
        Platform.runLater(() -> {
            if (menuButtons != null && menuButtons.length > 0 && menuButtons[0] != null) {
                menuButtons[0].fire();
            }
        });

        return root;
    }

    public static void updateTexts(LocalizationManager localization, CustomTableView tableView) {
        if (languageLabel != null) languageLabel.setText(localization.get("settingbox.language.choose"));
        if (uiLabel != null) uiLabel.setText(localization.get("setting.box.ui.placeholder"));
        if (uilabelFLASH != null) uilabelFLASH.setText(localization.get("setting.box.ui.flash"));
        if (uilabelPOINT != null) uilabelPOINT.setText(localization.get("setting.box.ui.point"));
        if (uilabelGRIDE != null) uilabelGRIDE.setText(localization.get("setting.box.ui.gride"));
        if (uiDEFAUTBUTTON != null) uiDEFAUTBUTTON.setText(localization.get("setting.box.ui.defaut"));
        if (saveButton != null) saveButton.setText(localization.get("button.save"));
        if (otherDescrption != null) otherDescrption.setText(localization.get("setting.box.other.description"));
        if (discordURL != null) discordURL.setText(localization.get("setting.box.other.join"));

        if (tableView != null) {
            tableView.updatePlaceholderText(localization.get("table.placeholder"));
        }

        if (tableLightCheckBox != null) tableLightCheckBox.setLabel(localization.get("setting.box.ui.tableLight"));
        if (shimmerRow != null) shimmerRow.setLabel(localization.get("setting.box.ui.shimmers"));
        if (backgroundLightCheckBox != null) backgroundLightCheckBox.setLabel(localization.get("setting.box.ui.backgroundBlur"));

        if (menuButtons != null) {
            String[] keys = {
                    localization.get("setting.box.language"),
                    localization.get("setting.box.ui"),
                    localization.get("setting.box.audio"),
                    localization.get("setting.box.controls"),
                    localization.get("setting.box.other")
            };
            for (int i = 0; i < menuButtons.length; i++) {
                if (i < keys.length && menuButtons[i] != null) {
                    menuButtons[i].setText(keys[i]);
                }
            }
        }
    }

    // Optional: if you want to force-close from outside
    public static void hideIfOpen() {
        if (overlayRoot != null && overlayRoot.isVisible()) {
            overlayRoot.setVisible(false);
            overlayRoot.setMouseTransparent(true);
        }
    }
    public static void prewarm(
            StackPane appRoot,
            LocalizationManager localization,
            BackgroundGridLayer background,
            CustomLongButton longButton,
            Main main,
            CustomBorder borderTable,
            CustomTableView tableView
    ) {
        if (overlayRoot == null) {
            overlayRoot = buildOverlay(appRoot, localization, background, longButton, main, borderTable, tableView);
            appRoot.getChildren().add(overlayRoot);
        }

        //
        Platform.runLater(() -> {
            overlayRoot.applyCss();
            overlayRoot.layout();
            overlayRoot.setVisible(false);
            overlayRoot.setMouseTransparent(true);
            overlayRoot.setOpacity(1);
        });
    }
}
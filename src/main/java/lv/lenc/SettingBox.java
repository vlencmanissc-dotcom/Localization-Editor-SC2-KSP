package lv.lenc;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import java.util.HashMap;
import java.util.Map;

public class SettingBox {
    private static GlowingLabel languageLabel;
    private static GlowingLabel uiLabel;
    private static GlowingLabel uilabelFLASH;
    private static GlowingLabel uilabelPOINT;
    private static GlowingLabel uilabelGRIDE;
    private static GlowingLabel uilabelBlurLight;
    private static GlowingLabel otherDescrption;
    private static GlowingLabel uilabelShimmer;
    private static CustomAlternativeButton uiDEFAUTBUTTON;
    private static CustomAlternativeButton saveButton;
    private static CustomAlternativeButton discordURL;
    private static CustomLanguageButton[] menuButtons;
    private static LabeledCheckRow tableLightCheckBox;
    private static LabeledCheckRow shimmerRow;
    private static LabeledCheckRow backgroundLightCheckBox;

    public static void show(LocalizationManager localization, BackgroundGridLayer background, CustomLongButton longButton, Main main, CustomBorder borderTable, CustomTableView tableView) {

        final double WIDTH  = UiScaleHelper.scaleX(473);
        final double HEIGHT = UiScaleHelper.scaleY(509);

        Stage window = new Stage();
        window.initOwner(longButton.getScene().getWindow());   // или main.Window если хочешь
        window.initModality(Modality.WINDOW_MODAL);

        window.setAlwaysOnTop(true);

        window.initStyle(StageStyle.UNDECORATED);
        window.setWidth(WIDTH);
        window.setHeight(HEIGHT);

        String texturePath = SettingBox.class.getResource("/Assets/Textures/").toExternalForm();

        Image borderImage = new Image(texturePath + "ui_nova_archives_listitem_normal_cropped_52_boosted.png");

        //  Image highlightImage = new Image(texturePath + "ui_nova_archives_listitem_over.png");
        Image highlightImage = new Image(texturePath + "ui_nova_archives_listitem_selected.png");

        BorderImage border = new BorderImage(borderImage, new BorderWidths(1), Insets.EMPTY, new BorderWidths(1), true,
                BorderRepeat.STRETCH, BorderRepeat.STRETCH);
        double highlightThickness = UiScaleHelper.scale(8);  // min(scaleX, scaleY)
        BorderImage highlightBorder = new BorderImage(
                highlightImage,
                new BorderWidths(highlightThickness),
                Insets.EMPTY,
                new BorderWidths(highlightThickness),
                true,
                BorderRepeat.STRETCH,
                BorderRepeat.STRETCH
        );
        BorderImage righthighlightBorder = highlightBorder;

        Region frame = new Region();
        frame.setPrefSize(WIDTH, HEIGHT);
        frame.setStyle("-fx-background-color: black;");
        frame.setBorder(new Border(border));

        // Левая панель с кнопками
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

        StackPane.setMargin(lefthighlightRegion, new Insets(
                0,
                UiScaleHelper.scaleX(16),
                0,
                0
        ));

        StackPane.setAlignment(lefthighlightRegion, Pos.TOP_LEFT);

        lefthighlightRegion.setBorder(new Border(highlightBorder));
        Image selectionImage = new Image(texturePath + "ui_nova_equipmentupgrades_carditemiconframe_orange_selectedbartop.png");
        ImageView selectionMarkImage = new ImageView(selectionImage);
        selectionMarkImage.setManaged(false);
        selectionMarkImage.setRotate(270);
        selectionMarkImage.setFitWidth(UiScaleHelper.scaleX(75));
        selectionMarkImage.setPreserveRatio(true);
        selectionMarkImage.setVisible(false); // по умолчанию скрыт

        // StackPane leftPanel = new StackPane();
        //      leftPanel.getChildren().addAll(lefthighlightRegion, selectionMarkImage, buttonBox);

        Pane leftPanel = new Pane();
        lefthighlightRegion.setLayoutX(0);
        lefthighlightRegion.setLayoutY(0);
        buttonBox.setLayoutX(0);
        buttonBox.setLayoutY(0); // или сколько надо по смещению


        leftPanel.getChildren().addAll(lefthighlightRegion, buttonBox, selectionMarkImage);


        CustomCloseButton closeButton = new CustomCloseButton();
        StackPane.setAlignment(closeButton, Pos.TOP_RIGHT);
        closeButton.setOnAction(e -> {
            ((Stage) closeButton.getScene().getWindow()).close();
            longButton.deselect();
        });
        window.setOnHidden(e -> longButton.deselect());


        // Language Panel
        languageLabel = new GlowingLabel(localization.get("settingbox.language.choose"));
        CustomComboBoxClassic<String> languageComboBox = new CustomComboBoxClassic<>(texturePath, false,200,45,15,13);
        languageComboBox.getItems().addAll(
                localization.get("settingbox.language.ru"),
                localization.get("settingbox.language.en")
        );
        languageComboBox.setOnAction(e -> {
            int selectedIndex = languageComboBox.getSelectionModel().getSelectedIndex();
            String lang = selectedIndex == 0 ? "ru" : "en";
            SettingsManager.saveLanguage(lang);
            localization.changeLanguage(lang);

            if (selectedIndex == 0) {
                localization.changeLanguage("ru");
                SettingsManager.saveLanguage("ru");
            } else if (selectedIndex == 1) {
                localization.changeLanguage("en");
                SettingsManager.saveLanguage("en");
            }

            background.getScene().getRoot().applyCss();

            Platform.runLater(() -> {
                main.updateTexts();
                SettingBox.updateTexts(localization, tableView);
            });
        });

        String currentLang = SettingsManager.loadLanguage();
        if (currentLang.equals("ru")) {
            languageComboBox.setValue(localization.get("settingbox.language.ru"));
            languageComboBox.getSelectionModel().select(0);
        } else {
            languageComboBox.setValue(localization.get("settingbox.language.en"));
            languageComboBox.getSelectionModel().select(1);
        }

        VBox languageBox = new VBox(UiScaleHelper.scaleY(10), languageLabel, languageComboBox);
        languageBox.setAlignment(Pos.TOP_CENTER);
        Pane languageView = new StackPane(languageBox);
        // UI Settings Panel
        uiLabel = new GlowingLabel(
                localization.get("setting.box.ui.placeholder"));
        VBox.setMargin(uiLabel, new Insets(0, 0, UiScaleHelper.scaleY(10), 0)); // нижний отступ = 30
        uilabelFLASH = new GlowingLabel(
                localization.get("setting.box.ui.flash")
        );

        CustomSlider sliderFlash = new CustomSlider(0, 100, background.getFlashAlpha() * 100);
        uilabelPOINT = new GlowingLabel(
                localization.get("setting.box.ui.point")
        );
        CustomSlider sliderPoint = new CustomSlider(0, 100, background.getPointAlpha() * 100);
        uilabelGRIDE = new GlowingLabel(
                localization.get("setting.box.ui.gride")
        );
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
        // Для дымки:
        backgroundLightCheckBox.getCheckBox().selectedProperty().addListener((obs, oldVal, newVal) -> {
            background.blurredLights.setVisible(newVal);
        });

// Для шиммеров:
        shimmerRow.getCheckBox().selectedProperty().addListener((obs, oldVal, newVal) -> {
            background.shimmerContainer.setVisible(newVal);
        });

// Для свечения таблицы:
        tableLightCheckBox.getCheckBox().selectedProperty().addListener((obs, oldVal, newVal) -> {
            borderTable.setTableLightingVisible(newVal);
        });
        uiDEFAUTBUTTON = new CustomAlternativeButton(localization.get("setting.box.ui.defaut"), 0.6, 0.8,170.0,54,14);
        uiDEFAUTBUTTON.setOnAction(e -> {
            background.setFlashAlpha(SettingsManager.DEFAULT_FLASH_ALPHA);
            background.setGridAlpha(SettingsManager.DEFAULT_GRID_ALPHA);
            background.setPointAlpha(SettingsManager.DEFAULT_POINT_ALPHA);
            sliderFlash.setValue(33); // Если хочешь тоже по дефолту — смотри пункт 3!
            slideGRIDE.setValue(1);
            sliderPoint.setValue(8);
            tableLightCheckBox.getCheckBox().setSelected(SettingsManager.DEFAULT_TABLE_LIGHTING);
            shimmerRow.getCheckBox().setSelected(SettingsManager.DEFAULT_SHIMMERS);
            backgroundLightCheckBox.getCheckBox().setSelected(SettingsManager.DEFAULT_BACKGROUND_BLUR);
        });

        saveButton = new CustomAlternativeButton(
                localization.get("button.save"),
                0.6, 0.8,170.0,54,14
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
        VBox uiBox = new VBox(UiScaleHelper.scaleY(10), uiLabel, uilabelFLASH, sliderFlash, uilabelPOINT, sliderPoint, uilabelGRIDE,
                slideGRIDE, backgroundLightCheckBox, tableLightCheckBox, shimmerRow, uiDEFAUTBUTTON, saveButton);
        uiBox.setAlignment(Pos.TOP_CENTER);
        Pane uiView = new StackPane(uiBox);
        // About Autor

        otherDescrption = new GlowingLabel(
                localization.get("setting.box.other.description"));
        VBox.setMargin(otherDescrption, new Insets(0, 0, UiScaleHelper.scaleY(10), 0)); // нижний отступ = 30
        otherDescrption.setWrapText(true); // Включить перенос строк
        otherDescrption.setPrefSize(UiScaleHelper.scaleX(260),UiScaleHelper.scaleY(100));
        discordURL = new CustomAlternativeButton(
                localization.get("setting.box.other.join"),
                 0.6, 0.8,200.0,55.0,14.0);
       // discordURL.setMinSize(200, 55);
        VBox OtherBox = new VBox(10, otherDescrption, discordURL);
        discordURL.setOnAction(e -> {
            try {
                java.awt.Desktop.getDesktop().browse(new java.net.URI("https://discord.com/invite/UKYgsB6Zrx"));
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });
        OtherBox.setAlignment(Pos.TOP_CENTER);
        Pane OtherView = new StackPane(OtherBox);

        // Панели по карте
        Map<Integer, Pane> viewMap = new HashMap<>();
        viewMap.put(0, languageView);
        viewMap.put(1, uiView);
        viewMap.put(4, OtherView);
        // Настройка панели справа
        languageView.setVisible(false);
        uiView.setVisible(false);
        OtherView.setVisible(false);

        StackPane rightPanel = new StackPane(languageView, uiView, OtherView);
        rightPanel.setMinSize(UiScaleHelper.scaleX(316), UiScaleHelper.scaleY(470));
        rightPanel.setMaxSize(UiScaleHelper.scaleX(316), UiScaleHelper.scaleY(470));
        rightPanel.setPrefSize(UiScaleHelper.scaleX(316), UiScaleHelper.scaleY(470));
        rightPanel.setBorder(new Border(righthighlightBorder));
        rightPanel.setAlignment(Pos.TOP_CENTER);
        rightPanel.setPadding(new Insets(10));
        StackPane.setMargin(lefthighlightRegion, new Insets(0, -UiScaleHelper.scaleX(16), 0, 0));
        // Кнопки
        String[] keys = {
                localization.get("setting.box.language"),
                localization.get("setting.box.ui"),
                localization.get("setting.box.audio"),
                localization.get("setting.box.controls"),
                localization.get("setting.box.other"),

        };

        languageView.setMinSize(UiScaleHelper.scaleX(306), UiScaleHelper.scaleY(470));
        uiView.setMinSize(UiScaleHelper.scaleX(306), UiScaleHelper.scaleY(470));
        OtherView.setMinSize(UiScaleHelper.scaleX(306), UiScaleHelper.scaleY(470));
        menuButtons = new CustomLanguageButton[5];
        for (int i = 0; i < keys.length; i++) {
            String key = keys[i];
            menuButtons[i] = new CustomLanguageButton(key,104,56,16);

            VBox.setMargin(menuButtons[i], new Insets(0, 0, 0, -1));

            final int index = i;
            final CustomLanguageButton button = menuButtons[i];
            button.setOnAction(e -> {

                // Hide all right-side panels
                viewMap.values().forEach(p -> p.setVisible(false));

                // Show selected panel
                Pane pane = viewMap.get(index);
                if (pane != null) {
                    pane.setVisible(true);
                }

                // Get index of clicked button inside VBox
                int buttonIndex = buttonBox.getChildren().indexOf(button);

                // Actual button height after layout
                double buttonHeight = button.getHeight();

                // IMPORTANT: vertical spacing must use scaleY (not scaleX)
                double spacing = UiScaleHelper.scaleY(20);
                double paddingTop = UiScaleHelper.scaleY(20);

                // Calculate vertical center of selected button
                double centerY = paddingTop
                        + buttonIndex * (buttonHeight + spacing)
                        + buttonHeight / 2.0;

                // Get marker image height
                double markerHeight = selectionMarkImage.getBoundsInParent().getHeight();

                // Base offset (was hardcoded +28 before)
                double baseOffset = UiScaleHelper.scaleY(28);

                // Global UI scale (1.0 on FullHD, <1 on HD and below)
                double scaleFactor = UiScaleHelper.scale(1);

                // Lift marker slightly on HD only (adjust value if needed)
                double hdLift = (scaleFactor < 1.0)
                        ? UiScaleHelper.scaleY(6)
                        : 0.0;

                // Final vertical position
                double adjustedY = centerY - markerHeight / 2.0 + baseOffset - hdLift;

                selectionMarkImage.setLayoutY(adjustedY);
                selectionMarkImage.setLayoutX(-UiScaleHelper.scaleX(20));
                selectionMarkImage.setVisible(true);

                // ----- Animation -----

                selectionMarkImage.setOpacity(0);

                double startTranslate = UiScaleHelper.scaleY(-6);
                double fromY = UiScaleHelper.scaleY(-9);
                double toY   = UiScaleHelper.scaleY(3);

                // Slight animation correction for HD only
                double animLift = (scaleFactor < 1.0)
                        ? UiScaleHelper.scaleY(2)
                        : 0.0;

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
            ;

            buttonBox.getChildren().add(menuButtons[i]);
        }

        // Сборка финального интерфейса
        HBox contentLayout = new HBox(0, leftPanel, rightPanel); // убрали spacing
        contentLayout.setAlignment(Pos.TOP_LEFT);
        contentLayout.setPadding(new Insets(
                UiScaleHelper.scaleY(20),
                UiScaleHelper.scaleX(6),
                UiScaleHelper.scaleY(20),
                UiScaleHelper.scaleX(8)
        ));
        //  StackPane root = new StackPane(frame, contentLayout,closeButton);
        Pane root = new Pane();
        root.setPrefSize(WIDTH, HEIGHT);
        root.getChildren().addAll(frame, contentLayout, closeButton);

        Platform.runLater(() -> {
            closeButton.setLayoutX(WIDTH - closeButton.getWidth() + UiScaleHelper.scaleX(2));
            closeButton.setLayoutY(-UiScaleHelper.scaleY(3));
        });
        Scene scene = new Scene(root, WIDTH, HEIGHT);
        scene.setFill(null);

        window.setScene(scene);


        scene.getStylesheets().addAll(
                SettingBox.class.getResource("/Assets/Style/custom-checkbox.css").toExternalForm(),
                SettingBox.class.getResource("/Assets/Style/CustomAlternativeButton.css").toExternalForm(),
                SettingBox.class.getResource("/Assets/Style/CustomCloseButton.css").toExternalForm(),
                SettingBox.class.getResource("/Assets/Style/CustomComboBoxClassic.css").toExternalForm(),
                SettingBox.class.getResource("/Assets/Style/CustomLanguageButton.css").toExternalForm(),
                SettingBox.class.getResource("/Assets/Style/CustomSlider.css").toExternalForm(),
                SettingBox.class.getResource("/Assets/Style/GlowingLabel.css").toExternalForm()
        );

        window.setOpacity(0.0);
        window.show();
        Platform.runLater(() -> {
            root.applyCss();
            root.layout();

            window.setOpacity(1.0);
            window.toFront();
            window.requestFocus();
        });
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
        tableView.updatePlaceholderText(localization.get("table.placeholder"));
        if (tableLightCheckBox != null) {
            tableLightCheckBox.setLabel(localization.get("setting.box.ui.tableLight"));
        }
        if (shimmerRow != null) {
            shimmerRow.setLabel(localization.get("setting.box.ui.shimmers"));
        }
        if (backgroundLightCheckBox != null) {
            backgroundLightCheckBox.setLabel(localization.get("setting.box.ui.backgroundBlur"));
        }

        // Обновим названия кнопок в меню слева
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


}

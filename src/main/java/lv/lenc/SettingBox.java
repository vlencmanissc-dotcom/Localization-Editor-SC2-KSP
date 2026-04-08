package lv.lenc;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderImage;
import javafx.scene.layout.BorderRepeat;
import javafx.scene.layout.BorderWidths;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import javafx.util.Duration;

public class SettingBox {
    private static final double SETTINGS_WINDOW_SCALE = 1.5125;
    private static final double API_ROW_WIDTH = 238.0;
    private static final double API_FIELD_WIDTH = 184.0;
    private static final double API_ICON_SLOT_WIDTH = 48.0;

    private static double effectiveSettingsScale() {
        double ui = UiScaleHelper.scale(1.0);
        if (ui >= 1.0) {
            return SETTINGS_WINDOW_SCALE;
        }
        // On smaller-than-FHD resolutions reduce the extra boost,
        // so text and paddings stay readable and do not collapse.
        double adaptiveFactor = 0.68 + 0.32 * ui;
        return SETTINGS_WINDOW_SCALE * adaptiveFactor;
    }

    private static double sv(double fullHdValue) {
        return fullHdValue * effectiveSettingsScale();
    }

    private static double sx(double fullHdValue) {
        return UiScaleHelper.scaleX(sv(fullHdValue));
    }

    private static double sy(double fullHdValue) {
        return UiScaleHelper.scaleY(sv(fullHdValue));
    }

    private static double ss(double fullHdValue) {
        return UiScaleHelper.scale(sv(fullHdValue));
    }

    private static void tuneSettingLabel(GlowingLabel label, double baseFullHdFontPx) {
        if (label == null) return;
        label.setFont(Font.font("Arial Black", sy(baseFullHdFontPx)));
    }

    private static void tuneSettingCheckRow(LabeledCheckRow row) {
        if (row == null) return;

        row.getLabel().setFont(Font.font("Arial Black", sy(17)));
        row.getLabel().setAlignment(Pos.CENTER_LEFT);
        row.getLabel().setTextAlignment(TextAlignment.LEFT);
        row.getCheckBox().setStyle("-fx-font-size: " + sy(18) + "px;");
        row.getCheckBox().setTranslateY(sy(1.5));
        row.setSpacing(sx(10));
        row.setPadding(new Insets(sy(1), sx(12), sy(1), sx(12)));
    }

    private static String buildGoogleApiKeyLabel(LocalizationManager localization) {
        return localization.get("setting.box.other.googleApiKey");
    }

    private static String buildGoogleApiKeyHint(LocalizationManager localization) {
        return localization.get("setting.box.other.googleApiKeyHint");
    }

    private static String buildSiliconFlowApiKeyLabel(LocalizationManager localization) {
        return localization.get("setting.box.other.siliconflowApiKey");
    }

    private static String buildSiliconFlowApiKeyHint(LocalizationManager localization) {
        return localization.get("setting.box.other.siliconflowApiKeyHint");
    }
    private static String localizedOrFallback(LocalizationManager localization, String key, String fallback) {
        String value = localization.get(key);
        if (value == null || value.isBlank() || key.equals(value)) {
            return fallback;
        }
        return value;
    }
    private static String buildCloudflareServiceLabel(LocalizationManager localization) {
        String fallback = localization.get("setting.box.other.cloudflareAccountId")
                + " / "
                + localization.get("setting.box.other.cloudflareApiToken");
        return localizedOrFallback(localization, "setting.box.other.cloudflareService", fallback);
    }
    private static String buildCloudflareServiceHint(LocalizationManager localization) {
        String fallback = localization.get("setting.box.other.cloudflareApiTokenHint");
        return localizedOrFallback(localization, "setting.box.other.cloudflareServiceHint", fallback);
    }

    // --- UI refs for localization updates ---
    private static final GaussianBlur blurEffect = new GaussianBlur(0);
    private static Node blurredTarget; // что размываем (обычно appRoot)
    private static GlowingLabel languageLabel;
    private static GlowingLabel uiLabel;
    private static GlowingLabel uilabelFLASH;
    private static GlowingLabel uilabelPOINT;
    private static GlowingLabel uilabelGRIDE;
    private static GlowingLabel controlsLabel;
    private static GlowingLabel googleApiKeyLabel;
    private static GlowingLabel cloudflareServiceLabel;
    private static GlowingLabel geminiApiKeyLabel;
    private static GlowingLabel siliconFlowApiKeyLabel;
    private static GlowingLabel deepLApiKeyLabel;
    private static GlowingLabel otherDescrption;

    private static CustomAlternativeButton uiDEFAUTBUTTON;
    private static CustomAlternativeButton saveButton;
    private static CustomAlternativeButton saveApiKeysButton;
    private static CustomAlternativeButton apiGuideButton;
    private static CustomAlternativeButton discordURL;
    private static CustomAlternativeButton clearCacheButton;

    private static CustomLanguageButton[] menuButtons;
    private static final List<LanguageOption> LANGUAGE_OPTIONS = List.of(
            new LanguageOption("ru", "Русский"),
            new LanguageOption("de", "Deutsch"),
            new LanguageOption("en", "English"),
            new LanguageOption("es-MX", "Español (Latinoamérica)"),
            new LanguageOption("es-ES", "Español (España)"),
            new LanguageOption("fr", "Français"),
            new LanguageOption("it", "Italiano"),
            new LanguageOption("pl", "Polski"),
            new LanguageOption("pt-BR", "Português (Brasil)"),
            new LanguageOption("ko", "한국어"),
            new LanguageOption("zh-CN", "简体中文"),
            new LanguageOption("zh-TW", "繁體中文")
    );

    private static LabeledCheckRow tableLightCheckBox;
    private static LabeledCheckRow shimmerRow;
    private static LabeledCheckRow backgroundLightCheckBox;
    private static LabeledCheckRow translationCachePersistRow;
    private static LabeledCheckRow useGpuDockerRow;
    private static LabeledCheckRow baseGlossaryRow;
    private static LabeledCheckRow unitsGlossaryRow;
    private static LabeledCheckRow weaponsGlossaryRow;
    private static LabeledCheckRow abilitiesGlossaryRow;
    private static GlowingLabel dictionariesLabel;
    private static ApiHintIcon dictionariesHintIcon;
    private static TextField googleApiKeyField;
    private static TextField cloudflareAccountIdField;
    private static TextField cloudflareApiTokenField;
    private static TextField geminiApiKeyField;
    private static TextField siliconFlowApiKeyField;
    private static TextField deepLApiKeyField;
    private static ApiHintIcon googleApiKeyHintIcon;
    private static ApiHintIcon cloudflareServiceHintIcon;
    private static ApiHintIcon geminiApiKeyHintIcon;
    private static ApiHintIcon siliconFlowApiKeyHintIcon;
    private static ApiHintIcon deepLApiKeyHintIcon;

    // --- overlay instance (kept between openings) ---
    private static StackPane overlayRoot;     // full-screen dim layer
    private static StackPane windowHolder;    // centers the window
    private static Pane windowContent;        // actual settings window (your old root)
    private static final List<Consumer<Boolean>> visibilityListeners = new CopyOnWriteArrayList<>();

    private SettingBox() {}

    private static void persistApiKeys() {
        if (googleApiKeyField == null) {
            return;
        }
        SettingsManager.saveGoogleTranslateApiKey(googleApiKeyField.getText());
        if (cloudflareAccountIdField != null) {
            SettingsManager.saveCloudflareAccountId(cloudflareAccountIdField.getText());
        }
        if (cloudflareApiTokenField != null) {
            SettingsManager.saveCloudflareApiToken(cloudflareApiTokenField.getText());
        }
        if (geminiApiKeyField != null) {
            SettingsManager.saveGeminiApiKey(geminiApiKeyField.getText());
        }
        if (siliconFlowApiKeyField != null) {
            SettingsManager.saveSiliconFlowApiKey(siliconFlowApiKeyField.getText());
        }
        if (deepLApiKeyField != null) {
            SettingsManager.saveDeepLApiKey(deepLApiKeyField.getText());
        }
    }

    private static final class LanguageOption {
        final String code;
        final String nativeName;

        LanguageOption(String code, String nativeName) {
            this.code = code;
            this.nativeName = nativeName;
        }
    }

    private static String findNativeNameByCode(String code) {
        for (LanguageOption opt : LANGUAGE_OPTIONS) {
            if (opt.code.equalsIgnoreCase(code)) return opt.nativeName;
        }
        return "English";
    }

    private static String findCodeByNativeName(String nativeName) {
        for (LanguageOption opt : LANGUAGE_OPTIONS) {
            if (opt.nativeName.equals(nativeName)) return opt.code;
        }
        return "en";
    }

    private static void tuneApiKeyField(TextField field, String promptText) {
        if (field == null) return;
        field.setPromptText(promptText);
        field.setMinWidth(sx(API_FIELD_WIDTH));
        field.setPrefWidth(sx(API_FIELD_WIDTH));
        field.setMaxWidth(sx(API_FIELD_WIDTH));
        field.setStyle(
                "-fx-background-color: rgba(4, 16, 16, 0.95);"
                        + "-fx-text-fill: #9fffe7;"
                        + "-fx-prompt-text-fill: rgba(159,255,231,0.45);"
                        + "-fx-highlight-fill: rgba(0,255,170,0.35);"
                        + "-fx-highlight-text-fill: white;"
                        + "-fx-border-color: rgba(0, 255, 170, 0.45);"
                        + "-fx-border-width: 1;"
                        + "-fx-background-radius: 6;"
                        + "-fx-border-radius: 6;"
                        + "-fx-font-size: " + sy(14) + "px;"
        );
    }

    private static HBox createApiFieldRow(TextField field, Node hintIcon) {
        HBox row = new HBox(sx(6), field, hintIcon);
        row.setAlignment(Pos.CENTER);
        row.setMinWidth(sx(API_ROW_WIDTH));
        row.setPrefWidth(sx(API_ROW_WIDTH));
        row.setMaxWidth(sx(API_ROW_WIDTH));
        return row;
    }
    private static HBox createApiFieldOnlyRow(TextField field) {
        Region rightSpacer = new Region();
        rightSpacer.setMinWidth(sx(API_ICON_SLOT_WIDTH));
        rightSpacer.setPrefWidth(sx(API_ICON_SLOT_WIDTH));
        rightSpacer.setMaxWidth(sx(API_ICON_SLOT_WIDTH));
        HBox row = new HBox(sx(6), field, rightSpacer);
        row.setAlignment(Pos.CENTER);
        row.setMinWidth(sx(API_ROW_WIDTH));
        row.setPrefWidth(sx(API_ROW_WIDTH));
        row.setMaxWidth(sx(API_ROW_WIDTH));
        field.setMinWidth(sx(API_FIELD_WIDTH));
        field.setPrefWidth(sx(API_FIELD_WIDTH));
        field.setMaxWidth(sx(API_FIELD_WIDTH));
        return row;
    }

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
        if (appRoot != null && appRoot.getScene() != null) {
            UiScaleHelper.refreshFromScene(appRoot.getScene());
        }

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
        notifyVisibilityChanged(true);

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
                                    sy(10),
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
            notifyVisibilityChanged(false);
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

        double fromY = sy(-26);

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
        final double WIDTH  = sx(500);
        final double HEIGHT = sy(509);

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

        double highlightThickness = ss(8);
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
        VBox buttonBox = new VBox(sy(20));
        final double leftMenuTopPadding = sy(14);
        buttonBox.setPadding(new Insets(
                leftMenuTopPadding,
                sx(10),
                sy(10),
                sx(13)
        ));

        Region lefthighlightRegion = new Region();
        lefthighlightRegion.setMinWidth(sx(162));
        lefthighlightRegion.setMinHeight(sy(484));
        StackPane.setAlignment(lefthighlightRegion, Pos.TOP_LEFT);
        lefthighlightRegion.setBorder(new Border(highlightBorder));

        Image selectionImage = new Image(texturePath + "ui_nova_equipmentupgrades_carditemiconframe_orange_selectedbartop.png");
        ImageView selectionMarkImage = new ImageView(selectionImage);
        selectionMarkImage.setManaged(false);
        selectionMarkImage.setRotate(270);
        selectionMarkImage.setFitWidth(sx(75));
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
        double closeButtonSize = sy(34);
        closeButton.setPrefSize(closeButtonSize, closeButtonSize);
        closeButton.setMinSize(closeButtonSize, closeButtonSize);
        closeButton.setMaxSize(closeButtonSize, closeButtonSize);
        closeButton.setOnAction(e -> close(longButton));

        // -------------------------
        // Language Panel
        // -------------------------
        languageLabel = new GlowingLabel(localization.get("settingbox.language.choose"));
        tuneSettingLabel(languageLabel, 19);

        CustomComboBoxClassic<String> languageComboBox =
                new CustomComboBoxClassic<>(texturePath, false, sv(260), sv(54), sv(11.9), sv(10.5), sv(28), 12);

        languageComboBox.getItems().setAll(
                LANGUAGE_OPTIONS.stream().map(o -> o.nativeName).toList()
        );

        languageComboBox.setOnAction(e -> {
            String selectedName = languageComboBox.getValue();
            String lang = findCodeByNativeName(selectedName);

            SettingsManager.saveLanguage(lang);
            localization.changeLanguage(lang);

            background.getScene().getRoot().applyCss();

            Platform.runLater(() -> {
                main.updateTexts();
                SettingBox.updateTexts(localization, tableView);
            });
        });

        String currentLang = SettingsManager.loadLanguage();
        languageComboBox.setValue(findNativeNameByCode(currentLang));

        VBox languageBox = new VBox(sy(10), languageLabel, languageComboBox);
        languageBox.setAlignment(Pos.TOP_CENTER);
        Pane languageView = new StackPane(languageBox);

        // -------------------------
        // UI Settings Panel
        // -------------------------
        uiLabel = new GlowingLabel(localization.get("setting.box.ui.placeholder"));
        tuneSettingLabel(uiLabel, 19);
        VBox.setMargin(uiLabel, new Insets(0, 0, sy(10), 0));

        uilabelFLASH = new GlowingLabel(localization.get("setting.box.ui.flash"));
        tuneSettingLabel(uilabelFLASH, 18);
        CustomSlider sliderFlash = new CustomSlider(0, 100, background.getFlashAlpha() * 100);

        uilabelPOINT = new GlowingLabel(localization.get("setting.box.ui.point"));
        tuneSettingLabel(uilabelPOINT, 18);
        CustomSlider sliderPoint = new CustomSlider(0, 100, background.getPointAlpha() * 100);

        uilabelGRIDE = new GlowingLabel(localization.get("setting.box.ui.gride"));
        tuneSettingLabel(uilabelGRIDE, 18);
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
        tuneSettingCheckRow(tableLightCheckBox);
        shimmerRow = new LabeledCheckRow(
                localization.get("setting.box.ui.shimmers"),
                SettingsManager.loadCheckboxState(SettingsManager.SHIMMERS_KEY, true)
        );
        tuneSettingCheckRow(shimmerRow);
        backgroundLightCheckBox = new LabeledCheckRow(
                localization.get("setting.box.ui.backgroundBlur"),
                SettingsManager.loadCheckboxState(SettingsManager.BLUR_KEY, true)
        );
        tuneSettingCheckRow(backgroundLightCheckBox);

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
                0.6, 0.8, sv(148.0), sv(50), sv(10.6)
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
                0.6, 0.8, sv(148.0), sv(50), sv(10.6)
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
            SettingsManager.saveTranslationCachePersistence(
                    translationCachePersistRow.getCheckBox().isSelected()
            );
            SettingsManager.saveUseGpuDocker(
                    useGpuDockerRow.getCheckBox().isSelected()
            );
            persistApiKeys();
        });

        HBox defaultSaveRow = new HBox(sx(8), uiDEFAUTBUTTON, saveButton);
        defaultSaveRow.setAlignment(Pos.CENTER);
        VBox.setMargin(defaultSaveRow, new Insets(sy(2), 0, 0, 0));
        defaultSaveRow.setTranslateY(-sy(22));

        Region uiBottomSpacer = new Region();
        VBox.setVgrow(uiBottomSpacer, Priority.ALWAYS);

        VBox uiBox = new VBox(
                sy(10),
                uiLabel,
                uilabelFLASH, sliderFlash,
                uilabelPOINT, sliderPoint,
                uilabelGRIDE, slideGRIDE,
                backgroundLightCheckBox,
                tableLightCheckBox,
                shimmerRow,
                uiBottomSpacer,
                defaultSaveRow
        );
        uiBox.setAlignment(Pos.TOP_CENTER);
        Pane uiView = new StackPane(uiBox);

        // -------------------------
        // Controls Panel (cache/GPU)
        // -------------------------
        translationCachePersistRow = new LabeledCheckRow(
                localization.get("setting.box.other.translationCachePersist"),
                SettingsManager.loadTranslationCachePersistence()
        );
        tuneSettingCheckRow(translationCachePersistRow);
        translationCachePersistRow.getCheckBox().selectedProperty().addListener((obs, oldVal, newVal) -> {
            TranslationService.setPersistentCacheEnabled(newVal);
            SettingsManager.saveTranslationCachePersistence(newVal);
            if (!newVal) {
                TranslationService.clearTranslationCache();
            }
        });

        useGpuDockerRow = new LabeledCheckRow(
                localization.get("setting.box.other.useGpuDocker"),
                SettingsManager.loadUseGpuDocker()
        );
        tuneSettingCheckRow(useGpuDockerRow);
        useGpuDockerRow.getCheckBox().selectedProperty().addListener((obs, oldVal, newVal) -> {
            TranslationService.setGpuDockerEnabled(newVal);
            SettingsManager.saveUseGpuDocker(newVal);
        });

        clearCacheButton = new CustomAlternativeButton(
                localization.get("setting.box.other.clearCache"),
                0.6, 0.8, sv(250.0), sv(62.0), sv(10.6)
        );
        clearCacheButton.setOnAction(e -> TranslationService.clearTranslationCache());

        translationCachePersistRow.setMaxWidth(sx(286));
        useGpuDockerRow.setMaxWidth(sx(286));
        translationCachePersistRow.setAlignment(Pos.CENTER_LEFT);
        useGpuDockerRow.setAlignment(Pos.CENTER_LEFT);
        double apiHintIconSize = sy(48); // 3x bigger than previous 16px icon
        double apiHintTooltipFont = sy(12);

        googleApiKeyLabel = new GlowingLabel(buildGoogleApiKeyLabel(localization));
        tuneSettingLabel(googleApiKeyLabel, 15);
        googleApiKeyLabel.setWrapText(true);
        googleApiKeyLabel.setPrefWidth(sx(286));

        googleApiKeyField = new TextField(SettingsManager.loadGoogleTranslateApiKey());
        tuneApiKeyField(googleApiKeyField, localization.get("setting.box.other.googleApiKeyPrompt"));
        googleApiKeyField.setOnAction(e -> persistApiKeys());
        googleApiKeyField.focusedProperty().addListener((obs, oldVal, focused) -> {
            if (!focused) {
                persistApiKeys();
            }
        });
        googleApiKeyHintIcon = new ApiHintIcon(
                buildGoogleApiKeyHint(localization),
                apiHintIconSize,
                apiHintTooltipFont
        );
        HBox googleApiKeyRow = createApiFieldRow(googleApiKeyField, googleApiKeyHintIcon);

        cloudflareServiceLabel = new GlowingLabel(buildCloudflareServiceLabel(localization));
        tuneSettingLabel(cloudflareServiceLabel, 15);
        cloudflareServiceLabel.setWrapText(true);
        cloudflareServiceLabel.setMinWidth(sx(API_ROW_WIDTH));
        cloudflareServiceLabel.setPrefWidth(sx(API_ROW_WIDTH));
        cloudflareServiceLabel.setMaxWidth(sx(API_ROW_WIDTH));

        cloudflareAccountIdField = new TextField(SettingsManager.loadCloudflareAccountId());
        tuneApiKeyField(cloudflareAccountIdField, localization.get("setting.box.other.cloudflareAccountIdPrompt"));
        cloudflareAccountIdField.setOnAction(e -> persistApiKeys());
        cloudflareAccountIdField.focusedProperty().addListener((obs, oldVal, focused) -> {
            if (!focused) {
                persistApiKeys();
            }
        });
        cloudflareServiceHintIcon = new ApiHintIcon(
                buildCloudflareServiceHint(localization),
                apiHintIconSize,
                apiHintTooltipFont
        );
        HBox cloudflareTitleRow = new HBox(cloudflareServiceLabel);
        cloudflareTitleRow.setAlignment(Pos.CENTER);
        cloudflareTitleRow.setMinWidth(sx(API_ROW_WIDTH));
        cloudflareTitleRow.setPrefWidth(sx(API_ROW_WIDTH));
        cloudflareTitleRow.setMaxWidth(sx(API_ROW_WIDTH));

        cloudflareApiTokenField = new TextField(SettingsManager.loadCloudflareApiToken());
        tuneApiKeyField(cloudflareApiTokenField, localization.get("setting.box.other.cloudflareApiTokenPrompt"));
        cloudflareApiTokenField.setOnAction(e -> persistApiKeys());
        cloudflareApiTokenField.focusedProperty().addListener((obs, oldVal, focused) -> {
            if (!focused) {
                persistApiKeys();
            }
        });
        VBox cloudflareFieldsColumn = new VBox(sy(10), cloudflareAccountIdField, cloudflareApiTokenField);
        cloudflareFieldsColumn.setAlignment(Pos.CENTER);
        cloudflareFieldsColumn.setMinWidth(sx(API_FIELD_WIDTH));
        cloudflareFieldsColumn.setPrefWidth(sx(API_FIELD_WIDTH));
        cloudflareFieldsColumn.setMaxWidth(sx(API_FIELD_WIDTH));
        HBox cloudflareFieldsRow = new HBox(sx(6), cloudflareFieldsColumn, cloudflareServiceHintIcon);
        cloudflareFieldsRow.setAlignment(Pos.CENTER);
        cloudflareFieldsRow.setMinWidth(sx(API_ROW_WIDTH));
        cloudflareFieldsRow.setPrefWidth(sx(API_ROW_WIDTH));
        cloudflareFieldsRow.setMaxWidth(sx(API_ROW_WIDTH));

        geminiApiKeyLabel = new GlowingLabel(localization.get("setting.box.other.geminiApiKey"));
        tuneSettingLabel(geminiApiKeyLabel, 15);
        geminiApiKeyLabel.setWrapText(true);
        geminiApiKeyLabel.setPrefWidth(sx(286));

        geminiApiKeyField = new TextField(SettingsManager.loadGeminiApiKey());
        tuneApiKeyField(geminiApiKeyField, localization.get("setting.box.other.geminiApiKeyPrompt"));
        geminiApiKeyField.setOnAction(e -> persistApiKeys());
        geminiApiKeyField.focusedProperty().addListener((obs, oldVal, focused) -> {
            if (!focused) {
                persistApiKeys();
            }
        });
        geminiApiKeyHintIcon = new ApiHintIcon(
                localization.get("setting.box.other.geminiApiKeyHint"),
                apiHintIconSize,
                apiHintTooltipFont
        );
        HBox geminiApiKeyRow = createApiFieldRow(geminiApiKeyField, geminiApiKeyHintIcon);

        siliconFlowApiKeyLabel = new GlowingLabel(buildSiliconFlowApiKeyLabel(localization));
        tuneSettingLabel(siliconFlowApiKeyLabel, 15);
        siliconFlowApiKeyLabel.setWrapText(true);
        siliconFlowApiKeyLabel.setPrefWidth(sx(286));

        siliconFlowApiKeyField = new TextField(SettingsManager.loadSiliconFlowApiKey());
        tuneApiKeyField(siliconFlowApiKeyField, localization.get("setting.box.other.siliconflowApiKeyPrompt"));
        siliconFlowApiKeyField.setOnAction(e -> persistApiKeys());
        siliconFlowApiKeyField.focusedProperty().addListener((obs, oldVal, focused) -> {
            if (!focused) {
                persistApiKeys();
            }
        });
        siliconFlowApiKeyHintIcon = new ApiHintIcon(
                buildSiliconFlowApiKeyHint(localization),
                apiHintIconSize,
                apiHintTooltipFont
        );
        HBox siliconFlowApiKeyRow = createApiFieldRow(siliconFlowApiKeyField, siliconFlowApiKeyHintIcon);

        deepLApiKeyLabel = new GlowingLabel(localization.get("setting.box.other.deeplApiKey"));
        tuneSettingLabel(deepLApiKeyLabel, 15);
        deepLApiKeyLabel.setWrapText(true);
        deepLApiKeyLabel.setPrefWidth(sx(286));

        deepLApiKeyField = new TextField(SettingsManager.loadDeepLApiKey());
        tuneApiKeyField(deepLApiKeyField, localization.get("setting.box.other.deeplApiKeyPrompt"));
        deepLApiKeyField.setOnAction(e -> persistApiKeys());
        deepLApiKeyField.focusedProperty().addListener((obs, oldVal, focused) -> {
            if (!focused) {
                persistApiKeys();
            }
        });
        deepLApiKeyHintIcon = new ApiHintIcon(
                localization.get("setting.box.other.deeplApiKeyHint"),
                apiHintIconSize,
                apiHintTooltipFont
        );
        HBox deepLApiKeyRow = createApiFieldRow(deepLApiKeyField, deepLApiKeyHintIcon);

        saveApiKeysButton = new CustomAlternativeButton(
                localization.get("button.save"),
                0.6, 0.8, sv(186.0), sv(48.0), sv(10.4)
        );
        saveApiKeysButton.setOnAction(e -> persistApiKeys());

        HBox saveApiKeysWrap = new HBox(saveApiKeysButton);
        saveApiKeysWrap.setAlignment(Pos.CENTER);

        apiGuideButton = new CustomAlternativeButton(
                localization.get("setting.box.api.guide"),
                0.6, 0.8, sv(286.0), sv(46.0), sv(10.4)
        );
        apiGuideButton.setOnAction(e -> {
            try {
                java.awt.Desktop.getDesktop().browse(new java.net.URI(
                        "https://github.com/VoVanRusLvSC2/Localization-Editor-SC2-KSP/blob/main/README_TRANSLATE.txt"
                ));
            } catch (Exception ex) {
                AppLog.exception(ex);
            }
        });
        HBox apiGuideWrap = new HBox(apiGuideButton);
        apiGuideWrap.setAlignment(Pos.CENTER);
        VBox.setMargin(apiGuideWrap, new Insets(sy(2), 0, 0, 0));

        HBox clearCacheWrap = new HBox(clearCacheButton);
        clearCacheWrap.setAlignment(Pos.CENTER);
        VBox.setMargin(clearCacheWrap, new Insets(sy(14), 0, sy(6), 0));

        controlsLabel = new GlowingLabel(localization.get("setting.box.controls"));
        tuneSettingLabel(controlsLabel, 17);
        VBox.setMargin(controlsLabel, new Insets(0, 0, sy(10), 0));

        VBox controlsBox = new VBox(
                sy(14),
                controlsLabel,
                translationCachePersistRow,
                useGpuDockerRow,
                clearCacheWrap
        );
        controlsBox.setAlignment(Pos.TOP_CENTER);
        controlsBox.setPadding(new Insets(sy(18), sx(10), sy(12), sx(10)));
        Pane controlsView = new StackPane(controlsBox);

        // -------------------------
        // Dictionaries Panel
        // -------------------------
        dictionariesLabel = new GlowingLabel(localization.get("setting.box.dictionaries"));
        tuneSettingLabel(dictionariesLabel, 28);
        dictionariesLabel.setAlignment(Pos.CENTER_LEFT);
        dictionariesLabel.setTextAlignment(TextAlignment.LEFT);
        dictionariesLabel.setMaxWidth(Region.USE_PREF_SIZE);
        dictionariesLabel.setPrefWidth(Region.USE_COMPUTED_SIZE);
        VBox.setMargin(dictionariesLabel, new Insets(0, 0, sy(10), 0));

        String dictHintText = localization.get("setting.box.dictionaries.hint");
        dictionariesHintIcon = new ApiHintIcon(dictHintText, sy(62), sy(15));
        dictionariesHintIcon.setTranslateY(-sy(1));

        HBox dictTitleRow = new HBox(sx(3), dictionariesLabel, dictionariesHintIcon);
        dictTitleRow.setAlignment(Pos.CENTER_LEFT);
        dictTitleRow.setPrefWidth(sx(286));
        dictTitleRow.setMaxWidth(sx(286));

        baseGlossaryRow = new LabeledCheckRow(
                localization.get("setting.box.dictionaries.baseGlossary"),
                SettingsManager.loadCheckboxState(SettingsManager.BASE_GLOSSARY_KEY, SettingsManager.DEFAULT_BASE_GLOSSARY)
        );
        tuneSettingCheckRow(baseGlossaryRow);
        baseGlossaryRow.setPrefWidth(sx(286));
        baseGlossaryRow.setMaxWidth(sx(286));
        baseGlossaryRow.setAlignment(Pos.CENTER_LEFT);

        unitsGlossaryRow = new LabeledCheckRow(
                localization.get("setting.box.dictionaries.unitsDictionary"),
                SettingsManager.loadCheckboxState(SettingsManager.UNITS_GLOSSARY_KEY, SettingsManager.DEFAULT_UNITS_GLOSSARY)
        );
        tuneSettingCheckRow(unitsGlossaryRow);
        unitsGlossaryRow.setPrefWidth(sx(286));
        unitsGlossaryRow.setMaxWidth(sx(286));
        unitsGlossaryRow.setAlignment(Pos.CENTER_LEFT);

        weaponsGlossaryRow = new LabeledCheckRow(
                localization.get("setting.box.dictionaries.weaponsDictionary"),
                SettingsManager.loadCheckboxState(SettingsManager.WEAPONS_GLOSSARY_KEY, SettingsManager.DEFAULT_WEAPONS_GLOSSARY)
        );
        tuneSettingCheckRow(weaponsGlossaryRow);
        weaponsGlossaryRow.setPrefWidth(sx(286));
        weaponsGlossaryRow.setMaxWidth(sx(286));
        weaponsGlossaryRow.setAlignment(Pos.CENTER_LEFT);

        abilitiesGlossaryRow = new LabeledCheckRow(
                localization.get("setting.box.dictionaries.abilitiesDictionary"),
                SettingsManager.loadCheckboxState(SettingsManager.ABILITIES_GLOSSARY_KEY, SettingsManager.DEFAULT_ABILITIES_GLOSSARY)
        );
        tuneSettingCheckRow(abilitiesGlossaryRow);
        abilitiesGlossaryRow.setPrefWidth(sx(286));
        abilitiesGlossaryRow.setMaxWidth(sx(286));
        abilitiesGlossaryRow.setAlignment(Pos.CENTER_LEFT);

        baseGlossaryRow.getCheckBox().selectedProperty().addListener((obs, oldVal, newVal) -> {
            SettingsManager.saveProperty(SettingsManager.BASE_GLOSSARY_KEY, Boolean.toString(newVal));
            main.getGlossaryService().loadGlossariesAsyncFromResources();
        });
        unitsGlossaryRow.getCheckBox().selectedProperty().addListener((obs, oldVal, newVal) -> {
            SettingsManager.saveProperty(SettingsManager.UNITS_GLOSSARY_KEY, Boolean.toString(newVal));
            main.getGlossaryService().loadGlossariesAsyncFromResources();
        });
        weaponsGlossaryRow.getCheckBox().selectedProperty().addListener((obs, oldVal, newVal) -> {
            SettingsManager.saveProperty(SettingsManager.WEAPONS_GLOSSARY_KEY, Boolean.toString(newVal));
            main.getGlossaryService().loadGlossariesAsyncFromResources();
        });
        abilitiesGlossaryRow.getCheckBox().selectedProperty().addListener((obs, oldVal, newVal) -> {
            SettingsManager.saveProperty(SettingsManager.ABILITIES_GLOSSARY_KEY, Boolean.toString(newVal));
            main.getGlossaryService().loadGlossariesAsyncFromResources();
        });

        VBox dictionariesBox = new VBox(
                sy(10),
                dictTitleRow,
                baseGlossaryRow,
                unitsGlossaryRow,
                weaponsGlossaryRow,
                abilitiesGlossaryRow
        );
        dictionariesBox.setAlignment(Pos.TOP_LEFT);
        dictionariesBox.setFillWidth(true);
        dictionariesBox.setPadding(new Insets(sy(18), sx(10), sy(12), sx(10)));
        Pane dictionariesView = new StackPane(dictionariesBox);

        // -------------------------
        // API Panel (Google key)
        // -------------------------
        VBox apiServicesContent = new VBox(
                sy(8),
                googleApiKeyLabel,
                googleApiKeyRow,
                cloudflareTitleRow,
                cloudflareFieldsRow,
                geminiApiKeyLabel,
                geminiApiKeyRow,
                siliconFlowApiKeyLabel,
                siliconFlowApiKeyRow,
                deepLApiKeyLabel,
                deepLApiKeyRow
        );
        apiServicesContent.setAlignment(Pos.TOP_CENTER);
        apiServicesContent.setFillWidth(true);
        apiServicesContent.setPadding(new Insets(sy(10), sx(5), sy(6), sx(5)));
        apiServicesContent.setPrefWidth(sx(282));
        apiServicesContent.setMaxWidth(sx(282));

        ScrollPane apiServicesScroll = new ScrollPane(apiServicesContent);
        apiServicesScroll.setFitToWidth(true);
        apiServicesScroll.setFitToHeight(false);
        apiServicesScroll.setPannable(true);
        apiServicesScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        apiServicesScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.ALWAYS);
        apiServicesScroll.setPrefViewportHeight(sy(312));
        apiServicesScroll.setMinViewportHeight(sy(312));
        apiServicesScroll.setMaxHeight(sy(312));
        apiServicesScroll.setPrefWidth(sx(292));
        apiServicesScroll.setMaxWidth(sx(292));
        apiServicesScroll.getStyleClass().add("settings-api-services-scroll");
        apiServicesScroll.setStyle("-fx-background-color: transparent; -fx-background-insets: 0; -fx-padding: 0;");

        StackPane apiServicesPanel = new StackPane(apiServicesScroll);
        apiServicesPanel.setPrefWidth(sx(292));
        apiServicesPanel.setMaxWidth(sx(292));
        apiServicesPanel.setPrefHeight(sy(336));
        apiServicesPanel.setMaxHeight(sy(336));
        apiServicesPanel.setPadding(new Insets(sy(4), sx(4), sy(4), sx(4)));
        apiServicesPanel.setStyle(
                "-fx-background-color: linear-gradient(to bottom, rgba(42, 13, 8, 0.97), rgba(28, 9, 7, 0.97));"
                        + "-fx-border-color: transparent;"
                        + "-fx-border-image-source: url('" + texturePath + "ui_nova_archives_listitem_selected.png');"
                        + "-fx-border-image-slice: 14;"
                        + "-fx-border-image-width: 8;"
                        + "-fx-border-image-repeat: stretch;"
                        + "-fx-effect: dropshadow(gaussian, rgba(255, 132, 46, 0.46), " + sy(15) + ", 0.34, 0, 0);"
        );

        VBox apiBox = new VBox(
                sy(8),
                apiServicesPanel,
                apiGuideWrap,
                saveApiKeysWrap
        );
        apiBox.setAlignment(Pos.TOP_CENTER);
        apiBox.setPadding(new Insets(sy(10), sx(10), sy(12), sx(10)));
        apiBox.setTranslateY(-sy(14));
        Pane apiView = new StackPane(apiBox);

        // -------------------------
        // Other Panel (Discord only)
        // -------------------------
        otherDescrption = new GlowingLabel(localization.get("setting.box.other.description"));
        tuneSettingLabel(otherDescrption, 17);
        VBox.setMargin(otherDescrption, new Insets(0, 0, sy(10), 0));
        otherDescrption.setWrapText(true);
        otherDescrption.setPrefSize(sx(260), sy(100));

        discordURL = new CustomAlternativeButton(
                localization.get("setting.box.other.join"),
                0.6, 0.8, sv(200.0), sv(55.0), sv(12.0)
        );
        discordURL.setOnAction(e -> {
            try {
                java.awt.Desktop.getDesktop().browse(new java.net.URI("https://discord.com/invite/UKYgsB6Zrx"));
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });

        VBox otherBox = new VBox(sy(10), otherDescrption, discordURL);
        otherBox.setAlignment(Pos.TOP_CENTER);
        Pane otherView = new StackPane(otherBox);

        // Panels mapped by index
        Map<Integer, Pane> viewMap = new HashMap<>();
        viewMap.put(0, languageView);
        viewMap.put(1, uiView);
        viewMap.put(2, dictionariesView);
        viewMap.put(3, controlsView);
        viewMap.put(4, apiView);
        viewMap.put(5, otherView);

        // Configure right panel
        languageView.setVisible(false);
        uiView.setVisible(false);
        dictionariesView.setVisible(false);
        controlsView.setVisible(false);
        apiView.setVisible(false);
        otherView.setVisible(false);

        StackPane rightPanel = new StackPane(languageView, uiView, dictionariesView, controlsView, apiView, otherView);
        rightPanel.setMinSize(sx(316), sy(484));
        rightPanel.setMaxSize(sx(316), sy(484));
        rightPanel.setPrefSize(sx(316), sy(484));
        rightPanel.setBorder(new Border(highlightBorder));
        rightPanel.setAlignment(Pos.TOP_CENTER);
        rightPanel.setPadding(new Insets(sy(10), sx(10), sy(10), sx(10)));

        // Menu buttons
        String[] keys = {
                localization.get("setting.box.language"),
                localization.get("setting.box.ui"),
                localization.get("setting.box.dictionaries"),
                localization.get("setting.box.controls"),
                localization.get("setting.box.api"),
                localization.get("setting.box.other"),
        };

        languageView.setMinSize(sx(306), sy(484));
        uiView.setMinSize(sx(306), sy(484));
        dictionariesView.setMinSize(sx(306), sy(484));
        controlsView.setMinSize(sx(306), sy(484));
        apiView.setMinSize(sx(306), sy(484));
        otherView.setMinSize(sx(306), sy(484));

        menuButtons = new CustomLanguageButton[6];

        for (int i = 0; i < keys.length; i++) {
            String key = keys[i];
            menuButtons[i] = new CustomLanguageButton(key, sv(136), sv(56), sv(10.5));

            VBox.setMargin(menuButtons[i], new Insets(0, 0, 0, sx(-1)));

            final int index = i;
            final CustomLanguageButton button = menuButtons[i];

            button.setOnAction(e -> {
                viewMap.values().forEach(p -> p.setVisible(false));

                Pane pane = viewMap.get(index);
                if (pane != null) pane.setVisible(true);

                int buttonIndex = buttonBox.getChildren().indexOf(button);
                double buttonHeight = button.getHeight();
                double spacing = sy(20);
                double paddingTop = leftMenuTopPadding;

                double centerY = paddingTop
                        + buttonIndex * (buttonHeight + spacing)
                        + buttonHeight / 2.0;

                double markerHeight = selectionMarkImage.getBoundsInParent().getHeight();
                double baseOffset = sy(36);
                double scaleFactor = UiScaleHelper.scale(1);

                double hdLift = (scaleFactor < 1.0) ? sy(6) : 0.0;
                double adjustedY = centerY - markerHeight / 2.0 + baseOffset - hdLift;

                selectionMarkImage.setLayoutY(adjustedY);
                selectionMarkImage.setLayoutX(-sx(25));
                selectionMarkImage.setVisible(true);

                selectionMarkImage.setOpacity(0);

                double startTranslate = sy(-6);
                double fromY = sy(-9);
                double toY = sy(3);

                double animLift = (scaleFactor < 1.0) ? sy(2) : 0.0;

                selectionMarkImage.setTranslateY(startTranslate - animLift);

                FadeTransition fade = new FadeTransition(Duration.seconds(0.33), selectionMarkImage);
                fade.setFromValue(0);
                fade.setToValue(1);
                fade.setInterpolator(Interpolator.EASE_OUT);

                TranslateTransition moveDown = new TranslateTransition(Duration.seconds(0.33), selectionMarkImage);
                moveDown.setFromY(fromY - animLift);
                moveDown.setToY(toY - animLift);
                moveDown.setInterpolator(Interpolator.EASE_OUT);

                selectionMarkImage.getTransforms().clear();
                selectionMarkImage.setOpacity(0);
                selectionMarkImage.setTranslateY(startTranslate - animLift);

                ParallelTransition pt = new ParallelTransition(fade, moveDown);
                pt.playFromStart();
            });

            if (index == 2) {
                // Dictionaries tab is active (previously Audio was disabled here)
            }

            buttonBox.getChildren().add(menuButtons[i]);
        }

        // Layout
        HBox contentLayout = new HBox(0, leftPanel, rightPanel);
        contentLayout.setAlignment(Pos.TOP_LEFT);
        contentLayout.setPadding(new Insets(
                sy(16),
                sx(6),
                sy(2),
                sx(8)
        ));

        Pane root = new Pane();
        root.setPrefSize(WIDTH, HEIGHT);
        root.setMinSize(WIDTH, HEIGHT);
        root.setMaxSize(WIDTH, HEIGHT);

        root.getChildren().addAll(frame, contentLayout, closeButton);

        // place close button after layout
        root.layoutBoundsProperty().addListener((obs, oldVal, newVal) -> {
            double w = newVal.getWidth();
            closeButton.setLayoutX(w - closeButton.getWidth() + sx(8));
            closeButton.setLayoutY(-sy(8));
        });

        // default open first tab (language)
        Platform.runLater(() -> {
            javafx.animation.PauseTransition delay = new javafx.animation.PauseTransition(Duration.millis(120));
            delay.setOnFinished(e -> {
                if (menuButtons != null && menuButtons.length > 0 && menuButtons[0] != null) {
                    menuButtons[0].fire();
                }
            });
            delay.play();
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
        if (saveApiKeysButton != null) saveApiKeysButton.setText(localization.get("button.save"));
        if (apiGuideButton != null) apiGuideButton.setText(localization.get("setting.box.api.guide"));
        if (controlsLabel != null) controlsLabel.setText(localization.get("setting.box.controls"));
        if (otherDescrption != null) otherDescrption.setText(localization.get("setting.box.other.description"));
        if (discordURL != null) discordURL.setText(localization.get("setting.box.other.join"));
        if (dictionariesLabel != null) dictionariesLabel.setText(localization.get("setting.box.dictionaries"));
        if (baseGlossaryRow != null) baseGlossaryRow.setLabel(localization.get("setting.box.dictionaries.baseGlossary"));
        if (unitsGlossaryRow != null) unitsGlossaryRow.setLabel(localization.get("setting.box.dictionaries.unitsDictionary"));
        if (weaponsGlossaryRow != null) weaponsGlossaryRow.setLabel(localization.get("setting.box.dictionaries.weaponsDictionary"));
        if (abilitiesGlossaryRow != null) abilitiesGlossaryRow.setLabel(localization.get("setting.box.dictionaries.abilitiesDictionary"));
        if (dictionariesHintIcon != null) dictionariesHintIcon.setHintText(localization.get("setting.box.dictionaries.hint"));

        if (tableView != null) {
            tableView.updatePlaceholderText(localization.get("table.placeholder"));
        }

        if (tableLightCheckBox != null) tableLightCheckBox.setLabel(localization.get("setting.box.ui.tableLight"));
        if (shimmerRow != null) shimmerRow.setLabel(localization.get("setting.box.ui.shimmers"));
        if (backgroundLightCheckBox != null) backgroundLightCheckBox.setLabel(localization.get("setting.box.ui.backgroundBlur"));
        if (translationCachePersistRow != null) translationCachePersistRow.setLabel(localization.get("setting.box.other.translationCachePersist"));
        if (useGpuDockerRow != null) useGpuDockerRow.setLabel(localization.get("setting.box.other.useGpuDocker"));
        if (googleApiKeyLabel != null) googleApiKeyLabel.setText(buildGoogleApiKeyLabel(localization));
        if (googleApiKeyField != null) googleApiKeyField.setPromptText(localization.get("setting.box.other.googleApiKeyPrompt"));
        if (googleApiKeyHintIcon != null) googleApiKeyHintIcon.setHintText(buildGoogleApiKeyHint(localization));
        if (cloudflareServiceLabel != null) cloudflareServiceLabel.setText(buildCloudflareServiceLabel(localization));
        if (cloudflareAccountIdField != null) cloudflareAccountIdField.setPromptText(localization.get("setting.box.other.cloudflareAccountIdPrompt"));
        if (cloudflareServiceHintIcon != null) cloudflareServiceHintIcon.setHintText(buildCloudflareServiceHint(localization));
        if (cloudflareApiTokenField != null) cloudflareApiTokenField.setPromptText(localization.get("setting.box.other.cloudflareApiTokenPrompt"));
        if (geminiApiKeyLabel != null) geminiApiKeyLabel.setText(localization.get("setting.box.other.geminiApiKey"));
        if (geminiApiKeyField != null) geminiApiKeyField.setPromptText(localization.get("setting.box.other.geminiApiKeyPrompt"));
        if (geminiApiKeyHintIcon != null) geminiApiKeyHintIcon.setHintText(localization.get("setting.box.other.geminiApiKeyHint"));
        if (siliconFlowApiKeyLabel != null) siliconFlowApiKeyLabel.setText(buildSiliconFlowApiKeyLabel(localization));
        if (siliconFlowApiKeyField != null) siliconFlowApiKeyField.setPromptText(localization.get("setting.box.other.siliconflowApiKeyPrompt"));
        if (siliconFlowApiKeyHintIcon != null) siliconFlowApiKeyHintIcon.setHintText(buildSiliconFlowApiKeyHint(localization));
        if (deepLApiKeyLabel != null) deepLApiKeyLabel.setText(localization.get("setting.box.other.deeplApiKey"));
        if (deepLApiKeyField != null) deepLApiKeyField.setPromptText(localization.get("setting.box.other.deeplApiKeyPrompt"));
        if (deepLApiKeyHintIcon != null) deepLApiKeyHintIcon.setHintText(localization.get("setting.box.other.deeplApiKeyHint"));
        if (clearCacheButton != null) clearCacheButton.setText(localization.get("setting.box.other.clearCache"));

        if (menuButtons != null) {
            String[] keys = {
                    localization.get("setting.box.language"),
                    localization.get("setting.box.ui"),
                    localization.get("setting.box.dictionaries"),
                    localization.get("setting.box.controls"),
                    localization.get("setting.box.api"),
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
            notifyVisibilityChanged(false);
        }
    }

    public static boolean isOverlayVisible() {
        return overlayRoot != null && overlayRoot.isVisible() && !overlayRoot.isMouseTransparent();
    }

    public static void addVisibilityListener(Consumer<Boolean> listener) {
        if (listener != null) {
            visibilityListeners.add(listener);
        }
    }

    private static void notifyVisibilityChanged(boolean visible) {
        for (Consumer<Boolean> listener : visibilityListeners) {
            try {
                listener.accept(visible);
            } catch (Exception ignored) {
            }
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

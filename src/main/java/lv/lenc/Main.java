package lv.lenc;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.layout.*;
import javafx.stage.Screen;
import javafx.stage.Stage;

import java.io.File;
import java.util.List;
import java.nio.file.Paths;
import java.util.List;
import static lv.lenc.TranslationService.*;

public class Main extends Application {

    public static final String RU = "RU";
    public static final String EN = "EN";

    // UI / state
    Stage Window;
    Scene mainScene;
    BorderPane layout;

    private final LocalizationProjectContext project = new LocalizationProjectContext();
    private LocalizationManager localization;

    private TranslateCancelSaveButton translate;
    private CustomLongButton settingButton;
    private MyButton translateChooseAll;
    private MyButton quitButton;

    private TitleLabelGlow editorTitleLabel;
    private GlowingLabelWithBorder fileTitleLabel;
    private GlowingLabel BoxAlertTitle;
    private GlowingLabel BoxAlertDescription;

    private CustomComboBoxTexture<String> languageDropdown;
    private CustomComboBoxClassic<String> translateType;
    private CustomFileChooser fileSelected;
    private CustomBorder borderTable;

    private String sourceUi = null;
    private String[] valueKey;
    private String[] translateTypeText;

    private boolean translateToAll = false;
    private Process libreProcess;
    private TranslationProgressOverlay progressOverlay;
    private StackPane root;
    private final GlossaryService glossaryService = new GlossaryService();
    private final BooleanProperty fileOpened = new SimpleBooleanProperty(false);
    private final BooleanProperty chooseAllMode = new SimpleBooleanProperty(false);
    public static void main(String[] args) {
        launch(args);
    }


    public void start(Stage primaryStage) {
        Window = primaryStage;

        initLocalization();
        ensureLibreTranslate();
        progressOverlay = new TranslationProgressOverlay(localization);
        CustomTableView tableView = createTableView();
        createControls(primaryStage, tableView);
        wireEvents(primaryStage, tableView);
        buildScene(primaryStage, tableView);

        //    applyInitialDisabledState();
        glossaryService.loadGlossariesAsyncFromResources();
        // primaryStage.show();
    }

    // ---------------------------
    // Init
    // ---------------------------

    private void initLocalization() {
        String savedLanguage = SettingsManager.loadLanguage();
        localization = new LocalizationManager(savedLanguage);

        valueKey = new String[]{"ruRU", "deDE", "enUS", "esMX", "esES", "frFR", "itIT", "plPL", "ptBR", "koKR", "zhCN", "zhTW"};
        translateTypeText = new String[]{
                localization.get("combox.translate"),
                localization.get("combox.GPTFree"),
                localization.get("combox.GPTturbo")
        };
    }

    private void ensureLibreTranslate() {
        if (isLtAlive()) return;

        try {
            libreProcess = startLtProcess();
            waitLtReady(60, 300);
        } catch (Exception e) {
            System.err.println("[LT] auto-start failed: " + e.getMessage());
        }
    }

    private CustomTableView createTableView() {
        CustomTableView tableView = new CustomTableView(
                getClass().getResource("/Assets/Textures/").toString(),
                localization,
                (UiScaleHelper.SCREEN_WIDTH * 0.786),
                (UiScaleHelper.SCREEN_HEIGHT * 0.37),
                glossaryService
        );

        borderTable = new CustomBorder(tableView);
        return tableView;
    }

    // ---------------------------
    // UI creation
    // ---------------------------

    private void createControls(Stage primaryStage, CustomTableView tableView) {
        layout = new BorderPane();
        layout.setStyle("-fx-background-color: rgba(0, 0, 0, 1);");

        translate = new TranslateCancelSaveButton(
                localization,
                getClass().getResource("/Assets/Textures/").toExternalForm(),
                true,
                0.25,
                0.3
        );
        //  translate.setDisable(true);

        translateChooseAll = UIElementFactory.createCustomLongAlternativeButton(
                localization.get("button.chooseAll"),
                getClass().getResource("/Assets/Textures/").toExternalForm(),
                0.6,
                0.8
        );

        quitButton = UIElementFactory.createCustomQuitButton(
                localization.get("button.quit"),
                getClass().getResource("/Assets/Textures/").toExternalForm()
        );
        quitButton.setTranslateY(UiScaleHelper.scaleY(-80));
        quitButton.setTranslateX(UiScaleHelper.scaleX(-450));

        fileTitleLabel = new GlowingLabelWithBorder(localization.get("label.file.name"));
        fileSelected = new CustomFileChooser(
                createFileSelectable(tableView),
                UiScaleHelper.scaleX(70),
                UiScaleHelper.scaleY(70)
        );

        BoxAlertTitle = new GlowingLabel(localization.get("label.ExitConfirmation"));
        BoxAlertDescription = new GlowingLabel(localization.get("label.ExitConfirmationDescription"));

        languageDropdown = new CustomComboBoxTexture<>(
                getClass().getResource("/Assets/Textures/").toExternalForm(),
                165,
                58
        );
        // languageDropdown.disable(true);
        languageDropdown.getItems().setAll(valueKey);
        languageDropdown.setValue(valueKey[2]);

        translateType = new CustomComboBoxClassic<>(
                getClass().getResource("/Assets/Textures/").toExternalForm(),
                true,
                UiScaleHelper.scaleX(60),
                UiScaleHelper.scaleY(60),
                UiScaleHelper.scaleY(16),
                UiScaleHelper.scaleX(13)
        );
        translateType.getItems().addAll(translateTypeText);
        translateType.setValue(translateTypeText[0]);

        editorTitleLabel = new TitleLabelGlow(localization.get("label.editor.title"), localization);
        editorTitleLabel.setTranslateY(UiScaleHelper.scaleY(30));

        settingButton = new CustomLongButton(
                localization.get("button.setting"),
                true,
                330,
                58,
                16,
                0.3,
                0.6
        );
        settingButton.setAlignment(Pos.CENTER);
        settingButton.setTranslateY(UiScaleHelper.scaleY(-120));

        // table in center
        StackPane tableWithBorder = new StackPane();
        borderTable.setTranslateY(UiScaleHelper.scaleY(-3));
        tableWithBorder.getChildren().addAll(borderTable, tableView);
        layout.setCenter(tableWithBorder);

        translate.disableProperty().bind(
                fileOpened.not().or(glossaryService.glossaryLoadingProperty())
        );
        translate.setCustomText(localization.get("translating.loading"));
        glossaryService.glossaryLoadingProperty().addListener((obs, oldVal, loading) -> {
            if (loading) {
                translate.setCustomText(localization.get("translating.loading"));
            } else {
                translate.clearCustomText();
            }
        });
        translateChooseAll.disableProperty().bind(fileOpened.not());
        languageDropdown.disableProperty().bind(
                fileOpened.not().or(chooseAllMode)
        );

    }

    private FileSelectable createFileSelectable(CustomTableView tableView) {
        return (File file) -> {
            if (file == null) return;

            fileTitleLabel.setText(file.getName());

            boolean ok = project.open(file, tableView);

            fileOpened.set(ok);
            System.out.println("[UI] project.open ok = " + ok);
            System.out.println("[UI] fileOpened = " + fileOpened.get());
            System.out.println("[UI] glossaryLoading = " + glossaryService.glossaryLoadingProperty().get());
            System.out.println("[UI] translate disabled = " + translate.isDisable());
            translateToAll = false;
            chooseAllMode.set(false);
            //translateToAll = false;

            //translate.setDisable(!ok);
            //.disable(!ok);
            System.out.println("ok=" + ok
                    + " translateDisabled=" + translate.isDisable()
                    + " chooseAllDisabled=" + translateChooseAll.isDisable());
            // remember source after loading
            sourceUi = tableView.getCurrentSourceUi() != null
                    ? tableView.getCurrentSourceUi()
                    : tableView.getMainSourceLang();

            if (ok) {
                String srcUi = (sourceUi != null) ? sourceUi : tableView.getMainSourceLang();

                java.util.ArrayList<String> filtered =
                        new java.util.ArrayList<>(java.util.Arrays.asList(valueKey));
                filtered.remove(srcUi);
                if (filtered.isEmpty()) filtered = new java.util.ArrayList<>(java.util.Arrays.asList(valueKey));

                languageDropdown.getItems().setAll(filtered);
                if (languageDropdown.getValue() == null || !filtered.contains(languageDropdown.getValue())) {
                    languageDropdown.setValue(filtered.get(0));
                }

                if (tableView.isLastLoadWasMulti() && tableView.getLoadedUiLanguages().size() > 1) {
                    tableView.showAllColumns();
                    sourceUi = tableView.getMainSourceLang();
                } else {
                    tableView.showOnly(sourceUi, languageDropdown.getValue());
                }
            } else {
                fileOpened.set(false);
                chooseAllMode.set(false);
            }


        };
    }

    // ---------------------------
    // Wiring (events & actions)
    // ---------------------------

    private void wireEvents(Stage primaryStage, CustomTableView tableView) {
        final TranslationProgressOverlay progressWin = this.progressOverlay;

        translateChooseAll.setOnAction(e -> {
            translateToAll = !translateToAll;
            chooseAllMode.set(!chooseAllMode.get());
            if (translateToAll) {
                tableView.showAllColumns();
            } else {
                String targetUi = languageDropdown.getValue();
                String srcUi = (sourceUi != null) ? sourceUi : tableView.getMainSourceLang();
                tableView.showOnly(srcUi, targetUi);
            }
            //  applyTranslateModeUI();
        });

        languageDropdown.setOnAction(e -> {
            if (translateToAll) return;
            String targetUi = languageDropdown.getValue();
            String srcUi = (sourceUi != null) ? sourceUi : tableView.getMainSourceLang();
            tableView.showOnly(srcUi, targetUi);
        });

        quitButton.setOnAction(e -> {
            ExitConfirmDialog.showConfirm(root, BoxAlertDescription, localization, exitConfirmed -> {
                if (exitConfirmed) {
                    Platform.exit();
                }
            });
        });

        translate.setTranslateStarter(() ->
                TranslateCancelSaveButton.runAsync(
                        () -> runTranslate(tableView, progressWin),
                        translate::setRunningThread
                )
        );
        translate.setSaveAction(() -> {
            boolean ok;
            if (translateToAll) {
                ok = project.saveAllTargets(tableView);
            } else {
                String targetUi = languageDropdown.getValue();
                ok = project.saveTarget(tableView, targetUi);
            }
            if (!ok) System.err.println("[SAVE] failed or context not ready");
            //    applyTranslateModeUI();
        });

        translate.setCancelHook(() -> {
            TranslationService.cancelInFlight();
            progressWin.close();
            Platform.runLater(() -> {
                //  translateChooseAll.disable(false);
                //  applyTranslateModeUI();
            });
        });


    }

    private void runTranslate(CustomTableView tableView, TranslationProgressOverlay progressWin) {
        if (!glossaryService.isGlossaryReady()) {
            System.err.println("[Glossary] glossary is still loading");
            return;
        }
        Thread.interrupted(); // reset interrupt flag

        Platform.runLater(() -> {

            progressWin.showReset();
        });

        String targetUi = languageDropdown.getValue();
        String srcUi = (sourceUi != null) ? sourceUi : tableView.getMainSourceLang();

        try {
            if (translateToAll) {
                tableView.translateFromColumnToOthers(
                        TranslationService.api,
                        srcUi,
                        () -> translate.isCancelRequested() || Thread.currentThread().isInterrupted(),
                        progressWin::updateFromProgress
                );
            } else {
                Platform.runLater(() ->
                        progressWin.update(0.0, srcUi + " -> " + targetUi, "")
                );

                tableView.translateFromSourceToTarget(
                        TranslationService.api,
                        srcUi,
                        targetUi,
                        () -> translate.isCancelRequested() || Thread.currentThread().isInterrupted(),
                        progressWin::updateFromProgress
                );

                Platform.runLater(() ->
                        progressWin.update(1.0, srcUi + " -> " + targetUi, localization.get("translating.done"))
                );
            }
        } finally {
            if (progressWin != null) progressWin.close();
            Platform.runLater(() -> {
            });
        }
    }

    // ---------------------------
    // Scene / layout
    // ---------------------------

    private void buildScene(Stage primaryStage, CustomTableView tableView) {
        // TOP UI
        VBox layoutMain = new VBox(UiScaleHelper.scaleY(20));
        HBox fileManager = new HBox(UiScaleHelper.scaleX(1));
        fileManager.setTranslateY(UiScaleHelper.scaleY(65));
        // fileSelected.setTranslateY(UiScaleHelper.scaleY(10));

        SquareDiscordURL discordURL = new SquareDiscordURL();
        discordURL.setTranslateY(UiScaleHelper.scaleY(-6));
        fileManager.getChildren().addAll(fileTitleLabel, discordURL, fileSelected);

        layoutMain.getChildren().addAll(
                editorTitleLabel,
                translateChooseAll,
                languageDropdown,
                fileManager,
                settingButton
        );
        layoutMain.setMaxHeight(Screen.getPrimary().getBounds().getHeight());
        VBox.setVgrow(layoutMain, Priority.ALWAYS);

        // BOTTOM RIGHT UI
        VBox bottomRightContainer = new VBox(UiScaleHelper.scaleY(10));
        bottomRightContainer.getChildren().addAll(
                translate,
                translateChooseAll,
                languageDropdown,
                translateType,
                quitButton
        );
        bottomRightContainer.setAlignment(Pos.BOTTOM_RIGHT);
        bottomRightContainer.setPadding(new Insets(
                UiScaleHelper.scaleY(50),
                UiScaleHelper.scaleX(80),
                UiScaleHelper.scaleY(10),
                UiScaleHelper.scaleX(100)
        ));
        bottomRightContainer.setPrefWidth(UiScaleHelper.scaleX(250));

        translateType.setTranslateY(UiScaleHelper.scaleY(-185));
        translateType.setTranslateX(UiScaleHelper.scaleX(60));
        languageDropdown.setTranslateX(UiScaleHelper.scaleX(-165));
        languageDropdown.setTranslateY(UiScaleHelper.scaleY(-80));
        translateChooseAll.setTranslateY(UiScaleHelper.scaleY(-20));

        HeaderFlashOverlay overlay = new HeaderFlashOverlay(tableView, layout);
        layout.getChildren().add(overlay.getOverlayPane());

        BackgroundGridLayer backgroundLayer = new BackgroundGridLayer();
        layout.getChildren().add(0, backgroundLayer);

        Object[] ui = SettingsManager.loadUiSettings();
        SettingsManager.applyUiSettings(ui, backgroundLayer, borderTable);


        settingButton.setOnAction(e -> {
            if (settingButton.isSelected()) return;
            settingButton.select();
            SettingBox.show(root, localization, backgroundLayer, settingButton, this, borderTable, tableView);
        });


        root = new StackPane();
        root.getChildren().addAll(layout, this.progressOverlay);

        mainScene = new Scene(root, UiScaleHelper.SCREEN_WIDTH, UiScaleHelper.SCREEN_HEIGHT);

        mainScene.getStylesheets().add(
                TranslationProgressOverlay.class
                        .getResource("/Assets/Style/translation-progress.css")
                        .toExternalForm()
        );

        mainScene.getStylesheets().addAll(
                SettingBox.class.getResource("/Assets/Style/CustomComboBoxClassic.css").toExternalForm(),
                getClass().getResource("/Assets/Style/CustomFileChooser.css").toExternalForm(),
                getClass().getResource("/Assets/Style/CustomLongButton.css").toExternalForm(),
                getClass().getResource("/Assets/Style/GlowingLabel.css").toExternalForm(),
                getClass().getResource("/Assets/Style/GlowingLabelBorder.css").toExternalForm(),
                getClass().getResource("/Assets/Style/HeaderFlashOverlay.css").toExternalForm(),
                getClass().getResource("/Assets/Style/SquareDiscordURL.css").toExternalForm()
        );

        layout.setTop(layoutMain);
        layout.setBottom(bottomRightContainer);
        BorderPane.setAlignment(bottomRightContainer, Pos.BOTTOM_RIGHT);

        primaryStage.setScene(mainScene);
        primaryStage.setTitle(localization.get("label.editor.title")); // or any desired title
        primaryStage.setMinWidth(900);
        primaryStage.setMinHeight(600);

        primaryStage.setScene(mainScene);
        primaryStage.setTitle(localization.get("label.editor.title"));
        primaryStage.sizeToScene();
        primaryStage.centerOnScreen();
        primaryStage.setIconified(false);

        // 1) Show immediately but invisible (avoid square/rectangle flash)
        primaryStage.setOpacity(0.0);
        primaryStage.show();

        // 2) On next frame enable fullscreen and make visible
        Platform.runLater(() -> {
            primaryStage.setFullScreenExitHint("");
            primaryStage.setFullScreen(true);

            primaryStage.setOpacity(1.0);
            primaryStage.toFront();
            primaryStage.requestFocus();


            Platform.runLater(() -> {
                root.applyCss();
                root.layout();


                Platform.runLater(() -> {
                    root.applyCss();
                    root.layout();


                    SettingBox.prewarm(root, localization, backgroundLayer, settingButton, this, borderTable, tableView);
                    ExitConfirmDialog.prewarm(root, BoxAlertDescription, localization);

                });
            });
        });
    }

    private void applyInitialDisabledState() {
        List<Disabable> list = new MyListTest<>();
        list.add(translateType);
        list.add(translateChooseAll);
        for (Disabable o : list) {
            o.disable(true);
        }

    }

    // ---------------------------
    // Existing methods
    // ---------------------------

    public void updateTexts() {
        translate.refreshText();
        translateChooseAll.setText(localization.get("button.chooseAll"));
        quitButton.setText(localization.get("button.quit"));
        editorTitleLabel.setText(localization.get("label.editor.title"));
        fileTitleLabel.setText(localization.get("label.file.name"));
        BoxAlertTitle.setText(localization.get("label.ExitConfirmation"));
        BoxAlertDescription.setText(localization.get("label.ExitConfirmationDescription"));
        settingButton.setText(localization.get("button.setting"));

        int selectedIndex = translateType.getSelectionModel().getSelectedIndex();
        translateType.getItems().clear();
        translateType.getItems().addAll(
                localization.get("combox.translate"),
                localization.get("combox.GPTFree"),
                localization.get("combox.GPTturbo")
        );
        if (selectedIndex >= 0 && selectedIndex < translateType.getItems().size()) {
            translateType.getSelectionModel().select(selectedIndex);
        }
    }
    @Override
    public void stop() {
        try {
            if (libreProcess != null && libreProcess.isAlive()) {
                libreProcess.destroy();
                libreProcess.waitFor(3, java.util.concurrent.TimeUnit.SECONDS);

                if (libreProcess.isAlive()) {
                    libreProcess.destroyForcibly();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private void applyTranslateModeUI() {
        languageDropdown.disable(translateToAll);
    }
}
package lv.lenc;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import javafx.animation.Interpolator;
import javafx.animation.Transition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.Duration;

public class Main extends Application {

    public static final String RU = "RU";
    public static final String EN = "EN";

    // UI / state
    Scene mainScene;
    BorderPane layout;

    private final LocalizationProjectContext project = new LocalizationProjectContext();
    private LocalizationManager localization;

    private TranslateCancelSaveButton translate;
    private CustomLongButton settingButton;
    private CustomAlternativeButton keyFilterButton;
    private CustomAlternativeButton tableFullscreenButton;
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
    private BackgroundGridLayer backgroundLayer;
    private TranslationVisualState translationVisualState;

    private String sourceUi = null;
    private String[] valueKey;

    private boolean translateToAll = false;
    private Process libreProcess;
    private TranslationProgressOverlay progressOverlay;
    private StackPane root;
    private VBox topControlsContainer;
    private VBox bottomControlsContainer;
    private HBox fileManagerBar;
    private StackPane tableContainer;
    private boolean tableFullscreenMode;
    private Transition tableModeTransition;
    private final GlossaryService glossaryService = new GlossaryService();
    private final BooleanProperty fileOpened = new SimpleBooleanProperty(false);
    private final BooleanProperty chooseAllMode = new SimpleBooleanProperty(false);
    private final BooleanProperty fileLoading = new SimpleBooleanProperty(false);
    private final AtomicInteger ltWarmupGeneration = new AtomicInteger();
    private volatile Thread ltWarmupThread;

    private static final class TranslationVisualState {
        final double gridAlpha;
        final double pointAlpha;
        final double flashAlpha;
        final boolean shimmersVisible;
        final boolean blurVisible;
        final boolean backgroundVisible;
        final boolean tableLightingVisible;

        TranslationVisualState(BackgroundGridLayer backgroundLayer, CustomBorder borderTable) {
            this.gridAlpha = backgroundLayer.getGridAlpha();
            this.pointAlpha = backgroundLayer.getPointAlpha();
            this.flashAlpha = backgroundLayer.getFlashAlpha();
            this.shimmersVisible = backgroundLayer.shimmerContainer.isVisible();
            this.blurVisible = backgroundLayer.blurredLights.isVisible();
            this.backgroundVisible = backgroundLayer.isVisible();
            this.tableLightingVisible = borderTable.isTableLightingVisible();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        initLocalization();
        // OPTIMIZED: Load translation models in background during app startup
        // Users will see progress window while GPU models initialize
        scheduleTranslationServerWarmup("ruRU", null, "app-start");
        progressOverlay = new TranslationProgressOverlay(localization);
        CustomTableView tableView = createTableView();
        createControls(tableView);
        wireEvents(tableView);
        
        // 
        setWindowIcon(primaryStage);
        
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

    private void createControls(CustomTableView tableView) {
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
        translateType.getItems().addAll(
                localization.get("combox.translate"),
                localization.get("combox.GPTFree"),
                localization.get("combox.GPTturbo")
        );
        translateType.setValue(translateType.getItems().get(0));

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

        keyFilterButton = new CustomAlternativeButton(
                localizedFilterText(),
                0.6, 0.8, 140.0, 56.0, 14.0
        );
        keyFilterButton.getStyleClass().remove("alt-button");
        keyFilterButton.getStyleClass().add("key-filter-table-button");

        tableFullscreenButton = new CustomAlternativeButton(
                localizedTableFullscreenText(),
                0.6, 0.8, 210.0, 56.0, 14.0
        );
        tableFullscreenButton.getStyleClass().remove("alt-button");
        tableFullscreenButton.getStyleClass().add("key-filter-table-button");
        tableFullscreenButton.setFocusTraversable(false);

        // table in center
        tableContainer = new StackPane();
        borderTable.setTranslateY(UiScaleHelper.scaleY(-3));
        tableContainer.getChildren().addAll(borderTable, tableView);
        layout.setCenter(tableContainer);

        translate.disableProperty().bind(
                fileOpened.not()
                        .or(glossaryService.glossaryLoadingProperty())
                        .or(fileLoading)
        );
        translate.setCustomText(localization.get("translating.loading"));
        glossaryService.glossaryLoadingProperty().addListener((obs, oldVal, loading) -> {
            if (loading || fileLoading.get()) {
                translate.setCustomText(localization.get("translating.loading"));
            } else {
                translate.clearCustomText();
            }
        });
        fileLoading.addListener((obs, oldVal, loading) -> {
            if (loading || glossaryService.glossaryLoadingProperty().get()) {
                translate.setCustomText(localization.get("translating.loading"));
            } else {
                translate.clearCustomText();
            }
        });
        translateChooseAll.disableProperty().bind(fileOpened.not());
        languageDropdown.disableProperty().bind(
                fileOpened.not().or(chooseAllMode)
        );
        keyFilterButton.disableProperty().bind(fileOpened.not());
        tableFullscreenButton.disableProperty().bind(fileOpened.not());

    }

    private FileSelectable createFileSelectable(CustomTableView tableView) {
        return (File file) -> {
            if (file == null) return;
            fileLoading.set(true);

            fileTitleLabel.setText(file.getName());

            try {
                boolean ok = project.open(file, tableView);

                fileOpened.set(ok);
                AppLog.info("[UI] project.open ok = " + ok);
                AppLog.info("[UI] fileOpened = " + fileOpened.get());
                AppLog.info("[UI] glossaryLoading = " + glossaryService.glossaryLoadingProperty().get());
                AppLog.info("[UI] fileLoading = " + fileLoading.get());
                AppLog.info("[UI] translate disabled = " + translate.isDisable());
                translateToAll = false;
                chooseAllMode.set(false);
                translate.resetToTranslateState();
                AppLog.info("ok=" + ok
                        + " translateDisabled=" + translate.isDisable()
                        + " chooseAllDisabled=" + translateChooseAll.isDisable());

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
            } finally {
                fileLoading.set(false);
            }
        };
    }

    // ---------------------------
    // Wiring (events & actions)
    // ---------------------------

    private void wireEvents(CustomTableView tableView) {
        final TranslationProgressOverlay progressWin = this.progressOverlay;

        translateChooseAll.setOnAction(e -> {
            translateToAll = !translateToAll;
            chooseAllMode.set(translateToAll);
            translate.resetToTranslateState();
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
            translate.resetToTranslateState();
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
        keyFilterButton.setOnAction(e -> KeyFilterWindow.show(root, tableView, localization));
        tableFullscreenButton.setOnAction(e -> toggleTableFullscreenMode(tableView));

        translate.setTranslateStarter(() -> {
            // Show a progress overlay immediately when user starts translation.
            progressWin.showReset();
            // Enter translation mode immediately on click so UI stops spending
            // resources before we even touch the server checks.
            enterTranslationTurboMode();
            return TranslateCancelSaveButton.runAsync(
                    () -> runTranslate(tableView, progressWin),
                    translate::setRunningThread
            );
        });
        translate.setErrorHandler(error -> {
            Throwable cause = (error != null && error.getCause() != null) ? error.getCause() : error;
            AppLog.exception(cause);
            progressWin.showError(
                    localization.get("translating.error.title"),
                    buildTranslateErrorLine1(cause),
                    buildTranslateErrorLine2(cause)
            );
        });
        translate.setSaveAction(() -> {
            boolean ok;
            if (translateToAll) {
                ok = project.saveAllTargets(tableView);
            } else {
                String targetUi = languageDropdown.getValue();
                ok = project.saveTarget(tableView, targetUi);
            }
            if (!ok) {
                throw new IllegalStateException("[SAVE] failed or context not ready");
            }
            AppLog.info("[SAVE] completed successfully");
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
            AppLog.error("[Glossary] glossary is still loading");
            return;
        }
        final long startedAtNanos = System.nanoTime();
        Thread.interrupted(); // reset interrupt flag

        String targetUi = languageDropdown.getValue();
        String srcUi = (sourceUi != null) ? sourceUi : tableView.getMainSourceLang();
        final boolean readyForImmediateTranslate = TranslationService.isLtAlive();

        runOnFxThreadAndWait(() -> {
            if (!readyForImmediateTranslate) {
                progressWin.update(
                        0.02,
                        localization.get("translating.server.preparing"),
                        localization.get("translating.server.models")
                );
            } else {
                progressWin.update(
                        0.0,
                        srcUi + " -> " + targetUi,
                        (TranslationService.isGpuActive() ? "GPU" : "CPU") + " @ " + TranslationService.BASE_URL
                );
            }
        });

        TranslationService.requestTranslationPerformanceMode();

        if (!TranslationService.ensureServerAvailable()) {
            throw new IllegalStateException("LibreTranslate is not reachable on " + TranslationService.BASE_URL);
        }

        stopBackgroundWarmup();

        if (!readyForImmediateTranslate) {
            // Only show explicit server-ready stage when the click really had to wait for server startup.
            runOnFxThreadAndWait(() -> progressWin.update(
                    0.05,
                    localization.get("translating.server.ready"),
                    (TranslationService.isGpuActive() ? "GPU" : "CPU") + " @ " + TranslationService.BASE_URL
            ));
        }

        boolean completed = false;
        try {
            if (translateToAll) {
                tableView.translateFromColumnToOthers(
                        TranslationService.api,
                        srcUi,
                        () -> translate.isCancelRequested() || Thread.currentThread().isInterrupted(),
                        progressWin::updateFromProgress
                );

                long tookMs = Math.max(0L, (System.nanoTime() - startedAtNanos) / 1_000_000L);
                String tookText = localization.get("translating.done") + " - " + formatElapsed(tookMs);
                runOnFxThreadAndWait(() -> progressWin.update(1.0, srcUi + " -> all", tookText));
            } else {
                Platform.runLater(() ->
                        progressWin.update(
                                0.0,
                                srcUi + " -> " + targetUi,
                                (TranslationService.isGpuActive() ? "GPU" : "CPU") + " @ " + TranslationService.BASE_URL
                        )
                );

                tableView.translateFromSourceToTarget(
                        TranslationService.api,
                        srcUi,
                        targetUi,
                        () -> translate.isCancelRequested() || Thread.currentThread().isInterrupted(),
                        progressWin::updateFromProgress
                );

                long tookMs = Math.max(0L, (System.nanoTime() - startedAtNanos) / 1_000_000L);
                String tookText = localization.get("translating.done") + " - " + formatElapsed(tookMs);
                runOnFxThreadAndWait(() -> progressWin.update(1.0, srcUi + " -> " + targetUi, tookText));
            }
            completed = true;
        } finally {
            long tookMs = Math.max(0L, (System.nanoTime() - startedAtNanos) / 1_000_000L);
            String mode = translateToAll ? "all" : targetUi;
            String status = completed ? "completed" : (translate.isCancelRequested() ? "cancelled" : "stopped");
            String perfMessage = "[PERF] translation " + srcUi + " -> " + mode
                    + " " + status
                    + " in " + formatElapsed(tookMs)
                    + " (" + tookMs + " ms)"
                    + ", backend=" + (TranslationService.isGpuActive() ? "GPU" : "CPU")
                    + ", endpoint=" + TranslationService.BASE_URL;
            AppLog.info(perfMessage);
            System.out.println(perfMessage);

            runOnFxThreadAndWait(() -> {
                progressWin.close();
                leaveTranslationTurboMode();
            });
            TranslationService.restoreTranslationPerformanceMode();
        }
    }

    private static String formatElapsed(long millis) {
        if (millis < 1_000L) {
            return millis + " ms";
        }
        long seconds = millis / 1_000L;
        long remMs = millis % 1_000L;
        if (seconds < 60L) {
            return seconds + "." + String.format("%03d", remMs).substring(0, 1) + " s";
        }
        long minutes = seconds / 60L;
        long remSec = seconds % 60L;
        return minutes + "m " + remSec + "s";
    }

    private String buildTranslateErrorLine1(Throwable error) {
        if (error == null) {
            return localization.get("translating.error.generic");
        }

        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            return localization.get("translating.error.generic");
        }
        return message;
    }

    private String buildTranslateErrorLine2(Throwable error) {
        String message = (error == null || error.getMessage() == null)
                ? ""
                : error.getMessage().toLowerCase();

        if (message.contains("libretranslate is not reachable") || message.contains("did not become ready")) {
            return localization.get("translating.error.hint.server");
        }

        return localization.get("translating.error.hint.generic");
    }

    private void scheduleTranslationServerWarmup(String srcUi, String targetUi, String reason) {
        Thread existing = ltWarmupThread;
        if (existing != null && existing.isAlive()) {
            return;
        }

        int generation = ltWarmupGeneration.incrementAndGet();

        Thread warmup = new Thread(() -> {
            AppLog.info("[LT] background server startup start (" + reason + ")");
            // OPTIMIZED: No thread priority boosting - all operations equal
            try {
                boolean ready = TranslationService.ensureServerAvailable();
                if (generation != ltWarmupGeneration.get()) {
                    AppLog.info("[LT] background server startup ignored (superseded)");
                    return;
                }
                if (ready) {
                    AppLog.info("[LT] background server ready at " + TranslationService.BASE_URL
                            + " (" + (TranslationService.isGpuActive() ? "GPU" : "CPU") + ")");
                } else {
                    AppLog.warn("[LT] background server startup did not finish, on-demand startup will be used.");
                }
            } catch (RuntimeException ex) {
                if (generation == ltWarmupGeneration.get()) {
                    AppLog.warn("[LT] background server startup failed: " + ex.getMessage());
                }
            }
        }, "lt-server-startup-" + generation);
        warmup.setDaemon(true);
        ltWarmupThread = warmup;
        warmup.start();
    }

    private void shutdownTranslationRuntime() {
        stopBackgroundWarmup();
        TranslationService.shutdown();
    }

    private void stopBackgroundWarmup() {
        ltWarmupGeneration.incrementAndGet();
        Thread warmup = ltWarmupThread;
        if (warmup != null && warmup.isAlive()) {
            warmup.interrupt();
        }
    }

    private void enterTranslationTurboMode() {
        if (backgroundLayer == null || borderTable == null || translationVisualState != null) {
            return;
        }

        translationVisualState = new TranslationVisualState(backgroundLayer, borderTable);

        backgroundLayer.setAnimationEnabled(false);
        backgroundLayer.shimmerContainer.setVisible(false);
        backgroundLayer.blurredLights.setVisible(false);
        backgroundLayer.setVisible(false);
        backgroundLayer.setFlashAlpha(0.0);
        backgroundLayer.setGridAlpha(Math.min(translationVisualState.gridAlpha, 0.015));
        backgroundLayer.setPointAlpha(Math.min(translationVisualState.pointAlpha, 0.08));

        borderTable.setAnimationEnabled(false);
        borderTable.setTableLightingVisible(false);
    }

    private void leaveTranslationTurboMode() {
        if (backgroundLayer == null || borderTable == null || translationVisualState == null) {
            return;
        }

        TranslationVisualState state = translationVisualState;
        translationVisualState = null;

        backgroundLayer.setGridAlpha(state.gridAlpha);
        backgroundLayer.setPointAlpha(state.pointAlpha);
        backgroundLayer.setFlashAlpha(state.flashAlpha);
        backgroundLayer.shimmerContainer.setVisible(state.shimmersVisible);
        backgroundLayer.blurredLights.setVisible(state.blurVisible);
        backgroundLayer.setVisible(state.backgroundVisible);
        backgroundLayer.setAnimationEnabled(true);

        borderTable.setTableLightingVisible(state.tableLightingVisible);
        borderTable.setAnimationEnabled(true);
    }

    private void runOnFxThreadAndWait(Runnable action) {
        if (action == null) {
            return;
        }
        if (Platform.isFxApplicationThread()) {
            action.run();
            return;
        }

        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                action.run();
            } finally {
                latch.countDown();
            }
        });

        try {
            latch.await();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    // ---------------------------
    // Scene / layout
    // ---------------------------

    private double normalTableWidth() {
        return UiScaleHelper.SCREEN_WIDTH * 0.786;
    }

    private double normalTableHeight() {
        return UiScaleHelper.SCREEN_HEIGHT * 0.37;
    }

    private double expandedTableWidth() {
        return UiScaleHelper.SCREEN_WIDTH * 0.935;
    }

    private double expandedTableHeight() {
        return UiScaleHelper.SCREEN_HEIGHT * 0.80;
    }

    private void applyDefaultBottomControlLayout() {
        bottomControlsContainer.setSpacing(UiScaleHelper.scaleY(10));
        bottomControlsContainer.setPadding(new Insets(
                UiScaleHelper.scaleY(50),
                UiScaleHelper.scaleX(80),
                UiScaleHelper.scaleY(10),
                UiScaleHelper.scaleX(100)
        ));
        bottomControlsContainer.setPrefWidth(UiScaleHelper.scaleX(250));

        translateType.setTranslateY(UiScaleHelper.scaleY(-185));
        translateType.setTranslateX(UiScaleHelper.scaleX(60));
        languageDropdown.setTranslateX(UiScaleHelper.scaleX(-165));
        languageDropdown.setTranslateY(UiScaleHelper.scaleY(-80));
        translateChooseAll.setTranslateY(UiScaleHelper.scaleY(-20));
        quitButton.setTranslateY(UiScaleHelper.scaleY(-80));
        quitButton.setTranslateX(UiScaleHelper.scaleX(-450));
    }

    private void applyExpandedBottomControlLayout() {
        // Keep the original floating control arrangement in table-focus mode.
        applyDefaultBottomControlLayout();
    }

    private String localizedTableFullscreenText() {
        return localization.get(tableFullscreenMode ? "button.tableWindowed" : "button.tableFullscreen");
    }

    private void setManagedVisible(Node node, boolean visible) {
        if (node == null) return;
        node.setVisible(visible);
        node.setManaged(visible);
    }

    private void animateTableFocusTransition(CustomTableView tableView, boolean expanded) {
        double initialW = tableView.getPrefWidth() > 1 ? tableView.getPrefWidth() : tableView.getWidth();
        double initialH = tableView.getPrefHeight() > 1 ? tableView.getPrefHeight() : tableView.getHeight();
        final double startW = (initialW <= 1) ? normalTableWidth() : initialW;
        final double startH = (initialH <= 1) ? normalTableHeight() : initialH;

        final double endW = expanded ? expandedTableWidth() : normalTableWidth();
        final double endH = expanded ? expandedTableHeight() : normalTableHeight();
        final double startContainerY = (tableContainer != null) ? tableContainer.getTranslateY() : 0.0;
        final double endContainerY = expanded ? UiScaleHelper.scaleY(10) : 0.0;
        final double startBorderY = borderTable.getTranslateY();
        final double endBorderY = expanded ? 0.0 : UiScaleHelper.scaleY(-3);

        if (tableModeTransition != null) {
            tableModeTransition.stop();
        }

        if (tableContainer != null) {
            tableContainer.setCache(true);
            tableContainer.setCacheHint(javafx.scene.CacheHint.SPEED);
        }

        Transition transition = new Transition() {
            {
                setCycleDuration(Duration.millis(520));
                setInterpolator(Interpolator.SPLINE(0.22, 0.72, 0.20, 1.0));
            }

            @Override
            protected void interpolate(double frac) {
                double w = startW + (endW - startW) * frac;
                double h = startH + (endH - startH) * frac;
                tableView.setViewportSize(w, h, false);

                if (tableContainer != null) {
                    tableContainer.setTranslateY(startContainerY + (endContainerY - startContainerY) * frac);
                }
                borderTable.setTranslateY(startBorderY + (endBorderY - startBorderY) * frac);
            }
        };

        transition.setOnFinished(e -> {
            tableView.setViewportSize(endW, endH, true);
            if (tableContainer != null) {
                tableContainer.setTranslateY(endContainerY);
                tableContainer.setCache(false);
            }
            borderTable.setTranslateY(endBorderY);
        });

        tableModeTransition = transition;
        transition.play();
    }

    private void insertBeforeProgress(Node node) {
        if (root == null || node == null || root.getChildren().contains(node)) {
            return;
        }
        int overlayIndex = root.getChildren().indexOf(progressOverlay);
        if (overlayIndex < 0) {
            root.getChildren().add(node);
        } else {
            root.getChildren().add(overlayIndex, node);
        }
    }

    private void applyTableFullscreenMode(CustomTableView tableView) {
        boolean expanded = tableFullscreenMode;

        if (expanded) {
            layout.setTop(null);
            layout.setBottom(null);

            setManagedVisible(editorTitleLabel, false);
            setManagedVisible(settingButton, false);
            applyExpandedBottomControlLayout();

            if (fileManagerBar != null) {
                fileManagerBar.setTranslateY(0);
            }
            if (topControlsContainer != null) {
                topControlsContainer.setSpacing(0);
                StackPane.setAlignment(topControlsContainer, Pos.TOP_LEFT);
                StackPane.setMargin(
                        topControlsContainer,
                        new Insets(UiScaleHelper.scaleY(16), UiScaleHelper.scaleX(20), 0, UiScaleHelper.scaleX(20))
                );
                insertBeforeProgress(topControlsContainer);
            }

            if (bottomControlsContainer != null) {
                StackPane.setAlignment(bottomControlsContainer, Pos.BOTTOM_RIGHT);
                StackPane.setMargin(
                        bottomControlsContainer,
                        new Insets(0, UiScaleHelper.scaleX(28), UiScaleHelper.scaleY(18), 0)
                );
                insertBeforeProgress(bottomControlsContainer);
            }
        } else {
            if (root != null) {
                root.getChildren().remove(topControlsContainer);
                root.getChildren().remove(bottomControlsContainer);
            }

            setManagedVisible(editorTitleLabel, true);
            setManagedVisible(settingButton, true);
            applyDefaultBottomControlLayout();

            if (fileManagerBar != null) {
                fileManagerBar.setTranslateY(UiScaleHelper.scaleY(65));
            }
            if (topControlsContainer != null) {
                topControlsContainer.setSpacing(UiScaleHelper.scaleY(20));
            }

            layout.setTop(topControlsContainer);
            layout.setBottom(bottomControlsContainer);
        }

        if (tableContainer != null) {
            tableContainer.setPadding(expanded
                    ? new Insets(UiScaleHelper.scaleY(26), UiScaleHelper.scaleX(22), UiScaleHelper.scaleY(96), UiScaleHelper.scaleX(22))
                    : Insets.EMPTY);
        }

        borderTable.applyTableFocusEdgeDecorations(expanded);
        tableView.setTableFocusMode(expanded);
        animateTableFocusTransition(tableView, expanded);

        if (tableFullscreenButton != null) {
            tableFullscreenButton.setText(localizedTableFullscreenText());
        }

        if (root != null) {
            root.applyCss();
            root.layout();
        }

        AppLog.info("[UI] table fullscreen mode = " + expanded);
    }

    private void toggleTableFullscreenMode(CustomTableView tableView) {
        tableFullscreenMode = !tableFullscreenMode;
        applyTableFullscreenMode(tableView);
    }

    private void buildScene(Stage primaryStage, CustomTableView tableView) {
        // TOP UI
        topControlsContainer = new VBox(UiScaleHelper.scaleY(20));
        topControlsContainer.setPickOnBounds(false);
        fileManagerBar = new HBox(UiScaleHelper.scaleX(8));
        fileManagerBar.setPickOnBounds(false);
        fileManagerBar.setTranslateY(UiScaleHelper.scaleY(65));
        // fileSelected.setTranslateY(UiScaleHelper.scaleY(10));

        SquareDiscordURL discordURL = new SquareDiscordURL();
        discordURL.setTranslateY(UiScaleHelper.scaleY(-6));
        fileManagerBar.getChildren().addAll(fileTitleLabel, discordURL, fileSelected, keyFilterButton, tableFullscreenButton);

        topControlsContainer.getChildren().addAll(
                editorTitleLabel,
                translateChooseAll,
                languageDropdown,
                fileManagerBar,
                settingButton
        );
        topControlsContainer.setMaxHeight(Screen.getPrimary().getBounds().getHeight());
        VBox.setVgrow(topControlsContainer, Priority.ALWAYS);

        // BOTTOM RIGHT UI
        bottomControlsContainer = new VBox(UiScaleHelper.scaleY(10));
        bottomControlsContainer.setPickOnBounds(false);
        bottomControlsContainer.getChildren().addAll(
                translate,
                translateChooseAll,
                languageDropdown,
                translateType,
                quitButton
        );
        // Keep default view order so modal overlays (KeyFilter/Settings/ExitConfirm) stay above controls.
        bottomControlsContainer.setViewOrder(0);
        translate.setViewOrder(0);
        translateChooseAll.setViewOrder(0);
        languageDropdown.setViewOrder(0);
        translateType.setViewOrder(0);
        quitButton.setViewOrder(0);
        bottomControlsContainer.setAlignment(Pos.BOTTOM_RIGHT);
        applyDefaultBottomControlLayout();

        HeaderFlashOverlay overlay = new HeaderFlashOverlay(tableView, layout);
        layout.getChildren().add(overlay.getOverlayPane());

        backgroundLayer = new BackgroundGridLayer();
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
                getClass().getResource("/Assets/Style/KeyFilter.css").toExternalForm(),
                getClass().getResource("/Assets/Style/SquareDiscordURL.css").toExternalForm()
        );

        layout.setTop(topControlsContainer);
        layout.setBottom(bottomControlsContainer);
        BorderPane.setAlignment(bottomControlsContainer, Pos.BOTTOM_RIGHT);

        primaryStage.setScene(mainScene);
        primaryStage.setTitle(localization.get("label.editor.title")); // or any desired title
        primaryStage.setMinWidth(900);
        primaryStage.setMinHeight(600);
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

    public void updateTexts() {
        translate.refreshText();
        translateChooseAll.setText(localization.get("button.chooseAll"));
        quitButton.setText(localization.get("button.quit"));
        editorTitleLabel.setText(localization.get("label.editor.title"));
        fileTitleLabel.setText(localization.get("label.file.name"));
        BoxAlertTitle.setText(localization.get("label.ExitConfirmation"));
        BoxAlertDescription.setText(localization.get("label.ExitConfirmationDescription"));
        settingButton.setText(localization.get("button.setting"));
        if (keyFilterButton != null) keyFilterButton.setText(localizedFilterText());
        if (tableFullscreenButton != null) tableFullscreenButton.setText(localizedTableFullscreenText());

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
            shutdownTranslationRuntime();
            if (libreProcess != null && libreProcess.isAlive()) {
                libreProcess.destroy();
                libreProcess.waitFor(3, java.util.concurrent.TimeUnit.SECONDS);

                if (libreProcess.isAlive()) {
                    libreProcess.destroyForcibly();
                }
            }
        } catch (InterruptedException | RuntimeException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            AppLog.exception(e);
        }
    }
    private void setWindowIcon(Stage primaryStage) {
        // JavaFX Å Ć¦Å Ā¾Å Ā´Å Ā´Å ĀµÅā‚¬Å Ā¶Å ĆøÅ Ā²Å Ā°Å ĀµÅā€: PNG, JPEG, GIF, BMP (Å ĀÆÅ ā€¢ Å Ć¦Å Ā¾Å Ā´Å Ā´Å ĀµÅā‚¬Å Ā¶Å ĆøÅ Ā²Å Ā°Å ĀµÅā€ ICO)
        // Å ĀÅā€¹Åā€Å Ā°Å ĀµÅ Ā¼ÅĀÅĀø Å Ā·Å Ā°Å Ā³Åā‚¬ÅĀÅ Ā·Å ĆøÅā€ÅĀ Icon.png, Å ĀµÅĀÅ Ā»Å Ćø Å Ā½Å Āµ Å Ā½Å Ā°Å Ā¹Å Ā´Å ĀµÅ Ā½Å Ā° - Å ĆøÅĀÅ Ć¦Å Ā¾Å Ā»ÅĀÅ Ā·ÅĀÅ ĀµÅ Ā¼ Discord.png
        try {
            String[] iconPaths = {
                "Assets/Textures/Icon.png",    // Å ĀÅā‚¬Å ĆøÅ Ā¾Åā‚¬Å ĆøÅā€Å ĀµÅā€ 1: Icon Å Ā² Åā€˛Å Ā¾Åā‚¬Å Ā¼Å Ā°Åā€Å Āµ PNG
                "Assets/Textures/Icon.ico"     // fallback if PNG is missing
            };
            
            for (String iconPath : iconPaths) {
                java.net.URL iconUrl = getClass().getResource("/" + iconPath);
                if (iconUrl != null) {
                    try (java.io.InputStream stream = iconUrl.openStream()) {
                        javafx.scene.image.Image icon = new javafx.scene.image.Image(stream);
                        if (!icon.isError()) {
                            primaryStage.getIcons().add(icon);
                            AppLog.info("[Main] Window icon loaded: " + iconPath);
                            return;
                        }
                    } catch (Exception e) {
                        AppLog.error("[Main] Failed to load " + iconPath + ": " + e.getMessage());
                    }
                }
            }
            AppLog.error("[Main] No icon could be loaded. Consider creating Icon.png from Icon.ico");
        } catch (Exception e) {
            AppLog.error("[Main] Error in icon loading: " + e.getMessage());
        }
    }

    private String localizedFilterText() {
        try {
            return localization.get("button.filter");
        } catch (Exception ignored) {
            return "ru".equalsIgnoreCase(localization.getCurrentLanguage()) ? "Š¤ŠøŠ»ŃŃ‚Ń€" : "Filter";
        }
    }
}


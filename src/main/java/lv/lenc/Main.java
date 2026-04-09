package lv.lenc;

import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

import javafx.animation.PauseTransition;
import javafx.animation.FadeTransition;
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
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;
import javafx.util.Duration;

public class Main extends Application {

    public static final String RU = "RU";
    public static final String EN = "EN";
    private static final String TEXTURE_ROOT = UiAssets.textureRoot();
    private static final String LIBRE_TRANSLATE_LABEL = "LibreTranslate AI";
    private static final String SILICONFLOW_DEEPSEEK_LABEL = "SiliconFlow AI (DeepSeek-V3.2)";
    private static final int STARTUP_ANIMATION_GRACE_MS = 3000;

    // UI / state
    Scene mainScene;
    BorderPane layout;

    private final LocalizationProjectContext project = new LocalizationProjectContext();
    private LocalizationManager localization;

    private TranslateCancelSaveButton translate;
    private CustomLongButton settingButton;
    private CustomAlternativeButton keyFilterButton;
    private CustomAlternativeButton tableFullscreenButton;
    private CustomAlternativeButton tableSearchButton;
    private CustomAlternativeButton closeFileButton;
    private CustomAlternativeButton windowedModeButton;
    private CustomAlternativeButton appFullscreenButton;
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
    private HBox windowModeBar;
    private StackPane tableContainer;
    private boolean tableFullscreenMode;
    private Transition tableModeTransition;
    private TableSearchPopup tableSearchPopup;
    private Stage primaryStageRef;
    private final GlossaryService glossaryService = new GlossaryService();

    public GlossaryService getGlossaryService() {
        return glossaryService;
    }

    private final BooleanProperty fileOpened = new SimpleBooleanProperty(false);
    private final BooleanProperty chooseAllMode = new SimpleBooleanProperty(false);
    private final BooleanProperty fileLoading = new SimpleBooleanProperty(false);
    private final AtomicInteger ltWarmupGeneration = new AtomicInteger();
    private volatile Thread ltWarmupThread;

    private static final class FileOpenDialogResult {
        final String fileOption;
        final String mainLanguage;

        FileOpenDialogResult(String fileOption, String mainLanguage) {
            this.fileOption = fileOption;
            this.mainLanguage = mainLanguage;
        }
    }

    private static final class TranslationVisualState {
        final double gridAlpha;
        final double pointAlpha;
        final double flashAlpha;
        final boolean shimmersVisible;
        final boolean blurVisible;
        final boolean backgroundVisible;
        final boolean tableLightingVisible;
        final boolean tableFrameEffectsVisible;

        TranslationVisualState(BackgroundGridLayer backgroundLayer, CustomBorder borderTable) {
            this.gridAlpha = backgroundLayer.getGridAlpha();
            this.pointAlpha = backgroundLayer.getPointAlpha();
            this.flashAlpha = backgroundLayer.getFlashAlpha();
            this.shimmersVisible = backgroundLayer.shimmerContainer.isVisible();
            this.blurVisible = backgroundLayer.blurredLights.isVisible();
            this.backgroundVisible = backgroundLayer.isVisible();
            this.tableLightingVisible = borderTable.isTableLightingVisible();
            this.tableFrameEffectsVisible = borderTable.isFrameEffectsVisible();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        initLocalization();
        TranslationService.setSelectedBackend(loadSavedTranslationBackend());
        // OPTIMIZED: Load translation models in background during app startup
        // Users will see progress window while GPU models initialize
        if (TranslationService.selectedBackendRequiresLocalServer()) {
            scheduleTranslationServerWarmup("ruRU", null, "app-start");
        }
        progressOverlay = new TranslationProgressOverlay(localization);
        CustomTableView tableView = createTableView();
        createControls(tableView);
        wireEvents(tableView);
        
        // 
        setWindowIcon(primaryStage);
        
        buildScene(primaryStage, tableView);
        UiSoundManager.ensureBackgroundMusicPlayback();

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
                TEXTURE_ROOT,
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
                TEXTURE_ROOT,
                true,
                0.25,
                0.3
        );
        //  translate.setDisable(true);

        translateChooseAll = UIElementFactory.createCustomLongAlternativeButton(
                localization.get("button.chooseAll"),
                TEXTURE_ROOT,
                0.6,
                0.8
        );

        quitButton = UIElementFactory.createCustomQuitButton(
                localization.get("button.quit"),
                TEXTURE_ROOT
        );
        quitButton.setTranslateY(UiScaleHelper.scaleY(-80));
        quitButton.setTranslateX(UiScaleHelper.scaleX(-450));

        fileTitleLabel = new GlowingLabelWithBorder(localization.get("label.file.name"), 220, 70, 13.2);
        fileSelected = new CustomFileChooser(
                localization,
                createFileSelectable(tableView),
                70,
                70
        );

        BoxAlertTitle = new GlowingLabel(localization.get("label.ExitConfirmation"));
        BoxAlertDescription = new GlowingLabel(localization.get("label.ExitConfirmationDescription"));

        languageDropdown = new CustomComboBoxTexture<>(
                TEXTURE_ROOT,
                165,
                58
        );
        // languageDropdown.disable(true);
        languageDropdown.getItems().setAll(valueKey);
        languageDropdown.setValue(valueKey[2]);

        translateType = new CustomComboBoxClassic<>(
                TEXTURE_ROOT,
                true,
                343,
                54,
                11.2,
                9.8,
                22,
                10
        );
        translateType.getStyleClass().add("ai-backend-combo");
        refreshTranslateBackendItems(TranslationService.getSelectedBackend());

        editorTitleLabel = new TitleLabelGlow(localization.get("label.editor.title"), localization);
        editorTitleLabel.setTranslateY(UiScaleHelper.scaleY(30));

        settingButton = new CustomLongButton(
                localization.get("button.setting"),
                true,
                330,
                58,
                12.6,
                0.3,
                0.6
        );
        settingButton.setAlignment(Pos.CENTER);
        settingButton.setTranslateY(UiScaleHelper.scaleY(-120));

        keyFilterButton = new CustomAlternativeButton(
                localizedFilterText(),
                0.6, 0.8, 180.0, 56.0, 14.0
        );
        keyFilterButton.getStyleClass().remove("alt-button");
        keyFilterButton.getStyleClass().add("key-filter-table-button");

        tableFullscreenButton = new CustomAlternativeButton(
                localizedTableFullscreenText(),
                0.6, 0.8, 240.0, 56.0, 14.0
        );
        tableFullscreenButton.getStyleClass().remove("alt-button");
        tableFullscreenButton.getStyleClass().add("key-filter-table-button");
        tableFullscreenButton.setFocusTraversable(false);

        tableSearchButton = new CustomAlternativeButton(
                localizedSearchText(),
                0.6, 0.8, 160.0, 56.0, 14.0
        );
        tableSearchButton.getStyleClass().remove("alt-button");
        tableSearchButton.getStyleClass().add("key-filter-table-button");
        tableSearchButton.setFocusTraversable(false);

        closeFileButton = new CustomAlternativeButton(
                "\u2716",
                0.6, 0.8, 56.0, 56.0, 28.0
        );
        closeFileButton.getStyleClass().remove("alt-button");
        closeFileButton.getStyleClass().add("key-filter-table-button");
        closeFileButton.getStyleClass().add("close-file-button");
        closeFileButton.setFocusTraversable(false);

        tableSearchPopup = new TableSearchPopup(localization, tableView);

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
        translateType.disableProperty().bind(fileOpened.not());
        keyFilterButton.disableProperty().bind(fileOpened.not());
        tableFullscreenButton.disableProperty().bind(fileOpened.not());
        tableSearchButton.disableProperty().bind(fileOpened.not());
        closeFileButton.disableProperty().bind(fileOpened.not().or(fileLoading));

    }

    private FileSelectable createFileSelectable(CustomTableView tableView) {
        return (File file) -> {
            if (file == null) return;
            if (tableSearchPopup != null) {
                tableSearchPopup.reset();
            }
            File archiveInput = resolveArchiveInput(file);
            if (archiveInput != null) {
                fileTitleLabel.setText(archiveInput.getName());
            } else {
                fileTitleLabel.setText(file.getName());
            }

            boolean archiveOpen = archiveInput != null;
            FileUtil.OpenPlan plan = archiveOpen ? FileUtil.buildOpenPlan(archiveInput) : null;
            if (archiveOpen) {
                if (plan == null || plan.getFileOptions() == null || plan.getFileOptions().isEmpty()) {
                    plan = FileUtil.buildFallbackArchivePlan();
                }
                showArchiveOpenPopup(archiveInput, plan,
                        choice -> openSelectedInput(tableView, archiveInput, choice.fileOption, choice.mainLanguage),
                        () -> {
                            fileOpened.set(false);
                            chooseAllMode.set(false);
                        });
                return;
            }

            openSelectedInput(tableView, file, null, null);
        };
    }

    private boolean openSelectedInput(CustomTableView tableView,
                                      File file,
                                      String preferredFile,
                                      String preferredMainLanguage) {
        fileLoading.set(true);
        try {
            File loadTarget = FileUtil.resolveLoadInput(file, preferredFile, preferredMainLanguage);
            if (loadTarget == null && isArchiveInput(file) && (preferredFile == null || preferredFile.isBlank())) {
                AppLog.warn("[UI] Archive auto-open target not resolved, retrying auto-resolve.");
                loadTarget = FileUtil.resolveLoadInput(file, null, preferredMainLanguage);
            }
            if (loadTarget == null) {
                fileOpened.set(false);
                chooseAllMode.set(false);
                AppLog.warn("[UI] Selected archive file cannot be opened: " + preferredFile + " / main=" + preferredMainLanguage);
                return false;
            }

            boolean ok = project.open(file, loadTarget, preferredFile, preferredMainLanguage, tableView);
            if (!ok && isArchiveInput(file) && (preferredFile == null || preferredFile.isBlank())) {
                File fallbackTarget = FileUtil.resolveLoadInput(file, null, preferredMainLanguage);
                if (fallbackTarget != null && !fallbackTarget.equals(loadTarget)) {
                    AppLog.warn("[UI] Archive auto-open retry with fallback file.");
                    ok = project.open(file, fallbackTarget, preferredFile, preferredMainLanguage, tableView);
                    if (ok) {
                        loadTarget = fallbackTarget;
                    }
                }
            }

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
            if (preferredMainLanguage != null
                    && tableView.getLoadedUiLanguages().contains(preferredMainLanguage)) {
                sourceUi = preferredMainLanguage;
            }

            if (ok) {
                if (project.getOpenedFile() != null) {
                    fileTitleLabel.setText(project.getOpenedFile().getName());
                }
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
                    if (preferredMainLanguage != null
                            && tableView.getLoadedUiLanguages().contains(preferredMainLanguage)) {
                        sourceUi = preferredMainLanguage;
                    } else {
                        sourceUi = tableView.getMainSourceLang();
                    }
                } else {
                    tableView.showOnly(sourceUi, languageDropdown.getValue());
                }

            } else {
                fileOpened.set(false);
                chooseAllMode.set(false);
            }
            return ok;
        } catch (Exception ex) {
            AppLog.error("[UI] openSelectedInput failed", ex);
            fileOpened.set(false);
            chooseAllMode.set(false);
            return false;
        } finally {
            fileLoading.set(false);
        }
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
        tableSearchButton.setOnAction(e -> {
            if (tableSearchPopup != null) {
                tableSearchPopup.toggle(tableSearchButton);
            }
        });
        closeFileButton.setOnAction(e -> closeCurrentFile(tableView));
        translateType.setOnAction(e -> {
            TranslationService.TranslationBackend backend = selectedTranslationBackend();
            TranslationService.setSelectedBackend(backend);
            if (backend == TranslationService.TranslationBackend.LIBRE_TRANSLATE) {
                scheduleTranslationServerWarmup("ruRU", null, "backend-switch");
            } else {
                stopBackgroundWarmup();
            }
        });

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
            UiSoundManager.playError();
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

        TranslationService.setSelectedBackend(selectedTranslationBackend());
        TranslationService.resetRunCharStats();
        String targetUi = languageDropdown.getValue();
        String srcUi = (sourceUi != null) ? sourceUi : tableView.getMainSourceLang();
        final TranslationService.TranslationBackend currentBackend = TranslationService.getSelectedBackend();
        final boolean geminiBackend = currentBackend == TranslationService.TranslationBackend.GEMINI;
        final boolean localServerBackend = TranslationService.selectedBackendRequiresLocalServer();
        final boolean readyForImmediateTranslate = TranslationService.isSelectedBackendReady();

        runOnFxThreadAndWait(() -> {
            if (!readyForImmediateTranslate) {
                if (localServerBackend) {
                    progressWin.update(
                            0.02,
                            localization.get("translating.server.preparing"),
                            localization.get("translating.server.models")
                    );
                } else {
                    String checkingKey = switch (TranslationService.getSelectedBackend()) {
                        case CLOUDFLARE_M2M100 -> "translating.cloudflare.checking";
                        case GOOGLE_WEB_FREE -> "translating.googlefree.checking";
                        case GEMINI -> "translating.gemini.checking";
                        case SILICONFLOW, SILICONFLOW_DEEPSEEK_V3, SILICONFLOW_M2M100 -> "translating.siliconflow.checking";
                        case DEEPL_FREE -> "translating.deepl.checking";
                        default -> "translating.google.checking";
                    };
                    String hintKey = switch (TranslationService.getSelectedBackend()) {
                        case CLOUDFLARE_M2M100 -> "translating.cloudflare.hint.key";
                        case GOOGLE_WEB_FREE -> "translating.googlefree.hint.key";
                        case GEMINI -> "translating.gemini.hint.key";
                        case SILICONFLOW, SILICONFLOW_DEEPSEEK_V3, SILICONFLOW_M2M100 -> "translating.siliconflow.hint.key";
                        case DEEPL_FREE -> "translating.deepl.hint.key";
                        default -> "translating.google.hint.key";
                    };
                    progressWin.update(
                            0.02,
                            localization.get(checkingKey),
                            localization.get(hintKey)
                    );
                }
            } else {
                progressWin.update(
                        0.0,
                        srcUi + " -> " + targetUi,
                        TranslationService.describeSelectedBackend()
                );
            }
        });

        boolean completed = false;
        Throwable perfFailure = null;
        String perfStage = "startup";
        try {
            TranslationService.requestTranslationPerformanceMode();

            perfStage = "availability-check";
            if (!TranslationService.ensureSelectedBackendAvailable()) {
                throw new IllegalStateException(TranslationService.selectedBackendLabel() + " is not available");
            }

            stopBackgroundWarmup();

            if (!readyForImmediateTranslate) {
                runOnFxThreadAndWait(() -> {
                    if (localServerBackend) {
                        progressWin.update(
                                0.05,
                                localization.get("translating.server.ready"),
                                TranslationService.describeSelectedBackend()
                        );
                    } else {
                        progressWin.update(
                                0.05,
                                srcUi + " -> " + targetUi,
                                TranslationService.describeSelectedBackend()
                        );
                    }
                });
            }

            if (geminiBackend) {
                String started = "[GEMINI] translation start " + srcUi + " -> " + (translateToAll ? "all" : targetUi)
                        + ", endpoint=" + GeminiTranslationProvider.activeEndpointForLogs()
                        + ", model=" + GeminiTranslationProvider.MODEL_ID;
                AppLog.info(started);
                System.out.println(started);
            }
            if (translateToAll) {
                perfStage = "translate-all";
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
                                TranslationService.describeSelectedBackend()
                        )
                );

                perfStage = "translate-single";
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
        } catch (Throwable ex) {
            perfFailure = ex;
            throw ex;
        } finally {
            long tookMs = Math.max(0L, (System.nanoTime() - startedAtNanos) / 1_000_000L);
            String mode = translateToAll ? "all" : targetUi;
            String status = completed
                    ? "completed"
                    : (translate.isCancelRequested() ? "cancelled" : (perfFailure != null ? "failed" : "stopped"));
            String provider = TranslationService.selectedBackendLabel();
            String endpoint;
            String service;
            String model = "-";
            if (TranslationService.selectedBackendRequiresLocalServer()) {
                endpoint = TranslationService.BASE_URL;
                service = "LibreTranslate";
            } else if (TranslationService.getSelectedBackend() == TranslationService.TranslationBackend.GEMINI) {
                endpoint = GeminiTranslationProvider.activeEndpointForLogs();
                service = "Gemini Vertex AI";
                model = GeminiTranslationProvider.MODEL_ID;
            } else if (TranslationService.selectedBackendUsesSiliconFlowApi()) {
                endpoint = SiliconFlowTranslationProvider.activeEndpointForLogs();
                service = "SiliconFlow API";
                model = SiliconFlowTranslationProvider.activeModelForLogs();
            } else if (TranslationService.getSelectedBackend() == TranslationService.TranslationBackend.DEEPL_FREE) {
                endpoint = DeepLTranslationProvider.activeEndpointForLogs();
                service = "DeepL API Free";
            } else if (TranslationService.getSelectedBackend() == TranslationService.TranslationBackend.CLOUDFLARE_M2M100) {
                endpoint = CloudflareM2M100TranslationProvider.activeEndpointForLogs();
                service = "Cloudflare Worker AI";
                model = CloudflareM2M100TranslationProvider.MODEL_ID;
            } else if (TranslationService.getSelectedBackend() == TranslationService.TranslationBackend.GOOGLE_WEB_FREE) {
                endpoint = GoogleWebFreeTranslationProvider.activeEndpointForLogs();
                service = "Google Translate Free (Web)";
            } else {
                endpoint = "https://translation.googleapis.com/language/translate/v2";
                service = "Google Cloud Translation";
            }
            String backendMode = TranslationService.selectedBackendRequiresLocalServer()
                    ? (TranslationService.isGpuActive() ? "GPU" : "CPU")
                    : "API";
            String perfMessage = "[PERF] translation " + srcUi + " -> " + mode
                    + " " + status
                    + " in " + formatElapsed(tookMs)
                    + " (" + tookMs + " ms)"
                    + ", provider=" + provider
                    + ", service=" + service
                    + ", model=" + model
                    + ", backend=" + backendMode
                    + ", endpoint=" + endpoint
                    + ", stage=" + perfStage
                    + (perfFailure == null ? "" : ", reason=" + summarizePerfFailure(perfFailure))
                    + ", " + TranslationService.runCharSummaryForPerf();
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
        String startupHint = TranslationService.getLastStartupFailureHint();
        if (startupHint != null && !startupHint.isBlank()) {
            return startupHint;
        }

        String message = (error == null || error.getMessage() == null)
                ? ""
                : error.getMessage().toLowerCase();

        if (message.contains("libretranslate is not reachable") || message.contains("did not become ready")) {
            return localization.get("translating.error.hint.server");
        }
        if (TranslationService.getSelectedBackend() == TranslationService.TranslationBackend.GOOGLE_CLOUD) {
            return localization.get("translating.error.hint.google");
        }
        if (TranslationService.getSelectedBackend() == TranslationService.TranslationBackend.CLOUDFLARE_M2M100) {
            return localization.get("translating.error.hint.cloudflare");
        }
        if (TranslationService.getSelectedBackend() == TranslationService.TranslationBackend.GOOGLE_WEB_FREE) {
            return localization.get("translating.error.hint.googlefree");
        }
        if (TranslationService.getSelectedBackend() == TranslationService.TranslationBackend.GEMINI) {
            return localization.get("translating.error.hint.gemini");
        }
        if (TranslationService.selectedBackendUsesSiliconFlowApi()) {
            return localization.get("translating.error.hint.siliconflow");
        }
        if (TranslationService.getSelectedBackend() == TranslationService.TranslationBackend.DEEPL_FREE) {
            return localization.get("translating.error.hint.deepl");
        }

        return localization.get("translating.error.hint.generic");
    }

    private static String summarizePerfFailure(Throwable error) {
        if (error == null) {
            return "-";
        }
        Throwable root = error;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage();
        if (message == null || message.isBlank()) {
            message = root.getClass().getSimpleName();
        }
        message = message.replace('\r', ' ').replace('\n', ' ').trim();
        if (message.length() > 160) {
            message = message.substring(0, 157) + "...";
        }
        return message;
    }

    private TranslationService.TranslationBackend selectedTranslationBackend() {
        int selectedIndex = translateType == null ? 0 : translateType.getSelectionModel().getSelectedIndex();
        return switch (selectedIndex) {
            case 1 -> TranslationService.TranslationBackend.GOOGLE_CLOUD;
            case 2 -> TranslationService.TranslationBackend.CLOUDFLARE_M2M100;
            case 3 -> TranslationService.TranslationBackend.GEMINI;
            case 4 -> TranslationService.TranslationBackend.SILICONFLOW;
            case 5 -> TranslationService.TranslationBackend.SILICONFLOW_DEEPSEEK_V3;
            case 6 -> TranslationService.TranslationBackend.DEEPL_FREE;
            case 7 -> TranslationService.TranslationBackend.LIBRE_TRANSLATE;
            default -> TranslationService.TranslationBackend.GOOGLE_WEB_FREE;
        };
    }

    private TranslationService.TranslationBackend loadSavedTranslationBackend() {
        String backendName = SettingsManager.loadTranslationBackendName();
        try {
            return TranslationService.TranslationBackend.valueOf(backendName);
        } catch (IllegalArgumentException ex) {
            return TranslationService.TranslationBackend.GOOGLE_WEB_FREE;
        }
    }

    private void refreshTranslateBackendItems(TranslationService.TranslationBackend selectedBackend) {
        translateType.getItems().setAll(
                localization.get("combox.googlefree"),
                localization.get("combox.google"),
                localization.get("combox.cloudflare"),
                localization.get("combox.gemini"),
                localization.get("combox.siliconflow"),
                SILICONFLOW_DEEPSEEK_LABEL,
                localization.get("combox.deepl"),
                LIBRE_TRANSLATE_LABEL
        );
        int selectedIndex = switch (selectedBackend) {
            case GOOGLE_WEB_FREE -> 0;
            case GOOGLE_CLOUD -> 1;
            case CLOUDFLARE_M2M100 -> 2;
            case GEMINI -> 3;
            case SILICONFLOW -> 4;
            case SILICONFLOW_DEEPSEEK_V3 -> 5;
            case SILICONFLOW_M2M100 -> 2;
            case DEEPL_FREE -> 6;
            case LIBRE_TRANSLATE -> 7;
            default -> 0;
        };
        translateType.getSelectionModel().select(selectedIndex);
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
        borderTable.setFrameEffectsVisible(false);
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
        borderTable.setFrameEffectsVisible(state.tableFrameEffectsVisible);
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

        translateType.setTranslateY(UiScaleHelper.scaleY(-102));
        translateType.setTranslateX(UiScaleHelper.scaleX(9));
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
        primaryStageRef = primaryStage;
        // TOP UI
        topControlsContainer = new VBox(UiScaleHelper.scaleY(20));
        topControlsContainer.setPickOnBounds(false);
        fileManagerBar = new HBox(UiScaleHelper.scaleX(8));
        fileManagerBar.setPickOnBounds(false);
        fileManagerBar.setTranslateY(UiScaleHelper.scaleY(65));
        // fileSelected.setTranslateY(UiScaleHelper.scaleY(10));

        SquareDiscordURL discordURL = new SquareDiscordURL();
        discordURL.setTranslateY(UiScaleHelper.scaleY(-6));
        fileManagerBar.getChildren().addAll(
                fileTitleLabel,
                discordURL,
                fileSelected,
                keyFilterButton,
                tableFullscreenButton,
                tableSearchButton,
                closeFileButton
        );

        topControlsContainer.getChildren().addAll(
                editorTitleLabel,
                translateChooseAll,
                languageDropdown,
                fileManagerBar,
                settingButton
        );
        topControlsContainer.setMaxHeight(UiScaleHelper.SCREEN_HEIGHT);
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
        applyStartupAnimationGracePeriod();


        settingButton.setOnAction(e -> {
            if (settingButton.isSelected()) return;
            settingButton.select();
            SettingBox.show(root, localization, backgroundLayer, settingButton, this, borderTable, tableView);
        });


        root = new StackPane();
        root.setMinSize(UiScaleHelper.SCREEN_WIDTH, UiScaleHelper.SCREEN_HEIGHT);
        root.setPrefSize(UiScaleHelper.SCREEN_WIDTH, UiScaleHelper.SCREEN_HEIGHT);
        root.setMaxSize(UiScaleHelper.SCREEN_WIDTH, UiScaleHelper.SCREEN_HEIGHT);
        root.getChildren().addAll(layout, this.progressOverlay);

        windowModeBar = new HBox(UiScaleHelper.scaleX(8));
        windowModeBar.setPickOnBounds(false);
        windowModeBar.setAlignment(Pos.TOP_RIGHT);
        windowModeBar.setTranslateX(UiScaleHelper.scaleX(-18));
        windowModeBar.setTranslateY(UiScaleHelper.scaleY(18));

        windowedModeButton = new CustomAlternativeButton(
                localizedWindowedModeText(),
                0.6, 0.8, 182.0, 56.0, 14.0
        );
        windowedModeButton.getStyleClass().remove("alt-button");
        windowedModeButton.getStyleClass().add("key-filter-table-button");
        windowedModeButton.setFocusTraversable(false);

        appFullscreenButton = new CustomAlternativeButton(
                localizedAppFullscreenText(),
                0.6, 0.8, 182.0, 56.0, 14.0
        );
        appFullscreenButton.getStyleClass().remove("alt-button");
        appFullscreenButton.getStyleClass().add("key-filter-table-button");
        appFullscreenButton.setFocusTraversable(false);

        windowModeBar.getChildren().addAll(windowedModeButton, appFullscreenButton);
        StackPane.setAlignment(windowModeBar, Pos.TOP_RIGHT);
        root.getChildren().add(windowModeBar);

        StackPane viewportRoot = new StackPane(root);
        viewportRoot.setAlignment(Pos.CENTER);
        viewportRoot.setStyle("-fx-background-color: rgba(0, 0, 0, 1);");

        mainScene = new Scene(viewportRoot, UiScaleHelper.REAL_SCREEN_WIDTH, UiScaleHelper.REAL_SCREEN_HEIGHT);
        CustomCursorManager.install(mainScene);

        AppStyles.applyMainScene(mainScene);

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
        primaryStage.fullScreenProperty().addListener((obs, oldValue, isFull) -> updateWindowModeButtonTexts());
        primaryStage.maximizedProperty().addListener((obs, oldValue, isMaximized) -> updateWindowModeButtonTexts());

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

        windowedModeButton.setOnAction(e -> switchToWindowedMode(primaryStage));
        appFullscreenButton.setOnAction(e -> toggleAppFullscreen(primaryStage));
        updateWindowModeButtonTexts();
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
        if (tableSearchButton != null) tableSearchButton.setText(localizedSearchText());
        if (closeFileButton != null) closeFileButton.setText("\u2716");
        if (windowedModeButton != null) windowedModeButton.setText(localizedWindowedModeText());
        if (appFullscreenButton != null) appFullscreenButton.setText(localizedAppFullscreenText());
        if (tableSearchPopup != null) tableSearchPopup.updateTexts();

        TranslationService.TranslationBackend selectedBackend = selectedTranslationBackend();
        refreshTranslateBackendItems(selectedBackend);
    }

    private void switchToWindowedMode(Stage stage) {
        if (stage == null) return;
        stage.setFullScreen(false);
        stage.setMaximized(!stage.isMaximized());
        updateWindowModeButtonTexts();
    }

    private void toggleAppFullscreen(Stage stage) {
        if (stage == null) return;
        stage.setFullScreen(!stage.isFullScreen());
        updateWindowModeButtonTexts();
    }

    private void updateWindowModeButtonTexts() {
        Stage stage = primaryStageRef;
        if (stage == null) return;
        if (windowedModeButton != null) {
            windowedModeButton.setText(localizedWindowedModeText());
        }
        if (appFullscreenButton != null) {
            appFullscreenButton.setText(stage.isFullScreen()
                    ? localizedExitFullscreenText()
                    : localizedAppFullscreenText());
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

    private boolean isRussianUi() {
        String language = localization != null ? localization.getCurrentLanguage() : "";
        return language != null && language.regionMatches(true, 0, "ru", 0, 2);
    }

    private String localizedSearchText() {
        return localizedTextWithFallback("button.search", "\u041f\u043e\u0438\u0441\u043a", "Search");
    }

    private String localizedWindowedModeText() {
        return localizedTextWithFallback("button.windowedMode", "\u041e\u043a\u043e\u043d\u043d\u044b\u0439 \u0440\u0435\u0436\u0438\u043c", "Windowed");
    }

    private String localizedAppFullscreenText() {
        return localizedTextWithFallback("button.appFullScreen", "\u041d\u0430 \u0432\u0435\u0441\u044c \u044d\u043a\u0440\u0430\u043d", "Fullscreen");
    }

    private String localizedExitFullscreenText() {
        return localizedTextWithFallback("button.exitFullScreen", "\u0412\u044b\u0439\u0442\u0438 \u0438\u0437 fullscreen", "Exit fullscreen");
    }

    private String localizedTextWithFallback(String key, String ruFallback, String enFallback) {
        try {
            String value = localization.get(key);
            if (value != null && !value.isBlank() && !value.equals(key)) {
                return value;
            }
        } catch (Exception ignored) {
        }
        return isRussianUi() ? ruFallback : enFallback;
    }

    private String localizedFilterText() {
        return localizedTextWithFallback("button.filter", "\u0424\u0438\u043b\u044c\u0442\u0440", "Filter");
    }

    private void applyStartupAnimationGracePeriod() {
        if (backgroundLayer == null || borderTable == null) {
            return;
        }

        final boolean shimmersVisible = backgroundLayer.shimmerContainer.isVisible();
        final boolean blurVisible = backgroundLayer.blurredLights.isVisible();
        final boolean backgroundVisible = backgroundLayer.isVisible();
        final boolean backgroundManaged = backgroundLayer.isManaged();
        final boolean tableLightingVisible = borderTable.isTableLightingVisible();
        final boolean tableFrameEffectsVisible = borderTable.isFrameEffectsVisible();
        final double shimmerOpacity = backgroundLayer.shimmerContainer.getOpacity();

        backgroundLayer.setAnimationEnabled(false);
        borderTable.setAnimationEnabled(false);
        backgroundLayer.shimmerContainer.setVisible(false);
        backgroundLayer.blurredLights.setVisible(false);
        // Hide full background layer during first seconds to avoid startup stutter/flicker.
        backgroundLayer.setVisible(false);
        backgroundLayer.setManaged(false);
        borderTable.setTableLightingVisible(false);
        borderTable.setFrameEffectsVisible(false);

        PauseTransition enableLater = new PauseTransition(Duration.millis(STARTUP_ANIMATION_GRACE_MS));
        enableLater.setOnFinished(event -> {
            // Keep turbo-mode ownership: if turbo mode is active, it controls animation state itself.
            if (translationVisualState == null) {
                backgroundLayer.setVisible(backgroundVisible);
                backgroundLayer.setManaged(backgroundManaged);
                if (shimmersVisible) {
                    backgroundLayer.shimmerContainer.setVisible(true);
                    backgroundLayer.shimmerContainer.setOpacity(shimmerOpacity <= 0.0 ? 1.0 : shimmerOpacity);
                }
                if (blurVisible) {
                    backgroundLayer.blurredLights.setVisible(true);
                    // Do not fade this node directly: its opacity is animated by timeline.
                    backgroundLayer.blurredLights.setOpacity(38.0 / 255.0);
                }
                borderTable.setTableLightingVisible(tableLightingVisible);
                borderTable.setFrameEffectsVisible(tableFrameEffectsVisible);

                // Start all visual loops strictly from frame 0 at the end of grace period.
                backgroundLayer.restartAnimationsFromStart();
                borderTable.restartAnimationsFromStart();
                backgroundLayer.setAnimationEnabled(true);
                borderTable.setAnimationEnabled(true);

                Duration fadeDuration = Duration.millis(850);
                if (backgroundVisible) {
                    backgroundLayer.setOpacity(0.0);
                    FadeTransition fadeIn = new FadeTransition(fadeDuration, backgroundLayer);
                    fadeIn.setFromValue(0.0);
                    fadeIn.setToValue(1.0);
                    fadeIn.play();
                } else {
                    backgroundLayer.setOpacity(1.0);
                }
                if (tableFrameEffectsVisible) {
                    borderTable.fadeInFrameEffects(fadeDuration, true);
                }
            }
        });
        enableLater.play();
    }

    private boolean isArchiveInput(File file) {
        if (file == null) return false;
        String lower = file.getName().toLowerCase(Locale.ROOT);
        return lower.endsWith(".mpq") || lower.endsWith(".sc2map") || lower.endsWith(".sc2mod");
    }

    private File resolveArchiveInput(File selected) {
        if (selected == null) {
            return null;
        }
        File cursor = selected.getAbsoluteFile();
        while (cursor != null) {
            if (isArchiveInput(cursor)) {
                return cursor;
            }
            cursor = cursor.getParentFile();
        }
        return null;
    }

    private void showArchiveOpenPopup(File selectedInput,
                                      FileUtil.OpenPlan plan,
                                      Function<FileOpenDialogResult, Boolean> onConfirm,
                                      Runnable onCancel) {
        if (plan == null || plan.getFileOptions().isEmpty()) {
            onCancel.run();
            return;
        }
        if (root == null) {
            String fileOption = plan.getDefaultFileOption() != null ? plan.getDefaultFileOption() : plan.getFileOptions().get(0);
            String mainLang = plan.getDefaultMainLanguage() != null ? plan.getDefaultMainLanguage() : "enUS";
            onConfirm.apply(new FileOpenDialogResult(fileOption, mainLang));
            return;
        }

        removeArchivePopupOverlays();

        String titleText = isRussianUi()
                ? "\u041f\u0430\u0440\u0430\u043c\u0435\u0442\u0440\u044b \u043e\u0442\u043a\u0440\u044b\u0442\u0438\u044f \u0430\u0440\u0445\u0438\u0432\u0430"
                : "Archive Open Settings";
        String selectedPrefix = isRussianUi()
                ? "\u0412\u044b\u0431\u0440\u0430\u043d\u043e: "
                : "Selected: ";
        String fileLabelText = isRussianUi()
                ? "\u041a\u0430\u043a\u043e\u0439 \u0444\u0430\u0439\u043b \u043e\u0442\u043a\u0440\u044b\u0442\u044c:"
                : "File to open:";
        String sourceLabelText = isRussianUi()
                ? "MAIN \u044f\u0437\u044b\u043a \u0438\u0441\u0442\u043e\u0447\u043d\u0438\u043a\u0430:"
                : "Main source language:";

        Region dim = new Region();
        dim.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        dim.getStyleClass().add("key-filter-overlay-dim");
        dim.setStyle("-fx-background-color: rgba(0, 0, 0, 0.52);");

        StackPane titleBadge = buildArchivePopupBadge(titleText);
        titleBadge.getStyleClass().add("archive-open-title-wrap");

        Label selectedLabel = new Label(selectedPrefix + (selectedInput != null ? selectedInput.getName() : ""));
        selectedLabel.getStyleClass().add("key-filter-status");
        selectedLabel.getStyleClass().add("archive-open-status");
        selectedLabel.setWrapText(true);
        selectedLabel.setStyle("-fx-font-size: " + UiScaleHelper.scaleY(12) + "px;");
        Region flavorBar = new Region();
        flavorBar.getStyleClass().add("archive-open-flavor-bar");
        flavorBar.setMinHeight(UiScaleHelper.scaleY(4));
        flavorBar.setPrefHeight(UiScaleHelper.scaleY(4));
        flavorBar.setMaxWidth(Double.MAX_VALUE);
        Label openErrorLabel = new Label();
        openErrorLabel.getStyleClass().add("archive-open-status");
        openErrorLabel.getStyleClass().add("archive-open-error");
        openErrorLabel.setWrapText(true);
        openErrorLabel.setVisible(false);
        openErrorLabel.setManaged(false);
        Label fileLabel = new Label(fileLabelText);
        fileLabel.getStyleClass().add("key-filter-lang-label");
        fileLabel.getStyleClass().add("archive-open-label");
        fileLabel.setStyle("-fx-font-size: " + UiScaleHelper.scaleY(15) + "px;");

        ComboBox<String> fileCombo = new ComboBox<>();
        fileCombo.getItems().setAll(plan.getFileOptions());
        fileCombo.setValue(plan.getDefaultFileOption() != null ? plan.getDefaultFileOption() : plan.getFileOptions().get(0));
        fileCombo.getStyleClass().add("key-filter-lang-combo");
        fileCombo.getStyleClass().add("archive-open-combo");
        fileCombo.getStyleClass().add("archive-open-textured-combo");
        fileCombo.setVisibleRowCount(Math.max(1, Math.min(4, fileCombo.getItems().size())));
        fileCombo.setMinWidth(UiScaleHelper.scaleX(610));
        fileCombo.setPrefWidth(UiScaleHelper.scaleX(610));
        fileCombo.setMaxWidth(Double.MAX_VALUE);
        fileCombo.setOnShowing(e ->
                fileCombo.setVisibleRowCount(Math.max(1, Math.min(4, fileCombo.getItems().size()))));
        fileCombo.setCellFactory(cb -> new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item);
                }
            }
        });
        fileCombo.setButtonCell(new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item);
                }
            }
        });

        Label langLabel = new Label(sourceLabelText);
        langLabel.getStyleClass().add("key-filter-lang-label");
        langLabel.getStyleClass().add("archive-open-label");
        langLabel.setStyle("-fx-font-size: " + UiScaleHelper.scaleY(15) + "px;");

        ComboBox<String> mainLangCombo = new ComboBox<>();
        mainLangCombo.getStyleClass().add("key-filter-lang-combo");
        mainLangCombo.getStyleClass().add("archive-open-combo");
        mainLangCombo.getStyleClass().add("archive-open-textured-combo");
        mainLangCombo.setVisibleRowCount(4);
        mainLangCombo.setMinWidth(UiScaleHelper.scaleX(610));
        mainLangCombo.setPrefWidth(UiScaleHelper.scaleX(610));
        mainLangCombo.setMaxWidth(Double.MAX_VALUE);
        mainLangCombo.setCellFactory(cb -> new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item);
            }
        });
        mainLangCombo.setButtonCell(new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item);
            }
        });

        VBox fileRow = new VBox(UiScaleHelper.scaleY(5), fileLabel, fileCombo);
        fileRow.getStyleClass().add("key-filter-lang-line");
        VBox.setVgrow(fileCombo, Priority.NEVER);
        HBox.setHgrow(fileCombo, Priority.ALWAYS);

        VBox langRow = new VBox(UiScaleHelper.scaleY(5), langLabel, mainLangCombo);
        langRow.getStyleClass().add("key-filter-lang-line");
        VBox.setVgrow(mainLangCombo, Priority.NEVER);
        HBox.setHgrow(mainLangCombo, Priority.ALWAYS);

        CustomAlternativeButton okButton = new CustomAlternativeButton(
                isRussianUi() ? "\u041e\u0442\u043a\u0440\u044b\u0442\u044c" : "Open",
                0.30, 0.62, 188, 54, 16
        );
        okButton.getStyleClass().add("key-filter-action-button");
        okButton.getStyleClass().add("archive-open-action-button");
        CustomAlternativeButton cancelButton = new CustomAlternativeButton(
                isRussianUi() ? "\u041e\u0442\u043c\u0435\u043d\u0430" : "Cancel",
                0.30, 0.62, 150, 54, 15
        );
        cancelButton.getStyleClass().add("key-filter-action-button");
        cancelButton.getStyleClass().add("archive-open-action-button");        final Runnable syncActionState = () -> {
            boolean hasLang = !mainLangCombo.getItems().isEmpty();
            okButton.setDisable(!hasLang);
            cancelButton.setDisable(false);
            fileCombo.setDisable(false);
            mainLangCombo.setDisable(mainLangCombo.getItems().size() <= 1);
            openErrorLabel.setVisible(openErrorLabel.isManaged());
        };
        Runnable refreshMainLanguages = () -> {
            String selectedFileOption = fileCombo.getValue();
            List<String> availableLanguages = plan.getMainLanguages(selectedFileOption);
            mainLangCombo.getItems().setAll(availableLanguages);
            mainLangCombo.setVisibleRowCount(Math.max(1, Math.min(4, availableLanguages.size())));
            if (availableLanguages.isEmpty()) {
                mainLangCombo.setValue(null);
                syncActionState.run();
                return;
            }

            String currentLang = mainLangCombo.getValue();
            String nextLang = currentLang;
            if (nextLang == null || !availableLanguages.contains(nextLang)) {
                nextLang = plan.getDefaultMainLanguage();
            }
            if (nextLang == null || !availableLanguages.contains(nextLang)) {
                nextLang = availableLanguages.contains("enUS") ? "enUS" : availableLanguages.get(0);
            }
            mainLangCombo.setValue(nextLang);
            syncActionState.run();
        };
        mainLangCombo.setOnShowing(e -> {
            if (mainLangCombo.getItems().size() <= 1) {
                Platform.runLater(mainLangCombo::hide);
            }
        });
        refreshMainLanguages.run();
        fileCombo.valueProperty().addListener((obs, oldValue, newValue) -> refreshMainLanguages.run());

        HBox buttons = new HBox(UiScaleHelper.scaleX(10), okButton, cancelButton);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        VBox panel = new VBox(
                UiScaleHelper.scaleY(12),
                titleBadge,
                flavorBar,
                selectedLabel,
                openErrorLabel,                fileRow,
                langRow,
                buttons
        );
        panel.setAlignment(Pos.TOP_LEFT);
        panel.getStyleClass().add("table-search-popup");
        panel.getStyleClass().add("archive-open-popup");
        panel.setPadding(new Insets(
                UiScaleHelper.scaleY(16),
                UiScaleHelper.scaleX(16),
                UiScaleHelper.scaleY(16),
                UiScaleHelper.scaleX(16)
        ));
        double panelWidth = UiScaleHelper.scaleX(840);
        panel.setMinWidth(panelWidth);
        panel.setPrefWidth(panelWidth);
        panel.setMaxWidth(panelWidth);
        double panelHeight = UiScaleHelper.scaleY(470);
        panel.setMinHeight(panelHeight);
        panel.setPrefHeight(panelHeight);
        panel.setMaxHeight(panelHeight);
        panel.setFillWidth(true);

        StackPane chrome = new StackPane(panel);
        chrome.getStyleClass().add("key-filter-green-outline");
        chrome.getStyleClass().add("archive-open-chrome");
        chrome.setPadding(new Insets(
                UiScaleHelper.scaleY(10),
                UiScaleHelper.scaleX(10),
                UiScaleHelper.scaleY(10),
                UiScaleHelper.scaleX(10)
        ));
        double chromeWidth = panelWidth + UiScaleHelper.scaleX(20);
        double chromeHeight = panelHeight + UiScaleHelper.scaleY(20);
        chrome.setMinWidth(chromeWidth);
        chrome.setPrefWidth(chromeWidth);
        chrome.setMaxWidth(chromeWidth);
        chrome.setMinHeight(chromeHeight);
        chrome.setPrefHeight(chromeHeight);
        chrome.setMaxHeight(chromeHeight);

        StackPane overlay = new StackPane(dim, chrome);
        overlay.getStyleClass().add("key-filter-root-pane");
        overlay.setPickOnBounds(true);
        overlay.setFocusTraversable(true);
        overlay.setViewOrder(-20_000);
        StackPane.setAlignment(chrome, Pos.CENTER);

        Runnable closeOverlay = this::removeArchivePopupOverlays;
        Runnable cancelAction = () -> {
            closeOverlay.run();
            onCancel.run();
        };
        Runnable confirmAction = () -> {
            String fileOption = fileCombo.getValue();
            String mainLang = mainLangCombo.getValue();
            if (fileOption == null || fileOption.isBlank()) return;
            if (mainLang == null || mainLang.isBlank()) {
                mainLang = sourceUi != null ? sourceUi : "enUS";
            }
            boolean opened = Boolean.TRUE.equals(onConfirm.apply(new FileOpenDialogResult(fileOption, mainLang)));
            if (opened) {
                closeOverlay.run();
                return;
            }
            String failText = isRussianUi()
                    ? "Выбранный файл не удалось распаковать. Выберите другой файл."
                    : "Selected file cannot be extracted. Choose another file.";
            openErrorLabel.setText(failText);
            openErrorLabel.setManaged(true);
            openErrorLabel.setVisible(true);
        };

        cancelButton.setOnAction(e -> cancelAction.run());
        okButton.setOnAction(e -> confirmAction.run());
        overlay.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                cancelAction.run();
                e.consume();
            } else if (e.getCode() == KeyCode.ENTER) {
                confirmAction.run();
                e.consume();
            }
        });
        dim.setOnMouseClicked(e -> e.consume());

        root.getChildren().add(overlay);
        overlay.toFront();
        syncActionState.run();
        Platform.runLater(() -> {
            overlay.requestFocus();
            fileCombo.requestFocus();
        });
    }

    private void removeArchivePopupOverlays() {
        if (root == null) {
            return;
        }
        root.getChildren().removeIf(node ->
                node.getStyleClass().contains("key-filter-root-pane")
                        && node instanceof StackPane
                        && ((StackPane) node).getChildren().stream()
                        .anyMatch(child -> child.getStyleClass().contains("key-filter-green-outline")));
    }

    private StackPane buildArchivePopupBadge(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("key-filter-title");
        label.setStyle("-fx-font-size: " + UiScaleHelper.scaleY(20) + "px;");

        StackPane shell = new StackPane(label);
        shell.getStyleClass().add("key-filter-title-shell");
        shell.setPadding(new Insets(
                UiScaleHelper.scaleY(7),
                UiScaleHelper.scaleX(14),
                UiScaleHelper.scaleY(7),
                UiScaleHelper.scaleX(14)
        ));

        Region overhead = new Region();
        overhead.getStyleClass().add("key-filter-title-overhead");
        overhead.setManaged(false);
        overhead.setMouseTransparent(true);
        overhead.prefWidthProperty().bind(shell.widthProperty());
        overhead.setPrefHeight(UiScaleHelper.scaleY(7));
        StackPane.setAlignment(overhead, Pos.TOP_CENTER);

        StackPane wrap = new StackPane(shell, overhead);
        wrap.getStyleClass().add("key-filter-title-wrap");
        wrap.setMaxWidth(Double.MAX_VALUE);
        StackPane.setAlignment(shell, Pos.CENTER_LEFT);
        return wrap;
    }

    private void closeCurrentFile(CustomTableView tableView) {
        if (tableSearchPopup != null) {
            tableSearchPopup.reset();
        }
        if (tableFullscreenMode) {
            tableFullscreenMode = false;
            applyTableFullscreenMode(tableView);
        }
        project.clear();
        tableView.clearLoadedData();
        sourceUi = null;
        translateToAll = false;
        chooseAllMode.set(false);
        fileOpened.set(false);
        fileTitleLabel.setText(localization.get("label.file.name"));
        languageDropdown.getItems().setAll(valueKey);
        languageDropdown.setValue(valueKey[2]);
        translate.resetToTranslateState();
        AppLog.info("[UI] current file closed and table cleared");
    }
}



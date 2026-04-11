package lv.lenc;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.util.Duration;

public final class KeyFilterWindow {
    private KeyFilterWindow() {}

    private static final double TREE_DIVIDER_NORMAL = 0.38;
    private static final Duration RESIZE_ANIM = Duration.millis(260);

    private static final GaussianBlur BLUR = new GaussianBlur(0);
    private static Node blurredTarget;
    private static StackPane overlayRoot;
    private static StackPane overlayHost;
    private static StackPane holder;
    private static Pane panelRoot;
    private static TreeView<NodeData> tree;

    private static boolean fullscreen = false;
    private static boolean editorFocused = false;
    private static String selectedKey;
    private static Timeline sizeAnim;

    private static final Map<String, TextArea> editors = new LinkedHashMap<>();
    private static final Map<String, TextFlow> editorPreviews = new LinkedHashMap<>();
    private static final Map<String, Label> editorLangLabels = new LinkedHashMap<>();
    private static double editorValueHeight = -1.0;
    private static final List<String> EDIT_LANGS = List.of(
            "ruRU", "deDE", "enUS", "esMX", "esES", "frFR", "itIT", "plPL", "ptBR", "koKR", "zhCN", "zhTW"
    );

    private static final class MarkupToken {
        final int start;
        final int endExclusive;
        final String raw;
        final char openChar;
        final char closeChar;
        final boolean closing;
        final boolean selfClosing;

        MarkupToken(int start, int endExclusive, String raw, char openChar, char closeChar, boolean closing, boolean selfClosing) {
            this.start = start;
            this.endExclusive = endExclusive;
            this.raw = raw;
            this.openChar = openChar;
            this.closeChar = closeChar;
            this.closing = closing;
            this.selfClosing = selfClosing;
        }
    }

    private static final class NodeData {
        final String title;
        final String fullPath;

        NodeData(String title, String fullPath) {
            this.title = title;
            this.fullPath = fullPath;
        }

        @Override
        public String toString() {
            return title;
        }
    }

    public static void show(StackPane appRoot, CustomTableView table) {
        show(appRoot, table, null);
    }

    public static void show(StackPane appRoot, CustomTableView table, LocalizationManager localization) {
        show(appRoot, table, localization, null);
    }

    public static void show(StackPane appRoot, CustomTableView table, LocalizationManager localization, String selectedKey) {
        if (appRoot == null || table == null) return;
        if (appRoot.getScene() != null) {
            UiScaleHelper.refreshFromScene(appRoot.getScene());
        }

        ensureOverlayAttached(appRoot);

        holder.getChildren().clear();
        panelRoot = buildPanel(appRoot, table, localization, selectedKey);
        holder.getChildren().add(panelRoot);

        blurredTarget = appRoot.getChildren().stream()
                .filter(node -> node != overlayRoot)
                .findFirst()
                .orElse(appRoot);
        if (blurredTarget.getEffect() == null) {
            BLUR.setRadius(0);
            blurredTarget.setEffect(BLUR);
        }

        overlayRoot.setVisible(true);
        overlayRoot.setMouseTransparent(false);
        overlayRoot.setOpacity(1.0);
        overlayRoot.setViewOrder(-10_000);
        overlayRoot.toFront();

        Platform.runLater(() -> {
            overlayRoot.toFront();
            overlayRoot.requestFocus();
            playOpen(panelRoot);
            Timeline blurIn = new Timeline(
                    new KeyFrame(Duration.ZERO, new KeyValue(BLUR.radiusProperty(), 0)),
                    new KeyFrame(Duration.millis(220), new KeyValue(BLUR.radiusProperty(), UiScaleHelper.scaleY(10), Interpolator.EASE_OUT))
            );
            blurIn.play();

            // Select the specified key if provided
            if (selectedKey != null && !selectedKey.isEmpty()) {
                selectKeyInTree(selectedKey);
            }
        });
    }

    private static void close() {
        if (overlayRoot == null) return;
        if (sizeAnim != null) sizeAnim.stop();

        Pane panel = panelRoot;
        if (panel == null) {
            hideOverlayImmediately();
            return;
        }

        FadeTransition fade = new FadeTransition(Duration.millis(180), panel);
        fade.setFromValue(Math.max(0.0, panel.getOpacity()));
        fade.setToValue(0.0);
        fade.setInterpolator(Interpolator.EASE_IN);

        ScaleTransition scale = new ScaleTransition(Duration.millis(180), panel);
        scale.setFromX(currentSize(panel.getScaleX(), 1.0, 1.0));
        scale.setFromY(currentSize(panel.getScaleY(), 1.0, 1.0));
        scale.setToX(0.985);
        scale.setToY(0.985);
        scale.setInterpolator(Interpolator.EASE_IN);

        Timeline blurOut = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(BLUR.radiusProperty(), BLUR.getRadius())),
                new KeyFrame(Duration.millis(160), new KeyValue(BLUR.radiusProperty(), 0, Interpolator.EASE_IN))
        );
        blurOut.setOnFinished(e -> {
            if (blurredTarget != null && blurredTarget.getEffect() == BLUR) {
                blurredTarget.setEffect(null);
            }
        });

        ParallelTransition exit = new ParallelTransition(fade, scale, blurOut);
        exit.setOnFinished(e -> {
            panel.setOpacity(1.0);
            panel.setScaleX(1.0);
            panel.setScaleY(1.0);
            hideOverlayImmediately();
        });
        exit.play();
    }

    private static void hideOverlayImmediately() {
        overlayRoot.setVisible(false);
        overlayRoot.setMouseTransparent(true);
    }

    private static StackPane buildOverlay(StackPane appRoot) {
        Region dim = new Region();
        dim.getStyleClass().add("key-filter-overlay-dim");
        dim.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        holder = new StackPane();
        holder.setPickOnBounds(false);
        StackPane.setAlignment(holder, Pos.CENTER);

        StackPane overlay = new StackPane(dim, holder);
        overlay.setVisible(false);
        overlay.setMouseTransparent(true);
        overlay.setFocusTraversable(true);
        overlay.setViewOrder(-10_000);
        bindOverlayToHost(overlay, appRoot);

        dim.setOnMouseClicked(e -> e.consume());
        overlay.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                close();
                e.consume();
            }
        });
        return overlay;
    }

    private static void ensureOverlayAttached(StackPane appRoot) {
        if (overlayRoot == null) {
            overlayRoot = buildOverlay(appRoot);
            overlayHost = appRoot;
            appRoot.getChildren().add(overlayRoot);
            return;
        }
        if (overlayHost != appRoot) {
            if (overlayHost != null) {
                overlayHost.getChildren().remove(overlayRoot);
            }
            bindOverlayToHost(overlayRoot, appRoot);
            appRoot.getChildren().add(overlayRoot);
            overlayHost = appRoot;
        }
    }

    private static void bindOverlayToHost(StackPane overlay, StackPane appRoot) {
        overlay.prefWidthProperty().unbind();
        overlay.prefHeightProperty().unbind();
        overlay.prefWidthProperty().bind(appRoot.widthProperty());
        overlay.prefHeightProperty().bind(appRoot.heightProperty());
    }

    private static void playOpen(Pane panel) {
        panel.setOpacity(0.0);
        panel.setScaleX(0.972);
        panel.setScaleY(0.972);
        panel.setTranslateY(UiScaleHelper.scaleY(8));

        FadeTransition fade = new FadeTransition(Duration.millis(220), panel);
        fade.setFromValue(0.0);
        fade.setToValue(1.0);
        fade.setInterpolator(Interpolator.EASE_OUT);

        ScaleTransition scale = new ScaleTransition(Duration.millis(220), panel);
        scale.setFromX(0.972);
        scale.setToX(1.0);
        scale.setFromY(0.972);
        scale.setToY(1.0);
        scale.setInterpolator(Interpolator.EASE_OUT);

        Timeline lift = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(panel.translateYProperty(), UiScaleHelper.scaleY(8))),
                new KeyFrame(Duration.millis(220), new KeyValue(panel.translateYProperty(), 0, Interpolator.EASE_OUT))
        );

        new ParallelTransition(fade, scale, lift).play();
    }

    private static Pane buildPanel(StackPane appRoot, CustomTableView table, LocalizationManager localization, String initialKey) {
        editors.clear();
        editorPreviews.clear();
        editorLangLabels.clear();
        fullscreen = false;
        editorFocused = false;
        selectedKey = initialKey;
        if (editorValueHeight <= 0) {
            editorValueHeight = UiScaleHelper.scaleY(102);
        }

        double rootW = appRoot.getWidth() > 1 ? appRoot.getWidth() : UiScaleHelper.SCREEN_WIDTH;
        double rootH = appRoot.getHeight() > 1 ? appRoot.getHeight() : UiScaleHelper.SCREEN_HEIGHT;

        // Preferred popup size (scaled from Full HD), clamped to actual root size.
        double preferredWidth = UiScaleHelper.scaleX(1500);
        double preferredHeight = UiScaleHelper.scaleY(880);
        double baseWidth = clamp(
                preferredWidth,
                UiScaleHelper.scaleX(900),
                Math.max(UiScaleHelper.scaleX(900), rootW - UiScaleHelper.scaleX(16))
        );
        double baseHeight = clamp(
                preferredHeight,
                UiScaleHelper.scaleY(560),
                Math.max(UiScaleHelper.scaleY(560), rootH - UiScaleHelper.scaleY(16))
        );

        Region frame = new Region();
        frame.getStyleClass().add("key-filter-green-outline");
        frame.setPrefSize(baseWidth, baseHeight);
        frame.setMinSize(baseWidth, baseHeight);
        frame.setMaxSize(baseWidth, baseHeight);
        frame.setStyle("-fx-background-color: transparent;");

        StackPane mainTitle = buildHeaderBadge(localize(localization, "keyfilter.title", "Key Filter", "Key Filter"), false);

        CustomAlternativeButton focusButton = new CustomAlternativeButton(
                localize(localization, "keyfilter.focusEditor", "Focus editor", "Focus editor"),
                0.6, 0.8, 245, 72, 15
        );
        styleFilterButton(focusButton);
        setFocusButtonText(focusButton, localization, false);

        TextField selectedPath = new TextField();
        selectedPath.setPromptText(localize(localization, "keyfilter.prompt", "Select a branch or exact key", "Select a branch or exact key"));
        selectedPath.setEditable(false);
        selectedPath.getStyleClass().add("key-filter-selected-path");

        tree = new TreeView<>();
        tree.setShowRoot(false);
        tree.getStyleClass().add("key-filter-tree");
        tree.setCellFactory(view -> new TreeCell<>() {
            @Override
            protected void updateItem(NodeData item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.title);
                if (empty || item == null) {
                    setTranslateY(0.0);
                    setStyle("");
                    return;
                }
                double scale = UiScaleHelper.scale(1.0);
                double padTop = scale >= 0.98
                        ? Math.max(UiScaleHelper.scaleY(0.8), 0.6)
                        : Math.max(UiScaleHelper.scaleY(0.45), 0.35);
                double padBottom = scale >= 0.98
                        ? Math.max(UiScaleHelper.scaleY(1.4), 1.0)
                        : Math.max(UiScaleHelper.scaleY(0.95), 0.8);
                double padX = scale >= 0.98
                        ? Math.max(UiScaleHelper.scaleX(7.5), 6.0)
                        : Math.max(UiScaleHelper.scaleX(5.0), 4.0);
                double lift = scale >= 0.98 ? Math.max(UiScaleHelper.scaleY(0.7), 0.5) : 0.0;
                setTranslateY(-lift);
                setStyle("-fx-padding: " + padTop + "px " + padX + "px " + padBottom + "px " + padX + "px; -fx-text-overrun: ellipsis;");
            }
        });
        applyTreeTypography(tree);
        TreeItem<NodeData> root = new TreeItem<>(new NodeData("All keys", ""));
        root.setExpanded(true);
        buildTree(root, table);
        tree.setRoot(root);

        Label leftHeader = new Label(localize(localization, "keyfilter.filterWindow", "Filter window", "Filter window"));
        leftHeader.getStyleClass().add("key-filter-section-title");

        Label rightHeader = new Label(localize(localization, "keyfilter.editorWindow", "Editor window", "Editor window"));
        rightHeader.getStyleClass().add("key-filter-section-title");

        StackPane editorTitle = buildHeaderBadge(localize(localization, "keyfilter.editorHeader", "Text Editor", "Text Editor"), true);

        Label selectionInfo = new Label(localize(localization, "keyfilter.selectionHint", "Pick an exact key from the tree.", "Pick an exact key from the tree."));
        selectionInfo.getStyleClass().add("key-filter-status");
        selectionInfo.setWrapText(true);

        Label emptyState = new Label(localize(localization, "keyfilter.noSelection", "No exact key selected yet. Editing is disabled.", "No exact key selected yet. Editing is disabled."));
        emptyState.getStyleClass().add("key-filter-empty-state");
        emptyState.setWrapText(true);

        VBox treePane = new VBox(UiScaleHelper.scaleY(10), leftHeader, selectedPath, tree);
        treePane.getStyleClass().add("key-filter-tree-pane");
        VBox.setVgrow(tree, Priority.ALWAYS);

        VBox editorPane = new VBox(UiScaleHelper.scaleY(10), editorTitle, rightHeader, selectionInfo, emptyState);
        editorPane.getStyleClass().add("key-filter-editor-shell");
        editorPane.getStyleClass().add("key-filter-editor-pane");
        for (String lang : EDIT_LANGS) {
            editorPane.getChildren().add(buildEditorBlock(lang));
        }
        applyEditorTypography();

        ScrollPane editorScroll = new ScrollPane(editorPane);
        editorScroll.getStyleClass().add("key-filter-editors-scroll");
        editorScroll.setFitToWidth(true);
        editorScroll.setFitToHeight(false);
        editorScroll.setPannable(false);
        editorScroll.addEventFilter(ScrollEvent.SCROLL, e -> {
            if (redirectScrollToEditor(editorScroll, e)) {
                e.consume();
            }
        });
        VBox.setVgrow(editorScroll, Priority.ALWAYS);

        SplitPane split = new SplitPane(treePane, editorScroll);
        split.getStyleClass().add("key-filter-split-pane");
        split.setDividerPositions(TREE_DIVIDER_NORMAL);

        CustomAlternativeButton applyButton = new CustomAlternativeButton(
                localize(localization, "keyfilter.apply", "Apply filter", "Apply filter"),
                0.6, 0.8, 250, 70, 15
        );
        styleFilterButton(applyButton);
        applyButton.setOnAction(e -> {
            String path = selectedPath.getText();
            table.applyKeyPrefixFilter((path == null || path.isBlank()) ? null : path.trim());
            refreshTree(tree, table);
        });

        CustomAlternativeButton resetButton = new CustomAlternativeButton(
                localize(localization, "keyfilter.reset", "Reset filter", "Reset filter"),
                0.6, 0.8, 250, 70, 15
        );
        styleFilterButton(resetButton);
        resetButton.setOnAction(e -> {
            table.clearKeyFilter();
            selectedPath.clear();
            selectedKey = null;
            clearEditors();
            setEditorsEnabled(false);
            emptyState.setVisible(true);
            emptyState.setManaged(true);
            selectionInfo.setText(localize(localization, "keyfilter.selectionHint", "Pick an exact key from the tree.", "Pick an exact key from the tree."));
            refreshTree(tree, table);
        });

        CustomAlternativeButton fullscreenButton = new CustomAlternativeButton(
                localize(localization, "keyfilter.fullScreen", "Full screen", "Full screen"),
                0.6, 0.8, 232, 70, 15
        );
        styleFilterButton(fullscreenButton);

        CustomAlternativeButton closeBottomButton = new CustomAlternativeButton(
                localize(localization, "keyfilter.close", "Close", "Close"),
                0.6, 0.8, 214, 70, 15
        );
        styleFilterButton(closeBottomButton);
        closeBottomButton.setOnAction(e -> close());

        CustomAlternativeButton saveAllButton = new CustomAlternativeButton(
                localize(localization, "keyfilter.saveAll", "Save all", "Save all"),
                0.6, 0.8, 250, 70, 15
        );
        styleFilterButton(saveAllButton);
        saveAllButton.setOnAction(e -> saveCurrentEditorToRow(table));

        CustomAlternativeButton showInTableButton = new CustomAlternativeButton(
                localize(localization, "keyfilter.showInTable", "Show in table", "Show in table"),
                0.6, 0.8, 300, 70, 15
        );
        styleFilterButton(showInTableButton);
        showInTableButton.setOnAction(e -> showSelectedInTable(table));

        HBox topBar = new HBox(UiScaleHelper.scaleX(12));
        topBar.getStyleClass().add("key-filter-header-row");
        Region topSpacer = new Region();
        HBox.setHgrow(topSpacer, Priority.ALWAYS);
        topBar.getChildren().addAll(mainTitle, topSpacer, focusButton);
        topBar.setAlignment(Pos.CENTER_LEFT);

        HBox midActions = new HBox(UiScaleHelper.scaleX(10), saveAllButton, showInTableButton);
        midActions.setAlignment(Pos.CENTER_RIGHT);
        midActions.setMaxWidth(Double.MAX_VALUE);

        HBox bottomActions = new HBox(UiScaleHelper.scaleX(10), applyButton, resetButton, fullscreenButton, closeBottomButton);
        bottomActions.setAlignment(Pos.CENTER_RIGHT);
        bottomActions.setMaxWidth(Double.MAX_VALUE);

        VBox content = new VBox(UiScaleHelper.scaleY(10), topBar, split, midActions, bottomActions);
        content.setPadding(new Insets(UiScaleHelper.scaleY(18), UiScaleHelper.scaleX(20), UiScaleHelper.scaleY(16), UiScaleHelper.scaleX(20)));
        VBox.setVgrow(split, Priority.ALWAYS);

        focusButton.setOnAction(e -> {
            editorFocused = !editorFocused;
            split.setDividerPositions(editorFocused ? 0.18 : TREE_DIVIDER_NORMAL);
            treePane.setOpacity(editorFocused ? 0.18 : 1.0);
            setFocusButtonText(focusButton, localization, editorFocused);
            if (editorFocused) {
                selectionInfo.setText(localize(localization, "keyfilter.editorOnly", "Editor mode: the left tree is condensed.", "Editor mode: the left tree is condensed."));
            } else {
                updateSelectionInfo(selectionInfo, localization, selectedKey);
            }
            Platform.runLater(editorScroll::requestFocus);
        });

        tree.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV == null || newV.getValue() == null) {
                selectedPath.clear();
                selectedKey = null;
                clearEditors();
                setEditorsEnabled(false);
                emptyState.setVisible(true);
                emptyState.setManaged(true);
                selectionInfo.setText(localize(localization, "keyfilter.selectionHint", "Pick an exact key from the tree.", "Pick an exact key from the tree."));
                return;
            }

            NodeData value = newV.getValue();
            selectedPath.setText(value.fullPath);

            LocalizationData row = findRow(table, value.fullPath);
            if (row == null) {
                selectedKey = null;
                clearEditors();
                setEditorsEnabled(false);
                emptyState.setVisible(true);
                emptyState.setManaged(true);
                selectionInfo.setText(localize(localization, "keyfilter.branchOnly", "Branch selected. Pick an exact key for editing.", "Branch selected. Pick an exact key for editing."));
                return;
            }

            selectedKey = value.fullPath;
            emptyState.setVisible(false);
            emptyState.setManaged(false);
            setEditorsEnabled(true);
            loadRowIntoEditors(row);
            updateSelectionInfo(selectionInfo, localization, selectedKey);
        });

        if (table.getAllKeysForFilter().isEmpty()) {
            clearEditors();
            setEditorsEnabled(false);
            emptyState.setVisible(true);
            emptyState.setManaged(true);
            selectionInfo.setText(localize(localization, "keyfilter.selectionHint", "Pick an exact key from the tree.", "Pick an exact key from the tree."));
        }

        CustomCloseButton closeTopButton = new CustomCloseButton();
        closeTopButton.setOnAction(e -> close());
        double buttonSize = UiScaleHelper.SCREEN_HEIGHT * (26.0 / 1080.0);
        closeTopButton.setLayoutX(baseWidth - buttonSize - UiScaleHelper.scaleX(10));
        closeTopButton.setLayoutY(UiScaleHelper.scaleY(10));

        final double minPopupW = UiScaleHelper.scaleX(900);
        final double minPopupH = UiScaleHelper.scaleY(560);
        final double[] windowedWidth = new double[] { baseWidth };
        final double[] windowedHeight = new double[] { baseHeight };

        BorderPane panelLayout = new BorderPane();
        panelLayout.getStyleClass().add("key-filter-panel");
        panelLayout.setPrefSize(baseWidth, baseHeight);
        panelLayout.setMinSize(baseWidth, baseHeight);
        panelLayout.setMaxSize(baseWidth, baseHeight);
        panelLayout.setCenter(content);

        Region orangeOutline = new Region();
        orangeOutline.getStyleClass().add("key-filter-orange-outline");
        orangeOutline.setPrefSize(baseWidth - UiScaleHelper.scaleX(28), baseHeight - UiScaleHelper.scaleY(28));
        orangeOutline.setMinSize(orangeOutline.getPrefWidth(), orangeOutline.getPrefHeight());
        orangeOutline.setMaxSize(orangeOutline.getPrefWidth(), orangeOutline.getPrefHeight());
        orangeOutline.setLayoutX(UiScaleHelper.scaleX(14));
        orangeOutline.setLayoutY(UiScaleHelper.scaleY(14));

        Pane rootPane = new Pane();
        rootPane.getStyleClass().add("key-filter-root-pane");
        rootPane.setPrefSize(baseWidth, baseHeight);
        rootPane.setMinSize(baseWidth, baseHeight);
        rootPane.setMaxSize(baseWidth, baseHeight);
        rootPane.getChildren().addAll(frame, orangeOutline, panelLayout, closeTopButton);
        closeTopButton.getStyleClass().add("key-filter-close");

        rootPane.layoutBoundsProperty().addListener((obs, oldVal, b) -> {
            closeTopButton.setLayoutX(b.getWidth() - closeTopButton.getWidth() + UiScaleHelper.scaleX(2));
            closeTopButton.setLayoutY(-UiScaleHelper.scaleY(3));
        });

        fullscreenButton.setOnAction(e -> {
            fullscreen = !fullscreen;
            double maxPopupW = Math.max(minPopupW, appRoot.getWidth() - UiScaleHelper.scaleX(16));
            double maxPopupH = Math.max(minPopupH, appRoot.getHeight() - UiScaleHelper.scaleY(16));
            double targetW = fullscreen
                    ? maxPopupW
                    : clamp(windowedWidth[0], minPopupW, maxPopupW);
            double targetH = fullscreen
                    ? maxPopupH
                    : clamp(windowedHeight[0], minPopupH, maxPopupH);
            animatePopupSize(rootPane, frame, orangeOutline, panelLayout, targetW, targetH);
            fullscreenButton.setText(localize(
                    localization,
                    fullscreen ? "keyfilter.windowed" : "keyfilter.fullScreen",
                    fullscreen ? "Windowed mode" : "Full screen",
                    fullscreen ? "Windowed mode" : "Full screen"
            ));
        });

        appRoot.widthProperty().addListener((obs, oldVal, newVal) -> {
            if (appRoot.getScene() != null) {
                UiScaleHelper.refreshFromScene(appRoot.getScene());
            }
            applyTreeTypography(tree);
            if (fullscreen) {
                double targetW = Math.max(minPopupW, appRoot.getWidth() - UiScaleHelper.scaleX(16));
                double targetH = Math.max(minPopupH, appRoot.getHeight() - UiScaleHelper.scaleY(16));
                applyPopupSize(rootPane, frame, orangeOutline, panelLayout, targetW, targetH);
            }
        });
        appRoot.heightProperty().addListener((obs, oldVal, newVal) -> {
            if (appRoot.getScene() != null) {
                UiScaleHelper.refreshFromScene(appRoot.getScene());
            }
            applyTreeTypography(tree);
            if (fullscreen) {
                double targetW = Math.max(minPopupW, appRoot.getWidth() - UiScaleHelper.scaleX(16));
                double targetH = Math.max(minPopupH, appRoot.getHeight() - UiScaleHelper.scaleY(16));
                applyPopupSize(rootPane, frame, orangeOutline, panelLayout, targetW, targetH);
            }
        });

        return rootPane;
    }

    private static void animatePopupSize(Pane rootPane, Region frame, Region orangeOutline, BorderPane panelLayout, double targetW, double targetH) {
        if (rootPane == null || frame == null || orangeOutline == null || panelLayout == null) return;
        if (sizeAnim != null) sizeAnim.stop();

        double fromW = currentSize(rootPane.getWidth(), rootPane.getPrefWidth(), targetW);
        double fromH = currentSize(rootPane.getHeight(), rootPane.getPrefHeight(), targetH);

        DoubleProperty progress = new SimpleDoubleProperty(0.0);
        progress.addListener((obs, oldV, newV) -> {
            double p = newV.doubleValue();
            double w = fromW + (targetW - fromW) * p;
            double h = fromH + (targetH - fromH) * p;
            applyPopupSize(rootPane, frame, orangeOutline, panelLayout, w, h);
        });

        sizeAnim = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(progress, 0.0)),
                new KeyFrame(RESIZE_ANIM, new KeyValue(progress, 1.0, Interpolator.EASE_BOTH))
        );
        sizeAnim.setOnFinished(e -> applyPopupSize(rootPane, frame, orangeOutline, panelLayout, targetW, targetH));
        sizeAnim.play();
    }

    private static double currentSize(double current, double pref, double fallback) {
        if (current > 1.0) return current;
        if (pref > 1.0) return pref;
        return fallback;
    }

    private static double clamp(double value, double min, double max) {
        if (max < min) return min;
        return Math.max(min, Math.min(max, value));
    }

    private static void applyPopupSize(Pane rootPane, Region frame, Region orangeOutline, BorderPane panelLayout, double width, double height) {
        if (rootPane == null || frame == null || orangeOutline == null || panelLayout == null) return;

        rootPane.setPrefSize(width, height);
        rootPane.setMinSize(width, height);
        rootPane.setMaxSize(width, height);

        frame.setPrefSize(width, height);
        frame.setMinSize(width, height);
        frame.setMaxSize(width, height);

        panelLayout.setPrefSize(width, height);
        panelLayout.setMinSize(width, height);
        panelLayout.setMaxSize(width, height);

        double insetX = UiScaleHelper.scaleX(14);
        double insetY = UiScaleHelper.scaleY(14);
        double outlineW = Math.max(0.0, width - UiScaleHelper.scaleX(28));
        double outlineH = Math.max(0.0, height - UiScaleHelper.scaleY(28));
        orangeOutline.setPrefSize(outlineW, outlineH);
        orangeOutline.setMinSize(outlineW, outlineH);
        orangeOutline.setMaxSize(outlineW, outlineH);
        orangeOutline.setLayoutX(insetX);
        orangeOutline.setLayoutY(insetY);
    }

    private static StackPane buildHeaderBadge(String text, boolean compact) {
        Label label = new Label(text);
        label.getStyleClass().add("key-filter-title");
        if (compact) label.getStyleClass().add("key-filter-editor-badge-title");

        StackPane shell = new StackPane(label);
        shell.getStyleClass().add("key-filter-title-shell");
        if (compact) shell.getStyleClass().add("key-filter-editor-badge-shell");
        shell.setPadding(new Insets(UiScaleHelper.scaleY(8), UiScaleHelper.scaleX(14), UiScaleHelper.scaleY(8), UiScaleHelper.scaleX(14)));

        Region overhead = new Region();
        overhead.getStyleClass().add("key-filter-title-overhead");
        overhead.setManaged(false);
        overhead.setMouseTransparent(true);
        overhead.prefWidthProperty().bind(shell.widthProperty());
        overhead.setPrefHeight(UiScaleHelper.scaleY(7));
        StackPane.setAlignment(overhead, Pos.TOP_CENTER);

        StackPane wrap = new StackPane(shell, overhead);
        wrap.getStyleClass().add("key-filter-title-wrap");
        if (compact) wrap.getStyleClass().add("key-filter-editor-badge-wrap");
        return wrap;
    }

    private static void setFocusButtonText(CustomAlternativeButton focusButton, LocalizationManager localization, boolean focused) {
        if (focusButton == null) return;
        focusButton.setText(localize(
                localization,
                focused ? "keyfilter.splitView" : "keyfilter.focusEditor",
                focused ? "Split view" : "Focus editor",
                focused ? "Split view" : "Focus editor"
        ));
    }

    private static void styleFilterButton(CustomAlternativeButton button) {
        if (button == null) return;
        button.getStyleClass().add("key-filter-action-button");
        button.setEffect(null);
    }

    private static void updateSelectionInfo(Label info, LocalizationManager localization, String key) {
        if (info == null) return;
        if (key == null || key.isBlank()) {
            info.setText(localize(localization, "keyfilter.selectionHint", "Pick an exact key from the tree.", "Pick an exact key from the tree."));
            return;
        }
        String template = localize(
                localization,
                "keyfilter.selectedInfo",
                "Exact key selected: %s. You can edit texts for all languages.",
                "Exact key selected: %s. You can edit texts for all languages."
        );
        info.setText(String.format(Locale.ROOT, template, key));
    }

    private static Pane buildEditorBlock(String lang) {
        Label label = new Label(lang);
        label.getStyleClass().add("key-filter-edit-lang");
        editorLangLabels.put(lang, label);

        TextFlow previewFlow = new TextFlow();
        previewFlow.getStyleClass().add("key-filter-preview-flow");
        previewFlow.setLineSpacing(0);
        editorPreviews.put(lang, previewFlow);

        ScrollPane previewScroll = new ScrollPane(previewFlow);
        previewScroll.getStyleClass().add("key-filter-preview-scroll");
        previewScroll.setFitToWidth(true);
        previewScroll.setFitToHeight(true);
        previewScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        previewScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        previewScroll.setPannable(false);
        previewScroll.setFocusTraversable(false);
        previewScroll.setMouseTransparent(true);
        previewScroll.setVisible(false);
        previewScroll.setManaged(false);
        previewScroll.setCursor(CustomCursorManager.defaultCursor());

        TextArea area = new TextArea();
        area.getStyleClass().add("key-filter-edit-area");
        area.setCursor(CustomCursorManager.defaultCursor());
        area.setWrapText(true);
        area.setMinHeight(UiScaleHelper.scaleY(96));
        area.setPrefHeight(editorValueHeight);
        area.setPromptText("Text for " + lang);
        editors.put(lang, area);

        area.textProperty().addListener((obs, oldText, newText) -> {
            updateXmlHighlightMode(area, previewScroll, previewFlow, newText);
            renderXmlSyntax(previewFlow, newText);
            syncPreviewScroll(area, previewScroll, previewFlow);
        });

        area.scrollTopProperty().addListener((obs, oldVal, newVal) ->
                syncPreviewScroll(area, previewScroll, previewFlow)
        );

        area.focusedProperty().addListener((obs, wasFocused, isFocused) ->
                updateXmlHighlightMode(area, previewScroll, previewFlow, area.getText())
        );
        area.selectedTextProperty().addListener((obs, oldSel, newSel) ->
                updateXmlHighlightMode(area, previewScroll, previewFlow, area.getText())
        );

        previewScroll.viewportBoundsProperty().addListener((obs, oldB, newB) -> {
            if (newB != null) {
                previewFlow.setPrefWidth(Math.max(0, newB.getWidth()));
                syncPreviewScroll(area, previewScroll, previewFlow);
            }
        });

        Platform.runLater(() -> {
            updateXmlHighlightMode(area, previewScroll, previewFlow, area.getText());
            renderXmlSyntax(previewFlow, area.getText());
            syncPreviewScroll(area, previewScroll, previewFlow);
        });

        Label resizeGrip = new Label("///");
        resizeGrip.getStyleClass().add("key-filter-resize-grip");
        resizeGrip.setCursor(CustomCursorManager.dragFrameCursor());
        resizeGrip.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        resizeGrip.setPickOnBounds(true);
        resizeGrip.setFocusTraversable(false);

        StackPane editStack = new StackPane(area, previewScroll, resizeGrip);
        StackPane.setAlignment(area, Pos.TOP_LEFT);
        StackPane.setAlignment(previewScroll, Pos.TOP_LEFT);
        StackPane.setAlignment(resizeGrip, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(resizeGrip, new Insets(0, UiScaleHelper.scaleX(5), UiScaleHelper.scaleY(5), 0));
        area.setPadding(Insets.EMPTY);
        editStack.getStyleClass().add("key-filter-edit-stack");
        editStack.setMinHeight(UiScaleHelper.scaleY(96));
        editStack.setPrefHeight(editorValueHeight);
        final double[] dragStartY = new double[1];
        final double[] dragStartH = new double[1];
        final boolean[] valueResizeActive = new boolean[1];
        final double resizeBandY = UiScaleHelper.scaleY(18);
        final double resizeBandX = UiScaleHelper.scaleX(34);

        editStack.setOnMouseMoved(e -> {
            boolean nearBottomRight =
                    e.getX() >= Math.max(0, editStack.getWidth() - resizeBandX) &&
                    e.getY() >= Math.max(0, editStack.getHeight() - resizeBandY);
            editStack.setCursor(nearBottomRight ? CustomCursorManager.dragFrameCursor() : CustomCursorManager.defaultCursor());
        });
        editStack.setOnMousePressed(e -> {
            boolean nearBottomRight =
                    e.getX() >= Math.max(0, editStack.getWidth() - resizeBandX) &&
                    e.getY() >= Math.max(0, editStack.getHeight() - resizeBandY);
            if (!nearBottomRight) {
                valueResizeActive[0] = false;
                return;
            }
            valueResizeActive[0] = true;
            dragStartY[0] = e.getScreenY();
            dragStartH[0] = editorValueHeight;
            e.consume();
        });
        editStack.setOnMouseDragged(e -> {
            if (!valueResizeActive[0]) return;
            double dy = e.getScreenY() - dragStartY[0];
            double next = clamp(
                    dragStartH[0] + dy,
                    UiScaleHelper.scaleY(90),
                    UiScaleHelper.scaleY(520)
            );
            editorValueHeight = next;
            applyEditorValueHeight();
            e.consume();
        });
        editStack.setOnMouseReleased(e -> valueResizeActive[0] = false);
        resizeGrip.setOnMousePressed(e -> {
            valueResizeActive[0] = true;
            dragStartY[0] = e.getScreenY();
            dragStartH[0] = editorValueHeight;
            e.consume();
        });
        resizeGrip.setOnMouseDragged(e -> {
            if (!valueResizeActive[0]) return;
            double dy = e.getScreenY() - dragStartY[0];
            double next = clamp(
                    dragStartH[0] + dy,
                    UiScaleHelper.scaleY(90),
                    UiScaleHelper.scaleY(520)
            );
            editorValueHeight = next;
            applyEditorValueHeight();
            e.consume();
        });
        resizeGrip.setOnMouseReleased(e -> {
            valueResizeActive[0] = false;
            e.consume();
        });

        VBox box = new VBox(UiScaleHelper.scaleY(1), label, editStack);
        box.getStyleClass().add("key-filter-edit-block");
        return box;
    }

    private static void updateXmlHighlightMode(
            TextArea area,
            ScrollPane previewScroll,
            TextFlow previewFlow,
            String text
    ) {
        if (area == null || previewScroll == null || previewFlow == null) {
            return;
        }
        int selLen = area.getSelection() == null ? 0 : area.getSelection().getLength();
        boolean hasSelection = selLen > 0;
        boolean showOverlay = containsRichMarkup(text) && !hasSelection && !area.isFocused();
        if (showOverlay) {
            if (!area.getStyleClass().contains("key-filter-edit-overlay")) {
                area.getStyleClass().add("key-filter-edit-overlay");
            }
            previewScroll.setVisible(true);
            previewScroll.setManaged(true);
        } else {
            area.getStyleClass().remove("key-filter-edit-overlay");
            previewScroll.setVisible(false);
            previewScroll.setManaged(false);
        }
    }

    static boolean containsRichMarkup(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return findNextMarkupToken(text, 0) != null;
    }

    private static void syncPreviewScroll(TextArea area, ScrollPane previewScroll, TextFlow previewFlow) {
        if (area == null || previewScroll == null || previewFlow == null) return;

        double contentH = previewFlow.getBoundsInLocal().getHeight();
        double viewportH = previewScroll.getViewportBounds().getHeight();
        double max = Math.max(1.0, contentH - viewportH);
        double value = area.getScrollTop() / max;
        previewScroll.setVvalue(Math.max(0.0, Math.min(1.0, value)));
    }

    private static boolean redirectScrollToEditor(ScrollPane editorScroll, ScrollEvent event) {
        if (event == null || Math.abs(event.getDeltaY()) < 0.01) {
            return false;
        }
        Node target = event.getPickResult() == null ? null : event.getPickResult().getIntersectedNode();
        TextArea area = findEditorArea(target);
        if (area == null) {
            return false;
        }
        if (!area.isFocused()) {
            return scrollEditorList(editorScroll, event.getDeltaY());
        }
        if (scrollEditorTextArea(area, event.getDeltaY())) {
            return true;
        }
        return scrollEditorList(editorScroll, event.getDeltaY());
    }

    private static TextArea findEditorArea(Node node) {
        Node current = node;
        while (current != null) {
            if (current instanceof TextArea area) {
                return area;
            }
            if (current instanceof StackPane stack && stack.getStyleClass().contains("key-filter-edit-stack")) {
                for (Node child : stack.getChildren()) {
                    if (child instanceof TextArea area) {
                        return area;
                    }
                }
            }
            current = current.getParent();
        }
        return null;
    }

    private static boolean scrollEditorTextArea(TextArea area, double deltaY) {
        if (area == null || Math.abs(deltaY) < 0.01) {
            return false;
        }
        double before = area.getScrollTop();
        area.setScrollTop(Math.max(0.0, before - deltaY));
        return Math.abs(area.getScrollTop() - before) > 0.5;
    }

    private static boolean scrollEditorList(ScrollPane editorScroll, double deltaY) {
        if (editorScroll == null || Math.abs(deltaY) < 0.01) {
            return false;
        }
        Node content = editorScroll.getContent();
        if (content == null) {
            return false;
        }

        double viewportH = editorScroll.getViewportBounds().getHeight();
        double contentH = content.getLayoutBounds().getHeight();
        double max = Math.max(0.0, contentH - viewportH);
        if (max <= 0.5) {
            return false;
        }

        double before = editorScroll.getVvalue();
        double beforePx = before * max;
        double nextPx = clamp(beforePx - deltaY, 0.0, max);
        double next = nextPx / max;
        editorScroll.setVvalue(next);
        return Math.abs(editorScroll.getVvalue() - before) > 0.0001;
    }

    private static void renderXmlSyntax(TextFlow flow, String raw) {
        if (flow == null) return;
        flow.getChildren().clear();

        String text = raw == null ? "" : raw;
        if (text.isEmpty()) return;

        final String normalColor = "#97b1ae";
        final String tagNameColor = "#1f58c9";
        final String attrColor = "#2f7dff";
        final String valueColor = "#75d7ff";
        final String taggedInnerColor = "#75d7ff";

        int pos = 0;
        int openTagDepth = 0;
        while (pos < text.length()) {
            MarkupToken token = findNextMarkupToken(text, pos);
            if (token == null) {
                appendColoredText(
                        flow,
                        text.substring(pos),
                        openTagDepth > 0 ? taggedInnerColor : normalColor,
                        false
                );
                break;
            }

            if (token.start > pos) {
                appendColoredText(
                        flow,
                        text.substring(pos, token.start),
                        openTagDepth > 0 ? taggedInnerColor : normalColor,
                        false
                );
            }

            if (token.closing && openTagDepth > 0) {
                openTagDepth--;
            }
            appendTagSyntax(flow, token.raw, token.openChar, token.closeChar, tagNameColor, attrColor, valueColor);
            if (!token.closing && !token.selfClosing) {
                openTagDepth++;
            }
            pos = token.endExclusive;
        }
    }

    private static void appendTagSyntax(
            TextFlow flow,
            String tag,
            char openChar,
            char closeChar,
            String tagNameColor,
            String attrColor,
            String valueColor
    ) {
        if (tag == null || tag.isEmpty()) return;

        int i = 0;
        if (tag.charAt(i) == openChar) {
            appendColoredText(flow, String.valueOf(openChar), tagNameColor, true);
            i++;
        }

        if (i < tag.length() && tag.charAt(i) == '/') {
            appendColoredText(flow, "/", tagNameColor, true);
            i++;
        }

        int nameStart = i;
        while (i < tag.length() && isTagNameChar(tag.charAt(i))) i++;
        if (i > nameStart) {
            appendColoredText(flow, tag.substring(nameStart, i), tagNameColor, true);
        }

        while (i < tag.length()) {
            char ch = tag.charAt(i);

            if (ch == closeChar) {
                appendColoredText(flow, String.valueOf(closeChar), tagNameColor, true);
                i++;
                continue;
            }

            if (ch == '"' || ch == '\'') {
                char quote = ch;
                appendColoredText(flow, String.valueOf(quote), attrColor, true);
                i++;
                int valueStart = i;
                while (i < tag.length() && tag.charAt(i) != quote) i++;
                if (i > valueStart) {
                    appendColoredText(flow, tag.substring(valueStart, i), valueColor, false);
                }
                if (i < tag.length()) {
                    appendColoredText(flow, String.valueOf(quote), attrColor, true);
                    i++;
                }
                continue;
            }

            if (isAttrNameChar(ch)) {
                int attrStart = i;
                while (i < tag.length() && isAttrNameChar(tag.charAt(i))) i++;
                appendColoredText(flow, tag.substring(attrStart, i), attrColor, true);
                continue;
            }

            appendColoredText(flow, String.valueOf(ch), attrColor, true);
            i++;
        }
    }

    private static boolean isTagNameChar(char ch) {
        return Character.isLetterOrDigit(ch) || ch == '_' || ch == ':' || ch == '-' || ch == '.';
    }

    private static MarkupToken findNextMarkupToken(String text, int fromIndex) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        int start = Math.max(0, fromIndex);
        for (int i = start; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch != '<' && ch != '[') {
                continue;
            }
            if (i > 0 && text.charAt(i - 1) == '\\') {
                continue;
            }
            MarkupToken token = parseMarkupToken(text, i);
            if (token != null) {
                return token;
            }
        }
        return null;
    }

    private static MarkupToken parseMarkupToken(String text, int start) {
        if (text == null || start < 0 || start >= text.length()) {
            return null;
        }

        char openChar = text.charAt(start);
        char closeChar;
        if (openChar == '<') {
            closeChar = '>';
        } else if (openChar == '[') {
            closeChar = ']';
        } else {
            return null;
        }

        int end = findMarkupTokenEnd(text, start, closeChar);
        if (end < 0) {
            return null;
        }

        String raw = text.substring(start, end + 1);
        int i = 1;
        while (i < raw.length() - 1 && Character.isWhitespace(raw.charAt(i))) {
            i++;
        }
        if (i >= raw.length() - 1) {
            return null;
        }

        boolean closing = false;
        if (raw.charAt(i) == '/') {
            closing = true;
            i++;
            while (i < raw.length() - 1 && Character.isWhitespace(raw.charAt(i))) {
                i++;
            }
        }
        if (i >= raw.length() - 1) {
            return null;
        }

        char head = raw.charAt(i);
        if (!(Character.isLetter(head) || head == '!' || head == '?')) {
            return null;
        }

        int nameStart = i;
        i++;
        while (i < raw.length() - 1 && isTagNameChar(raw.charAt(i))) {
            i++;
        }
        if (i == nameStart) {
            return null;
        }

        boolean declaration = head == '!' || head == '?';
        boolean selfClosing = declaration || isSelfClosingMarkupToken(raw, closeChar);
        return new MarkupToken(start, end + 1, raw, openChar, closeChar, closing, selfClosing);
    }

    private static int findMarkupTokenEnd(String text, int start, char closeChar) {
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        for (int i = start + 1; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
                continue;
            }
            if (ch == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
                continue;
            }
            if (ch == closeChar && !inSingleQuote && !inDoubleQuote) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isSelfClosingMarkupToken(String tag, char closeChar) {
        if (tag == null || tag.isEmpty()) {
            return false;
        }
        int close = tag.lastIndexOf(closeChar);
        if (close < 0) {
            return false;
        }
        int i = close - 1;
        while (i >= 0 && Character.isWhitespace(tag.charAt(i))) {
            i--;
        }
        return i >= 0 && tag.charAt(i) == '/';
    }

    private static boolean isAttrNameChar(char ch) {
        return Character.isLetterOrDigit(ch) || ch == '_' || ch == ':' || ch == '-' || ch == '.';
    }

    private static void appendColoredText(TextFlow flow, String chunk, String color, boolean underline) {
        if (flow == null || chunk == null || chunk.isEmpty()) return;
        Text node = new Text(chunk);
        node.setFill(javafx.scene.paint.Color.web(color));
        node.setUnderline(underline);
        node.setStyle("-fx-font-family: \"Segoe UI\", \"Arial\", sans-serif; -fx-font-weight: bold; -fx-font-size: " + editorContentFontPx() + "px;");
        flow.getChildren().add(node);
    }

    private static double sf(double fullHdPx, double minPx) {
        return Math.max(UiScaleHelper.scaleY(fullHdPx), minPx);
    }

    private static double editorContentFontPx() {
        double scale = UiScaleHelper.scale(1.0);
        if (scale >= 0.98) {
            return 16.0;
        }
        return Math.max(UiScaleHelper.scaleFont(11.4, 10.8), 10.8);
    }

    private static double editorLangFontPx() {
        double scale = UiScaleHelper.scale(1.0);
        if (scale >= 0.98) {
            return 11.0;
        }
        return Math.max(UiScaleHelper.scaleFont(8.6, 9.6), 9.6);
    }

    private static void applyEditorTypography() {
        double editorFont = editorContentFontPx();
        double langFont = editorLangFontPx();
        for (Map.Entry<String, TextArea> entry : editors.entrySet()) {
            TextArea area = entry.getValue();
            if (area != null) {
                area.setStyle("-fx-font-size: " + editorFont + "px;");
            }
            TextFlow preview = editorPreviews.get(entry.getKey());
            if (preview != null) {
                preview.setStyle("-fx-font-size: " + editorFont + "px;");
            }
        }
        for (Label label : editorLangLabels.values()) {
            if (label != null) {
                label.setStyle("-fx-font-size: " + langFont + "px; -fx-font-weight: bold;");
            }
        }
    }

    private static void applyTreeTypography(TreeView<NodeData> tree) {
        if (tree == null) return;
        double scale = UiScaleHelper.scale(1.0);
        double treeFontSize = scale >= 0.98
                ? Math.max(UiScaleHelper.scaleFont(12.0, 10.4), 10.8)
                : Math.max(UiScaleHelper.scaleFont(10.9, 9.9), 9.9);
        double indent = scale >= 0.98
                ? Math.max(UiScaleHelper.scaleX(18), 16.0)
                : Math.max(UiScaleHelper.scaleX(12), 10.0);
        tree.setStyle("-fx-font-size: " + treeFontSize + "px; -fx-indent: " + indent + "px;");
        double cellHeight = scale >= 0.98
                ? Math.max(UiScaleHelper.scaleY(40), treeFontSize * 1.88)
                : Math.max(UiScaleHelper.scaleY(37), treeFontSize * 1.82);
        tree.setFixedCellSize(cellHeight);
        tree.refresh();
        applyEditorTypography();
    }

    private static void loadRowIntoEditors(LocalizationData row) {
        if (row == null) return;
        for (Map.Entry<String, TextArea> entry : editors.entrySet()) {
            String value = row.getByLang(entry.getKey());
            entry.getValue().setText(value == null ? "" : value);
        }
    }

    private static void clearEditors() {
        for (TextArea area : editors.values()) area.clear();
    }

    private static void setEditorsEnabled(boolean enabled) {
        for (TextArea area : editors.values()) area.setDisable(!enabled);
    }

    private static void applyEditorValueHeight() {
        for (TextArea area : editors.values()) {
            area.setPrefHeight(editorValueHeight);
            area.setMinHeight(UiScaleHelper.scaleY(96));
            Node parent = area.getParent();
            if (parent instanceof StackPane stack) {
                stack.setPrefHeight(editorValueHeight);
                stack.setMinHeight(UiScaleHelper.scaleY(96));
            }
        }
    }

    private static void saveCurrentEditorToRow(CustomTableView table) {
        if (table == null || selectedKey == null) return;
        LocalizationData row = findRow(table, selectedKey);
        if (row == null) return;

        for (Map.Entry<String, TextArea> entry : editors.entrySet()) {
            setByLang(row, entry.getKey(), entry.getValue().getText());
        }
        table.refresh();
    }

    private static void showSelectedInTable(CustomTableView table) {
        if (table == null || selectedKey == null) return;
        saveCurrentEditorToRow(table);

        LocalizationData row = findRow(table, selectedKey);
        if (row == null) {
            // If user applied a filter and row is hidden, restore full list first.
            table.clearKeyFilter();
            row = findRow(table, selectedKey);
        }
        if (row == null) return;

        final LocalizationData targetRow = row;
        Platform.runLater(() -> {
            table.requestFocus();
            table.getSelectionModel().clearSelection();
            table.getSelectionModel().select(targetRow);

            int rowIndex = table.getItems().indexOf(targetRow);
            if (rowIndex >= 0) {
                table.scrollTo(Math.max(0, rowIndex - 2));
                table.scrollTo(rowIndex);
            } else {
                table.scrollTo(targetRow);
            }
        });

        close();
    }

    private static void refreshTree(TreeView<NodeData> tree, CustomTableView table) {
        if (tree == null || table == null || tree.getRoot() == null) return;
        TreeItem<NodeData> root = tree.getRoot();
        root.getChildren().clear();
        buildTree(root, table);
        root.setExpanded(true);
    }

    private static void buildTree(TreeItem<NodeData> root, CustomTableView table) {
        for (String key : table.getAllKeysForFilter()) {
            String[] parts = key.split("/");
            TreeItem<NodeData> parent = root;
            StringBuilder actualPath = new StringBuilder();
            String previousPart = null;
            for (String rawPart : parts) {
                String part = rawPart.trim();
                if (part.isEmpty()) continue;
                if (actualPath.length() > 0) actualPath.append('/');
                actualPath.append(part);

                boolean repeatedSegment = previousPart != null && previousPart.equalsIgnoreCase(part);
                previousPart = part;
                if (repeatedSegment) {
                    continue;
                }

                String full = actualPath.toString();
                TreeItem<NodeData> node = findChild(parent, part, full);
                if (node == null) {
                    node = new TreeItem<>(new NodeData(part, full));
                    parent.getChildren().add(node);
                }
                parent = node;
            }
        }
    }

    private static TreeItem<NodeData> findChild(TreeItem<NodeData> parent, String title, String fullPath) {
        if (parent == null) {
            return null;
        }
        for (TreeItem<NodeData> child : parent.getChildren()) {
            NodeData value = child.getValue();
            if (value != null
                    && Objects.equals(value.title, title)
                    && Objects.equals(value.fullPath, fullPath)) {
                return child;
            }
        }
        return null;
    }

    private static void selectKeyInTree(String key) {
        if (tree == null || key == null || key.isEmpty()) return;
        TreeItem<NodeData> item = findTreeItemByKey(tree.getRoot(), key);
        if (item != null) {
            // Expand parents
            TreeItem<NodeData> parent = item.getParent();
            while (parent != null) {
                parent.setExpanded(true);
                parent = parent.getParent();
            }
            tree.getSelectionModel().select(item);
            tree.scrollTo(tree.getRow(item));
        }
    }

    private static TreeItem<NodeData> findTreeItemByKey(TreeItem<NodeData> root, String key) {
        if (root == null) return null;
        if (key.equals(root.getValue().fullPath)) return root;
        for (TreeItem<NodeData> child : root.getChildren()) {
            TreeItem<NodeData> found = findTreeItemByKey(child, key);
            if (found != null) return found;
        }
        return null;
    }

    private static LocalizationData findRow(CustomTableView table, String key) {
        if (table == null || key == null) return null;
        String target = normalizeKeyForLookup(key);
        return table.getItems().stream()
                .filter(Objects::nonNull)
                .filter(r -> Objects.equals(normalizeKeyForLookup(r.getKey()), target))
                // If duplicate keys exist, prefer the row that actually has text.
                .max(java.util.Comparator.comparingInt(KeyFilterWindow::filledLangCount))
                .orElse(null);
    }

    private static String normalizeKeyForLookup(String key) {
        if (key == null) return "";
        String normalized = key.replace('\\', '/').trim();
        normalized = normalized.replaceAll("\\s*/\\s*", "/");
        normalized = normalized.replaceAll("/+", "/");
        return normalized;
    }

    private static int filledLangCount(LocalizationData row) {
        if (row == null) return 0;
        int count = 0;
        for (String lang : EDIT_LANGS) {
            String value = row.getByLang(lang);
            if (value != null && !value.isBlank()) {
                count++;
            }
        }
        return count;
    }

    private static void setByLang(LocalizationData row, String lang, String value) {
        if (row == null || lang == null) return;
        String v = value == null ? "" : value;
        String normalized = lang.toLowerCase(Locale.ROOT);
        switch (normalized) {
            case "ruru":
                row.setRuRu(v);
                break;
            case "dede":
                row.setDeDe(v);
                break;
            case "enus":
                row.setEnUs(v);
                break;
            case "esmx":
                row.setEsMx(v);
                break;
            case "eses":
                row.setEsEs(v);
                break;
            case "frfr":
                row.setFrFr(v);
                break;
            case "itit":
                row.setItIt(v);
                break;
            case "plpl":
                row.setPlPl(v);
                break;
            case "ptbr":
                row.setPtBr(v);
                break;
            case "kokr":
                row.setKoKr(v);
                break;
            case "zhcn":
                row.setZhCn(v);
                break;
            case "zhtw":
                row.setZhTw(v);
                break;
            default:
                break;
        }
    }

    private static String localize(LocalizationManager localization, String key, String ruFallback, String enFallback) {
        if (localization != null) {
            try {
                String value = localization.get(key);
                if (value != null && !value.isBlank() && !value.equals(key)) return value;
            } catch (Exception ignored) {
            }
        }
        return "ru".equalsIgnoreCase(localization == null ? null : localization.getCurrentLanguage()) ? ruFallback : enFallback;
    }
}

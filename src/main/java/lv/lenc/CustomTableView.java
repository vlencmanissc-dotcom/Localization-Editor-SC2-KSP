package lv.lenc;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

import com.cybozu.labs.langdetect.LangDetectException;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.effect.BlurType;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.ScrollEvent;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.util.converter.DefaultStringConverter;


public class CustomTableView extends TableView<LocalizationData> {
    private static final double TABLE_TEXT_SCALE = 0.84;
    private static final double TABLE_SPECIAL_COLUMNS_BOOST = 1.30;
    private static final double TABLE_HEADER_BOOST = 1.10;
    String texturePath;
    private final List<TableColumn<LocalizationData, String>> editableColumns = new ArrayList<>();
    private double baseLangMinW;
    private double baseLangPrefW;
    private double baseLangMaxW;

    private double baseKeyMinW;
    private double baseKeyPrefW;

    private double baseNMinW;
    private double baseNPrefW;
    private boolean tableFocusMode = false;
    private boolean currentSingleMode = false;
    private boolean baseWidthsCaptured = false;
    private double currentRowHeight = UiScaleHelper.scaleY(52);

    private boolean lastLoadWasMulti = false;
    private final java.util.Set<String> loadedUiLanguages = new LinkedHashSet<>();
    private final Map<String, String> detectedUiByColumn = new ConcurrentHashMap<>();
    private static final Set<String> SUPPORTED_LANGS = Set.of(
            "ruRU", "deDE", "enUS", "esMX", "esES",
            "frFR", "itIT", "plPL", "ptBR", "koKR", "zhCN", "zhTW"
    );
    //private final LanguageDetectorService langService = new LanguageDetectorService();
    private LanguageDetectorService langService;
    private String currentSourceUi = null;
    private final GlossaryService glossaryService;
    private final LocalizationManager localization;
    private final ObservableList<LocalizationData> keyFilterBaseItems = FXCollections.observableArrayList();
    private String activeKeyPrefixFilter = null;
    private static final double MIN_CELL_FONT_PX = 11.0;
    private static final double MIN_HEADER_FONT_PX = 10.5;
    private static final double MIN_PLACEHOLDER_FONT_PX = 14.0;
    private final Set<String> detectedHeaderColumns = new LinkedHashSet<>();
    private final Set<String> pendingSaveHeaderColumns = new LinkedHashSet<>();
    private String activeHeaderColumnKey = null;
    private String preferredMainSourceUi = null;

    public CustomTableView(String texturePath,
                           LocalizationManager localizationManager,
                           double width,
                           double height,
                           GlossaryService glossaryService) {

        this.texturePath = texturePath;
        this.localization = localizationManager;
        // Styling
        //  this.applyScrollBarStyle();
        this.glossaryService = glossaryService;
        this.getStylesheets().add(UiAssets.css("custom-tableview.css"));
        try {
            this.langService = new LanguageDetectorService();
        } catch (LangDetectException e) {
            AppLog.exception(e);
            this.langService = null;
        }
        //TRUMB
        this.setRowFactory((TableView<LocalizationData> tv) -> {
            TableRow<LocalizationData> row = new TableRow<>();

            return row;
        });

        TableColumn<LocalizationData, String> countColumn = new TableColumn<>("N");

        countColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(String.valueOf(getItems().indexOf(cellData.getValue()) + 1))
        );
// Disable default grid lines (removes white horizontal and vertical lines)

        TableColumn<LocalizationData, String> keyColumn = new TableColumn<>("key");
        keyColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getKey()));

        TableColumn<LocalizationData, String> ruRUColumn = new TableColumn<>("ruRU");
        ruRUColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getByLang("ruRU")));
        // Ć„ĀÄā‚¬ĀÆÄā€Ā¬Ć„ĀÄā‚¬ĀÆÄā€Ā¬Ć„ĀÄā‚¬ĀÆÄā€Ā¬ German Ć„ĀÄā‚¬ĀÆÄā€Ā¬Ć„ĀÄā‚¬ĀÆÄā€Ā¬Ć„ĀÄā‚¬ĀÆÄā€Ā¬
        TableColumn<LocalizationData, String> deDEColumn = new TableColumn<>("deDE");
        deDEColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getByLang("deDE")));

        TableColumn<LocalizationData, String> enUSColumn = new TableColumn<>("enUS");
        enUSColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getByLang("enUS")));

        TableColumn<LocalizationData, String> esMXColumn = new TableColumn<>("esMX");
        esMXColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getByLang("esMX")));

        TableColumn<LocalizationData, String> esESColumn = new TableColumn<>("esES");
        esESColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getByLang("esES")));

        TableColumn<LocalizationData, String> frFRColumn = new TableColumn<>("frFR");
        frFRColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getByLang("frFR")));

        TableColumn<LocalizationData, String> itITColumn = new TableColumn<>("itIT");
        itITColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getByLang("itIT")));

        TableColumn<LocalizationData, String> plPLColumn = new TableColumn<>("plPL");
        plPLColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getByLang("plPL")));

        TableColumn<LocalizationData, String> ptBRColumn = new TableColumn<>("ptBR");
        ptBRColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getByLang("ptBR")));

        TableColumn<LocalizationData, String> koKRColumn = new TableColumn<>("koKR");
        koKRColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getByLang("koKR")));

        TableColumn<LocalizationData, String> zhCNColumn = new TableColumn<>("zhCN");
        zhCNColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getByLang("zhCN")));

        TableColumn<LocalizationData, String> zhTWColumn = new TableColumn<>("zhTW");
        zhTWColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getByLang("zhTW")));

        getColumns().addAll(List.of(
                countColumn, keyColumn, ruRUColumn, deDEColumn, enUSColumn, esMXColumn, esESColumn,
                frFRColumn, itITColumn, plPLColumn, ptBRColumn, koKRColumn, zhCNColumn, zhTWColumn
        ));
        editableColumns.addAll(List.of(
                ruRUColumn, deDEColumn, enUSColumn, esMXColumn, esESColumn,
                frFRColumn, itITColumn, plPLColumn, ptBRColumn, koKRColumn, zhCNColumn, zhTWColumn
        ));

        countColumn.getStyleClass().add("col-n");
        keyColumn.getStyleClass().add("col-key");
        countColumn.setReorderable(false);
        keyColumn.setReorderable(false);
        this.setEditable(true);
        this.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);

        // OPTIMIZED: Enable in-place cell editing with proper TextField support
        for (TableColumn<LocalizationData, String> langColumn : editableColumns) {
            langColumn.setOnEditCommit(event -> {
                try {
                    LocalizationData data = event.getRowValue();
                    String langCode = langColumn.getText();
                    String newValue = event.getNewValue();
                    if (data != null) {
                        data.setByLang(langCode, newValue == null ? "" : newValue);
                    }
                } catch (Exception ex) {
                    AppLog.exception(ex);
                }
            });
        }

        for (TableColumn<LocalizationData, ?> column : this.getColumns()) {
            column.setMinWidth(UiScaleHelper.scaleX(140));
            column.setMaxWidth(UiScaleHelper.scaleX(4000));
            column.setEditable(true);
        }
        keyColumn.setMinWidth(UiScaleHelper.scaleX(200));
        countColumn.setMaxWidth(UiScaleHelper.scaleX(150));
        countColumn.setMinWidth(UiScaleHelper.scaleX(110));
        countColumn.setPrefWidth(UiScaleHelper.scaleX(110));
        hideHeaderSortedArrow();
        enableHeaderColumnSelectionHighlighting();
        enableHeaderResizeCursorHints();
        enableHeaderSortClickFallback();
        hookHeaderStatePersistence();
        Label placeholderLabel = new Label(this.localization.get("table.placeholder"));
        this.setPlaceholder(placeholderLabel);
        applyDynamicTypography();
        this.setFixedCellSize(currentRowHeight);
        // edit
        applyCustomCellStyleToAllColumns();
        this.addEventFilter(ScrollEvent.SCROLL, event -> {
            if (isScrollBarTarget(event.getTarget())) {
                return;
            }
            if (!event.isControlDown()) {
                return;
            }
            double delta = event.getDeltaY();
            if (Math.abs(delta) < 0.01) {
                return;
            }
            double step = UiScaleHelper.scaleY(8);
            double next = currentRowHeight + (delta > 0 ? step : -step);
            currentRowHeight = Math.max(sf(42, 32), Math.min(UiScaleHelper.scaleY(220), next));
            setFixedCellSize(currentRowHeight);
            refresh();
            event.consume();
        });
        removeScrollCorner();
        boostVerticalScrollbarVisualLength();
        captureBaseColumnWidths();
        setViewportSize(width, height);
        widthProperty().addListener((obs, oldV, newV) -> {
            if (!baseWidthsCaptured || newV == null || oldV == null) {
                return;
            }
            if (Math.abs(newV.doubleValue() - oldV.doubleValue()) < 1.0) {
                return;
            }
            Platform.runLater(() -> applyColumnSizing(currentSingleMode));
            boostVerticalScrollbarVisualLength();
        });
        widthProperty().addListener((obs, oldV, newV) -> {
            if (newV == null || oldV == null) {
                return;
            }
            if (Math.abs(newV.doubleValue() - oldV.doubleValue()) < 1.0) {
                return;
            }
            applyDynamicTypography();
            boostVerticalScrollbarVisualLength();
        });
        heightProperty().addListener((obs, oldV, newV) -> boostVerticalScrollbarVisualLength());
        countColumn.setCellFactory(col -> new TableCell<>() {
            private final Label countLabel = new Label();
            private final javafx.scene.layout.StackPane countWrap = new javafx.scene.layout.StackPane(countLabel);

            {
                countLabel.getStyleClass().add("count-label");
                countLabel.setMouseTransparent(true);
                countLabel.setAlignment(Pos.CENTER);
                countWrap.setAlignment(Pos.CENTER);
                countWrap.setMouseTransparent(true);
                countWrap.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
                setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                setGraphic(countWrap);
                setAlignment(Pos.CENTER);
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    getStyleClass().removeAll("col-n");
                    countLabel.setText("");
                    return;
                }
                setText(null);
                setGraphic(countWrap);
                setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                if (!getStyleClass().contains("col-n")) getStyleClass().add("col-n");
                setStyle("");
                double countFont = resolveCountCellFontSize(this);
                String countFontFamily = resolveCountFontFamily();
                countLabel.setFont(Font.font(
                        countFontFamily,
                        FontWeight.BOLD,
                        countFont
                ));
                countLabel.setTextFill(Color.web("#ffe8cf"));
                DropShadow softBloom = new DropShadow(
                        BlurType.GAUSSIAN,
                        Color.rgb(255, 226, 187, 0.28),
                        Math.max(2.0, countFont * 0.22),
                        0.12,
                        0,
                        0
                );
                DropShadow warmGlow = new DropShadow(
                        BlurType.GAUSSIAN,
                        Color.rgb(255, 132, 60, 0.72),
                        Math.max(7.0, countFont * 0.58),
                        0.42,
                        0,
                        0
                );
                warmGlow.setInput(softBloom);
                countLabel.setEffect(warmGlow);
                countLabel.setStyle(
                        "-fx-font-family: '" + countFontFamily + "';"
                                + "-fx-font-size: " + countFont + "px;"
                                + "-fx-font-weight: 700;"
                );
                countLabel.setText(item);
            }
        });

        keyColumn.setCellFactory(col -> new TableCell<>() {
            {
                setOnMouseClicked(event -> {
                    if (isEmpty() || event.getButton() != javafx.scene.input.MouseButton.PRIMARY || event.getClickCount() != 1) {
                        return;
                    }

                    LocalizationData data = getTableRow().getItem();
                    if (data == null || data.getKey() == null) {
                        return;
                    }

                    Node current = getTableView();
                    javafx.scene.layout.StackPane stackPane = null;
                    while (current != null) {
                        if (current instanceof javafx.scene.layout.StackPane candidate) {
                            stackPane = candidate;
                        }
                        current = current.getParent();
                    }
                    if (stackPane != null) {
                        KeyFilterWindow.show(stackPane, (CustomTableView) getTableView(), CustomTableView.this.localization, data.getKey());
                        event.consume();
                    }
                });
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    getStyleClass().removeAll("col-key");
                    return;
                }
                setText(item);
                if (!getStyleClass().contains("col-key")) getStyleClass().add("col-key");
                setStyle("");
                double keyFont = resolveKeyCellFontSize(this);
                setFont(Font.font(
                        getFont().getFamily(),
                        FontWeight.BOLD,
                        keyFont
                ));
            }
        });
    }
    private double sf(double fullHdPx, double minPx) {
        return Math.max(UiScaleHelper.scaleY(fullHdPx), minPx);
    }

    private double sff(double fullHdPx, double minPx) {
        return Math.max(UiScaleHelper.scaleFont(fullHdPx), minPx);
    }

    private double tableText(double px) {
        return Math.max(px * TABLE_TEXT_SCALE, 7.0);
    }

    private double resolveKeyCellFontSize(TableCell<LocalizationData, String> cell) {
        return tableText(sff(13, 10.6)) * TABLE_SPECIAL_COLUMNS_BOOST;
    }

    private double resolveCountCellFontSize(TableCell<LocalizationData, String> cell) {
        double rowHeight = (getFixedCellSize() > 1.0) ? getFixedCellSize() : currentRowHeight;
        double target = rowHeight * 0.44;
        double scaledBase = tableText(sff(14.0, 10.4)) * 1.12;
        return Math.max(scaledBase, target);
    }

    private String resolveCountFontFamily() {
        if (Font.getFamilies().contains("Bahnschrift SemiBold")) {
            return "Bahnschrift SemiBold";
        }
        if (Font.getFamilies().contains("Bahnschrift")) {
            return "Bahnschrift";
        }
        if (Font.getFamilies().contains("Segoe UI Semibold")) {
            return "Segoe UI Semibold";
        }
        if (Font.getFamilies().contains("Segoe UI")) {
            return "Segoe UI";
        }
        if (Font.getFamilies().contains("Consolas")) {
            return "Consolas";
        }
        return Font.getFamilies().contains("Courier New") ? "Courier New" : "Monospaced";
    }

    private void applyDynamicTypography() {
        double baseFont = tableText(sff(14, MIN_CELL_FONT_PX));
        double headerFont = tableText(sff(13, MIN_HEADER_FONT_PX)) * TABLE_HEADER_BOOST;
        double placeholderFont = tableText(sff(36, MIN_PLACEHOLDER_FONT_PX));
        setStyle("-fx-font-size: " + baseFont + "px;");
        Label placeholder = (Label) getPlaceholder();
        if (placeholder != null) {
            placeholder.setStyle("-fx-font-size: " + placeholderFont + "px; -fx-text-fill: rgba(130,170,165,0.50);");
        }
        Platform.runLater(() -> {
            lookupAll(".column-header").forEach(headerNode -> {
                if (!(headerNode instanceof Parent parent)) {
                    return;
                }
                Node labelNode = parent.lookup(".label");
                if (labelNode == null) {
                    return;
                }
                String headerText = (labelNode instanceof Label label) ? label.getText() : null;
                boolean countHeader = "N".equalsIgnoreCase(headerText);
                double effectiveFont = countHeader ? headerFont : headerFont;
                labelNode.setStyle("-fx-font-size: " + effectiveFont + "px; -fx-font-weight: "
                        + (countHeader ? "700" : "bold") + ";");
            });
            updateHeaderStateStyles();
            enableHeaderResizeCursorHints();
        });
    }

    private void hookHeaderStatePersistence() {
        skinProperty().addListener((obs, oldSkin, newSkin) -> requestHeaderStateRefresh());
        sceneProperty().addListener((obs, oldScene, newScene) -> requestHeaderStateRefresh());
        getColumns().forEach(col ->
                col.visibleProperty().addListener((obs, oldV, newV) -> requestHeaderStateRefresh()));
        widthProperty().addListener((obs, oldV, newV) -> requestHeaderStateRefresh());
    }

    private void requestHeaderStateRefresh() {
        Platform.runLater(() -> {
            enableHeaderColumnSelectionHighlighting();
            enableHeaderResizeCursorHints();
            enableHeaderSortClickFallback();
            updateHeaderStateStyles();
            Platform.runLater(() -> {
                enableHeaderColumnSelectionHighlighting();
                enableHeaderResizeCursorHints();
                enableHeaderSortClickFallback();
                updateHeaderStateStyles();
                Platform.runLater(this::updateHeaderStateStyles);
            });
        });
    }

    private boolean isScrollBarTarget(Object target) {
        if (!(target instanceof Node node)) {
            return false;
        }
        Node current = node;
        while (current != null) {
            if (current instanceof ScrollBar) {
                return true;
            }
            if (current.getStyleClass().contains("scroll-bar")
                    || current.getStyleClass().contains("thumb")
                    || current.getStyleClass().contains("track")) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    private void boostVerticalScrollbarVisualLength() {
        Platform.runLater(() -> {
            for (Node node : lookupAll(".scroll-bar")) {
                if (node instanceof ScrollBar scrollBar && scrollBar.getOrientation() == Orientation.VERTICAL) {
                    scrollBar.setScaleY(1.08);
                    scrollBar.setTranslateY(UiScaleHelper.scaleY(5));
                }
            }
        });
    }

    private void captureBaseColumnWidths() {
        //
        TableColumn<LocalizationData, ?> sampleLang = getColumns().stream()
                .filter(c -> SUPPORTED_LANGS.contains(c.getText()))
                .findFirst()
                .orElse(null);

        if (sampleLang != null) {
            baseLangMinW  = sampleLang.getMinWidth();
            baseLangPrefW = sampleLang.getPrefWidth() > 0 ? sampleLang.getPrefWidth() : sampleLang.getMinWidth();
            baseLangMaxW  = sampleLang.getMaxWidth();
        } else {
            baseLangMinW = UiScaleHelper.scaleX(140);
            baseLangPrefW = baseLangMinW;
            baseLangMaxW = UiScaleHelper.scaleX(4000);
        }

        TableColumn<LocalizationData, ?> keyCol = getColumns().stream()
                .filter(c -> "key".equalsIgnoreCase(c.getText()))
                .findFirst().orElse(null);

        if (keyCol != null) {
            baseKeyMinW  = keyCol.getMinWidth();
            baseKeyPrefW = keyCol.getPrefWidth() > 0 ? keyCol.getPrefWidth() : keyCol.getMinWidth();
        } else {
            baseKeyMinW = UiScaleHelper.scaleX(200);
            baseKeyPrefW = baseKeyMinW;
        }

        TableColumn<LocalizationData, ?> nCol = getColumns().stream()
                .filter(c -> "N".equalsIgnoreCase(c.getText()))
                .findFirst().orElse(null);

        if (nCol != null) {
            baseNMinW  = nCol.getMinWidth();
            baseNPrefW = nCol.getPrefWidth() > 0 ? nCol.getPrefWidth() : nCol.getMinWidth();
        } else {
            baseNMinW = UiScaleHelper.scaleX(50);
            baseNPrefW = baseNMinW;
        }
        baseWidthsCaptured = true;
    }

    private int visibleSupportedLangCount() {
        return (int) getColumns().stream()
                .filter(col -> SUPPORTED_LANGS.contains(col.getText()) && col.isVisible())
                .count();
    }

    public void setTableFocusMode(boolean tableFocusMode) {
        this.tableFocusMode = tableFocusMode;
        Runnable relayout = () -> applyColumnSizing(currentSingleMode);
        if (Platform.isFxApplicationThread()) {
            relayout.run();
        } else {
            Platform.runLater(relayout);
        }
    }

    public void applyColumnSizing(boolean singleMode) {
        currentSingleMode = singleMode;
        double tableWidth = getEffectiveTableContentWidth();
        int visibleLangCount = visibleSupportedLangCount();
        boolean fillAvailableWidth = tableFocusMode && visibleLangCount > 0;
        double keyWidth = baseKeyPrefW;
        double stretchedLangWidth = baseLangPrefW;

        if (visibleLangCount > 0 && tableWidth > 0) {
            if (singleMode) {
                double keyWidthRatio = tableFocusMode ? 0.21 : 0.18;
                double keyMaxWidth = tableFocusMode ? UiScaleHelper.scaleX(500) : UiScaleHelper.scaleX(420);
                keyWidth = Math.max(baseKeyPrefW, Math.min(keyMaxWidth, tableWidth * keyWidthRatio));
                double chromeReserve = UiScaleHelper.scaleX(10);
                double availableForLangs = Math.max(
                        baseLangMinW * visibleLangCount,
                        tableWidth - baseNPrefW - keyWidth - chromeReserve
                );

                // Keep all visible language columns strictly equal, absorb rounding remainder into key column.
                stretchedLangWidth = Math.max(baseLangMinW, Math.floor(availableForLangs / visibleLangCount));
                double remainder = Math.max(0, availableForLangs - (stretchedLangWidth * visibleLangCount));
                keyWidth = keyWidth + remainder;
            } else if (fillAvailableWidth) {
                double keyMinWidth = UiScaleHelper.scaleX(220);
                double keyMaxWidth = UiScaleHelper.scaleX(360);
                double keyWidthRatio = visibleLangCount >= 8 ? 0.13 : 0.16;
                keyWidth = Math.max(keyMinWidth, Math.min(keyMaxWidth, tableWidth * keyWidthRatio));
                double reserved = baseNPrefW + keyWidth + UiScaleHelper.scaleX(12);
                double availableForLangs = Math.max(UiScaleHelper.scaleX(82) * visibleLangCount, tableWidth - reserved);
                stretchedLangWidth = Math.max(UiScaleHelper.scaleX(82), availableForLangs / visibleLangCount);
            }
        }

        for (TableColumn<LocalizationData, ?> col : getColumns()) {
            String name = col.getText();

            if ("N".equalsIgnoreCase(name)) {
                col.setMinWidth(baseNMinW);
                col.setPrefWidth(baseNPrefW);
                col.setMaxWidth(baseNPrefW);
                continue;
            }

            if ("key".equalsIgnoreCase(name)) {
                col.setMinWidth(baseKeyMinW);
                col.setPrefWidth((singleMode || fillAvailableWidth) ? keyWidth : baseKeyPrefW);
                // Keep key column resizable by user in any mode.
                col.setMaxWidth(UiScaleHelper.scaleX(4000));
                continue;
            }

            if (SUPPORTED_LANGS.contains(name)) {
                //
                if (col.isVisible() && singleMode) {
                    col.setMinWidth(baseLangMinW);
                    col.setPrefWidth(stretchedLangWidth);
                    col.setMaxWidth(baseLangMaxW);
                } else if (col.isVisible() && fillAvailableWidth) {
                    col.setMinWidth(baseLangMinW);
                    col.setPrefWidth(stretchedLangWidth);
                    col.setMaxWidth(baseLangMaxW);
                } else {
                    //
                    col.setMinWidth(baseLangMinW);
                    col.setPrefWidth(baseLangPrefW);
                    col.setMaxWidth(baseLangMaxW);
                }
            }
        }

        if (Platform.isFxApplicationThread()) {
            refresh();
            layout();
        } else {
            Platform.runLater(() -> {
                refresh();
                layout();
            });
        }
    }

    private double getVisibleVerticalScrollbarWidth() {
        double width = 0;
        for (Node node : lookupAll(".scroll-bar")) {
            if (node instanceof ScrollBar scrollBar
                    && scrollBar.getOrientation() == Orientation.VERTICAL
                    && scrollBar.isVisible()) {
                width = Math.max(width, scrollBar.getWidth());
            }
        }
        return width;
    }

    private double getEffectiveTableContentWidth() {
        double raw = getWidth() > 1 ? getWidth() : getPrefWidth();
        double insets = snappedLeftInset() + snappedRightInset();
        double scrollbar = getVisibleVerticalScrollbarWidth();
        return Math.max(0, raw - insets - scrollbar);
    }

    private void resetAllColumnWidthsToBase() {
        for (TableColumn<LocalizationData, ?> col : getColumns()) {
            String name = col.getText();
            if ("N".equalsIgnoreCase(name)) {
                col.setMinWidth(baseNMinW);
                col.setPrefWidth(baseNPrefW);
                col.setMaxWidth(baseNPrefW);
                continue;
            }
            if ("key".equalsIgnoreCase(name)) {
                col.setMinWidth(baseKeyMinW);
                col.setPrefWidth(baseKeyPrefW);
                col.setMaxWidth(UiScaleHelper.scaleX(4000));
                continue;
            }
            if (SUPPORTED_LANGS.contains(name)) {
                col.setMinWidth(baseLangMinW);
                col.setPrefWidth(baseLangPrefW);
                col.setMaxWidth(baseLangMaxW);
            }
        }
    }

    public void setViewportSize(double width, double height) {
        setViewportSize(width, height, true);
    }

    public void setViewportSize(double width, double height, boolean relayoutColumns) {
        this.setMinWidth(width);
        this.setPrefWidth(width);
        this.setMaxWidth(width);
        this.setMinHeight(height);
        this.setPrefHeight(height);
        this.setMaxHeight(height);
        applyDynamicTypography();

        if (!relayoutColumns || !baseWidthsCaptured) {
            return;
        }

        Runnable resizeLayout = () -> applyColumnSizing(currentSingleMode);
        if (Platform.isFxApplicationThread()) {
            resizeLayout.run();
        } else {
            Platform.runLater(resizeLayout);
        }
    }

    private void resetHorizontalScroll() {
        applyCss();
        layout();
        for (Node node : lookupAll(".scroll-bar")) {
            if (node instanceof ScrollBar scrollBar && scrollBar.getOrientation() == Orientation.HORIZONTAL) {
                scrollBar.setValue(scrollBar.getMin());
            }
        }
    }

    public String getCurrentSourceUi() {
        return currentSourceUi;
    }

    public void setPreferredMainSourceUi(String preferredMainSourceUi) {
        String normalized = normalizeUi(preferredMainSourceUi);
        this.preferredMainSourceUi = (normalized == null || normalized.isBlank()) ? null : normalized;
    }
    private void removeScrollCorner() {
        Platform.runLater(() -> {
            Node corner = this.lookup(".corner");
            if (corner != null) {
                corner.setStyle("-fx-background-color: transparent;");
            }
        });
    }


    //
    private void applyCustomCellStyleToAllColumns() {
        for (TableColumn<LocalizationData, String> editableCol : editableColumns) {
            editableCol.setCellFactory(column -> new TextFieldTableCell<>(new DefaultStringConverter()) {

                private boolean isHovered = false;
                private boolean isEditing = false;
                private String currentTextColor = "#80d2a2";

                {
                    setWrapText(true);
                    //
                    this.setOnMouseEntered(event -> {
                        isHovered = true;
                        updateCellStyle();
                    });
                    this.setOnMouseExited(event -> {
                        isHovered = false;
                        updateCellStyle();
                    });
                    this.setOnMouseClicked(event -> {
                        if (!isEmpty() && event.getButton() == javafx.scene.input.MouseButton.PRIMARY) {
                            if (event.getClickCount() == 2) {
                                // Å Ā¾Å Ā´Å Ā½Å Ā¾Å Ā¼ Å Å—Å Ā»Å ĆøÅ Å—Å Ā¾Å Ā¼ Å Ā°Å Å—Åā€Å ĆøÅ Ā²Å ĆøÅā‚¬ÅĀÅ ĀµÅ Ā¼ Åā‚¬Å ĀµÅ Ā´Å Ā°Å Å—Åā€Å ĆøÅā‚¬Å Ā¾Å Ā²Å Ā°Å Ā½Å ĆøÅ Āµ ÅĀøÅā€Å ĀµÅ ĀµÅ Å— ÅĀøÅ Ā·Åā€¹Å Å—Å Ā¾Å Ā²
                                if (!isEditing()) {
                                    getTableView().getSelectionModel().clearAndSelect(getIndex(), getTableColumn());
                                    getTableView().getFocusModel().focus(getIndex(), getTableColumn());
                                    startEdit();
                                    if (!isEditing()) {
                                        getTableView().edit(getIndex(), getTableColumn());
                                    }
                                    Platform.runLater(() -> {
                                        if (getGraphic() instanceof TextField textField) {
                                            textField.requestFocus();
                                            textField.selectAll();
                                        }
                                    });
                                    event.consume();
                                }
                            }
                        }
                    });
                    selectedProperty().addListener((obs, oldVal, newVal) -> updateCellStyle());
                    itemProperty().addListener((obs, oldVal, newVal) -> updateCellStyle());
                    emptyProperty().addListener((obs, oldVal, newVal) -> updateCellStyle());
                }
                private String styleWithTexture(String rowBg, String textureUrl) {
                    double padY = sf(5, 3);
                    double padX = Math.max(UiScaleHelper.scaleX(7), 5);
                    double borderSlice = sf(12, 9);
                    double borderWidth = sf(4, 3);

                    return "-fx-background-color: " + rowBg + ";"
                            + "-fx-background-insets: 0;"
                            + "-fx-padding: " + padY + " " + padX + ";"
                            + "-fx-alignment: center-left;"
                            + "-fx-border-insets: 0;"
                            + "-fx-border-color: transparent;"
                            + "-fx-border-image-slice: " + borderSlice + " fill;"
                            + "-fx-border-image-width: " + borderWidth + ";"
                            + "-fx-border-image-insets: 0;"
                            + "-fx-border-image-repeat: stretch;"
                            + "-fx-border-image-source: url('" + textureUrl + "');";
                }
                private void updateCellStyle() {
                    if (isEmpty()) {
                        setStyle("");
                        return;
                    }

                    int rowIndex = getIndex();
                    String rowBg = (rowIndex % 2 == 0)
                            ? "rgba(0, 0, 0, 0.5)"
                            : "rgba(0, 0, 0, 0.6)";

                    boolean missingValue = getItem() == null;

                    String base = texturePath + "ui_nova_archives_listitem_normal.png";
                    String over = texturePath + "ui_nova_archives_listitem_over.png";
                    String sel  = texturePath + "ui_nova_archives_listitem_selected.png";
                    String missingBase = texturePath + "ui_nova_archives_listitem_normal_red.png";
                    String missingOver = texturePath + "ui_nova_archives_listitem_over_red.png";

                    String tex = missingValue ? missingBase : base;
                    String textColor = missingValue ? "#ffd6d6" : "#80d2a2";

                    if (isEditing || isSelected()) {
                        tex = sel;
                        textColor = "white";
                    } else if (isHovered) {
                        tex = missingValue ? missingOver : over;
                        textColor = "#80d2a2"; // hover Ć…Ā Ä€Ā½Ć…Ā Ä€Āµ Ć…Ā Ä€Ā¼Ć…Ā Ä€ĀµĆ…Ā Ä€Ā½Ć…ĀÄ€ĆøĆ…Ā Ä€ĀµĆ…ĀÄā‚¬Ā Ć…ĀÄā‚¬Ā Ć…Ā Ä€Ā²Ć…Ā Ä€ĀµĆ…ĀÄā‚¬Ā Ć…ĀÄā‚¬ĀĆ…Ā Ä€ĀµĆ…Ā Ć…ā€”Ć…ĀÄ€ĀĆ…ĀÄā‚¬ĀĆ…Ā Ä€Ā°
                    }

                    if (missingValue && isHovered && !isEditing && !isSelected()) {
                        textColor = "#fff0f0";
                    }
                    currentTextColor = textColor;

                    setStyle(
                            styleWithTexture(rowBg, tex)
                                    + "-fx-text-fill: " + textColor + ";"
                                    + "-fx-font-weight: bold;"
                    );

                    applyStyledCellContent(getItem());
                }

                private void applyStyledCellContent(String value) {
                    if (isEmpty()) {
                        setGraphic(null);
                        setContentDisplay(ContentDisplay.TEXT_ONLY);
                        setText(null);
                        return;
                    }

                    if (isEditing() || isEditing) {
                        // Å ā€™Å Ā¾ Å Ā²Åā‚¬Å ĀµÅ Ā¼ÅĀø Åā‚¬Å ĀµÅ Ā´Å Ā°Å Å—Åā€Å ĆøÅā‚¬Å Ā¾Å Ā²Å Ā°Å Ā½Å ĆøÅĀø Å Ć¦Å Ā¾Å Å—Å Ā°Å Ā·Åā€¹Å Ā²Å Ā°Å ĀµÅ Ā¼ Å ĆøÅĀÅā€¦Å Ā¾Å Ā´Å Ā½Åā€¹Å Ā¹ Åā€Å ĀµÅ Å—ÅĀÅā€, Åā€Åā€Å Ā¾Å Ā±Åā€¹ Å Ā½Å Ā°ÅĀ Å Ā½Å Āµ Å Ā²Åā€¹Å Ā½Å Ā¾ÅĀÅ ĆøÅ Ā»Å Ā¾ Å Ā² Å Ā³Åā‚¬Å Ā°Åā€˛Å ĆøÅ Å—ÅĀ
                        setGraphic(getGraphic());
                        setContentDisplay(getGraphic() == null ? ContentDisplay.TEXT_ONLY : ContentDisplay.GRAPHIC_ONLY);
                        setText(getGraphic() == null ? value : null);
                        return;
                    }

                    String previewValue = buildPreviewValue(value);
                    String fittedPreview = fitPreviewToCell(previewValue);
                    if (fittedPreview == null || fittedPreview.isEmpty()) {
                        setGraphic(null);
                        setContentDisplay(ContentDisplay.TEXT_ONLY);
                        setText(null);
                        return;
                    }

                    if (!containsXmlLikeMarkup(fittedPreview)) {
                        Node plainPreview = buildPlainPreviewGraphic(fittedPreview);
                        setText(null);
                        setGraphic(plainPreview);
                        setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                        return;
                    }

                    Node xmlPreview = buildXmlPreviewGraphic(fittedPreview);
                    setText(null);
                    setGraphic(xmlPreview);
                    setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                }

                private String buildPreviewValue(String value) {
                    if (value == null) {
                        return null;
                    }

                    String preview = value
                            .replace("\r\n", "\n")
                            .replace('\r', '\n');

                    return preview;
                }

                private boolean containsXmlLikeMarkup(String value) {
                    if (value == null || value.isBlank()) return false;
                    int open = value.indexOf('<');
                    if (open < 0) return false;
                    int close = value.indexOf('>', open + 1);
                    return close > open;
                }

                private String fitPreviewToCell(String preview) {
                    return preview;
                }

                private Node buildXmlPreviewGraphic(String raw) {
                    TextFlow flow = buildXmlSyntaxFlow(raw);
                    return wrapPreviewFlow(flow, "table-xml-preview-wrap");
                }

                private Node buildPlainPreviewGraphic(String raw) {
                    Text textNode = new Text(raw == null ? "" : raw);
                    textNode.setFill(Color.web(currentTextColor));
                    textNode.setStyle("-fx-font-weight: bold;");

                    TextFlow flow = new TextFlow(textNode);
                    flow.getStyleClass().add("table-plain-flow");
                    flow.setLineSpacing(UiScaleHelper.scaleY(1.2));
                    return wrapPreviewFlow(flow, "table-plain-preview-wrap");
                }

                private Node wrapPreviewFlow(TextFlow flow, String wrapperStyleClass) {
                    javafx.scene.layout.StackPane wrapper = new javafx.scene.layout.StackPane(flow);
                    wrapper.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                    wrapper.getStyleClass().add(wrapperStyleClass);
                    wrapper.setPickOnBounds(false);
                    wrapper.setMinHeight(0);

                    double maxPreviewHeight = Math.max(UiScaleHelper.scaleY(32), getFixedCellSize() - UiScaleHelper.scaleY(18));
                    double horizontalPadding = UiScaleHelper.scaleX(18);

                    wrapper.prefWidthProperty().bind(widthProperty().subtract(horizontalPadding));
                    wrapper.maxWidthProperty().bind(widthProperty().subtract(horizontalPadding));
                    wrapper.setPrefHeight(maxPreviewHeight);
                    wrapper.setMaxHeight(maxPreviewHeight);

                    flow.prefWidthProperty().bind(wrapper.maxWidthProperty());
                    flow.maxWidthProperty().bind(wrapper.maxWidthProperty());

                    javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle();
                    clip.widthProperty().bind(wrapper.widthProperty());
                    clip.setHeight(maxPreviewHeight);
                    wrapper.setClip(clip);

                    return wrapper;
                }

                private TextFlow buildXmlSyntaxFlow(String raw) {
                    TextFlow flow = new TextFlow();
                    flow.getStyleClass().add("table-xml-flow");
                    flow.setLineSpacing(UiScaleHelper.scaleY(1.5));
                    String text = raw == null ? "" : raw;
                    int pos = 0;
                    int openTagDepth = 0;
                    final String textColor = "#80d2a2";
                    final String tagNameColor = "#1f58c9";
                    final String attrColor = "#2f7dff";
                    final String valueColor = "#75d7ff";
                    final String taggedInnerColor = "#75d7ff";

                    while (pos < text.length()) {
                        int open = text.indexOf('<', pos);
                        if (open < 0) {
                            appendChunk(flow, text.substring(pos), openTagDepth > 0 ? taggedInnerColor : textColor);
                            break;
                        }

                        if (open > pos) {
                            appendChunk(flow, text.substring(pos, open), openTagDepth > 0 ? taggedInnerColor : textColor);
                        }

                        int close = text.indexOf('>', open);
                        if (close < 0) {
                            appendChunk(flow, text.substring(open), openTagDepth > 0 ? taggedInnerColor : textColor);
                            break;
                        }

                        String tag = text.substring(open, close + 1);
                        boolean closing = isClosingTagToken(tag);
                        boolean selfClosing = isSelfClosingTagToken(tag);
                        if (closing && openTagDepth > 0) {
                            openTagDepth--;
                        }
                        appendTagWithSyntaxColors(flow, tag, tagNameColor, attrColor, valueColor);
                        if (!closing && !selfClosing) {
                            openTagDepth++;
                        }
                        pos = close + 1;
                    }

                    return flow;
                }

                private boolean isClosingTagToken(String tag) {
                    if (tag == null) return false;
                    int i = 0;
                    while (i < tag.length() && Character.isWhitespace(tag.charAt(i))) i++;
                    if (i >= tag.length() || tag.charAt(i) != '<') return false;
                    i++;
                    while (i < tag.length() && Character.isWhitespace(tag.charAt(i))) i++;
                    return i < tag.length() && tag.charAt(i) == '/';
                }

                private boolean isSelfClosingTagToken(String tag) {
                    if (tag == null || tag.isEmpty()) return false;
                    int gt = tag.lastIndexOf('>');
                    if (gt < 0) return false;
                    int i = gt - 1;
                    while (i >= 0 && Character.isWhitespace(tag.charAt(i))) i--;
                    return i >= 0 && tag.charAt(i) == '/';
                }

                private void appendTagWithSyntaxColors(
                        TextFlow flow,
                        String tag,
                        String tagNameColor,
                        String attrColor,
                        String valueColor
                ) {
                    if (tag == null || tag.isEmpty()) return;

                    int i = 0;
                    if (tag.charAt(i) == '<') {
                        appendChunk(flow, "<", tagNameColor);
                        i++;
                    }

                    if (i < tag.length() && tag.charAt(i) == '/') {
                        appendChunk(flow, "/", tagNameColor);
                        i++;
                    }

                    int nameStart = i;
                    while (i < tag.length() && isTagNameChar(tag.charAt(i))) i++;
                    if (i > nameStart) {
                        appendChunk(flow, tag.substring(nameStart, i), tagNameColor);
                    }

                    while (i < tag.length()) {
                        char ch = tag.charAt(i);

                        if (ch == '>') {
                            appendChunk(flow, ">", tagNameColor);
                            i++;
                            continue;
                        }

                        if (ch == '"' || ch == '\'') {
                            char quote = ch;
                            appendChunk(flow, String.valueOf(quote), attrColor);
                            i++;
                            int valStart = i;
                            while (i < tag.length() && tag.charAt(i) != quote) i++;
                            if (i > valStart) {
                                appendChunk(flow, tag.substring(valStart, i), valueColor);
                            }
                            if (i < tag.length()) {
                                appendChunk(flow, String.valueOf(quote), attrColor);
                                i++;
                            }
                            continue;
                        }

                        if (isAttrNameChar(ch)) {
                            int attrStart = i;
                            while (i < tag.length() && isAttrNameChar(tag.charAt(i))) i++;
                            appendChunk(flow, tag.substring(attrStart, i), attrColor);
                            continue;
                        }

                        appendChunk(flow, String.valueOf(ch), attrColor);
                        i++;
                    }
                }

                private boolean isTagNameChar(char ch) {
                    return Character.isLetterOrDigit(ch) || ch == '_' || ch == ':' || ch == '-' || ch == '.';
                }

                private boolean isAttrNameChar(char ch) {
                    return Character.isLetterOrDigit(ch) || ch == '_' || ch == ':' || ch == '-' || ch == '.';
                }

                private void appendChunk(TextFlow flow, String chunk, String color) {
                    if (chunk == null || chunk.isEmpty()) return;
                    Text node = new Text(chunk);
                    node.setFill(Color.web(color));
                    node.setStyle("-fx-font-weight: bold;");
                    flow.getChildren().add(node);
                }

                @Override
                public void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);

                    if (!empty) {

                        if (!getStyleClass().contains("custom-table-cell")) {
                            getStyleClass().add("custom-table-cell");
                        }
//                        int rowIndex = getIndex();
//                        String rowBg = (rowIndex % 2 == 0) ? "rgba(0, 0, 0, 0.5)" : "rgba(0, 0, 0, 0.6)";
//
//                        String styleBase =
//                                "-fx-background-color: " + rowBg + ";" +
//                                        "-fx-border-image-source: url('" + fullTexturePathNormal + "');" +
//                                        "-fx-font-weight: bold;" +
//                                        "-fx-text-fill: #80d2a2;";
//
//                        //
//                        if (isEditing || isSelected()) {
//                            setStyle(styleBase.replace(fullTexturePathNormal, fullTexturePathSelected));
//                        } else if (isHovered) {
//                            setStyle(styleBase.replace(fullTexturePathNormal, fullTexturePathOver));
//                        } else {
//                            setStyle(styleBase);
//                        }

                        applyStyledCellContent(item);

                    } else {
                        setText(null);
                        setGraphic(null);
                        setContentDisplay(ContentDisplay.TEXT_ONLY);
                        setStyle("");
                        isEditing = false;
                    }
                }

                @Override
                public void startEdit() {
                    super.startEdit();
                    if (!isEditing()) {
                        return;
                    }
                    isEditing = true;
                    updateCellStyle();

                    if (getGraphic() instanceof TextField textField) {
                        textField.getStyleClass().add("table-textfield-editing");

                        textField.setOnAction(e -> {
                            commitEdit(textField.getText());
                            e.consume();
                        });
                    }

                    Platform.runLater(() -> {
                        if (getGraphic() instanceof TextField textField) {
                            textField.requestFocus();
                            textField.selectAll();
                        }
                    });
                }

                @Override
                public void commitEdit(String newValue) {
                    super.commitEdit(newValue);
                    isEditing = false;
                    setTextFill(Color.web("#80d2a2"));
                    setFont(Font.font(
                            getFont().getFamily(),
                            FontWeight.BOLD,
                            getFont().getSize()
                    ));

                    LocalizationData rowData = getTableView().getItems().get(getIndex());
                    String columnName = getTableColumn().getText();

                    switch (columnName) {
                        case "ruRU" -> rowData.setRuRu(newValue);
                        case "deDE" -> rowData.setDeDe(newValue);
                        case "enUS" -> rowData.setEnUs(newValue);
                        case "esMX" -> rowData.setEsMx(newValue);
                        case "esES" -> rowData.setEsEs(newValue);
                        case "frFR" -> rowData.setFrFr(newValue);
                        case "itIT" -> rowData.setItIt(newValue);
                        case "plPL" -> rowData.setPlPl(newValue);
                        case "ptBR" -> rowData.setPtBr(newValue);
                        case "koKR" -> rowData.setKoKr(newValue);
                        case "zhCN" -> rowData.setZhCn(newValue);
                        case "zhTW" -> rowData.setZhTw(newValue);
                    }

                    CustomTableView.this.refresh();
                }

                @Override
                public void cancelEdit() {
                    super.cancelEdit();
                    isEditing = false;
                    updateCellStyle();
                }

            });

        }
    }

    private void hideHeaderSortedArrow() {
        Platform.runLater(() -> {
            Parent header = (Parent) this.lookup("TableHeaderRow");
            if (header == null) {
                //    AppLog.info("TableHeaderRow
            } else {
                //      AppLog.info("

                ImageView sortIcon = new ImageView(new Image(
                        getClass().getResource("/Assets/Textures/ui_battlenet_glues_greenbuttons_alternate_largedisabled_ONE_UPSCALE_APS.png").toExternalForm()
                ));
                sortIcon.setFitWidth(0);
                sortIcon.setFitHeight(0);
                sortIcon.setImage(null);

                for (var v : getColumns()) {
                    v.setSortNode(sortIcon);
                }

            }
        });
    }

    private void enableHeaderColumnSelectionHighlighting() {
        Platform.runLater(() -> {
            this.lookupAll(".column-header").forEach(header -> {
                if (header.getProperties().putIfAbsent("lv.lenc.headerOverlayHook", Boolean.TRUE) != null) {
                    return;
                }
            });
            updateHeaderStateStyles();
        });
    }

    private void enableHeaderResizeCursorHints() {
        Platform.runLater(() -> {
            this.lookupAll(".column-header").forEach(header -> {
                if (header.getProperties().putIfAbsent("lv.lenc.resizeCursorHook", Boolean.TRUE) != null) {
                    return;
                }
                header.setOnMouseMoved(event -> {
                    double threshold = UiScaleHelper.scaleX(10);
                    boolean nearEdge = event.getX() >= Math.max(0, header.getBoundsInLocal().getWidth() - threshold);
                    header.setCursor(nearEdge ? CustomCursorManager.horizontalResizeCursor() : null);
                });
                header.setOnMouseDragged(event ->
                        header.setCursor(CustomCursorManager.horizontalResizeCursor()));
                header.setOnMouseExited(event -> header.setCursor(null));
                header.setOnMouseReleased(event -> header.setCursor(null));
            });
            this.lookupAll(".column-resize-line").forEach(CustomCursorManager::applyHorizontalResizeCursor);
        });
    }

    private void enableHeaderSortClickFallback() {
        Platform.runLater(() -> {
            this.lookupAll(".column-header").forEach(header -> {
                if (header.getProperties().putIfAbsent("lv.lenc.sortClickHook", Boolean.TRUE) != null) {
                    return;
                }
                header.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_CLICKED, event -> {
                    if (event.getButton() != javafx.scene.input.MouseButton.PRIMARY || event.getClickCount() != 1) {
                        return;
                    }
                    double threshold = UiScaleHelper.scaleX(10);
                    if (event.getX() >= Math.max(0, header.getBoundsInLocal().getWidth() - threshold)) {
                        return;
                    }
                    String headerKey = resolveHeaderKey(header);
                    if (headerKey == null || headerKey.isBlank()) {
                        return;
                    }
                    TableColumn<LocalizationData, ?> column = findCol(headerKey);
                    if (column == null || !column.isSortable()) {
                        return;
                    }
                    cycleHeaderSort(column);
                    event.consume();
                });
            });
        });
    }

    private void cycleHeaderSort(TableColumn<LocalizationData, ?> column) {
        if (column == null) {
            return;
        }
        ObservableList<TableColumn<LocalizationData, ?>> sortOrder = getSortOrder();
        if (!sortOrder.contains(column)) {
            sortOrder.clear();
            sortOrder.add(column);
            column.setSortType(TableColumn.SortType.ASCENDING);
        } else if (column.getSortType() == TableColumn.SortType.ASCENDING) {
            column.setSortType(TableColumn.SortType.DESCENDING);
        } else {
            sortOrder.remove(column);
        }
        sort();
    }
    private static String normalizeUi(String raw) {
        if (raw == null) return "";
        String s = raw.trim().replace("-", "").replace("_", "");
        if (s.length() < 4) return raw.trim();

        String lang = s.substring(0, 2).toLowerCase(Locale.ROOT);
        String region = s.substring(2).toUpperCase(Locale.ROOT);
        return lang + region;
    }
    public void loadLanguagesToTable(Map<String, File> langFiles) {
        detectedHeaderColumns.clear();
        pendingSaveHeaderColumns.clear();
        activeHeaderColumnKey = null;
        if (langFiles == null || langFiles.isEmpty()) {
            lastLoadWasMulti = false;
            loadedUiLanguages.clear();
            detectedUiByColumn.clear();
            currentSourceUi = null;
            setItems(FXCollections.observableArrayList());
            Platform.runLater(this::refresh);
            return;
        }

        lastLoadWasMulti = langFiles.size() > 1;
        loadedUiLanguages.clear();
        detectedUiByColumn.clear();
        for (var e : langFiles.entrySet()) {
            File f = e.getValue();
            if (f != null && f.exists()) {
                loadedUiLanguages.add(e.getKey());
            }
        }
        if (langFiles.size() == 1) {
            currentSourceUi = langFiles.keySet().iterator().next();
        }
        Set<String> columnsToHighlight = ConcurrentHashMap.newKeySet();

        Map<String, Map<String, String>> perLang = langFiles.entrySet() // Map: langCode -> (KEY:VALUE)
                .parallelStream() // threaded
                .filter(entry -> entry.getValue() != null && entry.getValue().exists()) //
                .collect(Collectors.toConcurrentMap(
                        // MAP/KEY - RURU -> value GameString.txt
                        entry -> entry.getKey(), // Name Package
                        entry -> {
                            File file = entry.getValue(); // get into file
                            String fileText = null;
                            try {
                                fileText = java.nio.file.Files.readString(file.toPath()); // File to string
                            } catch (IOException e) {
                                AppLog.info("Error " + file);
                            }
                            if (fileText == null) return Collections.emptyMap(); // if did`nt read

                            Map<String, String> keyValueMap = parseKeyValue(fileText); // Get KEY:VALUE and get Value
                            List<String> values = new ArrayList<>(keyValueMap.values());
                            // HighLight
                            String detectedLang = detectLanguage(values); // Check Value Language
                            String normalizedColumn = normalizeUi(entry.getKey());
                            String normalizedDetected = normalizeUi(detectedLang);
                            detectedUiByColumn.put(
                                    normalizedColumn,
                                    (normalizedDetected == null || normalizedDetected.isBlank() || "unknown".equalsIgnoreCase(normalizedDetected))
                                            ? normalizedColumn
                                            : normalizedDetected
                            );
                            if (!entry.getKey().equalsIgnoreCase(detectedLang)) {
                                columnsToHighlight.add(entry.getKey());
                            }
                            return keyValueMap; // (ruRU): ("HELLO" -> "
                        }
                ));
        //Table
        Set<String> allKeys = perLang.values().stream() // // Take all keys from loaded language maps
                .flatMap(m -> m.keySet().stream())//
                .collect(Collectors.toCollection(LinkedHashSet::new));//

        ObservableList<LocalizationData> rows = FXCollections.observableArrayList(); //
        // Add to table
        for (String key : allKeys) { // Iterate over all unique keys
            LocalizationData id = new LocalizationData(key); //
            for (String code : perLang.keySet()) { // // For each language (e.g. "ruRU", "enUS", ...)
                String val = perLang.get(code).get(key); // // get value by key for this language
                String norm = normalizeUi(code); // "ptbr" -> "ptBR", "zhcn"
                switch (norm) {
                    case "ruRU" -> id.setRuRu(val);
                    case "deDE" -> id.setDeDe(val);
                    case "enUS" -> id.setEnUs(val);
                    case "esMX" -> id.setEsMx(val);
                    case "esES" -> id.setEsEs(val);
                    case "frFR" -> id.setFrFr(val);
                    case "itIT" -> id.setItIt(val);
                    case "plPL" -> id.setPlPl(val);
                    case "ptBR" -> id.setPtBr(val);
                    case "koKR" -> id.setKoKr(val);
                    case "zhCN" -> id.setZhCn(val);
                    case "zhTW" -> id.setZhTw(val);
                }
            }

            AppLog.info("Loaded codes: " + perLang.keySet());
            rows.add(id);
        }
        setItems(rows); // set data into table
        String resolvedMain = resolvePreferredOrDetectedMainSource();
        if (resolvedMain != null && !resolvedMain.isBlank()) {
            currentSourceUi = resolvedMain;
            detectedHeaderColumns.add(resolvedMain);
            activeHeaderColumnKey = null;
        }
        Platform.runLater(this::refresh);
        // Keep exactly one MAIN highlighted column.
        columnsToHighlight.clear();
        requestHeaderStateRefresh();

    }

    public void loadLanguagesToTable(File file) {
        detectedHeaderColumns.clear();
        pendingSaveHeaderColumns.clear();
        activeHeaderColumnKey = null;
        lastLoadWasMulti = false;
        loadedUiLanguages.clear();
        detectedUiByColumn.clear();
        if (file == null || !file.exists()) return;
        String fileText = null;
        try {
            fileText = java.nio.file.Files.readString(file.toPath());
        } catch (IOException e) {
            // AppLog.info("Error " + file);
        }
        if (fileText == null) return;

        //
        Map<String, String> keyValueMap = parseKeyValue(fileText);
        List<String> values = new ArrayList<>(keyValueMap.values());

        String detectedLang = detectLanguage(values);
        // highlightColumn(detectedLang);
        // (detectedLang)
        ObservableList<LocalizationData> rows = FXCollections.observableArrayList();
        BiConsumer<LocalizationData, String> setter;
        switch (detectedLang) {
            case "ruRU" -> setter = LocalizationData::setRuRu;
            case "deDE" -> setter = LocalizationData::setDeDe;
            case "enUS" -> setter = LocalizationData::setEnUs;
            case "esMX" -> setter = LocalizationData::setEsMx;
            case "esES" -> setter = LocalizationData::setEsEs;
            case "frFR" -> setter = LocalizationData::setFrFr;
            case "itIT" -> setter = LocalizationData::setItIt;
            case "plPL" -> setter = LocalizationData::setPlPl;
            case "ptBR" -> setter = LocalizationData::setPtBr;
            case "koKR" -> setter = LocalizationData::setKoKr;
            case "zhCN" -> setter = LocalizationData::setZhCn;
            case "zhTW" -> setter = LocalizationData::setZhTw;
            default -> setter = (d, v) -> {};
        }
        //  detectedLang = detectLanguage(values);
        loadedUiLanguages.add(detectedLang);
        detectedUiByColumn.put(detectedLang, detectedLang);
        currentSourceUi = resolvePreferredOrDetectedMainSource();
        if (currentSourceUi != null && !currentSourceUi.isBlank()) {
            detectedHeaderColumns.add(currentSourceUi);
        }
        activeHeaderColumnKey = null;
        for (Map.Entry<String, String> entry : keyValueMap.entrySet()) {
            LocalizationData data = new LocalizationData(entry.getKey());
            setter.accept(data, entry.getValue()); // "data" -> data.setRuRu(entry.getValue());
            rows.add(data);
        }
        setItems(rows);
        Platform.runLater(this::refresh);
        highlightColumn(detectedLang); // Highlight detected column

    }

    public static Map<String, String> parseKeyValue(String text) {
        return Arrays.stream(text.split("\\r?\\n"))
                .map(line -> line.split("=", 2))
                .filter(parts -> parts.length == 2)
                .collect(Collectors.toMap(
                        parts -> parts[0],
                        parts -> parts[1],
                        (a, b) -> a,                     //
                        LinkedHashMap::new              //
                ));
    }

    public void highlightColumn(String lang) {
        String normalized = normalizeUi(lang);
        if (!SUPPORTED_LANGS.contains(normalized)) {
            return;
        }
        detectedHeaderColumns.add(normalized);
        requestHeaderStateRefresh();
    }

    public void markPendingSaveForTarget(String targetUi) {
        String normalized = normalizeUi(targetUi);
        if (!SUPPORTED_LANGS.contains(normalized)) {
            return;
        }
        pendingSaveHeaderColumns.add(normalized);
        requestHeaderStateRefresh();
    }

    public void markPendingSaveForTargets(java.util.Collection<String> targetUis) {
        if (targetUis == null || targetUis.isEmpty()) {
            return;
        }
        boolean changed = false;
        for (String targetUi : targetUis) {
            String normalized = normalizeUi(targetUi);
            if (!SUPPORTED_LANGS.contains(normalized)) {
                continue;
            }
            if (pendingSaveHeaderColumns.add(normalized)) {
                changed = true;
            }
        }
        if (changed) {
            requestHeaderStateRefresh();
        }
    }

    public void markPendingSaveForAllTargets(String sourceUi) {
        String normalizedSource = normalizeUi(sourceUi);
        Set<String> targets = loadedUiLanguages.isEmpty()
                ? SUPPORTED_LANGS
                : loadedUiLanguages.stream().map(CustomTableView::normalizeUi).collect(Collectors.toCollection(LinkedHashSet::new));
        for (String target : targets) {
            if (!SUPPORTED_LANGS.contains(target) || target.equalsIgnoreCase(normalizedSource)) {
                continue;
            }
            pendingSaveHeaderColumns.add(target);
        }
        requestHeaderStateRefresh();
    }

    public void clearPendingSaveForTarget(String targetUi) {
        String normalized = normalizeUi(targetUi);
        if (normalized == null || normalized.isBlank()) {
            return;
        }
        pendingSaveHeaderColumns.remove(normalized);
        requestHeaderStateRefresh();
    }

    public void clearAllPendingSaveHeaders() {
        pendingSaveHeaderColumns.clear();
        requestHeaderStateRefresh();
    }

    private String resolveHeaderKey(Node headerNode) {
        if (!(headerNode instanceof Parent parent)) {
            return null;
        }
        Node labelNode = parent.lookup(".label");
        if (!(labelNode instanceof Label label)) {
            return null;
        }
        String headerText = label.getText();
        if (headerText == null || headerText.isBlank()) {
            return null;
        }
        String normalizedUi = normalizeUi(headerText);
        if (SUPPORTED_LANGS.contains(normalizedUi)) {
            return normalizedUi;
        }
        return headerText.trim();
    }

    private void updateHeaderStateStyles() {
        Runnable apply = () -> {
            this.lookupAll(".column-header").forEach(header -> {
                String headerKey = resolveHeaderKey(header);
                header.getStyleClass().removeAll(
                        "header-selected-overlay",
                        "header-detected-source",
                        "header-pending-save"
                );
                if (headerKey == null || headerKey.isBlank()) {
                    return;
                }
                if (headerKey.equals(activeHeaderColumnKey)) {
                    header.getStyleClass().add("header-selected-overlay");
                }
                if (detectedHeaderColumns.contains(headerKey)) {
                    header.getStyleClass().add("header-detected-source");
                }
                if (pendingSaveHeaderColumns.contains(headerKey)) {
                    header.getStyleClass().add("header-pending-save");
                }
            });
        };
        if (Platform.isFxApplicationThread()) {
            apply.run();
        } else {
            Platform.runLater(apply);
        }
    }


    public String detectLanguage(List<String> values) {
        if (langService == null) return "unknown";

        String bigString = values.stream()
                .filter(this::isMeaningful)
                .map(String::trim)
                .collect(java.util.stream.Collectors.joining(" "));

        if (bigString.isBlank()) return "unknown";

        try {
            return langService.detectLanguage(bigString);
        } catch (LangDetectException e) {
            AppLog.info("Error: " + e.getMessage());
            return "unknown";
        }
    }
    public void updatePlaceholderText(String text) {
        Label placeholder = (Label) getPlaceholder();
        if (placeholder != null) {
            placeholder.setText(text);
        } else {
            Label newPlaceholder = new Label(text);
            setPlaceholder(newPlaceholder);
        }
        applyDynamicTypography();
    }
    private boolean isMeaningful(String s) {
        if (s == null) return false;
        s = s.trim();
        if (s.isEmpty()) return false;
        return !s.equalsIgnoreCase("null"); // if "null" comes as a literal string
    }
    public String getMainSourceLang() {
        String preferred = resolvePreferredOrDetectedMainSource();
        if (preferred != null && !preferred.isBlank()) {
            return preferred;
        }

        Map<String, Integer> langCounts = new HashMap<>();

        for (TableColumn<LocalizationData, ?> col : getColumns()) {
            String lang = col.getText();
            if (!SUPPORTED_LANGS.contains(lang)) continue;

            int nonEmptyCount = 0;

            for (LocalizationData row : getItems()) {
                String val = getValueForLang(row, lang);
                if (isMeaningful(val)) nonEmptyCount++;
            }

            langCounts.put(lang, nonEmptyCount);
        }

        return langCounts.entrySet().stream()
                .max(Comparator.comparingInt(Map.Entry::getValue))
                .map(Map.Entry::getKey)
                .orElse("enUS");
    }

    private String resolvePreferredOrDetectedMainSource() {
        String preferred = normalizeUi(preferredMainSourceUi);
        if (preferred != null
                && !preferred.isBlank()
                && loadedUiLanguages.stream().map(CustomTableView::normalizeUi).anyMatch(preferred::equalsIgnoreCase)) {
            return preferred;
        }

        String current = normalizeUi(currentSourceUi);
        if (current != null
                && !current.isBlank()
                && loadedUiLanguages.stream().map(CustomTableView::normalizeUi).anyMatch(current::equalsIgnoreCase)) {
            return current;
        }

        for (String loadedUi : loadedUiLanguages) {
            String normalizedLoaded = normalizeUi(loadedUi);
            String detectedUi = resolveDetectedColumnUi(normalizedLoaded);
            if (normalizedLoaded.equalsIgnoreCase(detectedUi)) {
                return normalizedLoaded;
            }
        }

        return loadedUiLanguages.stream()
                .map(CustomTableView::normalizeUi)
                .filter(SUPPORTED_LANGS::contains)
                .findFirst()
                .orElse(null);
    }
    private String getValueForLang(LocalizationData d, String lang) {
        return switch (lang) {
            case "ruRU" -> d.getRuRu();
            case "deDE" -> d.getDeDE();
            case "enUS" -> d.getEnUs();
            case "esMX" -> d.getEsMx();
            case "esES" -> d.getEsEs();
            case "frFR" -> d.getFrFr();
            case "itIT" -> d.getItIt();
            case "plPL" -> d.getPlPl();
            case "ptBR" -> d.getPtBr();
            case "koKR" -> d.getKoKr();
            case "zhCN" -> d.getZhCn();
            case "zhTW" -> d.getZhTw();
            default -> null;
        };
    }
    // translation logic
    private void setValueForLang(LocalizationData d, String lang, String value) {
        switch (lang) {
            case "ruRU" -> d.setRuRu(value);
            case "deDE" -> d.setDeDe(value);
            case "enUS" -> d.setEnUs(value);
            case "esMX" -> d.setEsMx(value);
            case "esES" -> d.setEsEs(value);
            case "frFR" -> d.setFrFr(value);
            case "itIT" -> d.setItIt(value);
            case "plPL" -> d.setPlPl(value);
            case "ptBR" -> d.setPtBr(value);
            case "koKR" -> d.setKoKr(value);
            case "zhCN" -> d.setZhCn(value);
            case "zhTW" -> d.setZhTw(value);
        }
    }
    // translate

    private static String toApiLang(String uiLang) {
        return switch (uiLang) {
            case "ruRU" -> "ru";
            case "deDE" -> "de";
            case "enUS" -> "en";
            case "esMX", "esES" -> "es";
            case "frFR" -> "fr";
            case "itIT" -> "it";
            case "plPL" -> "pl";
            case "ptBR" -> "pt";
            case "koKR" -> "ko";
            case "zhCN", "zhTW" -> "zh";
            default -> "auto";
        };
    }

    private String resolveDetectedColumnUi(String requestedUi) {
        String normalizedRequested = normalizeUi(requestedUi);
        if (normalizedRequested == null || normalizedRequested.isBlank()) {
            return "enUS";
        }
        String detected = detectedUiByColumn.get(normalizedRequested);
        if (detected == null || detected.isBlank() || "unknown".equalsIgnoreCase(detected)) {
            return normalizedRequested;
        }
        return normalizeUi(detected);
    }
    public void translateFromColumnToOthers(
            LibreTranslateApi api,
            String sourceLang,
            java.util.function.BooleanSupplier cancelled
    ) {
        final java.util.function.BooleanSupplier cancelledFinal = (cancelled != null) ? cancelled : () -> false;
        final java.util.function.BooleanSupplier stop = () ->
                cancelledFinal.getAsBoolean() || Thread.currentThread().isInterrupted();
        Set<String> changedTargets = new LinkedHashSet<>();

        // 1) source  (ui)
        String sourceUi = (sourceLang != null && !sourceLang.isBlank())
                ? sourceLang
                : getMainSourceLang();
        String effectiveSourceUi = resolveDetectedColumnUi(sourceUi);

        // 2) text source
        java.util.List<String> texts = getColumnValues(sourceUi);
        boolean hasAny = texts.stream().anyMatch(s -> s != null && !s.isBlank());
        if (!hasAny) {
            AppLog.error("[LT] nothing to translate in column " + sourceUi);
            return;
        }

        // 3) goal: UI -> ISO,  UI (esMX/esES -> es)
        java.util.List<String> targetsUi = getTargetColumnsExcluding(sourceUi);

        java.util.Map<String, java.util.List<String>> isoToUis = new java.util.LinkedHashMap<>();
        for (String ui : targetsUi) {
            if (stop.getAsBoolean()) return;
            String iso = toApiLang(ui);
            if (iso == null || iso.isBlank() || "auto".equalsIgnoreCase(iso)) continue;
            isoToUis.computeIfAbsent(iso, k -> new java.util.ArrayList<>()).add(ui);
        }

        java.util.List<String> targetsIso = new java.util.ArrayList<>(isoToUis.keySet());
        if (targetsIso.isEmpty()) {
            AppLog.error("[LT] no valid target languages");
            return;
        }

        // 4) ISO source
        String sourceIso = toApiLang(effectiveSourceUi);
        if (sourceIso == null || sourceIso.isBlank() || "auto".equalsIgnoreCase(sourceIso)) {
            sourceIso = "en";
        }

        // 5) translate to all ISO
        try {
            if (stop.getAsBoolean()) return;


            java.util.Map<String, java.util.List<String>> byIso =
                    lv.lenc.TranslationService.translateAll(texts, sourceIso, targetsIso);

            if (stop.getAsBoolean()) return;

            // 6)
            for (var e : byIso.entrySet()) {
                if (stop.getAsBoolean()) return;

                String iso = e.getKey();
                java.util.List<String> colValues = e.getValue();

                java.util.List<String> uis = isoToUis.getOrDefault(iso, java.util.List.of());
                for (String ui : uis) {
                    if (stop.getAsBoolean()) return;
                    setColumnValues(ui, colValues);
                    if (colValues.stream().anyMatch(v -> v != null && !v.isBlank())) {
                        changedTargets.add(ui);
                    }
                }
            }

            if (stop.getAsBoolean()) return;

            if (!changedTargets.isEmpty()) {
                markPendingSaveForTargets(changedTargets);
            }
            javafx.application.Platform.runLater(this::refresh);

        } catch (IOException ex) {
            AppLog.error("[LT] translate failed: " + ex.getMessage());
            AppLog.exception(ex);
        }
    }
    public void translateFromColumnToOthers(
            LibreTranslateApi api,
            String sourceLang,
            java.util.function.BooleanSupplier cancelled,
            ProgressListener progress
    ) {
        final java.util.function.BooleanSupplier cancelledFinal =
                (cancelled != null) ? cancelled : () -> false;

        final java.util.function.BooleanSupplier stop = () ->
                cancelledFinal.getAsBoolean() || Thread.currentThread().isInterrupted();

        String sourceUi = (sourceLang != null && !sourceLang.isBlank())
                ? sourceLang
                : getMainSourceLang();
        String effectiveSourceUi = resolveDetectedColumnUi(sourceUi);

        final java.util.List<String> targetsUi = java.util.List.of(
                        "ruRU", "deDE", "enUS", "esMX", "esES", "frFR", "itIT", "plPL", "ptBR", "koKR", "zhCN", "zhTW"
                ).stream()
                .filter(ui -> !ui.equalsIgnoreCase(sourceUi))
                .toList();
        Set<String> changedTargets = new LinkedHashSet<>();

        final int totalUiTargets = targetsUi.size();
        if (totalUiTargets == 0) return;

        java.util.List<LocalizationData> allRows = getItems();
        if (allRows == null || allRows.isEmpty()) return;

        final String sourceUiFinal = sourceUi;
        final String sourceIso = toApiLang(effectiveSourceUi);

        for (int targetIndex = 0; targetIndex < targetsUi.size(); targetIndex++) {
            if (stop.getAsBoolean()) return;

            String targetUi = targetsUi.get(targetIndex);
            String targetIso = toApiLang(targetUi);
            if (targetIso == null || targetIso.isBlank() || "auto".equalsIgnoreCase(targetIso)) {
                continue;
            }

            final String targetUiFinal = targetUi;
            final int targetIndexFinal = targetIndex;
            final int uiOrdinalFinal = targetIndex + 1;
            // Glossary is always applied before MT via stable term tokens.
            // Prompt hints are additionally sent only to providers that support instruction prompts.
            final boolean aiHintMode = TranslationService.backendSupportsGlossaryInflectionHints();

            java.util.List<LocalizationData> rowsToTranslate = new java.util.ArrayList<>();
            java.util.List<String> textsToTranslate = new java.util.ArrayList<>();
            java.util.List<GlossaryService.FrozenTerms> frozenForRows = new java.util.ArrayList<>();
            java.util.Map<String, String> batchGlossaryHints = aiHintMode
                    ? new java.util.LinkedHashMap<>()
                    : java.util.Collections.emptyMap();

            for (LocalizationData row : allRows) {
                if (stop.getAsBoolean()) return;
                if (row == null) continue;

                String sourceText = row.getByLang(sourceUi);
                if (sourceText == null || sourceText.isBlank()) continue;

                GlossaryService.Category category = GlossaryService.detectCategory(row.getKey());

                GlossaryService.FrozenTerms frozen = glossaryService.freezeTerms(
                        category,
                        effectiveSourceUi,
                        targetUi,
                        sourceText
                );
                if (aiHintMode) {
                    mergeGlossaryHintsLimited(
                            batchGlossaryHints,
                            glossaryService.collectTermHints(category, effectiveSourceUi, targetUi, sourceText, 12),
                            60
                    );
                }

                rowsToTranslate.add(row);
                textsToTranslate.add(frozen.preparedText());
                frozenForRows.add(frozen);
            }

            if (textsToTranslate.isEmpty()) {
                continue;
            }

            try {
                if (aiHintMode) {
                    lv.lenc.TranslationService.setRuntimeGlossaryHints(batchGlossaryHints);
                }
                java.util.List<String> out = lv.lenc.TranslationService.translatePreservingTagsBatched(
                        api,
                        textsToTranslate,
                        sourceIso,
                        targetIso,
                        stop,
                        (frac, msg) -> {
                            if (progress == null) return;
                            double totalFrac = ((double) targetIndexFinal + frac) / totalUiTargets;
                            String line1 = sourceUiFinal + " -> " + targetUiFinal + " (" + uiOrdinalFinal + "/" + totalUiTargets + ")";
                            String line2 = extractBatchLine(msg);
                            progress.onProgress(totalFrac, line1 + "||" + line2);
                        }
                );

                int count = Math.min(rowsToTranslate.size(), out.size());
                if (count > 0) {
                    List<String> translatedSlice = new ArrayList<>(count);
                    List<GlossaryService.FrozenTerms> frozenSlice = new ArrayList<>(count);
                    boolean targetChanged = false;
                    for (int i = 0; i < count; i++) {
                        translatedSlice.add(out.get(i));
                        frozenSlice.add(frozenForRows.get(i));
                    }
                    List<String> resolved = glossaryService.unfreezeTermsBatch(translatedSlice, frozenSlice, targetUi);
                    for (int i = 0; i < count; i++) {
                        String translated = resolved.get(i);
                        if (translated == null || translated.isBlank()) continue;
                        setValueByLang(rowsToTranslate.get(i), targetUi, translated);
                        targetChanged = true;
                    }
                    if (targetChanged) {
                        changedTargets.add(targetUi);
                    }
                }

            } catch (IOException | InterruptedException ex) {
                if (ex instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                if (stop.getAsBoolean()) return;
                AppLog.error("[LT] translate failed for " + targetUi + ": " + ex.getMessage());
                AppLog.exception(ex);
                throw new RuntimeException(ex);
            } finally {
                if (aiHintMode) {
                    lv.lenc.TranslationService.clearRuntimeGlossaryHints();
                }
            }
        }

        if (!stop.getAsBoolean() && progress != null) {
            progress.onProgress(1.0, sourceUiFinal + " -> all||done");
        }
        if (!stop.getAsBoolean()) {
            if (!changedTargets.isEmpty()) {
                markPendingSaveForTargets(changedTargets);
            }
            javafx.application.Platform.runLater(this::refresh);
        }
    }
    private static String extractBatchLine(String msg) {
        if (msg == null) return "";
        String trimmed = msg.trim();
        int idx = trimmed.toLowerCase(Locale.ROOT).indexOf("batch");
        if (idx >= 0) return trimmed.substring(idx).trim();
        return trimmed;
    }

    private static Map<String, String> collectHintsForIndexes(List<Map<String, String>> rowHints,
                                                              List<Integer> indexes,
                                                              int limit) {
        if (rowHints == null || indexes == null || indexes.isEmpty() || limit <= 0) {
            return java.util.Collections.emptyMap();
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (Integer idx : indexes) {
            if (idx == null || idx < 0 || idx >= rowHints.size()) continue;
            mergeGlossaryHintsLimited(out, rowHints.get(idx), limit);
            if (out.size() >= limit) break;
        }
        return out;
    }

    private static void mergeGlossaryHintsLimited(Map<String, String> target,
                                                  Map<String, String> incoming,
                                                  int limit) {
        if (target == null || incoming == null || incoming.isEmpty() || limit <= 0) {
            return;
        }
        for (Map.Entry<String, String> e : incoming.entrySet()) {
            if (target.size() >= limit) break;
            String src = e.getKey();
            String trg = e.getValue();
            if (src == null || src.isBlank() || trg == null || trg.isBlank()) continue;
            target.putIfAbsent(src, trg);
        }
    }

    // (ui-code: "enUS", "ruRU"...)
    private java.util.List<String> getColumnValues(String uiLang) {
        java.util.List<String> out = new java.util.ArrayList<>(getItems().size());
        for (LocalizationData row : getItems()) {
            String v = getValueForLang(row, uiLang);
            out.add(v == null ? "" : v);
        }
        return out;
    }

    //
    private java.util.List<String> getTargetColumnsExcluding(String sourceUi) {
        return SUPPORTED_LANGS.stream()
                .filter(l -> !l.equalsIgnoreCase(sourceUi))
                .toList();
    }

    //
    private void setColumnValues(String uiLang, java.util.List<String> values) {
        var items = getItems();
        int n = Math.min(items.size(), values.size());
        for (int i = 0; i < n; i++) {
            setValueForLang(items.get(i), uiLang, values.get(i));
        }
    }
    public void translateFromSourceToTarget(String sourceUi, String targetUi, AtomicBoolean cancelledFinal) {
        final BooleanSupplier stop = () ->
                (cancelledFinal != null && cancelledFinal.get()) || Thread.currentThread().isInterrupted();

        if (stop.getAsBoolean()) return;

        sourceUi = normalizeUi(sourceUi);
        targetUi = normalizeUi(targetUi);

        if (sourceUi == null || sourceUi.isBlank()) sourceUi = getMainSourceLang();
        if (targetUi == null || targetUi.isBlank()) return;
        if (targetUi.equalsIgnoreCase(sourceUi)) return;

        List<LocalizationData> allRows = getItems();
        if (allRows == null || allRows.isEmpty()) return;

        List<LocalizationData> rowsToTranslate = new ArrayList<>();
        List<String> textsToTranslate = new ArrayList<>();
        List<GlossaryService.FrozenTerms> frozenForRows = new ArrayList<>();
        List<String> actualSourceUis = new ArrayList<>();
        List<Map<String, String>> rowGlossaryHints = new ArrayList<>();
        boolean translatedAny = false;
        // Glossary is always applied before MT by injecting known terms into outgoing text.
        // Prompt hints are additionally sent only to providers that support instruction prompts.
        final boolean aiHintMode = TranslationService.backendSupportsGlossaryInflectionHints();

        for (LocalizationData row : allRows) {
            if (stop.getAsBoolean()) return;
            if (row == null) continue;

            String actualSourceUi = resolveDetectedColumnUi(sourceUi);
            String sourceText = row.getByLang(sourceUi);

            // Ć…Ā Äā‚¬Ā¢Ć…ĀÄ€ĀĆ…Ā Ä€Ā»Ć…Ā Ä†Ćø Ć…Ā Ä†Ā¦Ć…Ā Ä€ĀµĆ…ĀÄā€Ā¬Ć…Ā Ä€ĀµĆ…Ā Ä€Ā²Ć…Ā Ä€Ā¾Ć…Ā Ä€Ā´Ć…Ā Ä†ĆøĆ…Ā Ä€Ā¼ Ć…Ā Ä€Ć†Ć…Ā Äā‚¬Ā¢ Ć…Ā Ä€Ā² enUS Ć…Ā Ä†Ćø enUS Ć…ĀÄ€ĀĆ…Ā Ä€Ā¶Ć…Ā Ä€Āµ Ć…Ā Ä€Ā·Ć…Ā Ä€Ā°Ć…Ā Ä†Ā¦Ć…Ā Ä€Ā¾Ć…Ā Ä€Ā»Ć…Ā Ä€Ā½Ć…Ā Ä€ĀµĆ…Ā Ä€Ā½ Ć„ĀÄā€Ā¬Äā‚¬ĀÆ Ć…Ā Ä€Ā±Ć…Ā Ä€ĀµĆ…ĀÄā€Ā¬Ć…ĀÄā‚¬ĀĆ…Ā Ä€Ā¼ enUS Ć…Ā Ć…ā€”Ć…Ā Ä€Ā°Ć…Ā Ć…ā€” Ć…Ā Ä†ĆøĆ…ĀÄ€ĀĆ…ĀÄā‚¬ĀĆ…Ā Ä€Ā¾Ć…ĀÄā‚¬ļ£¼Ć…Ā Ä€Ā½Ć…Ā Ä†ĆøĆ…Ā Ć…ā€”
            String enText = row.getByLang("enUS");
            if ((sourceText == null || sourceText.isBlank())
                    && !"enUS".equalsIgnoreCase(targetUi)
                    && enText != null && !enText.isBlank()) {
                actualSourceUi = resolveDetectedColumnUi("enUS");
                sourceText = enText;
            }

            if (sourceText == null || sourceText.isBlank()) continue;

            GlossaryService.Category category = GlossaryService.detectCategory(row.getKey());

            GlossaryService.FrozenTerms frozen = glossaryService.freezeTerms(
                    category,
                    actualSourceUi,
                    targetUi,
                    sourceText
            );
            Map<String, String> hintsForRow = aiHintMode
                    ? glossaryService.collectTermHints(category, actualSourceUi, targetUi, sourceText, 12)
                    : java.util.Collections.emptyMap();

            rowsToTranslate.add(row);
            textsToTranslate.add(frozen.preparedText());
            frozenForRows.add(frozen);
            actualSourceUis.add(actualSourceUi);
            rowGlossaryHints.add(hintsForRow);
        }

        if (textsToTranslate.isEmpty()) {
            refresh();
            return;
        }

        try {
            // Ć…Ā Ä€Ā Ć…Ā Ä€Ā°Ć…Ā Ä€Ā·Ć…Ā Ä€Ā±Ć…Ā Ä†ĆøĆ…Ā Ä€Ā²Ć…Ā Ä€Ā°Ć…Ā Ä€ĀµĆ…Ā Ä€Ā¼ Ć…Ā Ä†Ā¦Ć…Ā Ä€Ā¾ Ć…ĀÄā‚¬Ė›Ć…Ā Ä€Ā°Ć…Ā Ć…ā€”Ć…ĀÄā‚¬ĀĆ…Ā Ä†ĆøĆ…ĀÄā‚¬ļ£¼Ć…Ā Ä€ĀµĆ…ĀÄ€ĀĆ…Ā Ć…ā€”Ć…Ā Ä€Ā¾Ć…Ā Ä€Ā¼Ć…ĀÄ€Ā source language
            Map<String, List<Integer>> sourceIsoToIndexes = new LinkedHashMap<>();
            for (int i = 0; i < actualSourceUis.size(); i++) {
                String sourceIso = toApiLang(actualSourceUis.get(i));
                if (sourceIso == null || sourceIso.isBlank() || "auto".equalsIgnoreCase(sourceIso)) {
                    sourceIso = "en";
                }
                sourceIsoToIndexes.computeIfAbsent(sourceIso, k -> new ArrayList<>()).add(i);
            }

            String targetIso = toApiLang(targetUi);
            if (targetIso == null || targetIso.isBlank() || "auto".equalsIgnoreCase(targetIso)) return;

            for (Map.Entry<String, List<Integer>> entry : sourceIsoToIndexes.entrySet()) {
                if (stop.getAsBoolean()) return;

                String sourceIso = entry.getKey();
                List<Integer> indexes = entry.getValue();

                List<String> batchTexts = new ArrayList<>(indexes.size());
                for (Integer idx : indexes) {
                    batchTexts.add(textsToTranslate.get(idx));
                }
                Map<String, String> groupGlossaryHints = aiHintMode
                        ? collectHintsForIndexes(rowGlossaryHints, indexes, 60)
                        : java.util.Collections.emptyMap();

                Map<String, List<String>> byIso;
                if (aiHintMode) {
                    TranslationService.setRuntimeGlossaryHints(groupGlossaryHints);
                }
                try {
                    byIso = TranslationService.translateAll(batchTexts, sourceIso, List.of(targetIso));
                } finally {
                    if (aiHintMode) {
                        TranslationService.clearRuntimeGlossaryHints();
                    }
                }

                if (stop.getAsBoolean()) return;

                List<String> translated = byIso.get(targetIso);
                if (translated == null) continue;

                int count = Math.min(indexes.size(), translated.size());
                if (count > 0) {
                    List<String> translatedSlice = new ArrayList<>(count);
                    List<GlossaryService.FrozenTerms> frozenSlice = new ArrayList<>(count);
                    List<Integer> originalIndexes = new ArrayList<>(count);
                    for (int j = 0; j < count; j++) {
                        int originalIndex = indexes.get(j);
                        originalIndexes.add(originalIndex);
                        translatedSlice.add(translated.get(j));
                        frozenSlice.add(frozenForRows.get(originalIndex));
                    }
                    List<String> resolved = glossaryService.unfreezeTermsBatch(translatedSlice, frozenSlice, targetUi);
                    for (int j = 0; j < count; j++) {
                        String outText = resolved.get(j);
                        if (outText == null || outText.isBlank()) continue;
                        int originalIndex = originalIndexes.get(j);
                        setValueByLang(rowsToTranslate.get(originalIndex), targetUi, outText);
                        translatedAny = true;
                    }
                }
            }

            if (translatedAny) {
                markPendingSaveForTarget(targetUi);
            }
            refresh();
        } catch (IOException e) {
            AppLog.exception(e);
        }
    }
    public void translateFromSourceToTarget(
            LibreTranslateApi api,
            String sourceUi,
            String targetUi,
            java.util.function.BooleanSupplier cancelled,
            ProgressListener progress
    ) {
        final java.util.function.BooleanSupplier stop =
                (cancelled != null) ? cancelled : () -> false;

        sourceUi = normalizeUi(sourceUi);
        targetUi = normalizeUi(targetUi);

        if (sourceUi == null || sourceUi.isBlank()) sourceUi = getMainSourceLang();
        if (targetUi == null || targetUi.isBlank()) return;
        if (targetUi.equalsIgnoreCase(sourceUi)) return;

        String targetIso = toApiLang(targetUi);
        if (targetIso == null || targetIso.isBlank() || "auto".equalsIgnoreCase(targetIso)) return;

        List<LocalizationData> allRows = getItems();
        if (allRows == null || allRows.isEmpty()) return;

        List<LocalizationData> rowsToTranslate = new ArrayList<>();
        List<String> textsToTranslate = new ArrayList<>();
        List<GlossaryService.FrozenTerms> frozenForRows = new ArrayList<>();
        List<String> actualSourceUis = new ArrayList<>();
        List<Map<String, String>> rowGlossaryHints = new ArrayList<>();
        boolean translatedAny = false;
        // Glossary is always applied before MT by injecting known terms into outgoing text.
        // Prompt hints are additionally sent only to providers that support instruction prompts.
        final boolean aiHintMode = TranslationService.backendSupportsGlossaryInflectionHints();

        for (LocalizationData row : allRows) {
            if (stop.getAsBoolean()) return;
            if (row == null) continue;

            String actualSourceUi = resolveDetectedColumnUi(sourceUi);
            String sourceText = row.getByLang(sourceUi);

            String enText = row.getByLang("enUS");
            if ((sourceText == null || sourceText.isBlank())
                    && !"enUS".equalsIgnoreCase(targetUi)
                    && enText != null && !enText.isBlank()) {
                actualSourceUi = resolveDetectedColumnUi("enUS");
                sourceText = enText;
            }

            if (sourceText == null || sourceText.isBlank()) continue;

            GlossaryService.Category category = GlossaryService.detectCategory(row.getKey());

            GlossaryService.FrozenTerms frozen = glossaryService.freezeTerms(
                    category,
                    actualSourceUi,
                    targetUi,
                    sourceText
            );
            Map<String, String> hintsForRow = aiHintMode
                    ? glossaryService.collectTermHints(category, actualSourceUi, targetUi, sourceText, 12)
                    : java.util.Collections.emptyMap();

            rowsToTranslate.add(row);
            textsToTranslate.add(frozen.preparedText());
            frozenForRows.add(frozen);
            actualSourceUis.add(actualSourceUi);
            rowGlossaryHints.add(hintsForRow);
        }

        if (textsToTranslate.isEmpty()) {
            Platform.runLater(this::refresh);
            if (progress != null) progress.onProgress(1.0, sourceUi + " -> " + targetUi + "||");
            return;
        }

        try {
            Map<String, List<Integer>> sourceIsoToIndexes = new LinkedHashMap<>();
            for (int i = 0; i < actualSourceUis.size(); i++) {
                String sourceIso = toApiLang(actualSourceUis.get(i));
                if (sourceIso == null || sourceIso.isBlank() || "auto".equalsIgnoreCase(sourceIso)) {
                    sourceIso = "en";
                }
                sourceIsoToIndexes.computeIfAbsent(sourceIso, k -> new ArrayList<>()).add(i);
            }

            final String sourceUiFinal = sourceUi;
            final String targetUiFinal = targetUi;
            final int totalWorkItems = sourceIsoToIndexes.values().stream()
                    .mapToInt(List::size)
                    .sum();
            final int[] doneItems = new int[] {0};

            if (progress != null) {
                progress.onProgress(0.0, sourceUiFinal + " -> " + targetUiFinal + "||batch 0");
            }

            for (Map.Entry<String, List<Integer>> entry : sourceIsoToIndexes.entrySet()) {
                if (stop.getAsBoolean()) return;

                String sourceIso = entry.getKey();
                List<Integer> indexes = entry.getValue();
                final int groupSize = indexes.size();
                final int groupDoneBefore = doneItems[0];

                List<String> batchTexts = new ArrayList<>(indexes.size());
                for (Integer idx : indexes) {
                    batchTexts.add(textsToTranslate.get(idx));
                }
                Map<String, String> groupGlossaryHints = aiHintMode
                        ? collectHintsForIndexes(rowGlossaryHints, indexes, 60)
                        : java.util.Collections.emptyMap();

                List<String> out;
                if (aiHintMode) {
                    lv.lenc.TranslationService.setRuntimeGlossaryHints(groupGlossaryHints);
                }
                try {
                    out = lv.lenc.TranslationService.translatePreservingTagsBatched(
                            api,
                            batchTexts,
                            sourceIso,
                            targetIso,
                            stop,
                            (frac, msg) -> {
                                if (progress == null) return;
                                String line1 = sourceUiFinal + " -> " + targetUiFinal;
                                String line2 = (msg == null ? "" : msg);
                                double weighted = totalWorkItems <= 0
                                        ? frac
                                        : (groupDoneBefore + frac * groupSize) / (double) totalWorkItems;
                                progress.onProgress(Math.min(0.999, weighted), line1 + "||" + line2);
                            }
                    );
                } finally {
                    if (aiHintMode) {
                        lv.lenc.TranslationService.clearRuntimeGlossaryHints();
                    }
                }

                if (stop.getAsBoolean()) return;

                int count = Math.min(indexes.size(), out.size());
                if (count > 0) {
                    List<String> translatedSlice = new ArrayList<>(count);
                    List<GlossaryService.FrozenTerms> frozenSlice = new ArrayList<>(count);
                    List<Integer> originalIndexes = new ArrayList<>(count);
                    for (int j = 0; j < count; j++) {
                        int originalIndex = indexes.get(j);
                        originalIndexes.add(originalIndex);
                        translatedSlice.add(out.get(j));
                        frozenSlice.add(frozenForRows.get(originalIndex));
                    }
                    List<String> resolved = glossaryService.unfreezeTermsBatch(translatedSlice, frozenSlice, targetUi);
                    for (int j = 0; j < count; j++) {
                        String translated = resolved.get(j);
                        if (translated == null || translated.isBlank()) continue;
                        int originalIndex = originalIndexes.get(j);
                        setValueByLang(rowsToTranslate.get(originalIndex), targetUi, translated);
                        translatedAny = true;
                    }
                }

                doneItems[0] += groupSize;
            }

            if (translatedAny) {
                markPendingSaveForTarget(targetUi);
            }
            Platform.runLater(this::refresh);
            if (progress != null) {
                progress.onProgress(1.0, sourceUiFinal + " -> " + targetUiFinal + "||done");
            }

        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            AppLog.error("[LT] single translate failed: " + ex.getMessage());
            AppLog.exception(ex);
            throw new RuntimeException(ex);
        }
    }

    public String buildFileTextForLang(String uiLang) {
        StringBuilder sb = new StringBuilder();
        for (LocalizationData row : getItems()) {
            String key = row.getKey();
            if (key == null) continue;

            String val = getValueForLang(row, uiLang);
            if (val == null) val = "";

            sb.append(key).append("=").append(val).append("\n");
        }
        return sb.toString();
    }
    //--------
    private TableColumn<LocalizationData, ?> findCol(String name) {
        for (TableColumn<LocalizationData, ?> c : getColumns()) {
            if (c.getText().equalsIgnoreCase(name)) return c;
        }
        return null;
    }

    private void ensureCoreColumns() {
        TableColumn<LocalizationData, ?> nCol = findCol("N");
        TableColumn<LocalizationData, ?> keyCol = findCol("key");

        if (nCol != null) nCol.setVisible(true);
        if (keyCol != null) keyCol.setVisible(true);

        if (nCol != null && getColumns().remove(nCol)) {
            getColumns().add(0, nCol);
        }
        if (keyCol != null && getColumns().remove(keyCol)) {
            int keyIndex = Math.min(1, getColumns().size());
            getColumns().add(keyIndex, keyCol);
        }
    }


    public void showAllColumns() {
        Runnable apply = () -> {
            resetAllColumnWidthsToBase();
            for (TableColumn<LocalizationData, ?> c : getColumns()) {
                c.setVisible(true);
            }
            ensureCoreColumns();

            requestLayout();
            applyColumnSizing(false);
            resetHorizontalScroll();
            TableColumn<LocalizationData, ?> firstColumn = findCol("N");
            if (firstColumn != null) {
                scrollToColumn(firstColumn);
            }
            Platform.runLater(() -> {
                // Final pass after skin/scrollbars settle to keep fullscreen width stable.
                applyColumnSizing(false);
                requestHeaderStateRefresh();
            });
        };
        if (Platform.isFxApplicationThread()) {
            apply.run();
        } else {
            Platform.runLater(apply);
        }
    }
    public void showOnly(String sourceUi, String targetUi) {
        Runnable apply = () -> {
            resetAllColumnWidthsToBase();
            for (TableColumn<LocalizationData, ?> c : getColumns()) {
                c.setVisible(false);
            }
            ensureCoreColumns();

            if (sourceUi != null && !sourceUi.isBlank()) {
                TableColumn<LocalizationData, ?> s = findCol(sourceUi);
                if (s != null) s.setVisible(true);
            }

            if (targetUi != null && !targetUi.isBlank()) {
                TableColumn<LocalizationData, ?> t = findCol(targetUi);
                if (t != null) t.setVisible(true);
            }

            requestLayout();
            applyColumnSizing(true /* single mode */);
            resetHorizontalScroll();
            TableColumn<LocalizationData, ?> firstColumn = findCol("N");
            if (firstColumn != null) {
                scrollToColumn(firstColumn);
            }
            Platform.runLater(() -> {
                // Final pass after visibility + scrollbar state changes.
                applyColumnSizing(true /* single mode */);
                requestHeaderStateRefresh();
            });
        };
        if (Platform.isFxApplicationThread()) {
            apply.run();
        } else {
            Platform.runLater(apply);
        }
    }
    boolean isLastLoadWasMulti() { return lastLoadWasMulti; }

    java.util.Set<String> getLoadedUiLanguages() {
        return java.util.Collections.unmodifiableSet(loadedUiLanguages);
    }
    public String detectMainSourceLang(java.util.Collection<String> langs) {
        String best = null;
        int bestCount = -1;

        for (String lang : langs) {
            int cnt = 0;

            for (LocalizationData r : getItems()) {
                String v = r.getByLang(lang);
                if (v != null && !v.isBlank()) cnt++;
            }

            if (cnt > bestCount) {
                bestCount = cnt;
                best = lang;
            }
        }
        return best;
    }

    public String getActiveKeyPrefixFilter() {
        return activeKeyPrefixFilter;
    }

    public List<String> getAllKeysForFilter() {
        return getItems().stream()
                .map(LocalizationData::getKey)
                .filter(k -> k != null)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());
    }

    public void applyKeyPrefixFilter(String prefix) {
        String normalized = (prefix == null || prefix.isBlank()) ? null : prefix.trim();
        if (normalized == null) {
            clearKeyFilter();
            return;
        }

        if (activeKeyPrefixFilter == null || keyFilterBaseItems.isEmpty()) {
            keyFilterBaseItems.setAll(getItems());
        }

        activeKeyPrefixFilter = normalized;
        String lower = normalized.toLowerCase(Locale.ROOT);
        List<LocalizationData> filtered = keyFilterBaseItems.stream()
                .filter(row -> {
                    String key = row.getKey();
                    return key != null && key.toLowerCase(Locale.ROOT).startsWith(lower);
                })
                .collect(Collectors.toList());

        setItems(FXCollections.observableArrayList(filtered));
        refresh();
    }

    public void clearKeyFilter() {
        if (activeKeyPrefixFilter == null) {
            return;
        }
        setItems(FXCollections.observableArrayList(keyFilterBaseItems));
        keyFilterBaseItems.clear();
        activeKeyPrefixFilter = null;
        refresh();
    }

    public void clearLoadedData() {
        lastLoadWasMulti = false;
        loadedUiLanguages.clear();
        detectedUiByColumn.clear();
        currentSourceUi = null;
        keyFilterBaseItems.clear();
        activeKeyPrefixFilter = null;
        getSelectionModel().clearSelection();
        setItems(FXCollections.observableArrayList());
        refresh();
    }

    private static void setValueByLang(LocalizationData data, String lang, String value) {
        if (data == null || lang == null) return;

        switch (lang.toLowerCase(Locale.ROOT)) {
            case "ruru" -> data.setRuRu(value);
            case "dede" -> data.setDeDe(value);
            case "enus" -> data.setEnUs(value);
            case "esmx" -> data.setEsMx(value);
            case "eses" -> data.setEsEs(value);
            case "frfr" -> data.setFrFr(value);
            case "itit" -> data.setItIt(value);
            case "plpl" -> data.setPlPl(value);
            case "ptbr" -> data.setPtBr(value);
            case "kokr" -> data.setKoKr(value);
            case "zhcn" -> data.setZhCn(value);
            case "zhtw" -> data.setZhTw(value);
            default -> {
                // no-op for unknown language codes
            }
        }
    }
}



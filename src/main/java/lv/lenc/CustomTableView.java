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
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.util.converter.DefaultStringConverter;


public class CustomTableView extends TableView<LocalizationData> {
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

    private boolean lastLoadWasMulti = false;
    private final java.util.Set<String> loadedUiLanguages = new java.util.HashSet<>();
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
        this.getStylesheets().add(
                getClass().getResource("/Assets/Style/custom-tableview.css").toExternalForm()
        );
        try {
            this.langService = new LanguageDetectorService();
        } catch (LangDetectException e) {
            AppLog.exception(e);
            this.langService = null;
        }
        Platform.runLater(() -> {
            Node resizeLine = this.lookup(".column-resize-line");
            if (resizeLine != null) {
                resizeLine.setStyle("-fx-background-color: green;");
            }
        });
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
        countColumn.setMinWidth(UiScaleHelper.scaleX(100));
        countColumn.setPrefWidth(UiScaleHelper.scaleX(100));
        hideHeaderSortedArrow();
        enableHeaderColumnSelectionHighlighting();
        Label placeholderLabel = new Label(this.localization.get("table.placeholder"));
        this.setPlaceholder(placeholderLabel);
        this.setFixedCellSize(UiScaleHelper.scaleY(52));
        // edit
        applyCustomCellStyleToAllColumns();
        removeScrollCorner();
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
        });
        countColumn.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    getStyleClass().removeAll("col-n");
                    return;
                }
                setText(item);
                if (!getStyleClass().contains("col-n")) getStyleClass().add("col-n");
                setStyle(""); //
                setFont(Font.font(
                        getFont().getFamily(),
                        FontWeight.BOLD,
                        getFont().getSize()
                ));
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
                setFont(Font.font(
                        getFont().getFamily(),
                        FontWeight.BOLD,
                        getFont().getSize()
                ));
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
                col.setMaxWidth((singleMode || fillAvailableWidth) ? keyWidth : UiScaleHelper.scaleX(4000));
                continue;
            }

            if (SUPPORTED_LANGS.contains(name)) {
                //
                if (col.isVisible() && singleMode) {
                    col.setMinWidth(stretchedLangWidth);
                    col.setPrefWidth(stretchedLangWidth);
                    col.setMaxWidth(stretchedLangWidth);
                } else if (col.isVisible() && fillAvailableWidth) {
                    col.setMinWidth(stretchedLangWidth);
                    col.setPrefWidth(stretchedLangWidth);
                    col.setMaxWidth(stretchedLangWidth);
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

                {
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
                    double padY = UiScaleHelper.scaleY(5);
                    double padX = UiScaleHelper.scaleX(7);
                    double borderSlice = UiScaleHelper.scaleY(12);
                    double borderWidth = UiScaleHelper.scaleY(4);
                    double fontSize = UiScaleHelper.scaleY(14);

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
                            + "-fx-border-image-source: url('" + textureUrl + "');"
                            + "-fx-font-size: " + fontSize + "px;";
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
                        setGraphic(null);
                        setContentDisplay(ContentDisplay.TEXT_ONLY);
                        setText(fittedPreview);
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
                            .replace("\r\n", "  ")
                            .replace('\n', ' ')
                            .replace('\r', ' ')
                            .replace('\t', ' ')
                            .trim();

                    preview = preview.replaceAll(" {2,}", " ");

                    int maxPreviewLength = 190;
                    if (preview.length() > maxPreviewLength) {
                        preview = preview.substring(0, maxPreviewLength - 3).trim() + "...";
                    }

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
                    if (preview == null || preview.isBlank()) {
                        return preview;
                    }

                    double availableWidth = getPreviewAvailableWidth();
                    if (availableWidth <= UiScaleHelper.scaleX(40)) {
                        return preview;
                    }

                    Font previewFont = getPreviewFont();
                    double maxHeight = getPreviewMaxHeight(previewFont);

                    if (fitsWithinPreview(preview, availableWidth, maxHeight, previewFont)) {
                        return preview;
                    }

                    int low = 0;
                    int high = preview.length();
                    String best = "...";
                    while (low <= high) {
                        int mid = (low + high) >>> 1;
                        String candidate = buildEllipsizedPreview(preview, mid);
                        if (fitsWithinPreview(candidate, availableWidth, maxHeight, previewFont)) {
                            best = candidate;
                            low = mid + 1;
                        } else {
                            high = mid - 1;
                        }
                    }

                    return best;
                }

                private double getPreviewAvailableWidth() {
                    double horizontalPadding = UiScaleHelper.scaleX(18);
                    double cellWidth = getWidth();
                    if (cellWidth <= 1 && getTableColumn() != null) {
                        cellWidth = getTableColumn().getWidth();
                    }
                    return Math.max(UiScaleHelper.scaleX(60), cellWidth - horizontalPadding);
                }

                private Font getPreviewFont() {
                    Font baseFont = getFont() == null ? Font.getDefault() : getFont();
                    return Font.font(baseFont.getFamily(), FontWeight.BOLD, baseFont.getSize());
                }

                private double getPreviewMaxHeight(Font previewFont) {
                    Text sample = new Text("Ag");
                    sample.setFont(previewFont);
                    double lineHeight = sample.getLayoutBounds().getHeight();
                    double lineSpacing = UiScaleHelper.scaleY(1.5);
                    return (lineHeight * 2) + lineSpacing + UiScaleHelper.scaleY(3);
                }

                private boolean fitsWithinPreview(String value, double width, double maxHeight, Font previewFont) {
                    Text measure = new Text(value);
                    measure.setFont(previewFont);
                    measure.setWrappingWidth(width);
                    return measure.getLayoutBounds().getHeight() <= maxHeight;
                }

                private String buildEllipsizedPreview(String preview, int maxChars) {
                    if (preview == null || preview.isEmpty()) {
                        return preview;
                    }
                    if (maxChars >= preview.length()) {
                        return preview;
                    }
                    if (maxChars <= 3) {
                        return "...";
                    }

                    int cut = findPreviewBreak(preview, maxChars);
                    return preview.substring(0, cut).trim() + "...";
                }

                private int findPreviewBreak(String preview, int maxChars) {
                    int minIndex = Math.max(1, maxChars - 24);
                    for (int i = maxChars; i >= minIndex; i--) {
                        char ch = preview.charAt(i - 1);
                        if (Character.isWhitespace(ch) || ch == '>' || ch == '<' || ch == '/' || ch == '"' || ch == '\'') {
                            return i;
                        }
                    }
                    return maxChars;
                }

                private Node buildXmlPreviewGraphic(String raw) {
                    TextFlow flow = buildXmlSyntaxFlow(raw);
                    javafx.scene.layout.StackPane wrapper = new javafx.scene.layout.StackPane(flow);
                    wrapper.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                    wrapper.getStyleClass().add("table-xml-preview-wrap");
                    wrapper.setPickOnBounds(false);
                    wrapper.setMinHeight(0);

                    double maxPreviewHeight = UiScaleHelper.scaleY(32);
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
                header.setOnMouseClicked(event -> {
                    //     applySelectedHeaderStyle(header);
                });
            });
        });
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
        if (langFiles == null || langFiles.isEmpty()) {
            lastLoadWasMulti = false;
            loadedUiLanguages.clear();
            currentSourceUi = null;
            setItems(FXCollections.observableArrayList());
            Platform.runLater(this::refresh);
            return;
        }

        lastLoadWasMulti = langFiles.size() > 1;
        loadedUiLanguages.clear();
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
        Platform.runLater(this::refresh);
        for (String lang : columnsToHighlight) {
            highlightColumn(lang);
            AppLog.info("HighLighyColumn: " + lang);
        }

    }

    public void loadLanguagesToTable(File file) {
        lastLoadWasMulti = false;
        loadedUiLanguages.clear();
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
        currentSourceUi = detectedLang;
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
        TableColumn<LocalizationData, String> col =
                editableColumns.stream()
                        .filter(c -> c.getText().equalsIgnoreCase(lang))
                        .findFirst()
                        .orElse(null);
        if (col == null) return;

        //
        Platform.runLater(() -> {
            Node header = this.lookup(".column-header[data-column-index='" + this.getColumns().indexOf(col) + "']");
            if (header != null) {
                header.setStyle("-fx-background-color: rgba(255,140,0,0.18);"); //
            }
        });
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
    }
    private boolean isMeaningful(String s) {
        if (s == null) return false;
        s = s.trim();
        if (s.isEmpty()) return false;
        return !s.equalsIgnoreCase("null"); // if "null" comes as a literal string
    }
    public String getMainSourceLang() {
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
    public void translateFromColumnToOthers(
            LibreTranslateApi api,
            String sourceLang,
            java.util.function.BooleanSupplier cancelled
    ) {
        final java.util.function.BooleanSupplier cancelledFinal = (cancelled != null) ? cancelled : () -> false;
        final java.util.function.BooleanSupplier stop = () ->
                cancelledFinal.getAsBoolean() || Thread.currentThread().isInterrupted();

        // 1) source  (ui)
        String sourceUi = (sourceLang != null && !sourceLang.isBlank())
                ? sourceLang
                : getMainSourceLang();

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
        String sourceIso = toApiLang(sourceUi);
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
                }
            }

            if (stop.getAsBoolean()) return;

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

        final java.util.List<String> targetsUi = java.util.List.of(
                        "ruRU", "deDE", "enUS", "esMX", "esES", "frFR", "itIT", "plPL", "ptBR", "koKR", "zhCN", "zhTW"
                ).stream()
                .filter(ui -> !ui.equalsIgnoreCase(sourceUi))
                .toList();

        final int totalUiTargets = targetsUi.size();
        if (totalUiTargets == 0) return;

        java.util.List<LocalizationData> allRows = getItems();
        if (allRows == null || allRows.isEmpty()) return;

        final String sourceUiFinal = sourceUi;
        final String sourceIso = toApiLang(sourceUi);

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

            java.util.List<LocalizationData> rowsToTranslate = new java.util.ArrayList<>();
            java.util.List<String> textsToTranslate = new java.util.ArrayList<>();
            java.util.List<GlossaryService.FrozenTerms> frozenForRows = new java.util.ArrayList<>();

            for (LocalizationData row : allRows) {
                if (stop.getAsBoolean()) return;
                if (row == null) continue;

                String sourceText = row.getByLang(sourceUi);
                if (sourceText == null || sourceText.isBlank()) continue;

                GlossaryService.Category category = GlossaryService.detectCategory(row.getKey());

                // 1. exact/text glossary
                String glossaryHit = glossaryService.findBestMatch(
                        row.getKey(),
                        sourceUi,
                        sourceText,
                        targetUi
                );

                if (glossaryHit != null && !glossaryHit.isBlank()) {
                    setValueByLang(row, targetUi, glossaryHit);
                    continue;
                }

                // 2. term freeze before MT
                GlossaryService.FrozenTerms frozen = glossaryService.freezeTerms(
                        category,
                        sourceUi,
                        targetUi,
                        sourceText
                );

                rowsToTranslate.add(row);
                textsToTranslate.add(frozen.preparedText());
                frozenForRows.add(frozen);
            }

            if (textsToTranslate.isEmpty()) {
                continue;
            }

            try {
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
                for (int i = 0; i < count; i++) {
                    String translated = out.get(i);
                    if (translated == null || translated.isBlank()) continue;

                    translated = glossaryService.unfreezeTerms(translated, frozenForRows.get(i));
                    setValueByLang(rowsToTranslate.get(i), targetUi, translated);
                }

            } catch (IOException | InterruptedException ex) {
                if (ex instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                if (stop.getAsBoolean()) return;
                AppLog.error("[LT] translate failed for " + targetUi + ": " + ex.getMessage());
                AppLog.exception(ex);
                throw new RuntimeException(ex);
            }
        }

        if (!stop.getAsBoolean() && progress != null) {
            progress.onProgress(1.0, sourceUiFinal + " -> all||done");
        }
        if (!stop.getAsBoolean()) {
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

        for (LocalizationData row : allRows) {
            if (stop.getAsBoolean()) return;
            if (row == null) continue;

            String actualSourceUi = sourceUi;
            String sourceText = row.getByLang(sourceUi);

            // Ć…Ā Äā‚¬Ā¢Ć…ĀÄ€ĀĆ…Ā Ä€Ā»Ć…Ā Ä†Ćø Ć…Ā Ä†Ā¦Ć…Ā Ä€ĀµĆ…ĀÄā€Ā¬Ć…Ā Ä€ĀµĆ…Ā Ä€Ā²Ć…Ā Ä€Ā¾Ć…Ā Ä€Ā´Ć…Ā Ä†ĆøĆ…Ā Ä€Ā¼ Ć…Ā Ä€Ć†Ć…Ā Äā‚¬Ā¢ Ć…Ā Ä€Ā² enUS Ć…Ā Ä†Ćø enUS Ć…ĀÄ€ĀĆ…Ā Ä€Ā¶Ć…Ā Ä€Āµ Ć…Ā Ä€Ā·Ć…Ā Ä€Ā°Ć…Ā Ä†Ā¦Ć…Ā Ä€Ā¾Ć…Ā Ä€Ā»Ć…Ā Ä€Ā½Ć…Ā Ä€ĀµĆ…Ā Ä€Ā½ Ć„ĀÄā€Ā¬Äā‚¬ĀÆ Ć…Ā Ä€Ā±Ć…Ā Ä€ĀµĆ…ĀÄā€Ā¬Ć…ĀÄā‚¬ĀĆ…Ā Ä€Ā¼ enUS Ć…Ā Ć…ā€”Ć…Ā Ä€Ā°Ć…Ā Ć…ā€” Ć…Ā Ä†ĆøĆ…ĀÄ€ĀĆ…ĀÄā‚¬ĀĆ…Ā Ä€Ā¾Ć…ĀÄā‚¬ļ£¼Ć…Ā Ä€Ā½Ć…Ā Ä†ĆøĆ…Ā Ć…ā€”
            String enText = row.getByLang("enUS");
            if (!"enUS".equalsIgnoreCase(targetUi) && enText != null && !enText.isBlank()) {
                actualSourceUi = "enUS";
                sourceText = enText;
            }

            if (sourceText == null || sourceText.isBlank()) continue;

            GlossaryService.Category category = GlossaryService.detectCategory(row.getKey());

            String glossaryHit = glossaryService.findBestMatch(
                    row.getKey(),
                    actualSourceUi,
                    sourceText,
                    targetUi
            );

            if (glossaryHit != null && !glossaryHit.isBlank()) {
                setValueByLang(row, targetUi, glossaryHit);
                continue;
            }

//            String templatedHit = glossaryService.tryTemplateCompose(
//                    category,
//                    actualSourceUi,
//                    targetUi,
//                    sourceText
//            );
//
//            if (templatedHit != null && !templatedHit.isBlank()) {
//                setValueByLang(row, targetUi, templatedHit);
//                continue;
//            }

            GlossaryService.FrozenTerms frozen = glossaryService.freezeTerms(
                    category,
                    actualSourceUi,
                    targetUi,
                    sourceText
            );

            rowsToTranslate.add(row);
            textsToTranslate.add(frozen.preparedText());
            frozenForRows.add(frozen);
            actualSourceUis.add(actualSourceUi);
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

                Map<String, List<String>> byIso =
                        TranslationService.translateAll(batchTexts, sourceIso, List.of(targetIso));

                if (stop.getAsBoolean()) return;

                List<String> translated = byIso.get(targetIso);
                if (translated == null) continue;

                int count = Math.min(indexes.size(), translated.size());
                for (int j = 0; j < count; j++) {
                    int originalIndex = indexes.get(j);
                    String outText = translated.get(j);
                    if (outText == null || outText.isBlank()) continue;

                    outText = glossaryService.unfreezeTerms(outText, frozenForRows.get(originalIndex));
                    setValueByLang(rowsToTranslate.get(originalIndex), targetUi, outText);
                }
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

        for (LocalizationData row : allRows) {
            if (stop.getAsBoolean()) return;
            if (row == null) continue;

            String actualSourceUi = sourceUi;
            String sourceText = row.getByLang(sourceUi);

            String enText = row.getByLang("enUS");
            if (!"enUS".equalsIgnoreCase(targetUi) && enText != null && !enText.isBlank()) {
                actualSourceUi = "enUS";
                sourceText = enText;
            }

            if (sourceText == null || sourceText.isBlank()) continue;

            GlossaryService.Category category = GlossaryService.detectCategory(row.getKey());

            String glossaryHit = glossaryService.findBestMatch(
                    row.getKey(),
                    actualSourceUi,
                    sourceText,
                    targetUi
            );

            if (glossaryHit != null && !glossaryHit.isBlank()) {
                setValueByLang(row, targetUi, glossaryHit);
                continue;
            }

//            String templatedHit = glossaryService.tryTemplateCompose(
//                    category,
//                    actualSourceUi,
//                    targetUi,
//                    sourceText
//            );
//
//            if (templatedHit != null && !templatedHit.isBlank()) {
//                setValueByLang(row, targetUi, templatedHit);
//                continue;
//            }

            GlossaryService.FrozenTerms frozen = glossaryService.freezeTerms(
                    category,
                    actualSourceUi,
                    targetUi,
                    sourceText
            );

            rowsToTranslate.add(row);
            textsToTranslate.add(frozen.preparedText());
            frozenForRows.add(frozen);
            actualSourceUis.add(actualSourceUi);
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

                List<String> out = lv.lenc.TranslationService.translatePreservingTagsBatched(
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

                if (stop.getAsBoolean()) return;

                int count = Math.min(indexes.size(), out.size());
                for (int j = 0; j < count; j++) {
                    int originalIndex = indexes.get(j);
                    String translated = out.get(j);
                    if (translated == null || translated.isBlank()) continue;

                    translated = glossaryService.unfreezeTerms(translated, frozenForRows.get(originalIndex));
                    setValueByLang(rowsToTranslate.get(originalIndex), targetUi, translated);
                }

                doneItems[0] += groupSize;
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



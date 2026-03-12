package lv.lenc;

import com.cybozu.labs.langdetect.LangDetectException;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.util.converter.DefaultStringConverter;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;


public class CustomTableView extends TableView<LocalizationData> {
    String texturePath;
    private double baseLangMinW;
    private double baseLangPrefW;
    private double baseLangMaxW;

    private double baseKeyMinW;
    private double baseKeyPrefW;

    private double baseNMinW;
    private double baseNPrefW;

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

    public CustomTableView(String texturePath,
                           LocalizationManager localization,
                           double width,
                           double height,
                           GlossaryService glossaryService) {

        this.texturePath = texturePath;
        // Styling
        //  this.applyScrollBarStyle();
        this.glossaryService = glossaryService;
        this.getStylesheets().add(
                getClass().getResource("/Assets/Style/custom-tableview.css").toExternalForm()
        );
        try {
            this.langService = new LanguageDetectorService();
        } catch (LangDetectException e) {
            e.printStackTrace();
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
        keyColumn.setCellValueFactory(new PropertyValueFactory<>("key"));

        TableColumn<LocalizationData, String> ruRUColumn = new TableColumn<>("ruRU");
        ruRUColumn.setCellValueFactory(new PropertyValueFactory<>("ruRu"));
        // ─── German ───
        TableColumn<LocalizationData, String> deDEColumn = new TableColumn<>("deDE");
        deDEColumn.setCellValueFactory(new PropertyValueFactory<>("deDE"));

        TableColumn<LocalizationData, String> enUSColumn = new TableColumn<>("enUS");
        enUSColumn.setCellValueFactory(new PropertyValueFactory<>("enUs"));

        TableColumn<LocalizationData, String> esMXColumn = new TableColumn<>("esMX");
        esMXColumn.setCellValueFactory(new PropertyValueFactory<>("esMx"));

        TableColumn<LocalizationData, String> esESColumn = new TableColumn<>("esES");
        esESColumn.setCellValueFactory(new PropertyValueFactory<>("esEs"));

        TableColumn<LocalizationData, String> frFRColumn = new TableColumn<>("frFR");
        frFRColumn.setCellValueFactory(new PropertyValueFactory<>("frFr"));

        TableColumn<LocalizationData, String> itITColumn = new TableColumn<>("itIT");
        itITColumn.setCellValueFactory(new PropertyValueFactory<>("itIt"));

        TableColumn<LocalizationData, String> plPLColumn = new TableColumn<>("plPL");
        plPLColumn.setCellValueFactory(new PropertyValueFactory<>("plPl"));

        TableColumn<LocalizationData, String> ptBRColumn = new TableColumn<>("ptBR");
        ptBRColumn.setCellValueFactory(new PropertyValueFactory<>("ptBr"));

        TableColumn<LocalizationData, String> koKRColumn = new TableColumn<>("koKR");
        koKRColumn.setCellValueFactory(new PropertyValueFactory<>("koKr"));

        TableColumn<LocalizationData, String> zhCNColumn = new TableColumn<>("zhCN");
        zhCNColumn.setCellValueFactory(new PropertyValueFactory<>("zhCn"));

        TableColumn<LocalizationData, String> zhTWColumn = new TableColumn<>("zhTW");
        zhTWColumn.setCellValueFactory(new PropertyValueFactory<>("zhTw"));

        getColumns().addAll(countColumn, keyColumn, ruRUColumn, deDEColumn, enUSColumn, esMXColumn, esESColumn,
                frFRColumn, itITColumn, plPLColumn, ptBRColumn, koKRColumn, zhCNColumn, zhTWColumn);

        countColumn.getStyleClass().add("col-n");
        keyColumn.getStyleClass().add("col-key");
        this.setEditable(true);

        for (TableColumn<LocalizationData, ?> column : this.getColumns()) {
            column.setMinWidth(UiScaleHelper.scaleX(140));
            column.setMaxWidth(UiScaleHelper.scaleX(4000));
            column.setEditable(true);
        }
        keyColumn.setMinWidth(UiScaleHelper.scaleX(200));
        countColumn.setMaxWidth(UiScaleHelper.scaleX(150));
        countColumn.setMinWidth(UiScaleHelper.scaleX(100));
        countColumn.setPrefWidth(UiScaleHelper.scaleX(100));
        this.setMinWidth(width);
        this.setMaxWidth(width);
        this.setMinHeight(height);
        this.setMaxHeight(height);

        this.hideHeaderSortedArrow(this);
        this.enableHeaderColumnSelectionHighlighting();
        Label placeholderLabel = new Label(localization.get("table.placeholder"));
        this.setPlaceholder(placeholderLabel);
        this.setFixedCellSize(UiScaleHelper.scaleY(52));
        // edit
        applyCustomCellStyleToAllColumns();
        removeScrollCorner();
        captureBaseColumnWidths();
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
    }

    public void applyColumnSizing(boolean singleMode) {
        double mult = singleMode ? 4.5 : 1.0;

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
                //
                if (col.isVisible() && singleMode) {
                    col.setMinWidth(baseLangMinW * mult);
                    col.setPrefWidth(baseLangPrefW * mult);
                    col.setMaxWidth(baseLangMaxW);
                } else {
                    //
                    col.setMinWidth(baseLangMinW);
                    col.setPrefWidth(baseLangPrefW);
                    col.setMaxWidth(baseLangMaxW);
                }
            }
        }

        Platform.runLater(() -> {
            refresh();
            layout();
        });
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
        String headerTextureNormal = "ui_nova_archives_listitem_normal.png";
        String headerTextureOver = "ui_nova_archives_listitem_over.png";
        String headerTextureSelected = "ui_nova_archives_listitem_selected.png";
        String fullTexturePathNormal = texturePath + headerTextureNormal;
        String fullTexturePathOver = texturePath + headerTextureOver;
        String fullTexturePathSelected = texturePath + headerTextureSelected;
        String textFieldTexture = texturePath + "ui_nova_global_tooltip_orange.png";

        for (TableColumn<LocalizationData, ?> col : getColumns()) {
            if ("N".equals(col.getText()) || "key".equals(col.getText())) continue;

            @SuppressWarnings("unchecked")
            TableColumn<LocalizationData, String> editableCol = (TableColumn<LocalizationData, String>) col;

            editableCol.setCellFactory(column -> new TextFieldTableCell<>(new DefaultStringConverter()) {

                private boolean isHovered = false;
                //  private boolean isSelected = false;
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
                    selectedProperty().addListener((obs, oldVal, newVal) -> updateCellStyle());
                    itemProperty().addListener((obs, oldVal, newVal) -> updateCellStyle());
                    emptyProperty().addListener((obs, oldVal, newVal) -> updateCellStyle());
                }
                private String styleWithTexture(String rowBg, String textureUrl, boolean selected) {
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

                    String base = texturePath + "ui_nova_archives_listitem_normal.png";
                    String over = texturePath + "ui_nova_archives_listitem_over.png";
                    String sel  = texturePath + "ui_nova_archives_listitem_selected.png";

                    String tex = base;
                    String textColor = "#80d2a2";
                    boolean selectedNow = false;

                    if (isEditing || isSelected()) {
                        tex = sel;
                        textColor = "white";
                        selectedNow = true;
                    } else if (isHovered) {
                        tex = over;
                        textColor = "#80d2a2"; // hover не меняет цвет текста
                    }

                    setStyle(
                            styleWithTexture(rowBg, tex, selectedNow)
                                    + "-fx-text-fill: " + textColor + ";"
                                    + "-fx-font-weight: bold;"
                    );
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

                        column.setOnEditStart(event -> {
                            isEditing = true;
                            //    setStyle(styleBase.replace(fullTexturePathNormal, fullTexturePathSelected));
                        });

                        column.setOnEditCommit(event -> {
                            isEditing = false;
                            CustomTableView.this.refresh();
                        });

                        column.setOnEditCancel(event -> {
                            isEditing = false;
                            CustomTableView.this.refresh();
                        });

                    } else {
                        setText(null);
                        setStyle("");
                        setOnMouseEntered(null);
                        setOnMouseExited(null);
                        setOnMouseClicked(null);
                        //  isSelected = false;
                        isEditing = false;
                    }
                }

                @Override
                public void startEdit() {
                    super.startEdit();
                    isEditing = true;

                    //
                    if (getGraphic() instanceof TextField textField) {
                        textField.getStyleClass().add("table-textfield-editing");

                        textField.setOnAction(e -> {
                            commitEdit(textField.getText());
                            e.consume();
                        });
                    }
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

            });

        }
    }

    public void hideHeaderSortedArrow(CustomTableView tableview) {
        Platform.runLater(() -> {
            Parent header = (Parent) tableview.lookup("TableHeaderRow");
            if (header == null) {
                //    System.out.println("TableHeaderRow
            } else {
                //      System.out.println("

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

    public void enableHeaderColumnSelectionHighlighting() {
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

        lastLoadWasMulti = langFiles != null && langFiles.size() > 1;
        loadedUiLanguages.clear();
        for (var e : langFiles.entrySet()) {
            File f = e.getValue();
            if (f != null && f.exists()) {
                loadedUiLanguages.add(e.getKey());
            }
        }
        if (langFiles != null && langFiles.size() == 1) {
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
                            } catch (Exception e) {
                                System.out.println("Error " + file);
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

            System.out.println("Loaded codes: " + perLang.keySet());
            rows.add(id);
        }
        setItems(rows); // set data into table
        Platform.runLater(this::refresh);
        for (String lang : columnsToHighlight) {
            highlightColumn(lang);
            System.out.println("HighLighyColumn: " + lang);
        }

    }

    public void loadLanguagesToTable(File file) {
        lastLoadWasMulti = false;
        loadedUiLanguages.clear();
        if (file == null || !file.exists()) return;
        String fileText = null;
        try {
            fileText = java.nio.file.Files.readString(file.toPath());
        } catch (Exception e) {
            // System.out.println("Error " + file);
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
        @SuppressWarnings("unchecked")
        TableColumn<LocalizationData, String> col =
                (TableColumn<LocalizationData, String>) this.getColumns().stream()
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
            System.out.println("Error: " + e.getMessage());
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
            System.err.println("[LT] nothing to translate in column " + sourceUi);
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
            System.err.println("[LT] no valid target languages");
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

        } catch (Exception ex) {
            System.err.println("[LT] translate failed: " + ex.getMessage());
            ex.printStackTrace();
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

                // 2. smart glossary search
                String smartHit = glossaryService.findSmartMatch(
                        category,
                        row.getKey(),
                        sourceUi,
                        sourceText,
                        targetUi
                );

                if (smartHit != null && !smartHit.isBlank()) {
                    setValueByLang(row, targetUi, smartHit);
                    continue;
                }

                // 3. term freeze before MT
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
                javafx.application.Platform.runLater(this::refresh);
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

                javafx.application.Platform.runLater(this::refresh);

            } catch (Exception ex) {
                if (stop.getAsBoolean()) return;
                System.err.println("[LT] translate failed for " + targetUi + ": " + ex.getMessage());
                ex.printStackTrace();
            }
        }

        if (!stop.getAsBoolean() && progress != null) {
            progress.onProgress(1.0, sourceUiFinal + " -> all||done");
        }
    }
    private static String extractBatchLine(String msg) {
        if (msg == null) return "";
        //
        int idx = msg.indexOf("batch");
        if (idx >= 0) return msg.substring(idx).trim();
        return "";
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

            // Если переводим НЕ в enUS и enUS уже заполнен — берём enUS как источник
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

            String smartHit = glossaryService.findSmartMatch(
                    category,
                    row.getKey(),
                    actualSourceUi,
                    sourceText,
                    targetUi
            );

            if (smartHit != null && !smartHit.isBlank()) {
                setValueByLang(row, targetUi, smartHit);
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
            // Разбиваем по фактическому source language
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
        } catch (Exception e) {
            e.printStackTrace();
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

            String smartHit = glossaryService.findSmartMatch(
                    category,
                    row.getKey(),
                    actualSourceUi,
                    sourceText,
                    targetUi
            );

            if (smartHit != null && !smartHit.isBlank()) {
                setValueByLang(row, targetUi, smartHit);
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

            for (Map.Entry<String, List<Integer>> entry : sourceIsoToIndexes.entrySet()) {
                if (stop.getAsBoolean()) return;

                String sourceIso = entry.getKey();
                List<Integer> indexes = entry.getValue();

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
                            progress.onProgress(frac, line1 + "||" + line2);
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
            }

            Platform.runLater(this::refresh);

        } catch (Exception ex) {
            System.err.println("[LT] single translate failed: " + ex.getMessage());
            ex.printStackTrace();
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


    public void showAllColumns() {
        Platform.runLater(() -> {
            for (TableColumn<LocalizationData, ?> c : getColumns()) {
                c.setVisible(true);
            }
            requestLayout();

            //
            Platform.runLater(() -> applyColumnSizing(false));
        });
    }
    public void showOnly(String sourceUi, String targetUi) {
        Platform.runLater(() -> {
            for (TableColumn<LocalizationData, ?> c : getColumns()) {
                c.setVisible(false);
            }

            TableColumn<LocalizationData, ?> nCol = findCol("N");
            if (nCol != null) nCol.setVisible(true);

            TableColumn<LocalizationData, ?> keyCol = findCol("key");
            if (keyCol != null) keyCol.setVisible(true);

            if (sourceUi != null && !sourceUi.isBlank()) {
                TableColumn<LocalizationData, ?> s = findCol(sourceUi);
                if (s != null) s.setVisible(true);
            }

            if (targetUi != null && !targetUi.isBlank()) {
                TableColumn<LocalizationData, ?> t = findCol(targetUi);
                if (t != null) t.setVisible(true);
            }

            requestLayout();

            //
            Platform.runLater(() -> applyColumnSizing(true /* single mode */));
        });
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

    private static void setValueByLang(LocalizationData data, String lang, String value) {
        if (data == null || lang == null) return;

        switch (lang.toLowerCase(Locale.ROOT)) {
            case "ruru": data.setRuRu(value); break;
            case "dede": data.setDeDe(value); break;
            case "enus": data.setEnUs(value); break;
            case "esmx": data.setEsMx(value); break;
            case "eses": data.setEsEs(value); break;
            case "frfr": data.setFrFr(value); break;
            case "itit": data.setItIt(value); break;
            case "plpl": data.setPlPl(value); break;
            case "ptbr": data.setPtBr(value); break;
            case "kokr": data.setKoKr(value); break;
            case "zhcn": data.setZhCn(value); break;
            case "zhtw": data.setZhTw(value); break;
        }
    }
}
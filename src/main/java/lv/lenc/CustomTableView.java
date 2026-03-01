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
import javafx.util.converter.DefaultStringConverter;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
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
    public CustomTableView(String texturePath, LocalizationManager localization, double width,double height) {

        this.texturePath = texturePath;
        // Styling
        //  this.applyScrollBarStyle();

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

        TableColumn<LocalizationData, String> keyColumn = new TableColumn<>("key");
        keyColumn.setCellValueFactory(new PropertyValueFactory<>("key"));

        TableColumn<LocalizationData, String> ruRUColumn = new TableColumn<>("ruRU");
        ruRUColumn.setCellValueFactory(new PropertyValueFactory<>("ruRu"));
        // ─── немецкий ───
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
                setFont(javafx.scene.text.Font.font(
                        getFont().getFamily(),
                        javafx.scene.text.FontWeight.BOLD,
                        scaledFontPx(14)
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
                setFont(javafx.scene.text.Font.font(
                        getFont().getFamily(),
                        javafx.scene.text.FontWeight.BOLD,
                        scaledFontPx(14)
                ));
            }
        });
    }
    private void captureBaseColumnWidths() {
        // берем любую языковую колонку как эталон (например ruRU)
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
                // увеличиваем ТОЛЬКО для видимых колонок
                if (col.isVisible() && singleMode) {
                    col.setMinWidth(baseLangMinW * mult);
                    col.setPrefWidth(baseLangPrefW * mult);
                    col.setMaxWidth(baseLangMaxW);
                } else {
                    // возвращаем стандарт
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


    // Объяви это поле внутри твоего класса
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
                private boolean isSelected = false;
                private boolean isEditing = false;

                {
                    // добавляем ховер-эффект для ячейки
                    this.setOnMouseEntered(event -> {
                        isHovered = true;
                        updateCellStyle();
                    });
                    this.setOnMouseExited(event -> {
                        isHovered = false;
                        updateCellStyle();
                    });
                }

                private void updateCellStyle() {
                    int rowIndex = getIndex();
                    String rowBg = (rowIndex % 2 == 0) ? "rgba(0, 0, 0, 0.5)" : "rgba(0, 0, 0, 0.6)";
                    String baseTexture = texturePath + "ui_nova_archives_listitem_normal.png";
                    String hoverTexture = texturePath + "ui_nova_archives_listitem_over.png";
                    String selectedTexture = texturePath + "ui_nova_archives_listitem_selected.png";

                    String styleBase = "-fx-background-color: " + rowBg + ";" +
                            "-fx-border-image-source: url('" + baseTexture + "');" +
                            "-fx-text-fill: #80d2a2;" +
                            "-fx-font-weight: bold;";

                    if (isEditing || isSelected) {
                        setStyle(styleBase.replace(baseTexture, selectedTexture)
                                + "-fx-text-fill: white;"
                                + "-fx-font-weight: bold;"
                        );
                    } else if (isHovered) {
                        setStyle(styleBase.replace(baseTexture, hoverTexture)
                                + "-fx-text-fill: #b0ffd4;"
                                + "-fx-font-weight: bold;"
                        );
                    } else {
                        setStyle(styleBase);
                    }
                }

                @Override
                public void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);

                    if (!empty) {

                        if (!getStyleClass().contains("custom-table-cell")) {
                            getStyleClass().add("custom-table-cell");
                        }
                        int rowIndex = getIndex();
                        String rowBg = (rowIndex % 2 == 0) ? "rgba(0, 0, 0, 0.5)" : "rgba(0, 0, 0, 0.6)";

                        String styleBase = "-fx-background-color: " + rowBg + ";" +
                                "-fx-border-image-source: url('" + fullTexturePathNormal + "');"; // по умолчанию

                        // потом меняем в зависимости от состояния
                        if (isEditing || isSelected) {
                            setStyle(styleBase.replace(fullTexturePathNormal, fullTexturePathSelected));
                        } else if (isHovered) {
                            setStyle(styleBase.replace(fullTexturePathNormal, fullTexturePathOver));
                        } else {
                            setStyle(styleBase);
                        }

                        column.setOnEditStart(event -> {
                            isEditing = true;
                            setStyle(styleBase.replace(fullTexturePathNormal, fullTexturePathSelected));
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
                        isSelected = false;
                        isEditing = false;
                    }
                }

                @Override
                public void startEdit() {
                    super.startEdit();
                    isEditing = true;

                    // Обязательно: при старте редактирования делаем стиль жирный!
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
                    setFont(javafx.scene.text.Font.font(
                            getFont().getFamily(),
                            javafx.scene.text.FontWeight.BOLD,
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
                //    System.out.println("TableHeaderRow всё ещё не найден.");
            } else {
                //      System.out.println("Нашли TableHeaderRow!");

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
        if (s.length() == 4) {
            return s.substring(0, 2).toLowerCase() + s.substring(2).toUpperCase(); // ptbr -> ptBR
        }
        return raw;
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

        Map<String, Map<String, String>> perLang = langFiles.entrySet() // Ruru - KEY:VALUE
                .parallelStream() // threaded
                .filter(entry -> entry.getValue() != null && entry.getValue().exists()) // если есть файл то...
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
                            return keyValueMap; // (ruRU): ("HELLO" -> "Привет"),
                        }
                ));
        //Table
        Set<String> allKeys = perLang.values().stream() // Берем значение самого файла HELLO -> привет
                .flatMap(m -> m.keySet().stream())// множество ключей(из разных файлов) -> новый поток
                .collect(Collectors.toCollection(LinkedHashSet::new));// хранит ключи в порядке и без повторения

        ObservableList<LocalizationData> rows = FXCollections.observableArrayList(); // Динамический список
        // Add to table
        for (String key : allKeys) { // Перебираем все уникальные ключи в файле
            LocalizationData id = new LocalizationData(key); // Создаём объект строки таблицы с этим ключом
            for (String code : perLang.keySet()) { // Для каждого языка (например, "ruRU", "enUS", ...)
                String val = perLang.get(code).get(key); // берем ключь -> RuRU(code)->PARRAM/VALUE03=Привет Мир(key) (может быть null)
                String norm = normalizeUi(code); // "ptbr" -> "ptBR", "zhcn" -> "zhCN" и т.д.
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
        setItems(rows); // устанавливает список данных в таблицу
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

        // Детектируем язык по содержимому
        Map<String, String> keyValueMap = parseKeyValue(fileText);
        List<String> values = new ArrayList<>(keyValueMap.values());

        String detectedLang = detectLanguage(values);
        // highlightColumn(detectedLang);
        // Формируем строку для одной колонки (detectedLang)
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
            setter.accept(data, entry.getValue()); // установка ячейку "data" -> data.setRuRu(entry.getValue());
            rows.add(data);
        }
        setItems(rows); // добаляем в таблицу
        Platform.runLater(this::refresh);
        highlightColumn(detectedLang); // Подсветить определённую колонку

    }

    public static Map<String, String> parseKeyValue(String text) {
        return Arrays.stream(text.split("\\r?\\n"))
                .map(line -> line.split("=", 2))
                .filter(parts -> parts.length == 2)
                .collect(Collectors.toMap(
                        parts -> parts[0],
                        parts -> parts[1],
                        (a, b) -> a,                     // при дубликатах берём первый
                        LinkedHashMap::new              // сохраняем порядок
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

        // Добавляем кастомный стиль к header
        Platform.runLater(() -> {
            Node header = this.lookup(".column-header[data-column-index='" + this.getColumns().indexOf(col) + "']");
            if (header != null) {
                header.setStyle("-fx-background-color: rgba(255,140,0,0.18);"); // Оранжевый прозрачный
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
        return !s.equalsIgnoreCase("null"); // если где-то null приходит строкой
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
    // translate
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

        // 1) источник (ui)
        String sourceUi = (sourceLang != null && !sourceLang.isBlank())
                ? sourceLang
                : getMainSourceLang();

        // 2) тексты источника
        java.util.List<String> texts = getColumnValues(sourceUi);
        boolean hasAny = texts.stream().anyMatch(s -> s != null && !s.isBlank());
        if (!hasAny) {
            System.err.println("[LT] nothing to translate in column " + sourceUi);
            return;
        }

        // 3) цели: UI -> ISO, один ISO может соответствовать нескольким UI (esMX/esES -> es)
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

        // 4) ISO источника
        String sourceIso = toApiLang(sourceUi);
        if (sourceIso == null || sourceIso.isBlank() || "auto".equalsIgnoreCase(sourceIso)) {
            sourceIso = "en";
        }

        // 5) перевод на все ISO
        try {
            if (stop.getAsBoolean()) return;

            // ВАЖНО: должен вызываться translateAll, который переводит ВСЕ targetsIso,
            // а не только targetsIso.get(0)
            java.util.Map<String, java.util.List<String>> byIso =
                    lv.lenc.TranslationService.translateAll(texts, sourceIso, targetsIso);

            if (stop.getAsBoolean()) return;

            // 6) разложить по UI-колонкам
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

        // 1) источник (UI)
        String sourceUi = (sourceLang != null && !sourceLang.isBlank())
                ? sourceLang
                : getMainSourceLang();

        // 2) тексты источника
        java.util.List<String> texts = getColumnValues(sourceUi);
        boolean hasAny = texts.stream().anyMatch(s -> s != null && !s.isBlank());
        if (!hasAny) {
            System.err.println("[LT] nothing to translate in column " + sourceUi);
            return;
        }

        // 3) цели UI (в порядке таблицы!)
        // ВАЖНО: твой getTargetColumnsExcluding сейчас берёт SUPPORTED_LANGS.stream().toList()
        // у Set порядок не гарантируется. Поэтому фиксируем порядок вручную:
        final java.util.List<String> targetsUi = java.util.List.of(
                        "ruRU","deDE","enUS","esMX","esES","frFR","itIT","plPL","ptBR","koKR","zhCN","zhTW"
                ).stream()
                .filter(ui -> !ui.equalsIgnoreCase(sourceUi))
                .toList();

        final int totalUiTargets = targetsUi.size(); // сколько UI-колонок заполняем

        // 4) ISO-цели + карта iso -> ui[]
        final java.util.Map<String, java.util.List<String>> isoToUis = new java.util.LinkedHashMap<>();
        for (String ui : targetsUi) {
            if (stop.getAsBoolean()) return;

            String iso = toApiLang(ui);
            if (iso == null || iso.isBlank() || "auto".equalsIgnoreCase(iso)) continue;

            isoToUis.computeIfAbsent(iso, k -> new java.util.ArrayList<>()).add(ui);
        }

        // порядок ISO делаем по порядку UI-колонок
        final java.util.List<String> targetsIso = new java.util.ArrayList<>();
        {
            java.util.Set<String> added = new java.util.HashSet<>();
            for (String ui : targetsUi) {
                String iso = toApiLang(ui);
                if (iso == null || iso.isBlank() || "auto".equalsIgnoreCase(iso)) continue;
                if (added.add(iso)) targetsIso.add(iso);
            }
        }

        if (targetsIso.isEmpty()) {
            System.err.println("[LT] no valid target languages");
            return;
        }

        // 5) ISO источника
        String sourceIso = toApiLang(sourceUi);
        if (sourceIso == null || sourceIso.isBlank() || "auto".equalsIgnoreCase(sourceIso)) {
            sourceIso = "en";
        }

        // iso -> "ui для показа" (первый из группы)
        final java.util.Map<String, String> isoToShowUi = new java.util.HashMap<>();
        for (var e : isoToUis.entrySet()) {
            if (!e.getValue().isEmpty()) isoToShowUi.put(e.getKey(), e.getValue().get(0));
        }

        // 6) переводим ВСЕ ISO с прогрессом
        try {
            java.util.Map<String, java.util.List<String>> byIso =
                    lv.lenc.TranslationService.translateAll(
                            texts,
                            sourceIso,
                            targetsIso,
                            stop,
                            (all01, msg) -> {
                                if (progress == null) return;

                                // определяем "какой сейчас ISO" по all01
                                int isoCount = Math.max(1, targetsIso.size());
                                int isoIndex = Math.min(isoCount - 1, (int) Math.floor(all01 * isoCount));
                                String curIso = targetsIso.get(Math.max(0, isoIndex));

                                // показываем UI-цель, а не iso
                                String tgtUiShow = isoToShowUi.getOrDefault(curIso, curIso);

                                // номер UI-колонки (k/total) по targetsUi (который в порядке таблицы)
                                int k = 1;
                                for (int i = 0; i < targetsUi.size(); i++) {
                                    if (targetsUi.get(i).equalsIgnoreCase(tgtUiShow)) {
                                        k = i + 1;
                                        break;
                                    }
                                }

                                String line1 = sourceUi + " -> " + tgtUiShow + " (" + k + "/" + totalUiTargets + ")";
                                String line2 = extractBatchLine(msg);

                                progress.onProgress(all01, line1 + "||" + line2);
                            }
                    );

            if (stop.getAsBoolean()) return;

            // 7) разложить по UI-колонкам
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
            if (stop.getAsBoolean()) return; // отмена — молча
            System.err.println("[LT] translate failed: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private static String extractBatchLine(String msg) {
        if (msg == null) return "";
        // ожидаем что где-то будет "batch x/y"
        int idx = msg.indexOf("batch");
        if (idx >= 0) return msg.substring(idx).trim();
        return "";
    }

    // собрать все значения столбца (ui-код: "enUS", "ruRU"...)
    private java.util.List<String> getColumnValues(String uiLang) {
        java.util.List<String> out = new java.util.ArrayList<>(getItems().size());
        for (LocalizationData row : getItems()) {
            String v = getValueForLang(row, uiLang);
            out.add(v == null ? "" : v);
        }
        return out;
    }

    // целевые UI-столбцы: все поддерживаемые, кроме источника и "Key"/"N"
    private java.util.List<String> getTargetColumnsExcluding(String sourceUi) {
        return SUPPORTED_LANGS.stream()
                .filter(l -> !l.equalsIgnoreCase(sourceUi))
                .toList();
    }

    // проставить целый столбец значений (values.size() == числу строк)
    private void setColumnValues(String uiLang, java.util.List<String> values) {
        var items = getItems();
        int n = Math.min(items.size(), values.size());
        for (int i = 0; i < n; i++) {
            setValueForLang(items.get(i), uiLang, values.get(i));
        }
    }
    public void translateFromSourceToTarget(LibreTranslateApi api,
                                            String sourceUi,
                                            String targetUi,
                                            BooleanSupplier cancelled) {
        final BooleanSupplier cancelledFinal = (cancelled != null) ? cancelled : () -> false;
        final BooleanSupplier stop = () -> cancelledFinal.getAsBoolean() || Thread.currentThread().isInterrupted();

        if (stop.getAsBoolean()) return;

        if (sourceUi == null || sourceUi.isBlank()) sourceUi = getMainSourceLang();
        if (targetUi == null || targetUi.isBlank()) return;
        if (targetUi.equalsIgnoreCase(sourceUi)) return;

        List<String> texts = getColumnValues(sourceUi);

        // есть ли вообще что переводить
        boolean hasAny = texts.stream().anyMatch(s -> s != null && !s.isBlank());
        if (!hasAny) {
            System.err.println("[LT] nothing to translate in column " + sourceUi);
            return;
        }

        String sourceIso = toApiLang(sourceUi);
        String targetIso = toApiLang(targetUi);
        if ("auto".equalsIgnoreCase(sourceIso) || sourceIso == null) sourceIso = "en";
        if ("auto".equalsIgnoreCase(targetIso) || targetIso == null) return;

        try {
            // переводим ТОЛЬКО в 1 язык
            Map<String, List<String>> byIso =
                    TranslationService.translateAll(texts, sourceIso, List.of(targetIso));

            if (stop.getAsBoolean()) return;

            List<String> translated = byIso.get(targetIso);
            if (translated != null) {
                setColumnValues(targetUi, translated);
                Platform.runLater(this::refresh);
            }

        } catch (Exception ex) {
            if (stop.getAsBoolean()) return; // отменили — молча
            System.err.println("[LT] translate failed: " + ex.getMessage());
            ex.printStackTrace();
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

        // тексты из sourceUi
        java.util.List<String> texts = getColumnValues(sourceUi);
        boolean hasAny = texts.stream().anyMatch(s -> s != null && !s.isBlank());
        if (!hasAny) {
            if (progress != null) progress.onProgress(1.0, sourceUi + " -> " + targetUi + "||");
            return;
        }

        // ui -> iso
        String sourceIso = toApiLang(sourceUi);
        if (sourceIso == null || sourceIso.isBlank() || "auto".equalsIgnoreCase(sourceIso)) sourceIso = "en";

        String targetIso = toApiLang(targetUi);
        if (targetIso == null || targetIso.isBlank() || "auto".equalsIgnoreCase(targetIso)) return;

        try {
            // один язык: используем batched + progress
            java.util.List<String> out = lv.lenc.TranslationService.translatePreservingTagsBatched(
                    TranslationService.api,
                    texts,
                    sourceIso,
                    targetIso,
                    stop,
                    (frac, msg) -> {
                        if (progress == null) return;
                        String line1 = sourceUi + " -> " + targetUi;
                        String line2 = (msg == null ? "" : msg);
                        progress.onProgress(frac, line1 + "||" + line2);
                    }
            );

            if (stop.getAsBoolean()) return;

            setColumnValues(targetUi, out);
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

            // после перестроения — стандартные ширины
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

            // после перестроения — увеличенные ширины (x5)
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
    private static double scaledFontPx(double basePx) {
        // font лучше масштабировать мягко (не как scale() с округлениями)
        return UiScaleHelper.scaleY(basePx);
    }
}
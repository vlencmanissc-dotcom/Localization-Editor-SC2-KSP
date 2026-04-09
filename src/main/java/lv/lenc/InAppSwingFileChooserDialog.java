package lv.lenc;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.effect.Effect;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

final class InAppSwingFileChooserDialog {
    private static final double DIALOG_SCALE = 1.20;
    private static final long DISABLE_ANIMATIONS_UPTIME_MS = 6_000;
    private static final long TYPEAHEAD_RESET_MS = 950;
    private static final double BACKGROUND_BLUR_RADIUS = 5.2;
    private final Pane host;
    private final LocalizationManager localization;
    private final Consumer<File> onConfirm;
    private final StackPane overlay = new StackPane();
    private final Region dim = new Region();
    private final VBox panel = new VBox();
    private final TextField pathField = new TextField();
    private final TextField searchField = new TextField();
    private final TextField fileNameField = new TextField();
    private final ListView<LocationEntry> placesList = new ListView<>();
    private final TableView<FileEntry> filesTable = new TableView<>();
    private final ComboBox<FilterOption> filterCombo = new ComboBox<>();
    private final Label statusLabel = new Label();
    private final Button upButton = new Button();
    private final Button openButton = new Button();
    private final Button cancelButton = new Button();
    private File currentDirectory;
    private String pendingSelectionName;
    private boolean suppressPlacesSelection;
    private boolean closing;
    private double dragStartSceneX;
    private double dragStartSceneY;
    private double dragStartTranslateX;
    private double dragStartTranslateY;
    private String typeAheadBuffer = "";
    private long typeAheadLastInputAt;
    private final List<GaussianBlur> activeBlurEffects = new ArrayList<>();
    private final List<Node> blurredNodes = new ArrayList<>();
    private final Map<Node, Effect> previousEffects = new IdentityHashMap<>();

    private static final class LocationEntry {
        final String label;
        final File directory;
        final boolean rootsView;

        LocationEntry(String label, File directory, boolean rootsView) {
            this.label = label;
            this.directory = directory;
            this.rootsView = rootsView;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private static final class FilterOption {
        final String label;
        final List<String> extensions;
        final boolean allFiles;

        FilterOption(String label, boolean allFiles, String... extensions) {
            this.label = label;
            this.extensions = Arrays.asList(extensions);
            this.allFiles = allFiles;
        }

        boolean accepts(File file) {
            if (file == null) return false;
            if (file.isDirectory()) return true;
            if (allFiles) return true;
            String lower = file.getName().toLowerCase(Locale.ROOT);
            for (String ext : extensions) {
                if (lower.endsWith(ext)) return true;
            }
            return false;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private static final class FileEntry {
        final File file;
        final String name;
        final String type;

        FileEntry(File file, String name, String type) {
            this.file = file;
            this.name = name;
            this.type = type;
        }
    }

    private InAppSwingFileChooserDialog(Pane host,
                                        LocalizationManager localization,
                                        File initialSelection,
                                        Consumer<File> onConfirm) {
        this.host = host;
        this.localization = localization;
        this.onConfirm = onConfirm;

        configureButtons();
        configurePlaces();
        configureTable();
        configureFilters();
        configureFields();
        navigateTo(resolveInitialDirectory(initialSelection), initialSelection);
    }

    private double sf(double fullHdPx, double minPx) {
        return Math.max(UiScaleHelper.scaleY(fullHdPx * DIALOG_SCALE), minPx);
    }
    private double sfx(double fullHdPx, double minPx) {
        return Math.max(UiScaleHelper.scaleX(fullHdPx * DIALOG_SCALE), minPx);
    }

    static void show(Node owner,
                     LocalizationManager localization,
                     File initialSelection,
                     Consumer<File> onConfirm) {
        if (owner == null || owner.getScene() == null || onConfirm == null) {
            return;
        }
        UiScaleHelper.refreshFromScene(owner.getScene());
        Parent sceneRoot = owner.getScene().getRoot();
        if (!(sceneRoot instanceof Pane pane)) {
            return;
        }

        InAppSwingFileChooserDialog dialog =
                new InAppSwingFileChooserDialog(pane, localization, initialSelection, onConfirm);
        dialog.open();
    }

    private void open() {
        dim.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        dim.setStyle(FileOpenDialogStyle.overlayDimStyle());
        FileOpenDialogStyle.ensureStylesheet(overlay);

        Label title = new Label(text("\u041e\u0442\u043a\u0440\u044b\u0442\u044c", "Open"));
        title.setStyle(FileOpenDialogStyle.titleStyle(sf(18, 13)));

        statusLabel.setWrapText(true);
        statusLabel.setStyle(FileOpenDialogStyle.statusStyle(sf(12, 10)));

        HBox titleBar = new HBox(title);
        titleBar.setAlignment(Pos.CENTER_LEFT);
        enableDragging(titleBar);

        HBox pathBar = new HBox(sfx(8, 6), upButton, pathField, searchField);
        pathBar.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(pathField, Priority.ALWAYS);
        searchField.setPrefWidth(Math.max(sfx(240, 180), sfx(180, 140)));

        SplitPane splitPane = new SplitPane(placesList, filesTable);
        splitPane.getStyleClass().add("file-open-split");
        splitPane.setDividerPositions(0.24);
        splitPane.setPrefHeight(sf(380, 260));
        VBox.setVgrow(splitPane, Priority.ALWAYS);
        splitPane.setStyle("-fx-background-color: transparent;");

        Label fileNameLabel = new Label(text("\u0418\u043c\u044f \u0444\u0430\u0439\u043b\u0430:", "File name:"));
        styleFormLabel(fileNameLabel);
        HBox fileRow = new HBox(sfx(8, 6), fileNameLabel, fileNameField);
        fileRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(fileNameField, Priority.ALWAYS);

        Label filterLabel = new Label(text("\u0422\u0438\u043f:", "Filter:"));
        styleFormLabel(filterLabel);
        Region buttonSpacer = new Region();
        HBox.setHgrow(buttonSpacer, Priority.ALWAYS);
        HBox actions = new HBox(sfx(10, 8), filterLabel, filterCombo, buttonSpacer, openButton, cancelButton);
        actions.setAlignment(Pos.CENTER_LEFT);

        panel.setSpacing(sf(10, 7));
        panel.getChildren().setAll(
                titleBar,
                statusLabel,
                pathBar,
                splitPane,
                fileRow,
                actions
        );
        if (!panel.getStyleClass().contains("file-open-panel")) {
            panel.getStyleClass().add("file-open-panel");
        }
        panel.setPadding(new Insets(
                sf(14, 10),
                sfx(14, 10),
                sf(14, 10),
                sfx(14, 10)
        ));
        double panelW = Math.max(sfx(920, 720), sfx(720, 560));
        double panelH = Math.max(sf(590, 470), sf(470, 360));
        panel.setMinSize(panelW, panelH);
        panel.setPrefSize(panelW, panelH);
        panel.setMaxSize(panelW, panelH);
        panel.setStyle(FileOpenDialogStyle.panelStyle());

        overlay.getChildren().setAll(dim, panel);
        overlay.setPickOnBounds(true);
        overlay.setFocusTraversable(true);
        overlay.setViewOrder(-20_000);
        StackPane.setAlignment(panel, Pos.CENTER);
        overlay.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                close();
                event.consume();
            }
        });
        dim.setOnMouseClicked(event -> event.consume());

        applyBackgroundBlur();
        host.getChildren().add(overlay);
        overlay.toFront();
        applyTableHeaderScale();
        playOpenAnimation();
        Platform.runLater(pathField::requestFocus);
    }

    private void configureButtons() {
        upButton.setText(text("\u0412\u0432\u0435\u0440\u0445", "Up"));
        openButton.setText(text("\u041e\u0442\u043a\u0440\u044b\u0442\u044c", "Open"));
        cancelButton.setText(text("\u041e\u0442\u043c\u0435\u043d\u0430", "Cancel"));

        styleButton(upButton, 110);
        styleButton(openButton, 150);
        styleButton(cancelButton, 150);
        upButton.getStyleClass().add("file-open-button-compact");

        upButton.setOnAction(event -> navigateUp());
        openButton.setOnAction(event -> activateSelection());
        cancelButton.setOnAction(event -> close());
    }

    private void configurePlaces() {
        placesList.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        placesList.setItems(buildPlaces());
        placesList.getStyleClass().addAll("file-open-list", "file-open-places");
        placesList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(LocationEntry item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.label);
            }
        });
        placesList.setFixedCellSize(sf(30, 22));
        placesList.setPrefWidth(sfx(190, 150));
        placesList.setMinWidth(sfx(190, 150));
        placesList.setStyle(FileOpenDialogStyle.listSurfaceStyle(sf(13, 11)));
        placesList.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (handleTypeAheadInPlaces(event)) {
                event.consume();
            }
        });
        placesList.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
            if (suppressPlacesSelection || newValue == null) return;
            if (newValue.rootsView) {
                navigateTo(null, null);
            } else {
                navigateTo(newValue.directory, null);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private void configureTable() {
        filesTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        filesTable.getStyleClass().add("file-open-table");
        filesTable.setPlaceholder(new Label(text(
                "\u0412 \u044d\u0442\u043e\u0439 \u043f\u0430\u043f\u043a\u0435 \u043d\u0435\u0442 \u043f\u043e\u0434\u0445\u043e\u0434\u044f\u0449\u0438\u0445 \u0444\u0430\u0439\u043b\u043e\u0432.",
                "This folder has no matching files."
        )));
        filesTable.setStyle(FileOpenDialogStyle.tableSurfaceStyle(sf(13, 11)));
        filesTable.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        filesTable.setFixedCellSize(sf(30, 22));

        TableColumn<FileEntry, String> nameColumn = new TableColumn<>(text("\u0418\u043c\u044f", "Name"));
        nameColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().name));
        nameColumn.setPrefWidth(sfx(470, 280));

        TableColumn<FileEntry, String> typeColumn = new TableColumn<>(text("\u0422\u0438\u043f", "Type"));
        typeColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().type));
        typeColumn.setPrefWidth(sfx(150, 100));
        typeColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item);
                setAlignment(Pos.CENTER);
            }
        });

        filesTable.getColumns().setAll(nameColumn, typeColumn);

        filesTable.setRowFactory(table -> {
            TableRow<FileEntry> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() >= 2 && !row.isEmpty()) {
                    activateEntry(row.getItem());
                }
            });
            return row;
        });
        filesTable.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
            syncSelectionToField(newValue);
        });
        filesTable.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ENTER) {
                activateSelection();
                event.consume();
                return;
            }
            if (handleTypeAheadInFiles(event)) {
                event.consume();
            }
        });
    }

    private void configureFilters() {
        FilterOption localizationFilter = new FilterOption(
                text("\u041b\u043e\u043a\u0430\u043b\u0438\u0437\u0430\u0446\u0438\u044f (*.txt, *.SC2Map, *.SC2Mod, *.mpq)",
                        "Localization (*.txt, *.SC2Map, *.SC2Mod, *.mpq)"),
                false, ".txt", ".sc2map", ".sc2mod", ".mpq"
        );
        FilterOption txtFilter = new FilterOption(text("Text (*.txt)", "Text (*.txt)"), false, ".txt");
        FilterOption archiveFilter = new FilterOption(
                text("\u0410\u0440\u0445\u0438\u0432\u044b (*.SC2Map, *.SC2Mod, *.mpq)",
                        "Archives (*.SC2Map, *.SC2Mod, *.mpq)"),
                false, ".sc2map", ".sc2mod", ".mpq"
        );
        FilterOption allFiles = new FilterOption(text("\u0412\u0441\u0435 \u0444\u0430\u0439\u043b\u044b", "All files"), true);

        filterCombo.getItems().setAll(localizationFilter, txtFilter, archiveFilter, allFiles);
        filterCombo.getStyleClass().add("file-open-filter");
        filterCombo.setValue(localizationFilter);
        filterCombo.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(FilterOption item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.label);
            }
        });
        filterCombo.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(FilterOption item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.label);
            }
        });
        filterCombo.setPrefWidth(sfx(350, 240));
        filterCombo.setStyle(FileOpenDialogStyle.fieldStyle(sf(13, 11)));
        // Keep a static filter label: do not open dropdown list.
        filterCombo.setMouseTransparent(true);
        filterCombo.setFocusTraversable(false);
    }

    private void configureFields() {
        String fieldStyle = FileOpenDialogStyle.fieldStyle(sf(13, 11));
        pathField.setStyle(fieldStyle);
        searchField.setStyle(fieldStyle);
        fileNameField.setStyle(fieldStyle);
        searchField.setPromptText(text("\u041f\u043e\u0438\u0441\u043a \u0432 \u043f\u0430\u043f\u043a\u0435", "Search in folder"));

        pathField.setOnAction(event -> navigateFromPathField());
        searchField.textProperty().addListener((obs, oldValue, newValue) -> refreshEntries());
        fileNameField.setOnAction(event -> activateSelection());
        fileNameField.textProperty().addListener((obs, oldValue, newValue) -> updateOpenButtonState());
    }

    private ObservableList<LocationEntry> buildPlaces() {
        List<LocationEntry> entries = new ArrayList<>();
        entries.add(new LocationEntry(text("\u042d\u0442\u043e\u0442 \u043a\u043e\u043c\u043f\u044c\u044e\u0442\u0435\u0440", "This PC"), null, true));

        File home = new File(System.getProperty("user.home", ""));
        addPlace(entries, text("\u0414\u043e\u043c\u0430\u0448\u043d\u044f\u044f", "Home"), home);
        addPlace(entries, "Desktop", new File(home, "Desktop"));
        addPlace(entries, "Documents", new File(home, "Documents"));
        addPlace(entries, "Downloads", new File(home, "Downloads"));

        File[] roots = File.listRoots();
        if (roots != null) {
            for (File root : roots) {
                if (root != null && root.exists()) {
                    entries.add(new LocationEntry(root.getAbsolutePath(), root, false));
                }
            }
        }
        return FXCollections.observableArrayList(entries);
    }

    private void addPlace(List<LocationEntry> entries, String label, File dir) {
        if (dir != null && dir.exists() && dir.isDirectory()) {
            entries.add(new LocationEntry(label, dir, false));
        }
    }

    private void navigateUp() {
        if (currentDirectory == null) return;
        File parent = currentDirectory.getParentFile();
        navigateTo(parent, null);
    }

    private void navigateFromPathField() {
        String raw = pathField.getText();
        if (raw == null || raw.isBlank()) return;
        File target = new File(raw.trim());
        if (!target.exists()) return;
        if (target.isDirectory()) {
            navigateTo(target, null);
        } else {
            navigateTo(target.getParentFile(), target);
        }
    }

    private void activateSelection() {
        File target = resolveTargetFromField();
        if (target == null) {
            FileEntry selectedEntry = filesTable.getSelectionModel().getSelectedItem();
            if (selectedEntry != null) target = selectedEntry.file;
        }
        if (target == null) return;
        activateFile(target);
    }

    private void activateEntry(FileEntry entry) {
        if (entry == null) return;
        activateFile(entry.file);
    }

    private void activateFile(File target) {
        if (target == null || !target.exists()) return;
        if (target.isDirectory() && !isSelectableDirectory(target)) {
            navigateTo(target, null);
            return;
        }
        File archiveContainer = resolveArchiveContainer(target);
        if (archiveContainer != null) {
            close(() -> onConfirm.accept(archiveContainer));
            return;
        }
        close(() -> onConfirm.accept(target));
    }

    private boolean handleTypeAheadInFiles(KeyEvent event) {
        if (event == null || event.isControlDown() || event.isAltDown() || event.isMetaDown()) {
            return false;
        }
        if (event.getCode() == KeyCode.BACK_SPACE) {
            if (!typeAheadBuffer.isEmpty()) {
                typeAheadBuffer = typeAheadBuffer.substring(0, typeAheadBuffer.length() - 1);
                typeAheadLastInputAt = System.currentTimeMillis();
            }
            return true;
        }

        String typed = normalizeTypeAheadChar(event.getText());
        if (typed.isEmpty()) {
            return false;
        }
        String prefix = appendTypeAhead(typed);
        ObservableList<FileEntry> items = filesTable.getItems();
        if (items == null || items.isEmpty()) {
            return false;
        }

        int current = filesTable.getSelectionModel().getSelectedIndex();
        int matched = findByPrefix(items, current, prefix, fe -> fe != null ? fe.name : null);
        if (matched < 0 && prefix.length() > 1) {
            matched = findByPrefix(items, current, typed, fe -> fe != null ? fe.name : null);
            if (matched >= 0) {
                typeAheadBuffer = typed;
            }
        }
        if (matched >= 0) {
            filesTable.getSelectionModel().clearAndSelect(matched);
            filesTable.scrollTo(Math.max(0, matched - 3));
            FileEntry entry = items.get(matched);
            if (entry != null) {
                fileNameField.setText(entry.name);
                fileNameField.positionCaret(fileNameField.getText() != null ? fileNameField.getText().length() : 0);
            }
            return true;
        }
        return false;
    }

    private boolean handleTypeAheadInPlaces(KeyEvent event) {
        if (event == null || event.isControlDown() || event.isAltDown() || event.isMetaDown()) {
            return false;
        }
        String typed = normalizeTypeAheadChar(event.getText());
        if (typed.isEmpty()) {
            return false;
        }
        String prefix = appendTypeAhead(typed);
        ObservableList<LocationEntry> items = placesList.getItems();
        if (items == null || items.isEmpty()) {
            return false;
        }
        int current = placesList.getSelectionModel().getSelectedIndex();
        int matched = findByPrefix(items, current, prefix, le -> le != null ? le.label : null);
        if (matched < 0 && prefix.length() > 1) {
            matched = findByPrefix(items, current, typed, le -> le != null ? le.label : null);
            if (matched >= 0) {
                typeAheadBuffer = typed;
            }
        }
        if (matched >= 0) {
            placesList.getSelectionModel().clearAndSelect(matched);
            placesList.scrollTo(Math.max(0, matched - 3));
            return true;
        }
        return false;
    }

    private String appendTypeAhead(String typed) {
        long now = System.currentTimeMillis();
        if (now - typeAheadLastInputAt > TYPEAHEAD_RESET_MS) {
            typeAheadBuffer = typed;
        } else {
            typeAheadBuffer = typeAheadBuffer + typed;
        }
        typeAheadLastInputAt = now;
        return typeAheadBuffer;
    }

    private String normalizeTypeAheadChar(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        char ch = lower.charAt(0);
        if (Character.isLetterOrDigit(ch) || ch == '_' || ch == '-' || ch == '.') {
            return String.valueOf(ch);
        }
        return "";
    }

    private <T> int findByPrefix(List<T> items, int currentIndex, String prefix, Function<T, String> nameExtractor) {
        if (items == null || items.isEmpty() || prefix == null || prefix.isBlank()) {
            return -1;
        }
        String normalizedPrefix = prefix.toLowerCase(Locale.ROOT);
        int size = items.size();
        int start = Math.max(-1, currentIndex);
        for (int step = 1; step <= size; step++) {
            int idx = (start + step) % size;
            T item = items.get(idx);
            String candidate = nameExtractor.apply(item);
            if (candidate == null) continue;
            if (candidate.toLowerCase(Locale.ROOT).startsWith(normalizedPrefix)) {
                return idx;
            }
        }
        return -1;
    }

    private File resolveTargetFromField() {
        String raw = fileNameField.getText();
        if (raw == null || raw.isBlank()) {
            return null;
        }

        File direct = new File(raw.trim());
        if (direct.isAbsolute()) {
            return direct.exists() ? direct : null;
        }
        if (currentDirectory == null) {
            return null;
        }
        File resolved = new File(currentDirectory, raw.trim());
        return resolved.exists() ? resolved : null;
    }

    private void navigateTo(File directory, File preferredSelection) {
        currentDirectory = directory != null && directory.exists() ? directory : null;
        pendingSelectionName = preferredSelection == null ? null : preferredSelection.getName();
        refreshEntries();
        syncPlacesSelection();
    }

    private void refreshEntries() {
        ObservableList<FileEntry> items = FXCollections.observableArrayList();
        if (currentDirectory == null) {
            File[] roots = File.listRoots();
            if (roots != null) {
                Arrays.sort(roots, Comparator.comparing(File::getAbsolutePath, String.CASE_INSENSITIVE_ORDER));
                for (File root : roots) {
                    if (root != null && root.exists()) {
                        items.add(new FileEntry(root, root.getAbsolutePath(), text("\u0414\u0438\u0441\u043a", "Drive")));
                    }
                }
            }
            pathField.setText(text("\u042d\u0442\u043e\u0442 \u043a\u043e\u043c\u043f\u044c\u044e\u0442\u0435\u0440", "This PC"));
            statusLabel.setText(text(
                    "\u0412\u044b\u0431\u0435\u0440\u0438 \u0434\u0438\u0441\u043a \u0438\u043b\u0438 \u043f\u0430\u043f\u043a\u0443.",
                    "Choose a drive or folder."
            ));
        } else {
            File[] children = currentDirectory.listFiles();
            if (children != null) {
                Arrays.sort(children, Comparator
                        .comparing(File::isFile)
                        .thenComparing(file -> file.getName().toLowerCase(Locale.ROOT)));
                FilterOption filter = filterCombo.getValue();
                String query = normalizeSearch(searchField.getText());
                for (File child : children) {
                    if (child == null || !child.exists()) continue;
                    boolean matchesQuery = query.isBlank()
                            || child.getName().toLowerCase(Locale.ROOT).contains(query);
                    if (!matchesQuery) continue;
                    if (child.isDirectory() || filter == null || filter.accepts(child)) {
                        items.add(new FileEntry(child, child.getName(), describeType(child)));
                    }
                }
            }
            pathField.setText(currentDirectory.getAbsolutePath());
            statusLabel.setText(text(
                    "\u0414\u0432\u043e\u0439\u043d\u043e\u0439 \u043a\u043b\u0438\u043a \u043e\u0442\u043a\u0440\u043e\u0435\u0442 \u043f\u0430\u043f\u043a\u0443 \u0438\u043b\u0438 \u0444\u0430\u0439\u043b.",
                    "Double-click opens a folder or file."
            ));
        }

        filesTable.setItems(items);
        upButton.setDisable(currentDirectory == null);
        restorePendingSelection();
        updateOpenButtonState();
    }

    private void restorePendingSelection() {
        filesTable.getSelectionModel().clearSelection();
        if (pendingSelectionName == null || pendingSelectionName.isBlank()) {
            fileNameField.clear();
            return;
        }
        for (FileEntry entry : filesTable.getItems()) {
            if (pendingSelectionName.equalsIgnoreCase(entry.name)) {
                filesTable.getSelectionModel().select(entry);
                filesTable.scrollTo(entry);
                syncSelectionToField(entry);
                pendingSelectionName = null;
                return;
            }
        }
        fileNameField.setText(pendingSelectionName);
        pendingSelectionName = null;
    }

    private void syncSelectionToField(FileEntry entry) {
        if (entry == null) {
            updateOpenButtonState();
            return;
        }
        fileNameField.setText(entry.name);
        updateOpenButtonState();
    }

    private void updateOpenButtonState() {
        FileEntry entry = filesTable.getSelectionModel().getSelectedItem();
        boolean disable = entry == null && (fileNameField.getText() == null || fileNameField.getText().isBlank());
        openButton.setDisable(disable);
    }

    private void syncPlacesSelection() {
        suppressPlacesSelection = true;
        try {
            if (currentDirectory == null) {
                selectPlaceByLabel(text("\u042d\u0442\u043e\u0442 \u043a\u043e\u043c\u043f\u044c\u044e\u0442\u0435\u0440", "This PC"));
                return;
            }
            for (LocationEntry entry : placesList.getItems()) {
                if (!entry.rootsView && entry.directory != null && sameFile(entry.directory, currentDirectory)) {
                    placesList.getSelectionModel().select(entry);
                    return;
                }
            }
            placesList.getSelectionModel().clearSelection();
        } finally {
            suppressPlacesSelection = false;
        }
    }

    private void selectPlaceByLabel(String label) {
        for (LocationEntry entry : placesList.getItems()) {
            if (Objects.equals(entry.label, label)) {
                placesList.getSelectionModel().select(entry);
                return;
            }
        }
        placesList.getSelectionModel().clearSelection();
    }

    private boolean sameFile(File a, File b) {
        try {
            return a != null && b != null && a.getCanonicalFile().equals(b.getCanonicalFile());
        } catch (Exception ex) {
            return Objects.equals(a, b);
        }
    }

    private File resolveInitialDirectory(File initialSelection) {
        if (initialSelection == null) {
            return defaultStartDirectory();
        }
        if (initialSelection.isFile()) {
            return initialSelection.getParentFile();
        }
        if (initialSelection.isDirectory() && isSelectableDirectory(initialSelection)) {
            return initialSelection.getParentFile();
        }
        return initialSelection.isDirectory() ? initialSelection : defaultStartDirectory();
    }

    private File defaultStartDirectory() {
        File home = new File(System.getProperty("user.home", ""));
        return home.exists() ? home : null;
    }

    private boolean isSelectableDirectory(File file) {
        if (file == null || !file.isDirectory()) return false;
        String lower = file.getName().toLowerCase(Locale.ROOT);
        return lower.endsWith(".sc2map") || lower.endsWith(".sc2mod");
    }

    private File resolveArchiveContainer(File selected) {
        if (selected == null) {
            return null;
        }
        File cursor = selected.getAbsoluteFile();
        while (cursor != null) {
            String lower = cursor.getName().toLowerCase(Locale.ROOT);
            if (lower.endsWith(".sc2map") || lower.endsWith(".sc2mod") || lower.endsWith(".mpq")) {
                return cursor;
            }
            cursor = cursor.getParentFile();
        }
        return null;
    }

    private String describeType(File file) {
        if (file == null) return "";
        if (file.isDirectory()) {
            String lower = file.getName().toLowerCase(Locale.ROOT);
            if (lower.endsWith(".sc2map")) return "SC2Map";
            if (lower.endsWith(".sc2mod")) return "SC2Mod";
            return text("\u041f\u0430\u043f\u043a\u0430", "Folder");
        }
        String lower = file.getName().toLowerCase(Locale.ROOT);
        if (lower.endsWith(".txt")) return "TXT";
        if (lower.endsWith(".mpq")) return "MPQ";
        int dot = file.getName().lastIndexOf('.');
        return dot >= 0 ? file.getName().substring(dot + 1).toUpperCase(Locale.ROOT) : text("\u0424\u0430\u0439\u043b", "File");
    }

    private void styleButton(Button button, double width) {
        button.setMinWidth(sfx(width, width * 0.72));
        button.setPrefWidth(sfx(width, width * 0.72));
        button.setMinHeight(sf(38, 30));
        button.setPrefHeight(sf(38, 30));
        button.setFocusTraversable(false);
        button.setAlignment(Pos.CENTER);
        button.setStyle(
                "-fx-font-size: " + sf(13, 11) + "px;"
                        + "-fx-padding: 0 " + sfx(12, 8) + "px 0 "
                        + sfx(12, 8) + "px;"
        );
        if (!button.getStyleClass().contains("file-open-button")) {
            button.getStyleClass().add("file-open-button");
        }
        UiSoundManager.bindNovaButton(button);

        final boolean[] pressed = {false};
        button.addEventHandler(MouseEvent.MOUSE_ENTERED, e -> {
            if (!button.isDisabled() && !pressed[0]) {
                button.setScaleX(1.02);
                button.setScaleY(1.02);
            }
        });
        button.addEventHandler(MouseEvent.MOUSE_EXITED, e -> {
            pressed[0] = false;
            button.setScaleX(1.0);
            button.setScaleY(1.0);
        });
        button.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
            if (button.isDisabled() || e.getButton() != MouseButton.PRIMARY) {
                return;
            }
            pressed[0] = true;
            button.setScaleX(0.985);
            button.setScaleY(0.985);
        });
        button.addEventHandler(MouseEvent.MOUSE_RELEASED, e -> {
            if (button.isDisabled() || e.getButton() != MouseButton.PRIMARY) {
                return;
            }
            pressed[0] = false;
            boolean inside = button.contains(button.sceneToLocal(e.getSceneX(), e.getSceneY()));
            button.setScaleX(inside ? 1.02 : 1.0);
            button.setScaleY(inside ? 1.02 : 1.0);
        });
    }

    private void applyTableHeaderScale() {
        final double headerFont = sf(13, 11);
        Platform.runLater(() -> filesTable.lookupAll(".column-header .label")
                .forEach(node -> node.setStyle("-fx-font-size: " + headerFont + "px; -fx-font-weight: 700;")));
    }

    private void styleFormLabel(Label label) {
        label.setStyle(FileOpenDialogStyle.formLabelStyle(sf(12, 10)));
    }

    private void close() {
        close(null);
    }

    private void close(Runnable afterClose) {
        if (closing) {
            return;
        }
        closing = true;
        if (shouldSkipAnimations()) {
            host.getChildren().remove(overlay);
            clearBackgroundBlur();
            closing = false;
            if (afterClose != null) {
                afterClose.run();
            }
            return;
        }
        ParallelTransition transition = new ParallelTransition(
                buildOverlayTransition(false),
                buildBlurTransition(false)
        );
        transition.setOnFinished(event -> {
            host.getChildren().remove(overlay);
            clearBackgroundBlur();
            closing = false;
            if (afterClose != null) {
                afterClose.run();
            }
        });
        transition.play();
    }

    private boolean isRussianUi() {
        String language = localization != null ? localization.getCurrentLanguage() : "";
        return language != null && language.toLowerCase(Locale.ROOT).startsWith("ru");
    }

    private String text(String ruText, String enText) {
        return isRussianUi() ? ruText : enText;
    }

    private String normalizeSearch(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private void playOpenAnimation() {
        closing = false;
        if (shouldSkipAnimations()) {
            overlay.setOpacity(1);
            panel.setScaleX(1);
            panel.setScaleY(1);
            double radius = UiScaleHelper.scaleY(BACKGROUND_BLUR_RADIUS);
            for (GaussianBlur blur : activeBlurEffects) {
                if (blur != null) {
                    blur.setRadius(radius);
                }
            }
            return;
        }
        new ParallelTransition(
                buildOverlayTransition(true),
                buildBlurTransition(true)
        ).play();
    }

    private ParallelTransition buildOverlayTransition(boolean opening) {
        Duration fadeDuration = Duration.millis(170);
        Duration motionDuration = Duration.millis(185);

        if (opening) {
            overlay.setOpacity(0);
            panel.setScaleX(0.965);
            panel.setScaleY(0.965);
            panel.setTranslateY(UiScaleHelper.scaleY(12));
        }

        FadeTransition overlayFade = new FadeTransition(fadeDuration, overlay);
        overlayFade.setFromValue(opening ? 0 : overlay.getOpacity());
        overlayFade.setToValue(opening ? 1 : 0);

        ScaleTransition panelScale = new ScaleTransition(motionDuration, panel);
        panelScale.setFromX(opening ? 0.965 : panel.getScaleX());
        panelScale.setFromY(opening ? 0.965 : panel.getScaleY());
        panelScale.setToX(opening ? 1 : 0.975);
        panelScale.setToY(opening ? 1 : 0.975);

        TranslateTransition panelMove = new TranslateTransition(motionDuration, panel);
        panelMove.setFromY(opening ? UiScaleHelper.scaleY(12) : panel.getTranslateY());
        panelMove.setToY(opening ? 0 : panel.getTranslateY() + UiScaleHelper.scaleY(8));

        return new ParallelTransition(overlayFade, panelScale, panelMove);
    }

    private Timeline buildBlurTransition(boolean opening) {
        double to = opening ? UiScaleHelper.scaleY(BACKGROUND_BLUR_RADIUS) : 0.0;
        Timeline timeline = new Timeline();
        for (GaussianBlur blur : activeBlurEffects) {
            if (blur == null) {
                continue;
            }
            double from = opening ? 0.0 : blur.getRadius();
            timeline.getKeyFrames().addAll(
                    new KeyFrame(
                            Duration.ZERO,
                            new KeyValue(blur.radiusProperty(), from, Interpolator.EASE_BOTH)
                    ),
                    new KeyFrame(
                            Duration.millis(185),
                            new KeyValue(blur.radiusProperty(), to, Interpolator.EASE_BOTH)
                    )
            );
        }
        return timeline;
    }

    private void applyBackgroundBlur() {
        clearBackgroundBlur();
        List<Node> snapshot = new ArrayList<>(host.getChildren());
        for (Node node : snapshot) {
            if (node == null || node == overlay) {
                continue;
            }
            GaussianBlur blur = new GaussianBlur(0);
            previousEffects.put(node, node.getEffect());
            node.setEffect(blur);
            blurredNodes.add(node);
            activeBlurEffects.add(blur);
        }
    }

    private void clearBackgroundBlur() {
        for (Node node : blurredNodes) {
            if (node == null) {
                continue;
            }
            Effect previous = previousEffects.get(node);
            node.setEffect(previous);
        }
        activeBlurEffects.clear();
        blurredNodes.clear();
        previousEffects.clear();
    }

    private void enableDragging(Node dragHandle) {
        CustomCursorManager.applyDragGripCursor(dragHandle);
        dragHandle.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            if (event.getButton() != MouseButton.PRIMARY) {
                return;
            }
            dragStartSceneX = event.getSceneX();
            dragStartSceneY = event.getSceneY();
            dragStartTranslateX = panel.getTranslateX();
            dragStartTranslateY = panel.getTranslateY();
        });

        dragHandle.addEventFilter(MouseEvent.MOUSE_DRAGGED, event -> {
            if (!event.isPrimaryButtonDown()) {
                return;
            }
            double dx = event.getSceneX() - dragStartSceneX;
            double dy = event.getSceneY() - dragStartSceneY;
            panel.setTranslateX(dragStartTranslateX + dx);
            panel.setTranslateY(dragStartTranslateY + dy);
            event.consume();
        });
    }

    private boolean shouldSkipAnimations() {
        try {
            return ManagementFactory.getRuntimeMXBean().getUptime() < DISABLE_ANIMATIONS_UPTIME_MS;
        } catch (Exception ignored) {
            return false;
        }
    }

}

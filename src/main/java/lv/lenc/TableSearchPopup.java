package lv.lenc;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.stage.Screen;

public final class TableSearchPopup {
    private static final int MAX_RESULTS = 150;
    private static final List<String> SEARCH_LANGS = List.of(
            "ruRU", "deDE", "enUS", "esMX", "esES", "frFR", "itIT", "plPL", "ptBR", "koKR", "zhCN", "zhTW"
    );

    private final LocalizationManager localization;
    private final CustomTableView tableView;
    private final Popup popup = new Popup();
    private final VBox root = new VBox(UiScaleHelper.scaleY(12));
    private final HBox headerBar = new HBox(UiScaleHelper.scaleX(10));
    private final Label titleLabel = new Label();
    private final TextField queryField = new TextField();
    private final Label statusLabel = new Label();
    private final Label emptyLabel = new Label();
    private final ListView<SearchResult> resultsList = new ListView<>();
    private final CustomAlternativeButton openButton;
    private final CustomAlternativeButton clearButton;
    private final CustomAlternativeButton closeButton;
    private Node ownerNode;
    private double dragOffsetX;
    private double dragOffsetY;

    private static final class SearchResult {
        final LocalizationData row;
        final String key;
        final String preview;
        final int score;

        SearchResult(LocalizationData row, String key, String preview, int score) {
            this.row = row;
            this.key = key;
            this.preview = preview;
            this.score = score;
        }
    }

    public TableSearchPopup(LocalizationManager localization, CustomTableView tableView) {
        this.localization = localization;
        this.tableView = tableView;
        this.openButton = createActionButton(localize("tablesearch.open", "Open row"), 196.0);
        this.clearButton = createActionButton(localize("tablesearch.clear", "Clear"), 176.0);
        this.closeButton = createActionButton(localize("tablesearch.close", "Close"), 176.0);

        buildUi();
        wireEvents();
        updateTexts();
        reset();
    }

    public void toggle(Node owner) {
        if (popup.isShowing()) {
            popup.hide();
            return;
        }
        if (owner == null) {
            return;
        }

        ownerNode = owner;
        refreshResults();

        Bounds bounds = owner.localToScreen(owner.getBoundsInLocal());
        if (bounds == null) {
            return;
        }

        popup.show(owner, bounds.getMinX(), bounds.getMaxY() + UiScaleHelper.scaleY(8));
        Platform.runLater(() -> {
            positionPopup();
            queryField.requestFocus();
            queryField.selectAll();
        });
    }

    public void reset() {
        popup.hide();
        queryField.clear();
        resultsList.getItems().clear();
        resultsList.getSelectionModel().clearSelection();
        statusLabel.setText(localize("tablesearch.status.empty", "Type text to search in the current table."));
        emptyLabel.setText(localize("tablesearch.placeholder.empty", "Type text to start searching."));
    }

    public void updateTexts() {
        titleLabel.setText(localize("tablesearch.title", "Table Search"));
        queryField.setPromptText(localize("tablesearch.prompt", "Type a key or text..."));
        openButton.setText(localize("tablesearch.open", "Open row"));
        clearButton.setText(localize("tablesearch.clear", "Clear"));
        closeButton.setText(localize("tablesearch.close", "Close"));
        refreshResults();
    }

    private void buildUi() {
        AppStyles.applyTableSearchStyles(root);
        root.getStyleClass().add("table-search-popup");
        root.setPadding(new Insets(UiScaleHelper.scaleY(14), UiScaleHelper.scaleX(14), UiScaleHelper.scaleY(14), UiScaleHelper.scaleX(14)));
        root.setPrefWidth(UiScaleHelper.scaleX(620));
        root.setMinWidth(UiScaleHelper.scaleX(620));
        root.setMaxWidth(UiScaleHelper.scaleX(620));
        root.setPrefHeight(UiScaleHelper.scaleY(500));

        headerBar.getStyleClass().add("table-search-header");
        CustomCursorManager.applyDragGripCursor(headerBar);
        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);

        titleLabel.getStyleClass().add("table-search-title");
        headerBar.getChildren().addAll(titleLabel, headerSpacer);

        queryField.getStyleClass().addAll("key-filter-selected-path", "table-search-query");

        statusLabel.getStyleClass().add("table-search-status");
        statusLabel.setWrapText(true);

        emptyLabel.getStyleClass().add("table-search-placeholder");
        emptyLabel.setWrapText(true);

        resultsList.getStyleClass().add("table-search-list");
        resultsList.setPlaceholder(emptyLabel);
        resultsList.setCellFactory(list -> new SearchResultCell());
        VBox.setVgrow(resultsList, Priority.ALWAYS);

        HBox footer = new HBox(UiScaleHelper.scaleX(10));
        footer.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        footer.getChildren().addAll(clearButton, spacer, openButton, closeButton);

        root.getChildren().addAll(headerBar, queryField, statusLabel, resultsList, footer);

        popup.setAutoHide(true);
        popup.setHideOnEscape(true);
        popup.setAutoFix(true);
        popup.getContent().add(root);
    }

    private void wireEvents() {
        queryField.textProperty().addListener((obs, oldValue, newValue) -> refreshResults());
        queryField.setOnAction(e -> openSelectedOrFirst());
        queryField.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.DOWN && !resultsList.getItems().isEmpty()) {
                resultsList.requestFocus();
                if (resultsList.getSelectionModel().getSelectedIndex() < 0) {
                    resultsList.getSelectionModel().selectFirst();
                }
                e.consume();
            }
        });

        resultsList.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ENTER) {
                openSelectedOrFirst();
                e.consume();
            }
        });
        resultsList.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() >= 2) {
                openSelectedOrFirst();
            }
        });

        openButton.disableProperty().bind(Bindings.isNull(resultsList.getSelectionModel().selectedItemProperty()));
        openButton.setOnAction(e -> openSelectedOrFirst());
        clearButton.setOnAction(e -> {
            queryField.clear();
            queryField.requestFocus();
        });
        closeButton.setOnAction(e -> popup.hide());

        headerBar.addEventFilter(MouseEvent.MOUSE_PRESSED, this::beginDragPopup);
        headerBar.addEventFilter(MouseEvent.MOUSE_DRAGGED, this::dragPopup);

        popup.setOnHidden(e -> {
            if (ownerNode != null) {
                ownerNode.requestFocus();
            }
        });
    }

    private void refreshResults() {
        if (resultsList == null) {
            return;
        }

        String normalizedQuery = normalize(queryField.getText());
        if (normalizedQuery.isEmpty()) {
            resultsList.getItems().clear();
            resultsList.getSelectionModel().clearSelection();
            statusLabel.setText(localize("tablesearch.status.empty", "Type text to search in the current table."));
            emptyLabel.setText(localize("tablesearch.placeholder.empty", "Type text to start searching."));
            return;
        }

        List<SearchResult> matches = new ArrayList<>();
        for (LocalizationData row : tableView.getItems()) {
            SearchResult match = buildMatch(row, normalizedQuery);
            if (match != null) {
                matches.add(match);
            }
        }

        matches.sort(Comparator
                .comparingInt((SearchResult result) -> result.score)
                .thenComparing(result -> result.key, String.CASE_INSENSITIVE_ORDER));

        int totalMatches = matches.size();
        if (totalMatches == 0) {
            resultsList.getItems().clear();
            resultsList.getSelectionModel().clearSelection();
            statusLabel.setText(localize("tablesearch.status.none", "No matches found."));
            emptyLabel.setText(localize("tablesearch.placeholder.none", "No results."));
            return;
        }

        int visibleCount = Math.min(totalMatches, MAX_RESULTS);
        resultsList.getItems().setAll(matches.subList(0, visibleCount));
        resultsList.getSelectionModel().selectFirst();
        emptyLabel.setText(localize("tablesearch.placeholder.none", "No results."));

        if (totalMatches > MAX_RESULTS) {
            statusLabel.setText(formatLocalized(
                    "tablesearch.status.resultsLimited",
                    "Found %d matches. Showing first %d.",
                    totalMatches,
                    MAX_RESULTS
            ));
        } else {
            statusLabel.setText(formatLocalized(
                    "tablesearch.status.results",
                    "Found %d matches.",
                    totalMatches
            ));
        }
    }

    private SearchResult buildMatch(LocalizationData row, String normalizedQuery) {
        if (row == null) {
            return null;
        }

        String key = safe(row.getKey());
        if (key.isBlank()) {
            return null;
        }

        String loweredKey = key.toLowerCase(Locale.ROOT);
        if (loweredKey.startsWith(normalizedQuery)) {
            return new SearchResult(row, key, localize("tablesearch.match.key", "Matched in key"), 0);
        }
        if (loweredKey.contains(normalizedQuery)) {
            return new SearchResult(row, key, localize("tablesearch.match.key", "Matched in key"), 1);
        }

        for (int i = 0; i < SEARCH_LANGS.size(); i++) {
            String lang = SEARCH_LANGS.get(i);
            String value = row.getByLang(lang);
            if (value == null || value.isBlank()) {
                continue;
            }

            String collapsed = collapseWhitespace(value);
            String loweredValue = collapsed.toLowerCase(Locale.ROOT);
            int matchIndex = loweredValue.indexOf(normalizedQuery);
            if (matchIndex >= 0) {
                return new SearchResult(
                        row,
                        key,
                        formatLanguageLabel(lang) + ": " + buildSnippet(collapsed, matchIndex, normalizedQuery.length()),
                        10 + i
                );
            }
        }

        return null;
    }

    private void openSelectedOrFirst() {
        SearchResult selected = resultsList.getSelectionModel().getSelectedItem();
        if (selected == null && !resultsList.getItems().isEmpty()) {
            selected = resultsList.getItems().get(0);
        }
        if (selected == null) {
            return;
        }

        int rowIndex = tableView.getItems().indexOf(selected.row);
        if (rowIndex < 0) {
            rowIndex = findRowIndexByKey(selected.key);
        }
        if (rowIndex < 0) {
            return;
        }

        final int targetIndex = rowIndex;
        popup.hide();
        Platform.runLater(() -> {
            tableView.requestFocus();
            tableView.getSelectionModel().clearSelection();
            tableView.getSelectionModel().select(targetIndex);

            List<TableColumn<LocalizationData, ?>> visibleColumns = tableView.getVisibleLeafColumns();
            if (!visibleColumns.isEmpty()) {
                tableView.getFocusModel().focus(targetIndex, visibleColumns.get(0));
            }

            tableView.scrollTo(Math.max(0, targetIndex - 2));
            tableView.scrollTo(targetIndex);
        });
    }

    private int findRowIndexByKey(String key) {
        if (key == null || key.isBlank()) {
            return -1;
        }
        for (int i = 0; i < tableView.getItems().size(); i++) {
            LocalizationData row = tableView.getItems().get(i);
            if (key.equals(row.getKey())) {
                return i;
            }
        }
        return -1;
    }

    private void positionPopup() {
        if (!popup.isShowing() || ownerNode == null) {
            return;
        }

        Bounds bounds = ownerNode.localToScreen(ownerNode.getBoundsInLocal());
        if (bounds == null) {
            return;
        }

        root.applyCss();
        root.layout();

        double popupWidth = Math.max(root.prefWidth(-1), root.getWidth());
        double popupHeight = Math.max(root.prefHeight(-1), root.getHeight());
        Rectangle2D screenBounds = Screen.getScreensForRectangle(bounds.getMinX(), bounds.getMinY(), bounds.getWidth(), bounds.getHeight())
                .stream()
                .findFirst()
                .orElse(Screen.getPrimary())
                .getVisualBounds();

        double marginX = UiScaleHelper.scaleX(8);
        double marginY = UiScaleHelper.scaleY(8);

        double x = bounds.getMaxX() - popupWidth;
        double maxX = Math.max(screenBounds.getMinX() + marginX, screenBounds.getMaxX() - popupWidth - marginX);
        x = clamp(x, screenBounds.getMinX() + marginX, maxX);

        double y = bounds.getMaxY() + marginY;
        double maxY = Math.max(screenBounds.getMinY() + marginY, screenBounds.getMaxY() - popupHeight - marginY);
        if (y > maxY) {
            y = bounds.getMinY() - popupHeight - marginY;
        }
        y = clamp(y, screenBounds.getMinY() + marginY, maxY);

        popup.setX(x);
        popup.setY(y);
    }

    private void beginDragPopup(MouseEvent event) {
        if (!popup.isShowing()) {
            return;
        }
        dragOffsetX = event.getScreenX() - popup.getX();
        dragOffsetY = event.getScreenY() - popup.getY();
        event.consume();
    }

    private void dragPopup(MouseEvent event) {
        if (!popup.isShowing()) {
            return;
        }

        double desiredX = event.getScreenX() - dragOffsetX;
        double desiredY = event.getScreenY() - dragOffsetY;
        popup.setX(clampPopupX(desiredX, event.getScreenX(), event.getScreenY()));
        popup.setY(clampPopupY(desiredY, event.getScreenX(), event.getScreenY()));
        event.consume();
    }

    private double clampPopupX(double desiredX, double referenceScreenX, double referenceScreenY) {
        Rectangle2D bounds = popupScreenBounds(referenceScreenX, referenceScreenY);
        double popupWidth = Math.max(root.prefWidth(-1), root.getWidth());
        double minX = bounds.getMinX() + UiScaleHelper.scaleX(8);
        double maxX = bounds.getMaxX() - popupWidth - UiScaleHelper.scaleX(8);
        return clamp(desiredX, minX, maxX);
    }

    private double clampPopupY(double desiredY, double referenceScreenX, double referenceScreenY) {
        Rectangle2D bounds = popupScreenBounds(referenceScreenX, referenceScreenY);
        double popupHeight = Math.max(root.prefHeight(-1), root.getHeight());
        double minY = bounds.getMinY() + UiScaleHelper.scaleY(8);
        double maxY = bounds.getMaxY() - popupHeight - UiScaleHelper.scaleY(8);
        return clamp(desiredY, minY, maxY);
    }

    private Rectangle2D popupScreenBounds(double referenceScreenX, double referenceScreenY) {
        root.applyCss();
        root.layout();
        return Screen.getScreensForRectangle(referenceScreenX, referenceScreenY, 1, 1)
                .stream()
                .findFirst()
                .orElse(Screen.getPrimary())
                .getVisualBounds();
    }

    private CustomAlternativeButton createActionButton(String text, double widthPx) {
        CustomAlternativeButton button = new CustomAlternativeButton(
                text,
                0.6,
                0.8,
                widthPx,
                58.0,
                13.8
        );
        double scaledWidth = UiScaleHelper.scaleX(widthPx);
        double scaledHeight = UiScaleHelper.scaleY(58.0);
        button.setMinSize(scaledWidth, scaledHeight);
        button.setPrefSize(scaledWidth, scaledHeight);
        button.setMaxSize(scaledWidth, scaledHeight);
        button.setWrapText(false);
        button.setMnemonicParsing(false);
        button.getStyleClass().add("key-filter-action-button");
        button.setStyle("-fx-font-size: " + UiScaleHelper.scaleFont(14.2, 12.0) + "px;");

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
        return button;
    }

    private String localize(String key, String fallback) {
        if (localization == null) {
            return fallback;
        }
        try {
            String value = localization.get(key);
            if (value == null || value.isBlank() || key.equals(value)) {
                return fallback;
            }
            return value;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String formatLocalized(String key, String fallback, Object... args) {
        String template = localize(key, fallback);
        try {
            return String.format(Locale.ROOT, template, args);
        } catch (Exception ignored) {
            return String.format(Locale.ROOT, fallback, args);
        }
    }

    private static String normalize(String text) {
        return text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
    }

    private static String safe(String text) {
        return text == null ? "" : text;
    }

    private static String collapseWhitespace(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text.replace('\r', ' ').replace('\n', ' ').trim();
    }

    private static String buildSnippet(String text, int matchIndex, int queryLength) {
        if (text == null || text.isBlank()) {
            return "";
        }

        int start = Math.max(0, matchIndex - 28);
        int end = Math.min(text.length(), matchIndex + queryLength + 68);
        String snippet = text.substring(start, end).trim();

        if (start > 0) {
            snippet = "..." + snippet;
        }
        if (end < text.length()) {
            snippet = snippet + "...";
        }
        return snippet;
    }

    private static String formatLanguageLabel(String lang) {
        return switch (lang) {
            case "ruRU" -> "RU";
            case "deDE" -> "DE";
            case "enUS" -> "EN";
            case "esMX" -> "ES-MX";
            case "esES" -> "ES-ES";
            case "frFR" -> "FR";
            case "itIT" -> "IT";
            case "plPL" -> "PL";
            case "ptBR" -> "PT-BR";
            case "koKR" -> "KO";
            case "zhCN" -> "ZH-CN";
            case "zhTW" -> "ZH-TW";
            default -> lang;
        };
    }

    private static double clamp(double value, double min, double max) {
        if (max < min) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }

    private static final class SearchResultCell extends ListCell<SearchResult> {
        private final Label keyLabel = new Label();
        private final Label previewLabel = new Label();
        private final VBox content = new VBox(UiScaleHelper.scaleY(4));

        private SearchResultCell() {
            keyLabel.getStyleClass().add("table-search-result-key");
            previewLabel.getStyleClass().add("table-search-result-preview");
            previewLabel.setWrapText(true);
            content.getStyleClass().add("table-search-result");
            content.getChildren().addAll(keyLabel, previewLabel);
        }

        @Override
        protected void updateItem(SearchResult item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                setText(null);
                return;
            }

            keyLabel.setText(item.key);
            previewLabel.setText(item.preview);
            setText(null);
            setGraphic(content);
        }
    }
}

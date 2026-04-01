# Codebase Overview

## Purpose

This repository is a JavaFX desktop application for editing StarCraft II localization files such as `GameStrings.txt`, `ObjectStrings.txt`, and `TriggerStrings.txt`.

The main use case is:

1. Open one localization file from an SC2 project.
2. Load matching files from sibling `xxXX.SC2Data/LocalizedData` folders.
3. Display all known languages side by side in a table.
4. Translate missing content through LibreTranslate.
5. Save the generated output back into the correct SC2 directory structure.

## Tech Stack

- Java 17
- Maven
- JavaFX 22
- Retrofit + OkHttp for HTTP calls
- Gson/Jackson converters
- `com.cybozu.labs.langdetect` for language detection
- Custom JavaFX UI components and styling

## High-Level Architecture

The project is organized around a few core layers:

- Application startup and platform setup
- JavaFX UI composition and event wiring
- Table-based localization editing
- File system discovery and save routing for SC2 locale folders
- Translation service integration with LibreTranslate
- Glossary-based exact-match substitutions
- User settings and logging

## Main Entry Points

### `AppLauncher`

`src/main/java/lv/lenc/AppLauncher.java`

Responsibilities:

- Initializes logging through `AppLog`
- Installs a global uncaught exception handler
- Sets JavaFX and Java2D GPU-related system properties
- On Windows, writes a per-user registry preference to request the high-performance GPU for `java.exe` and `javaw.exe`
- Delegates to `Main.main(args)`

This class is the practical bootstrap layer for runtime setup.

### `Main`

`src/main/java/lv/lenc/Main.java`

This is the main JavaFX application class. It owns the primary screen, creates the custom controls, wires user interactions, and coordinates the application state.

Responsibilities include:

- Initializing UI localization
- Creating the table view and surrounding controls
- Managing the selected project/file context
- Starting background glossary loading
- Scheduling translation server warmup during startup
- Coordinating translation progress overlays and save actions

Conceptually, `Main` is the orchestration layer for the whole app.

## Core Data Model

### `LocalizationData`

`src/main/java/lv/lenc/LocalizationData.java`

Each table row is represented by `LocalizationData`.

It contains:

- A localization key
- One field per supported language

Supported language fields currently include:

- `ruRU`
- `deDE`
- `enUS`
- `esMX`
- `esES`
- `frFR`
- `itIT`
- `plPL`
- `ptBR`
- `koKR`
- `zhCN`
- `zhTW`

The class exposes language-agnostic helpers like `getByLang` and `setByLang`, which makes the table and translation code simpler.

## UI Layer

### `CustomTableView`

`src/main/java/lv/lenc/CustomTableView.java`

This is one of the central classes in the project.

Responsibilities:

- Displays localization rows in a JavaFX `TableView`
- Creates one editable column per supported language
- Loads localization content into rows
- Tracks the current source language
- Builds output text for saving per target language
- Supports table focus mode and column sizing behavior
- Integrates glossary and language detection helpers

This class is effectively the editor surface for the localization data.

### Custom UI Components

The repository contains many custom JavaFX controls and effects, for example:

- `CustomBorder`
- `CustomFileChooser`
- `CustomComboBoxClassic`
- `CustomComboBoxTexture`
- `TranslateCancelSaveButton`
- `TranslationProgressOverlay`
- `BackgroundGridLayer`
- `TitleLabelGlow`
- `GlowingLabelWithBorder`

The UI is not built around FXML controllers. Instead, it is assembled programmatically in Java, mostly from `Main` and reusable control classes.

## Project and File Handling

### `LocalizationProjectContext`

`src/main/java/lv/lenc/LocalizationProjectContext.java`

This class tracks the current editing context:

- the file opened by the user
- the inferred project root containing sibling locale directories

Responsibilities:

- Open the selected file into the table view
- Remember the project root
- Save the current target language
- Save all visible target languages

This is a small but important coordination object between UI and file utilities.

### `FileUtil`

`src/main/java/lv/lenc/FileUtil.java`

This is the main file-system routing class.

Key behavior:

- Detects whether a selected file belongs to a `xxXX.SC2Data/LocalizedData/...` structure
- Finds sibling locale folders within the same project root
- Builds a relative path from the selected file under `LocalizedData`
- Loads matching files from every available locale folder
- Resolves the correct output file for a target locale
- Writes output using UTF-8 and an atomic temp-file move
- Creates `.bak` backups when overwriting existing files

This is the code that makes the tool SC2-project-aware instead of acting like a plain text editor.

## Translation Pipeline

### `TranslationService`

`src/main/java/lv/lenc/TranslationService.java`

This is the largest service layer in the project and handles machine translation integration.

Core responsibilities:

- Configure and maintain the active LibreTranslate endpoint
- Probe candidate endpoints and switch between them
- Support CPU and GPU endpoint handling
- Warm up translation infrastructure during startup
- Batch requests by item count and character count
- Deduplicate repeated strings before translation
- Maintain an in-memory LRU-style translation cache
- Optionally persist cache data based on settings
- Preserve or protect markup and special symbol runs during translation
- Save translated output to the correct target file

The overall design suggests that translation throughput and stability are important concerns, not just correctness.

## Glossary Support

### `GlossaryService`

`src/main/java/lv/lenc/GlossaryService.java`

The glossary system provides exact-match substitutions for StarCraft II terminology.

Inputs loaded from resources include:

- CSV glossary files for units, buttons, and abilities
- TXT-based glossary resources for word-level matching

Responsibilities:

- Load glossary data asynchronously from resources
- Index exact matches by category, key, source language, source text, and target language
- Support category detection from key prefixes
- Provide fallback text-level and word-level lookups

This service exists to reduce incorrect machine translation of game-specific terminology.

## UI Localization

### `LocalizationManager`

`src/main/java/lv/lenc/LocalizationManager.java`

This class manages the application UI language using `ResourceBundle` files from `src/main/resources`.

Behavior:

- Loads the requested language bundle
- Falls back to a language-only bundle when needed
- Falls back to English if a key is missing
- Returns the key itself as a last resort

The `messages_*.properties` files in resources provide the translatable UI text.

## Settings and Persistence

### `SettingsManager`

`src/main/java/lv/lenc/SettingsManager.java`

Settings are stored in a plain `settings.properties` file.

Known settings include:

- UI language
- Background grid alpha values
- Flash and point alpha values
- Table lighting toggle
- Background blur toggle
- Shimmer toggle
- Translation cache persistence toggle
- GPU Docker usage toggle

This is a simple properties-backed settings store rather than a more structured config system.

## Language Detection

### `LanguageDetectorService`

`src/main/java/lv/lenc/LanguageDetectorService.java`

This class wraps the `langdetect` library and loads language profiles from `src/main/resources/profiles`.

It maps detected language codes to the UI language format used by the rest of the application, for example:

- `en` -> `enUS`
- `ru` -> `ruRU`
- `zh-cn` -> `zhCN`

There is currently minimal test coverage for this behavior under `src/test/java/lv/lenc/LanguageDetectorServiceTest.java`.

## Logging

### `AppLog`

`src/main/java/lv/lenc/AppLog.java`

Logging is centralized in `AppLog`.

Responsibilities include:

- Initial log setup
- OS-aware log directory selection
- Info/warn/error helpers
- Exception logging

This is used during startup, translation, save operations, and error handling.

## Resource Layout

Important resource groups:

- `src/main/resources/messages_*.properties`: UI translations
- `src/main/resources/Assets`: textures and styles
- `src/main/resources/glossary`: SC2 glossary data
- `src/main/resources/profiles`: language detection profiles

## Typical Runtime Flow

1. `AppLauncher` configures logging and runtime GPU preferences.
2. `Main` starts JavaFX, builds the UI, and loads saved settings.
3. The user selects a localization file.
4. `LocalizationProjectContext` and `FileUtil` discover sibling locale files.
5. `CustomTableView` loads the rows and shows all available languages.
6. `GlossaryService` supplies exact or word-level terminology matches.
7. `TranslationService` sends batches to LibreTranslate when translation is requested.
8. The edited or translated output is saved back into the proper target locale folder.

## Current Design Notes

- The app is strongly desktop-UI-driven. `Main` holds a lot of orchestration logic.
- The project uses custom controls heavily instead of declarative UI files.
- File handling is tailored specifically for SC2 `.SC2Data/LocalizedData` directory layouts.
- Translation behavior is more advanced than a simple API wrapper because it includes endpoint probing, batching, caching, and tag/symbol protection.
- Test coverage appears limited from the current repository layout.

## Good Files To Read First

If you want to continue understanding the project, start with these files in order:

1. `src/main/java/lv/lenc/Main.java`
2. `src/main/java/lv/lenc/CustomTableView.java`
3. `src/main/java/lv/lenc/FileUtil.java`
4. `src/main/java/lv/lenc/LocalizationProjectContext.java`
5. `src/main/java/lv/lenc/TranslationService.java`
6. `src/main/java/lv/lenc/GlossaryService.java`
7. `src/main/java/lv/lenc/SettingsManager.java`

That sequence gives the clearest path from UI orchestration to data handling and then into translation internals.
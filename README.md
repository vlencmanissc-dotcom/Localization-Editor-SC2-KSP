# Localization Editor SC2 KSP

Localization Editor SC2 KSP is a desktop tool for editing StarCraft II localization files.

What it does:
- Opens `GameStrings.txt`, `ObjectStrings.txt`, and `TriggerStrings.txt`
- Works with archives: `SC2Map`, `SC2Mod`, and `mpq`
- Translates text into selected languages
- Saves output to the correct `.SC2Data/LocalizedData` structure

## Releases
- https://github.com/VoVanRusLvSC2/Localization-Editor-SC2-KSP/releases

## Current Installer
- Current Windows installer build: `2.0.1`
- If `2.0.0` was already installed, use the `2.0.1` installer instead of rerunning the old `2.0.0` package.

## Quick Start
1. Download the latest release.
2. Install and launch the editor.
3. Open your `SC2Map` / `SC2Mod` / `mpq` archive or `.txt` localization file.

## Editable Glossaries After Install
The installer now places editable glossary files in the application folder:

- `glossary` next to `Localization Editor SC2 KSP.exe`
- example: `C:\Program Files\Localization Editor SC2 KSP\glossary`

You can edit these files after installation without rebuilding the app.

Default files placed there:
- `sc2_word_glossary_KSP.txt`
- `Addition_UnitNames_Detailed_KSP.txt`
- `Addition_Weapons_Detailed_KSP.txt`
- `Addition_Abilities_Detailed_KSP.txt`

On startup, the editor loads glossary files from that install-folder `glossary` first when they exist.
For legacy installs, `%LOCALAPPDATA%\Localization Editor SC2 KSP\glossary` is still used as a fallback if present.

## 2.0.1 Notes
- Installer version was bumped to `2.0.1` to avoid the Windows reinstall problem when `2.0.0` is already present.
- Default UI language is now `English` on first launch.
- Translation background stays visible during translation, but animations are disabled for better performance.
- File-open dialog controls were tightened and buttons were adjusted.
- Translation after cache clear and source-language detection were fixed.

## Dependencies
- Java: `JDK 17` (required)
- Build tool: `Maven` (for source build)
- OS: Windows desktop environment (primary target)

Main Maven dependencies used by the app:
- JavaFX: `javafx-controls`, `javafx-fxml`, `javafx-media` (`22.0.1`)
- HTTP/API: `retrofit2`, `okhttp logging-interceptor`
- JSON/Parsing: `gson`, `jackson`, `jsoup`
- Language detection: `langdetect`, `tika-core`
- Archive and native integration: `jmpq3`, `commons-compress`, `jna`, `jna-platform`

Optional local translation backend dependencies:
- Python `3.10+`
- `libretranslate`
- `minisbd` (if startup reports `download_models` import error, upgrade it)

## Translation Backends and AI Info
For backend details, pricing notes, and setup hints (Google, DeepL, Cloudflare, SiliconFlow, LibreTranslate, etc.), see:
- `README_TRANSLATE.txt`

## Optional: Local LibreTranslate
If you want to use LibreTranslate locally:

```bash
python -m pip install --upgrade pip
python -m pip install libretranslate
python -m libretranslate.main --host 127.0.0.1 --port 5000 --disable-web-ui
```

If startup fails with:
`ImportError: cannot import name 'download_models' from 'minisbd'`

run:

```bash
python -m pip install --upgrade minisbd
python -m libretranslate.main --host 127.0.0.1 --port 5000 --disable-web-ui
```

Health check:
- `http://127.0.0.1:5000/languages`

The app expects LibreTranslate at:
- `http://127.0.0.1:5000`

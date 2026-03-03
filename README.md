# Localization-Editor-SC2-KSP

A localization editor for StarCraft II maps and mods.  
Opens `GameStrings.txt` files, translates them into selected languages, and automatically saves them into the correct `.SC2Data/LocalizedData` directory structure.

---

## Requirements

### 1. Java (JDK 17+)

Check your Java version:

```bash
java -version
```

---

### 2. Maven (3.8+ recommended)

Check your Maven version:

```bash
mvn -v
```

---

### 3. Python (Optional – only for auto-translation)

Required only if you want to use LibreTranslate.

Recommended Python versions: **3.10–3.12**  
⚠ Very new Python versions (for example 3.14+) may not be supported yet.

Download Python from:  
https://www.python.org/downloads/

On Windows, make sure to check **"Add Python to PATH"** during installation.

---

### Windows Additional Requirement

LibreTranslate requires Microsoft Visual C++ Redistributable (x64).

Download and install:  
https://aka.ms/vs/17/release/vc_redist.x64.exe

---

### Install LibreTranslate

```bash
python -m pip install --upgrade pip
python -m pip install libretranslate
```

---

### Install OnnxRuntime (Required)

LibreTranslate requires **ONNX Runtime** to execute translation models.

On Windows, install the recommended version:

```bash
python -m pip install onnxruntime==1.16.3
```

If you need to reinstall:

```bash
python -m pip uninstall onnxruntime -y
python -m pip install onnxruntime==1.16.3
```

⚠ If you see a warning that the `Scripts` directory is not on `PATH`
(for example `Python311\Scripts`), either add it to `PATH`
or run `libretranslate.exe` directly from that folder.

---

### Start LibreTranslate server

⚠ Do **NOT** use:

```bash
python -m libretranslate --host 127.0.0.1 --port 5000
```

It may produce:

```
No module named libretranslate.__main__; 'libretranslate' is a package and cannot be directly executed
```

✅ Correct way to start the server:

```bash
libretranslate --host 127.0.0.1 --port 5000
```

After starting, you should see:

```
Running on http://127.0.0.1:5000
```

The application expects LibreTranslate running at:

```
http://127.0.0.1:5000
```

If LibreTranslate is not running, auto-translation will not work.

---

## Run with Maven

From the project root directory (where `pom.xml` is located), run:

```bash
mvn clean javafx:run
```

If needed:

```bash
mvn exec:java -Dexec.mainClass=lv.lenc.AppLauncher
```

---

## Build (Optional)

To compile the project:

```bash
mvn clean package
```

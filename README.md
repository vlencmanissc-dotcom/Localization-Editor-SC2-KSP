# Localization-Editor-SC2-KSP

A localization editor for StarCraft II maps and mods.  
Opens `GameStrings.txt`,`ObjectStrings.txt`,`TriggerStrings.txt` files, translates them into selected languages, and automatically saves them into the correct `.SC2Data/LocalizedData` directory structure.

---

## Requirements

### 1. Java (JDK 17+)

Download JDK 17 or newer from:

- [Oracle JDK](https://www.oracle.com/java/technologies/downloads/)
- [OpenJDK (Adoptium – recommended)](https://adoptium.net/)

Check your Java version:

```bash
java -version
```

### 2. Maven (3.8+ recommended)

Download Maven from:

- [Apache Maven Download Page](https://maven.apache.org/download.cgi)

After downloading, follow the installation guide:

- [Maven Installation Guide](https://maven.apache.org/install.html)

Check your Maven version:

```bash
mvn -v
```

---

### 3. Python (Optional – only for auto-translation)

Required only if you want to use LibreTranslate.

Recommended Python versions: **3.11 or 3.12**

Very new Python versions (for example **3.14+**) may produce dependency warnings with LibreTranslate.

Download Python from:  
https://www.python.org/downloads/

On Windows, make sure to check **"Add Python to PATH"** during installation.

---

### Windows Additional Requirement

LibreTranslate requires Microsoft Visual C++ Redistributable (x64).

Download and install:  
https://aka.ms/vs/17/release/vc_redist.x64.exe

---

### Install OnnxRuntime (Required)

LibreTranslate requires **ONNX Runtime** to execute translation models.

Install ONNX Runtime:

```bash
python -m pip install onnxruntime==1.24.1
```

If you need to reinstall:

```bash
python -m pip uninstall onnxruntime -y
python -m pip install onnxruntime==1.24.1
```
You can verify the installed version with:
```bash
python -c "import onnxruntime as ort; print(ort.__version__)"
```
⚠ If you see a warning that the `Scripts` directory is not on `PATH`
(for example `Python311\Scripts`), either add it to `PATH`
or run `libretranslate.exe` directly from that folder.

---
### Install LibreTranslate

```bash
python -m pip install --upgrade pip

python -m pip install "wheel==0.45.1"

python -m pip install libretranslate

```
### Fix requests dependency (important)

LibreTranslate currently requires a specific version of `requests`.

Install the compatible version:

```bash
python -m pip install requests==2.31.0
```
---

### Start LibreTranslate server

Start the LibreTranslate server using the following command:

First start (downloads translation models):

```bash
libretranslate --load-only en,ru,de,es,fr,it,pl,pt,ko,zh,zt --update-models
```
Later starts:

```bash
libretranslate --load-only en,ru,de,es,fr,it,pl,pt,ko,zh,zt
```
If everything is correct, you should see:
``
Running on http://127.0.0.1:5000
``

The application expects LibreTranslate running at:

```
http://127.0.0.1:5000
```

If LibreTranslate is not running, auto-translation will not work.
Test LibreTranslate (Optional)
You can test the translation server with:
```bash
curl -X POST http://127.0.0.1:5000/translate \
  -H "Content-Type: application/json" \
  -d "{\"q\":\"Hello world\",\"source\":\"en\",\"target\":\"de\"}"
```
  Expected output example:
  ```bash
  {
  "translatedText": "Hallo Welt"
}
```
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

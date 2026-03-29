# Localization-Editor-SC2-KSP

A localization editor for StarCraft II maps and mods.  
Opens `GameStrings.txt`,`ObjectStrings.txt`,`TriggerStrings.txt` files, translates them into selected languages, and automatically saves them into the correct `.SC2Data/LocalizedData` directory structure.

---
Download the latest version from:

https://github.com/vlencmanissc-dotcom/Localization-Editor-SC2-KSP/releases
## Simple Installation (Recommended)

### 1. Install the editor

Download the latest version from **GitHub Releases**:

https://github.com/vlencmanissc-dotcom/Localization-Editor-SC2-KSP/releases

Run the installer:

```
LocalizationEditor-Setup-1.1.1exe
```

After installation, launch the editor normally.

---

### 2. Enable auto-translation with Docker (optional)

Auto-translation requires **LibreTranslate**.

Install **Docker Desktop for Windows**:

https://www.docker.com/products/docker-desktop/

Download the **Windows AMD64** version.

During installation:

* enable **Use WSL 2 instead of Hyper-V**
* leave **Windows Containers** disabled

After installation:

1. Restart your computer
2. Start **Docker Desktop**
3. Wait until Docker shows that it is running

---

### 3. Start LibreTranslate

Open `Release ZIP/docker` and run:

```
start-libretranslate.bat
```

The batch file starts LibreTranslate for you through Docker Compose.

This will start LibreTranslate at:

```
http://127.0.0.1:5000
```

---

### 4. First start note

On the first start Docker will download:

* LibreTranslate container
* translation models

LibreTranslate will also download the required language models during the first start.

The first start can take a few minutes. That is normal.

Only these languages are loaded:

```
en, ru, de, es, fr, it, pl, pt, ko, zh, zt
```
---

### 5. Check that the server works

Open in your browser:

```
http://127.0.0.1:5000/languages
```

If you see JSON with languages, the translation server is running correctly.

The application expects LibreTranslate at:

```
http://127.0.0.1:5000
```

If LibreTranslate is not running, auto-translation will not work.
---
## Manual Installation (Advanced)

Use this method if you do not want to use Docker
or if you want to run LibreTranslate manually.

### Optional: Start LibreTranslate with Docker from the command line

Use this if you want Docker, but prefer command-line commands instead of the batch file.

From the project root directory, run:

```bash
cd "Release ZIP/docker"
docker compose -f docker-compose.yml up -d
```

To view the startup logs:

```bash
docker compose -f docker-compose.yml logs -f libretranslate
```

When the server is ready, the logs will contain a line like:

```
Listening at: http://[::]:5000
```

To stop the container later:

```bash
docker compose -f docker-compose.yml down
```

---

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

If `libretranslate` command exists but server still does not become ready,
start via Python module directly (recommended fallback):

```bash
python -m libretranslate.main --host 127.0.0.1 --port 5000 --disable-web-ui
```

If everything is correct, you should see:
```
Running on http://127.0.0.1:5000
```

The application expects LibreTranslate running at:

```
http://127.0.0.1:5000
```

If LibreTranslate is not running, auto-translation will not work.
Test LibreTranslate (Optional)
You can test the translation server with:
```bash
curl -X POST http://127.0.0.1:5000/translate -H "Content-Type: application/json" -d "{\"q\":\"Hello world\",\"source\":\"en\",\"target\":\"de\"}"
```
  Expected output example:
  ```bash
  {
  "translatedText": "Hallo Welt"
}
```

### Troubleshooting (Windows)

If startup fails with an error similar to:

`FileExistsError ... C:\Users\<user>\.config\argos-translate`

then `argos-translate` path is broken (file exists where directory is expected).

PowerShell fix:

```powershell
$cfg = "$env:USERPROFILE\.config\argos-translate"
if (Test-Path $cfg -PathType Leaf) { Remove-Item $cfg -Force }
New-Item -ItemType Directory -Path $cfg -Force | Out-Null
```

Then start LibreTranslate again and verify:

```bash
http://127.0.0.1:5000/languages
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

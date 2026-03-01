# Localization-Editor-SC2-KSP
A localization editor for StarCraft II maps and mods. Opens GameStrings.txt files, translates them into selected languages, and automatically saves them into the correct SC2Data directory structure.
---

## Requirements

Before running the project, install:

### 1. Java (JDK 17+)

Check version:

```bash
java -version
2. Maven (3.8+ recommended)

Check version:

mvn -v
3. Python (Optional – only for auto-translation)

Required only if you want to use LibreTranslate.

Install Python 3.9+:
https://www.python.org/downloads/

Install LibreTranslate:

pip install libretranslate

Start LibreTranslate server:

python -m libretranslate --host 127.0.0.1 --port 5000

The application expects LibreTranslate running at:

http://127.0.0.1:5000


---

# ▶ How to Run (Maven)

From the project root directory (where `pom.xml` is located):

```bash
mvn clean javafx:run

If needed:

mvn exec:java -Dexec.mainClass=lv.lenc.AppLauncher
Build (optional)

To compile the project:

mvn clean package

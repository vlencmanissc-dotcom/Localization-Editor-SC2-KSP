@echo off
pushd "%~dp0"
mvn exec:java -Dexec.mainClass=lv.lenc.AppLauncher
pause
popd
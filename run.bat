@echo off
setlocal
cd /d "%~dp0"
if not exist bin mkdir bin
for /r src %%f in (*.java) do echo %%f>>sources.txt
javac -encoding UTF-8 -d bin @sources.txt
if errorlevel 1 (
  del sources.txt
  echo Error al compilar. Verifique que tenga instalado JDK 17 o superior.
  pause
  exit /b 1
)
del sources.txt
java -cp bin pe.utp.dependencias.Main

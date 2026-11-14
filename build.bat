@echo off
setlocal
cd /d "%~dp0"

where javac >nul 2>nul
if errorlevel 1 (
  echo JDK nao encontrado. Instale o Java JDK 17 ou superior e adicione javac ao PATH.
  exit /b 1
)

if not exist build\classes mkdir build\classes
javac -encoding UTF-8 --release 17 -d build\classes -sourcepath src\main\java src\main\java\br\com\policialadrao\client\ClientMain.java src\main\java\br\com\policialadrao\server\ServerMain.java
if errorlevel 1 exit /b 1
xcopy "src\main\resources\*" "build\classes\" /S /E /I /Y >nul
if errorlevel 1 exit /b 1
echo Compilacao concluida em build\classes.

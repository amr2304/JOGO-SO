@echo off
setlocal
cd /d "%~dp0"
call build.bat
if errorlevel 1 exit /b 1
if not exist dist mkdir dist
jar --create --file dist\policia-e-ladrao.jar --main-class br.com.policialadrao.client.ClientMain -C build\classes .
if errorlevel 1 exit /b 1
echo JAR criado em dist\policia-e-ladrao.jar.

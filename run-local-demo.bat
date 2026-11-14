@echo off
setlocal
cd /d "%~dp0"
call build.bat
if errorlevel 1 exit /b 1

start "Servidor - Policia e Ladrao" java -cp "%~dp0build\classes" br.com.policialadrao.server.ServerMain --demo
timeout /t 2 /nobreak >nul
start "" javaw -cp "%~dp0build\classes" br.com.policialadrao.client.ClientMain
start "" javaw -cp "%~dp0build\classes" br.com.policialadrao.client.ClientMain

@echo off
setlocal
cd /d "%~dp0"

where node >nul 2>nul
if errorlevel 1 (
    echo.
    echo Node.js no esta instalado.
    echo Descargalo desde https://nodejs.org/ y vuelve a ejecutar este archivo.
    echo.
    pause
    exit /b 1
)

if not exist node_modules (
    echo.
    echo Instalando dependencias (solo la primera vez)...
    echo.
    call npm install --no-audit --no-fund
    if errorlevel 1 (
        echo.
        echo No se pudieron instalar las dependencias.
        echo Revisa tu conexion a Internet y vuelve a intentarlo.
        echo.
        pause
        exit /b 1
    )
)

echo.
echo Iniciando puente de TikTok...
echo Deja esta ventana abierta mientras juegas.
echo.
node bridge.mjs
echo.
echo El puente se ha detenido.
pause

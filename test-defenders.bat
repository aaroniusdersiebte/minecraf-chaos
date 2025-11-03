@echo off
REM ===================================================================
REM Defender Villager Test Suite
REM Zum Testen des neuen Defender-Systems
REM ===================================================================

SET SERVER_PATH=C:\Streaming\Code\minecraft server
SET COMMANDS_PATH=%SERVER_PATH%\chaos-commands

echo.
echo ====================================
echo  DEFENDER VILLAGER TEST SUITE
echo ====================================
echo.

REM Prüfe ob Pfad existiert
if not exist "%SERVER_PATH%" (
    echo FEHLER: Server-Pfad existiert nicht: %SERVER_PATH%
    echo Bitte passe den Pfad in der BAT-Datei an.
    pause
    exit /b 1
)

REM Erstelle chaos-commands Ordner falls nicht vorhanden
if not exist "%COMMANDS_PATH%" (
    echo Erstelle chaos-commands Ordner...
    mkdir "%COMMANDS_PATH%"
)

:menu
cls
echo.
echo ====================================
echo  DEFENDER VILLAGER TEST SUITE
echo ====================================
echo.
echo [1] Spawn WARRIOR (Krieger)
echo [2] Spawn ARCHER (Bogenschütze)
echo [3] Spawn HEALER (Heiler)
echo [4] Spawn BUILDER (Baumeister)
echo [5] Spawn TANK (Tank)
echo.
echo [6] Spawn ALL Classes (Test-Team)
echo.
echo [7] Start Wave (Teste XP-Gain)
echo [8] Set Village Core
echo [9] Show Defender Stats (Logs)
echo.
echo [0] Exit
echo.
set /p choice="Wähle Option: "

if "%choice%"=="1" goto spawn_warrior
if "%choice%"=="2" goto spawn_archer
if "%choice%"=="3" goto spawn_healer
if "%choice%"=="4" goto spawn_builder
if "%choice%"=="5" goto spawn_tank
if "%choice%"=="6" goto spawn_all
if "%choice%"=="7" goto start_wave
if "%choice%"=="8" goto set_core
if "%choice%"=="9" goto show_stats
if "%choice%"=="0" goto end
goto menu

:spawn_warrior
echo.
echo Spawne WARRIOR...
echo {"command":"spawn_defender","villager_name":"TestWarrior","class":"warrior"} > "%COMMANDS_PATH%\test_warrior_%TIME:~0,2%%TIME:~3,2%%TIME:~6,2%.json"
echo ✓ Warrior gespawnt!
timeout /t 2 /nobreak >nul
goto menu

:spawn_archer
echo.
echo Spawne ARCHER...
echo {"command":"spawn_defender","villager_name":"TestArcher","class":"archer"} > "%COMMANDS_PATH%\test_archer_%TIME:~0,2%%TIME:~3,2%%TIME:~6,2%.json"
echo ✓ Archer gespawnt!
timeout /t 2 /nobreak >nul
goto menu

:spawn_healer
echo.
echo Spawne HEALER...
echo {"command":"spawn_defender","villager_name":"TestHealer","class":"healer"} > "%COMMANDS_PATH%\test_healer_%TIME:~0,2%%TIME:~3,2%%TIME:~6,2%.json"
echo ✓ Healer gespawnt!
timeout /t 2 /nobreak >nul
goto menu

:spawn_builder
echo.
echo Spawne BUILDER...
echo {"command":"spawn_defender","villager_name":"TestBuilder","class":"builder"} > "%COMMANDS_PATH%\test_builder_%TIME:~0,2%%TIME:~3,2%%TIME:~6,2%.json"
echo ✓ Builder gespawnt!
timeout /t 2 /nobreak >nul
goto menu

:spawn_tank
echo.
echo Spawne TANK...
echo {"command":"spawn_defender","villager_name":"TestTank","class":"tank"} > "%COMMANDS_PATH%\test_tank_%TIME:~0,2%%TIME:~3,2%%TIME:~6,2%.json"
echo ✓ Tank gespawnt!
timeout /t 2 /nobreak >nul
goto menu

:spawn_all
echo.
echo Spawne KOMPLETTES TEST-TEAM...
echo {"command":"spawn_defender","villager_name":"TestWarrior","class":"warrior"} > "%COMMANDS_PATH%\test_all_1.json"
timeout /t 1 /nobreak >nul
echo {"command":"spawn_defender","villager_name":"TestArcher","class":"archer"} > "%COMMANDS_PATH%\test_all_2.json"
timeout /t 1 /nobreak >nul
echo {"command":"spawn_defender","villager_name":"TestHealer","class":"healer"} > "%COMMANDS_PATH%\test_all_3.json"
timeout /t 1 /nobreak >nul
echo {"command":"spawn_defender","villager_name":"TestBuilder","class":"builder"} > "%COMMANDS_PATH%\test_all_4.json"
timeout /t 1 /nobreak >nul
echo {"command":"spawn_defender","villager_name":"TestTank","class":"tank"} > "%COMMANDS_PATH%\test_all_5.json"
echo.
echo ✓ Alle 5 Klassen gespawnt!
timeout /t 3 /nobreak >nul
goto menu

:start_wave
echo.
echo Starte Wave-Test...
echo WICHTIG: Server muss auf NACHT sein!
echo.
set /p chaos_level="Chaos Level setzen (25/100/200): "
if "%chaos_level%"=="" set chaos_level=100
echo {"command":"add_chaos","amount":%chaos_level%} > "%COMMANDS_PATH%\test_chaos.json"
timeout /t 2 /nobreak >nul
echo ✓ Chaos auf %chaos_level% gesetzt!
echo ✓ Wave startet automatisch in der Nacht
timeout /t 3 /nobreak >nul
goto menu

:set_core
echo.
echo Setze Village Core...
echo WICHTIG: Benutze den In-Game Command!
echo.
echo /chaos setcore
echo.
echo Drücke beliebige Taste um zurückzukehren...
pause >nul
goto menu

:show_stats
echo.
echo Öffne Server Logs...
echo WICHTIG: Suche nach "DefenderManager" oder "Level" im Log
echo.
echo Log-Pfad: %SERVER_PATH%\logs\latest.log
echo.
if exist "%SERVER_PATH%\logs\latest.log" (
    notepad "%SERVER_PATH%\logs\latest.log"
) else (
    echo Log-Datei nicht gefunden!
)
goto menu

:end
echo.
echo ====================================
echo  Test-Suite beendet
echo ====================================
echo.
pause
exit /b 0

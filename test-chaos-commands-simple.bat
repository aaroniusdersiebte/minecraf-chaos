@echo off
title Chaos Stream Mod - Command Tester
color 0A

:MENU
cls
echo ============================================================
echo         CHAOS STREAM MOD - COMMAND TESTER
echo ============================================================
echo.
echo Server: C:\Streaming\Code\minecraft server
echo.
echo ------------------------------------------------------------
echo   TROLLING COMMANDS
echo ------------------------------------------------------------
echo   [1] Spawn TNT (3 TNT mit 4 Sek Fuse)
echo   [2] Random Teleport (100 Blocks Radius)
echo   [3] Change Weather zu Thunder
echo   [4] Lightning Strike (kosmetisch)
echo.
echo ------------------------------------------------------------
echo   HILFREICHE COMMANDS
echo ------------------------------------------------------------
echo   [5] Spawn Helper (Iron Golem)
echo   [6] Spawn Helper (Wolf - gezaehmt)
echo   [7] Give Buff (Speed II fuer 60 Sek)
echo   [8] Spawn Food (zufaellig)
echo   [9] Heal Player (volle Health + Hunger)
echo.
echo ------------------------------------------------------------
echo   BASIS COMMANDS
echo ------------------------------------------------------------
echo   [A] Add Chaos (25 Punkte)
echo   [B] Spawn Creeper (mit Lightning!)
echo   [C] Spawn Lootbox (mit Gold-Partikeln!)
echo   [D] Create Villager (Name: TestViewer)
echo.
echo ------------------------------------------------------------
echo   ADVANCED TESTS
echo ------------------------------------------------------------
echo   [T] TNT Chaos Test (5 TNT, kurze Fuse!)
echo   [R] Teleport Roulette (200 Blocks weit)
echo   [S] Speed Boost Extreme (Speed III, 2 Min)
echo ------------------------------------------------------------
echo.
echo   [0] Beenden
echo.
set /p choice="Waehle einen Test (1-9, A-D, T/R/S, 0): "

if /i "%choice%"=="1" goto TNT
if /i "%choice%"=="2" goto TELEPORT
if /i "%choice%"=="3" goto WEATHER
if /i "%choice%"=="4" goto LIGHTNING
if /i "%choice%"=="5" goto HELPER_GOLEM
if /i "%choice%"=="6" goto HELPER_WOLF
if /i "%choice%"=="7" goto BUFF
if /i "%choice%"=="8" goto FOOD
if /i "%choice%"=="9" goto HEAL
if /i "%choice%"=="A" goto CHAOS
if /i "%choice%"=="a" goto CHAOS
if /i "%choice%"=="B" goto CREEPER
if /i "%choice%"=="b" goto CREEPER
if /i "%choice%"=="C" goto LOOTBOX
if /i "%choice%"=="c" goto LOOTBOX
if /i "%choice%"=="D" goto VILLAGER
if /i "%choice%"=="d" goto VILLAGER
if /i "%choice%"=="T" goto TNT_CHAOS
if /i "%choice%"=="t" goto TNT_CHAOS
if /i "%choice%"=="R" goto TELEPORT_ROULETTE
if /i "%choice%"=="r" goto TELEPORT_ROULETTE
if /i "%choice%"=="S" goto SPEED_EXTREME
if /i "%choice%"=="s" goto SPEED_EXTREME
if /i "%choice%"=="0" goto END

echo Ungueltige Auswahl!
timeout /t 2 >nul
goto MENU

:TNT
cls
echo =======================================
echo   TNT SPAWN TEST
echo =======================================
echo {"command":"spawn_tnt","count":3,"fuse":80} > "C:\Streaming\Code\minecraft server\chaos-commands\test_tnt.json"
echo [OK] Command gesendet: 3x TNT mit 4 Sekunden Fuse
echo [OK] TNT spawnt ueber dem Spieler
echo [OK] Erwarte: Smoke-Partikel + TNT-Sound
echo.
timeout /t 3 >nul
goto MENU

:TELEPORT
cls
echo =======================================
echo   RANDOM TELEPORT TEST
echo =======================================
echo {"command":"random_teleport","radius":100} > "C:\Streaming\Code\minecraft server\chaos-commands\test_tp.json"
echo [OK] Command gesendet: Random Teleport (100 Blocks)
echo [OK] Erwarte: Portal-Partikel an alter UND neuer Position
echo [OK] Erwarte: Enderman-Teleport Sound
echo.
timeout /t 3 >nul
goto MENU

:WEATHER
cls
echo =======================================
echo   WEATHER CHANGE TEST
echo =======================================
echo {"command":"change_weather","type":"thunder"} > "C:\Streaming\Code\minecraft server\chaos-commands\test_weather.json"
echo [OK] Command gesendet: Wetter zu Thunder aendern
echo [OK] Erwarte: Trident-Thunder Sound
echo [OK] Erwarte: Gewitter fuer 5 Minuten
echo.
timeout /t 3 >nul
goto MENU

:LIGHTNING
cls
echo =======================================
echo   LIGHTNING STRIKE TEST
echo =======================================
echo {"command":"spawn_lightning"} > "C:\Streaming\Code\minecraft server\chaos-commands\test_lightning.json"
echo [OK] Command gesendet: Lightning Strike
echo [OK] Erwarte: Blitz am Spieler (KEIN Schaden!)
echo [OK] Erwarte: Thunder + Impact Sound
echo [OK] Erwarte: Explosion-Partikel
echo.
timeout /t 3 >nul
goto MENU

:HELPER_GOLEM
cls
echo =======================================
echo   HELPER SPAWN TEST (IRON GOLEM)
echo =======================================
echo {"command":"spawn_helper","type":"iron_golem"} > "C:\Streaming\Code\minecraft server\chaos-commands\test_golem.json"
echo [OK] Command gesendet: Iron Golem Helper
echo [OK] Erwarte: Herz-Partikel + Happy Villager
echo [OK] Erwarte: Bell + Golem Repair Sound
echo [OK] Golem beschuetzt den Spieler!
echo.
timeout /t 3 >nul
goto MENU

:HELPER_WOLF
cls
echo =======================================
echo   HELPER SPAWN TEST (WOLF)
echo =======================================
echo {"command":"spawn_helper","type":"wolf"} > "C:\Streaming\Code\minecraft server\chaos-commands\test_wolf.json"
echo [OK] Command gesendet: Wolf Helper (automatisch gezaehmt!)
echo [OK] Erwarte: Herz-Partikel + Happy Villager
echo [OK] Erwarte: Bell + Golem Repair Sound
echo [OK] Wolf ist bereits gezaehmt!
echo.
timeout /t 3 >nul
goto MENU

:BUFF
cls
echo =======================================
echo   BUFF TEST (SPEED II)
echo =======================================
echo {"command":"give_buff","type":"speed","duration":60,"amplifier":1} > "C:\Streaming\Code\minecraft server\chaos-commands\test_buff.json"
echo [OK] Command gesendet: Speed II fuer 60 Sekunden
echo [OK] Erwarte: Enchant-Partikel + END_ROD
echo [OK] Erwarte: Level-Up + Enchantment Table Sound
echo [OK] Buff-Typen: speed, regeneration, resistance, strength, jump
echo.
timeout /t 3 >nul
goto MENU

:FOOD
cls
echo =======================================
echo   FOOD SPAWN TEST
echo =======================================
echo {"command":"spawn_food"} > "C:\Streaming\Code\minecraft server\chaos-commands\test_food.json"
echo [OK] Command gesendet: Random Food Spawn
echo [OK] Erwarte: Happy Villager Partikel
echo [OK] Food landet direkt im Inventar
echo [OK] Moegliche Items: Beef, Golden Carrot, Porkchop, Bread
echo.
timeout /t 3 >nul
goto MENU

:HEAL
cls
echo =======================================
echo   HEAL PLAYER TEST
echo =======================================
echo {"command":"heal_player"} > "C:\Streaming\Code\minecraft server\chaos-commands\test_heal.json"
echo [OK] Command gesendet: Full Heal + Hunger Reset
echo [OK] Erwarte: Herz-Partikel + Totem-Partikel
echo [OK] Erwarte: Beacon + XP-Orb Sound
echo [OK] Spieler hat volle HP + Hunger!
echo.
timeout /t 3 >nul
goto MENU

:CHAOS
cls
echo =======================================
echo   ADD CHAOS TEST
echo =======================================
echo {"command":"add_chaos","amount":25,"source":"test"} > "C:\Streaming\Code\minecraft server\chaos-commands\test_chaos.json"
echo [OK] Command gesendet: +25 Chaos
echo [OK] Check mit /chaos level
echo.
timeout /t 2 >nul
goto MENU

:CREEPER
cls
echo =======================================
echo   CREEPER SPAWN TEST
echo =======================================
echo {"command":"spawn_creeper"} > "C:\Streaming\Code\minecraft server\chaos-commands\test_creeper.json"
echo [OK] Command gesendet: Creeper Spawn
echo [OK] Erwarte: LIGHTNING STRIKE!
echo [OK] Erwarte: Explosion-Partikel + Smoke
echo [OK] Erwarte: Creeper Hiss + Explosion Sound
echo.
timeout /t 3 >nul
goto MENU

:LOOTBOX
cls
echo =======================================
echo   LOOTBOX SPAWN TEST
echo =======================================
echo {"command":"spawn_lootbox"} > "C:\Streaming\Code\minecraft server\chaos-commands\test_lootbox.json"
echo [OK] Command gesendet: Lootbox Spawn
echo [OK] Erwarte: GOLDENE END_ROD Partikel!
echo [OK] Erwarte: Enchant-Sparkles + Firework
echo [OK] Erwarte: Level-Up + Firework Sound
echo [OK] Loot: Diamonds, Emeralds, Golden Apples, etc.
echo.
timeout /t 3 >nul
goto MENU

:VILLAGER
cls
echo =======================================
echo   VILLAGER SPAWN TEST
echo =======================================
echo {"command":"create_villager","name":"TestViewer"} > "C:\Streaming\Code\minecraft server\chaos-commands\test_villager.json"
echo [OK] Command gesendet: Villager "TestViewer"
echo [OK] Erwarte: Totem-Partikel + Happy Villager
echo [OK] Erwarte: Totem + Bell Sound
echo [OK] Villager hat blauen Namen: TestViewer
echo.
timeout /t 3 >nul
goto MENU

:TNT_CHAOS
cls
echo =======================================
echo   TNT CHAOS TEST (EXTREME!)
echo =======================================
echo {"command":"spawn_tnt","count":5,"fuse":40} > "C:\Streaming\Code\minecraft server\chaos-commands\test_tnt_chaos.json"
echo [OK] Command gesendet: 5x TNT mit 2 Sekunden Fuse!
echo [!!] VORSICHT: Explodiert sehr schnell!
echo.
timeout /t 3 >nul
goto MENU

:TELEPORT_ROULETTE
cls
echo =======================================
echo   TELEPORT ROULETTE (EXTREME!)
echo =======================================
echo {"command":"random_teleport","radius":200} > "C:\Streaming\Code\minecraft server\chaos-commands\test_tp_extreme.json"
echo [OK] Command gesendet: Random Teleport (200 Blocks!)
echo [!!] Kann SEHR weit weg teleportieren!
echo.
timeout /t 3 >nul
goto MENU

:SPEED_EXTREME
cls
echo =======================================
echo   SPEED BOOST EXTREME
echo =======================================
echo {"command":"give_buff","type":"speed","duration":120,"amplifier":2} > "C:\Streaming\Code\minecraft server\chaos-commands\test_speed_extreme.json"
echo [OK] Command gesendet: Speed III fuer 2 Minuten!
echo [!!] Spieler wird EXTREM schnell!
echo.
timeout /t 3 >nul
goto MENU

:END
cls
echo =======================================
echo   Chaos Command Tester beendet
echo =======================================
echo.
echo Danke fuers Testen!
echo.
timeout /t 2 >nul
exit

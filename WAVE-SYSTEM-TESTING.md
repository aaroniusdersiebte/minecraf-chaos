# Wave System Testing Guide

## Übersicht

Das neue Wellen-System spawnt Monster in progressiven Wellen während der Nacht, basierend auf dem Chaos-Level.

## Features

### 1. Wellen-Phasen
- **Vorzeichen-Phase (30 Sek.)**: Spawn-Punkte werden markiert mit Obsidian-Ringen und Glowstone
- **Welle 1**: Schwache Mobs (Zombies, Spinnen)
- **Welle 2**: Mittlere Mobs (Skeletons, Creeper)
- **Welle 3**: Starke Mobs (Hexen, Endermen)
- **Welle 4**: Elite-Mobs (nur bei Chaos ≥ 100) - mit Rüstung und Buffs
- **Welle 5**: Boss-Welle (nur bei Chaos ≥ 200) - mit Netherite-Ausrüstung

### 2. Spawn-Locations
- **3 zufällige Spawn-Punkte** pro Spieler
- **35-55 Blöcke Entfernung** vom Spieler
- **Visuelle Markierungen**: Obsidian-Ring + Glowstone am Boden
- **Partikel-Effekte**: Portal-Partikel während der ganzen Nacht

### 3. Mob-Schwierigkeit nach Chaos-Level

#### Chaos 0-50: "Unruhe"
- 3 Wellen pro Nacht
- 12-27 Mobs total
- Nur Basis-Mobs ohne Ausrüstung

#### Chaos 51-100: "Bedrohung"
- 3-4 Wellen pro Nacht
- 27-54 Mobs total
- Mobs mit Leder-Rüstung, leichte Buffs

#### Chaos 101-200: "Krise"
- 4-5 Wellen pro Nacht
- 54-120 Mobs total
- Mobs mit Eisen/Diamant-Rüstung, starke Buffs

#### Chaos 200+: "Apokalypse"
- 5 Wellen pro Nacht (inkl. Boss-Welle)
- 120+ Mobs total
- Mobs mit Netherite-Rüstung, maximale Buffs, glühend

### 4. Boss-Bar UI
- Zeigt den Countdown bis zur nächsten Welle
- Verschiedene Nachrichten für jede Welle
- Rote Farbe mit 10 Segmenten

### 5. Visuelle Effekte
- **Spawn-Marker**: Obsidian-Ringe mit Glowstone
- **Portal-Partikel**: Während der Vorzeichen-Phase
- **Spawn-Explosion**: Soul Fire Flames + Rauch + Donner-Sound
- **Boss-Mobs**: Leuchtend (glowing effect)

## Test-Commands

### Chaos-Level verwalten
```
/chaos level          # Zeigt aktuellen Chaos-Level
/chaos add <amount>   # Fügt Chaos hinzu (1-1000)
/chaos set <amount>   # Setzt Chaos auf bestimmten Wert (0-1000)
/chaos reset          # Setzt Chaos auf 0
```

### Wellen testen
```
/chaos night          # Setzt Zeit auf Nacht (13000)
/chaos wave           # Startet sofort eine Test-Welle
```

## Test-Szenarien

### Test 1: Basis-Wellen (Chaos 25)
```
/chaos set 25
/chaos night
```
**Erwartetes Verhalten:**
- 3 Spawn-Punkte werden mit Obsidian-Ringen markiert
- Nach 30 Sek. spawnt Welle 1 (4 Mobs pro Location = 12 total)
- Nach 60 Sek. Pause: Welle 2 (6 Mobs pro Location = 18 total)
- Nach 60 Sek. Pause: Welle 3 (8 Mobs pro Location = 24 total)
- Nur schwache Mobs ohne Ausrüstung

### Test 2: Elite-Wellen (Chaos 150)
```
/chaos set 150
/chaos night
```
**Erwartetes Verhalten:**
- 3 Spawn-Punkte mit Markierungen
- Welle 1: 12 Mobs (schwach)
- Welle 2: 18 Mobs (mittel)
- Welle 3: 24 Mobs (stark)
- Welle 4: 30 Mobs (Elite mit Diamant-Rüstung, Schwertern, Speed+Strength Buffs)
- Mobs haben 2x HP

### Test 3: Boss-Welle (Chaos 250)
```
/chaos set 250
/chaos night
```
**Erwartetes Verhalten:**
- Alle 5 Wellen werden gespawnt
- Welle 5 hat extrem starke Mobs:
  - Netherite-Rüstung (Protection IV)
  - Netherite-Schwert (Sharpness IV, Fire Aspect II)
  - Speed II, Strength I, Resistance I
  - 3x HP
  - Glühend

### Test 4: Sofort-Test (ohne zu warten)
```
/chaos set 100
/chaos wave
```
**Erwartetes Verhalten:**
- 3 Spawn-Punkte werden sofort markiert
- Welle 1 spawnt sofort ohne 30-Sek-Wartezeit
- Gut zum schnellen Testen der Spawn-Mechanik

### Test 5: Tag/Nacht-Zyklus
```
/chaos set 50
# Warte bis es natürlich Nacht wird (13000)
```
**Erwartetes Verhalten:**
- System startet automatisch bei Nacht
- Resettet sich automatisch bei Tag

## Visuelle Hinweise

### Spawn-Marker
- Obsidian-Ring: 5x5 Blöcke Radius
- Glowstone in der Mitte für Sichtbarkeit
- Portal-Partikel während der Nacht

### Chat-Nachrichten
- "⚠ CHAOS NIGHT BEGINS! ⚠" bei Nacht-Start
- "⚔ FIRST/SECOND/THIRD/ELITE/BOSS WAVE SPAWNING! ⚔" bei jeder Welle
- Farbcodiert: Gelb → Gold → Rot → Lila → Dunkelrot

### Sounds
- Ender-Dragon-Growl bei Nacht-Start
- Portal-Ambient während Vorzeichen-Phase
- Raid-Horn bei Wellen-Start
- Donner bei Mob-Spawn

## Debugging

### Log-Ausgaben
Die Mod loggt wichtige Events:
```
[chaosstream] Wave system reset (day time)
[chaosstream] Spawning chaos mobs...
```

### Probleme beheben

**Problem**: Keine Wellen spawnen
- Prüfe Chaos-Level: `/chaos level`
- Prüfe Zeit: `/time query daytime` (sollte > 13000 sein)
- Prüfe ob Spieler in Overworld ist

**Problem**: Spawn-Marker nicht sichtbar
- Spawn-Punkte sind 35-55 Blöcke entfernt
- Schaue nach Obsidian-Ringen am Boden
- Achte auf Portal-Partikel

**Problem**: Mobs zu schwach/zu stark
- Chaos-Level anpassen
- Bei Chaos < 100: Nur 3 Wellen
- Bei Chaos < 200: Keine Boss-Welle

## Build und Installation

### Windows:
1. Öffne Eingabeaufforderung in `chaos-stream-mod/`
2. Führe aus: `build.bat`
3. JAR befindet sich in `build/libs/chaos-stream-mod-1.0.0.jar`
4. Kopiere in deinen Server `mods/` Ordner

### Linux/WSL:
1. Installiere Java 17 oder höher
2. Im Terminal: `cd chaos-stream-mod`
3. Führe aus: `./gradlew build`
4. JAR befindet sich in `build/libs/chaos-stream-mod-1.0.0.jar`

## Technische Details

### Timing (in Ticks, 20 Ticks = 1 Sekunde)
- Vorzeichen-Phase: 600 Ticks (30 Sek.)
- Wellen-Spawn-Dauer: 100 Ticks (5 Sek.)
- Pause zwischen Wellen: 1200 Ticks (60 Sek.)

### Mob-Scaling
- Tier 1: Basis-Mobs
- Tier 2: +10% Speed, Leder-Rüstung (30%)
- Tier 3: +50% HP, Speed I, Eisen-Rüstung (50%)
- Tier 4: +100% HP, Speed I + Strength I, Diamant-Rüstung (70%), Diamant-Schwert (50%)
- Tier 5: +200% HP, Speed II + Strength I + Resistance I, Netherite-Set, Glowing

### Spawn-Distanzen
- Minimum: 35 Blöcke
- Maximum: 55 Blöcke
- Pro Spawn-Punkt: 3-8 Blöcke Radius für Mobs

---

## Quick Start

Schnellster Weg zum Testen:
```
/chaos set 150
/chaos wave
```
Dies startet sofort eine Test-Welle mit Elite-Mobs!

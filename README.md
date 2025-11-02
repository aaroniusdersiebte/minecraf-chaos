# Chaos Stream Mod

A Minecraft Fabric server mod for streaming with chaos meter and viewer interaction features.

## Features

### 🎮 Core Features
- **Chaos Meter System**: Tracks chaos level with automatic persistence
- **Monster Wave Spawning**: Spawns mobs near players during night based on chaos level
- **File-based Command System**: Simple JSON file watching for external integration
- **Viewer Interaction Commands**: Creeper spawning, loot boxes, and custom villagers

### 📊 Chaos Mechanics
- Chaos level increases through JSON commands
- Automatic chaos decay over time (1 point per minute)
- Spawn multiplier scales with chaos (1.0x to 5.0x)
- Monster waves spawn only at night
- More chaos = more monsters

### 🎯 Available Commands

#### Add Chaos
```json
{
  "command": "add_chaos",
  "amount": 10,
  "source": "follow"
}
```

#### Spawn Creeper
```json
{
  "command": "spawn_creeper",
  "player": "PlayerName"
}
```
*Note: `player` is optional - if omitted, spawns near first available player*

#### Spawn Lootbox
```json
{
  "command": "spawn_lootbox",
  "player": "PlayerName"
}
```
*Spawns a chest with random valuable loot (diamonds, emeralds, golden apples, etc.)*

#### Create Villager
```json
{
  "command": "create_villager",
  "name": "ViewerName",
  "player": "PlayerName"
}
```
*Spawns a villager with custom name*

#### Reset Chaos
```json
{
  "command": "reset_chaos"
}
```

#### Get Chaos Status
```json
{
  "command": "get_chaos"
}
```
*Logs current chaos level to server console*

## Installation

### Prerequisites
- JDK 17 or 21
- Minecraft 1.20.1
- Fabric Server with Fabric API

### Build Instructions

1. **Build the mod:**
   ```bash
   gradlew build
   ```
   On Windows:
   ```cmd
   gradlew.bat build
   ```

2. **Copy the JAR:**
   The compiled mod will be in `build/libs/chaos-stream-mod-1.0.0.jar`

3. **Install on server:**
   - Copy the JAR to your server's `mods/` folder
   - Make sure Fabric API is also installed
   - Restart the server

## Usage

### Setting up Commands Directory

The mod automatically creates a `chaos-commands/` directory in your server root. This is where you place JSON command files.

### Testing Commands

Two test scripts are included:

#### PowerShell Script (Interactive Menu)
```powershell
.\test-commands.ps1
```
Features:
- Interactive menu
- Add chaos (10/50/100 points)
- Spawn entities
- Create villagers
- Spam testing

#### Batch Script (Quick Commands)
```cmd
.\quick-test.bat
```
Quick access to common commands.

### Integration with Streamerbot

In Streamerbot, use the "File: Write to File" action to create JSON files in the `chaos-commands/` directory.

**Example C# code for Streamerbot:**
```csharp
using System;
using System.IO;

public bool Execute()
{
    var commandsDir = @".\server\chaos-commands\";
    var timestamp = DateTime.Now.ToString("yyyyMMdd_HHmmss_fff");
    var filename = commandsDir + "cmd_" + timestamp + ".json";

    // Add 10 chaos on follow
    var json = "{\"command\":\"add_chaos\",\"amount\":10,\"source\":\"follow\"}";

    File.WriteAllText(filename, json);
    return true;
}
```

## Configuration

### Chaos Scaling
Modify these values in `ChaosManager.java`:

```java
private static final long DECAY_INTERVAL = 60000; // Decay every 1 minute
private static final int DECAY_AMOUNT = 1;        // Decay by 1 point
```

### Spawn Settings
Modify in `SpawnHandler.java`:

```java
private static final int SPAWN_RADIUS = 20;              // Distance from player
private static final int SPAWN_CHECK_INTERVAL = 200;     // Check every 10 seconds
```

### Mob Types
Edit the `CHAOS_MOBS` array in `SpawnHandler.java`:

```java
private static final EntityType<?>[] CHAOS_MOBS = {
    EntityType.ZOMBIE,
    EntityType.SKELETON,
    EntityType.SPIDER,
    EntityType.CREEPER,
    EntityType.ENDERMAN,
    EntityType.WITCH
};
```

## File Structure

```
chaos-stream-mod/
├── src/main/java/com/chaosstream/
│   ├── ChaosMod.java           # Main mod class
│   ├── ChaosManager.java       # Chaos tracking & persistence
│   ├── FileWatcher.java        # JSON file monitoring
│   └── SpawnHandler.java       # Entity spawning logic
├── src/main/resources/
│   └── fabric.mod.json         # Mod metadata
├── build.gradle                # Build configuration
└── README.md                   # This file
```

## Data Files

- `chaos-data.json`: Persistent chaos level storage (auto-created in server root)
- `chaos-commands/`: Command JSON files directory (auto-created)

## Troubleshooting

### Commands not working?
1. Check that `chaos-commands/` directory exists
2. Verify JSON syntax is correct
3. Check server logs for errors
4. Ensure files are being created (not just modified)

### Mobs not spawning?
1. Chaos level must be > 0
2. Must be nighttime in-game
3. Players must be online
4. Check spawn rates in logs

### Build errors?
1. Ensure JDK 17 or 21 is installed
2. Run `gradlew clean build`
3. Check that Fabric version matches server

## Future Enhancements

Planned features for future versions:
- HTTP/WebSocket server for direct communication
- In-game GUI for chaos meter
- Custom chat messages and effects
- Boss spawning at high chaos levels
- Chaos events (weather, explosions, etc.)
- Configuration file support

## License

MIT License - Feel free to modify and use for your streams!

## Credits

Created for streaming chaos and viewer interaction.

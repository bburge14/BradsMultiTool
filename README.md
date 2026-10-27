# Brad's Multi-Tool

A RuneLite plugin for Gemstone Crab training, general PvM, and skilling:

- **Gemstone Crab HUD & radar** – outlines the active crab, dormant shell, and cave tunnels; shows crab HP, burrow ETA, XP type (from same-tick XP drops), crab damage/DPS, and session last/max hit. Voice + chat alert when the crab relocates.
- **General PvM HUD** – target HP bar, aggro timer, weakness hint, combat style, DPS (per encounter / per monster type / continuous session), last/max hit, estimated kill time. Lingers after combat for a configurable time.
- **Buff & prayer alerts** – detects when combat potion boosts expire and when prayer runs low or out, with optional repeats, chat messages, and an overhead "OUT OF PRAYER" flash.
- **Idle logout warning** – desktop notification, chat message, and HUD countdown 30 seconds before the 5-minute idle logout.
- **Skill tracker panel** – sidebar cards with XP gained, XP/hr, XP/min, last drop, plus pause/reset/hide per skill.

Voice alerts use Windows SAPI (Zira/David) via `wscript.exe`; on other operating systems they are skipped.

## Running

Requires JDK 11 or newer.

```powershell
.\gradlew.bat runBrad          # Windows
./gradlew runBrad              # macOS / Linux
.\gradlew.bat clean runBrad --info
```

`runBrad` launches a developer-mode RuneLite client with the plugin side-loaded (see `src/test/java/com/bradsmultitool/BradsMultiToolPluginTest.java`).

## Logging in with a Jagex Account

The dev client can't log in through the Jagex Launcher directly, but it reuses a session the official client saves for it:

1. Open RuneLite's configure tool: Windows Start menu → **RuneLite (configure)** (or run `RuneLite.exe --configure`).
2. In **Client arguments**, add `--insecure-write-credentials` and click **Save**.
3. Launch RuneLite **once through the Jagex Launcher** and log in. This writes `%USERPROFILE%\.runelite\credentials.properties`.
4. Close it and run `.\gradlew.bat runBrad`. The dev client picks up the credentials file automatically.

`credentials.properties` is a live login session token. Never share or commit it. To revoke it, delete the file (and remove the client argument if you no longer need it); if it may have leaked, end your sessions from your Jagex account settings.

# Brad's Multi-Tool

A RuneLite plugin for Gemstone Crab training, general PvM, and skilling:

- **Gemstone Crab HUD & radar** – outlines the active crab, dormant shell, and cave tunnels; shows crab HP, burrow ETA, XP type (from same-tick XP drops), crab damage/DPS, and session last/max hit. Voice + chat alert when the crab relocates.
- **General PvM HUD** – target HP bar, aggro timer, weakness hint, combat style, DPS (per encounter / per monster type / continuous session), last/max hit, estimated kill time. Lingers after combat for a configurable time.
- **Buff & prayer alerts** – detects when combat potion boosts expire and when prayer runs low or out, with optional repeats, chat messages, and an overhead "OUT OF PRAYER" flash.
- **Idle logout warning** – desktop notification, chat message, and HUD countdown 30 seconds before the 5-minute idle logout.
- **Skill tracker panel** – sidebar cards with XP gained, XP/hr, XP/min, level progress bar, XP remaining, and time-to-level; right-click a skill to pause, reset, or hide it.

Voice alerts use Windows SAPI (Zira/David) via `wscript.exe`; on other operating systems they are skipped.

## First-time setup (Windows, PowerShell)

**1. Install Java and Git** (skip what you already have; check with `java -version` and `git --version`):

```powershell
winget install --id EclipseAdoptium.Temurin.17.JDK -e
winget install --id Git.Git -e
```

Close and reopen PowerShell afterwards so `java` and `git` are on your PATH.

**2. Download the project and launch it:**

```powershell
New-Item -ItemType Directory -Force C:\dev | Out-Null
Set-Location C:\dev
git clone https://github.com/bburge14/bradsmultitool.git BradsMultiTool
Set-Location C:\dev\BradsMultiTool
git checkout claude/nifty-feynman-izouon   # skip once this branch is merged into main
.\gradlew.bat runBrad
```

The first run downloads Gradle and RuneLite, so it takes a few minutes. If the repo is private, Git opens a browser window to sign in to GitHub.

**3. (Optional) Desktop shortcut:** after cloning, run this once to put a launch button on your desktop:

```powershell
$s = (New-Object -ComObject WScript.Shell).CreateShortcut("$env:USERPROFILE\Desktop\Brad's Multi-Tool.lnk")
$s.TargetPath = "C:\dev\BradsMultiTool\Launch-BradsMultiTool.bat"
$s.WorkingDirectory = "C:\dev\BradsMultiTool"
$s.Save()
```

Double-clicking it (or `Launch-BradsMultiTool.bat` itself) checks your Jagex session file is present, then builds and launches RuneLite with the plugin.

**4. Log in:** follow [Logging in with a Jagex Account](#logging-in-with-a-jagex-account) below (once per PC).

**Getting updates later:**

```powershell
Set-Location C:\dev\BradsMultiTool
git pull
.\gradlew.bat runBrad
```

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

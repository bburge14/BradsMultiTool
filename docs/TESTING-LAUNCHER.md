# Testing the friends launcher end to end

What a friend does, from the download link to playing, on a clean Windows: Windows Sandbox (Windows 10/11 Pro) is a
throwaway copy of Windows that's wiped when you close it, so every run is a first-time install and nothing touches
your own setup.

## Before each test

1. **Push everything** from the Dev tab: Push to repos with the plugins and the launcher switched on. A launcher push
   also makes a release with the installer (the link below always gets the newest).
2. Sandbox on (once): Start > "Turn Windows features on or off" > tick **Windows Sandbox** > restart. Greyed out:
   virtualization is off in the BIOS (Intel VT-x / AMD SVM).
3. Save this as `Snuffys-Test.wsb` (Notepad, "Save as type: All files"):

```xml
<Configuration>
  <Networking>Enable</Networking>
  <MemoryInMB>6144</MemoryInMB>
  <LogonCommand>
    <Command>explorer.exe "https://github.com/bburge14/snuffys-launcher/releases/latest/download/Snuffys-Launcher-Setup.exe"</Command>
  </LogonCommand>
</Configuration>
```

Double-click it: a clean Windows opens and Edge starts downloading the installer. (More memory, or `<vGPU>Enable</vGPU>`,
can make the Sandbox close with "exited unexpectedly (0x80370106)" on some PCs: keep it as it is.)

## 1. Install

- [ ] Edge, "isn't commonly downloaded": hover the download > **...** > **Keep** > **Show more** > **Keep anyway**
- [ ] Open it; "Windows protected your PC": **More info** > **Run anyway**
- [ ] Installer: Downloading... > Unpacking... > the launcher opens, no black windows anywhere
- [ ] A failure says why (also in `%TEMP%\snuffys-launcher-setup-error.txt`)
- [ ] Desktop: a **Snuffy's Launcher** shortcut with the launcher's icon

## 2. Setup checklist (the Setup card)

- [ ] **Java**: "Getting Java (once, about 190 MB)..." then "Java: Snuffy's own". (It always uses its own Java 17,
      whatever else is installed.)
- [ ] **Plugins**: downloaded on opening (What's new lists their changes; each plugin shows its version)
- [ ] **RuneLite**: "Couldn't find RuneLite" at first. Install the **Jagex Launcher** (jagex.com), open it, pick
      **RuneLite** as the game client and let it install; then **Check again**: "RuneLite is installed."
- [ ] **Session saving**: **Turn it on** > Yes. "Session saving is on" (it adds `--insecure-write-credentials` to
      RuneLite's client arguments; a backup is kept)
- [ ] **Session**: **Save one**: the Jagex Launcher opens; log in, RuneLite as the client, **Play**. When RuneLite is
      open the row ticks ("Session saved for <name>"); close that RuneLite. (This logs your account in on the Sandbox:
      a new session, wiped with the Sandbox. Never copy your own `credentials.properties` in.)
- [ ] The card says All set and folds away

## 3. Play

- [ ] Pick plugins (the Toolbelt alone, or any of the separate ones) and **Launch**
- [ ] The first launch builds: it downloads Gradle and RuneLite's client (a few hundred MB, several minutes): the
      Launch bar fills (building, starting, running); **Show log** shows the build
- [ ] RuneLite opens (the developer client) already logged in to your account: **Play**, and you're in the game
- [ ] The plugins are in the sidebar and their settings in the wrench; open each one once
- [ ] The RuneLite chip says it's running the latest code
- [ ] Close RuneLite; Launch again (quicker now: no downloads)

## 4. Updates

- [ ] **A plugin update**: change something small here, Push to repos. In the Sandbox, within 15 minutes (or **Check
      again**): "Code: just updated", What's new lists it, the RuneLite chip turns red ("updates behind") and a Windows
      notification says so. **Relaunch**: RuneLite closes, rebuilds and opens with the change
- [ ] **A launcher update**: push the launcher. **Restart launcher** shows on What's new and the chip says "Launcher:
      restart it"; click it: the window closes and the new one opens (no black windows), RuneLite stays open
- [ ] A Dev-tab-only change to `launcher.ps1` leaves the Dev tab's launcher switch off ("Only Dev-tab changes")

## 5. Everything else

- [ ] Remove a plugin (its x): it's left out of updates and launches; add it back: it downloads again
- [ ] Close the launcher and open it from the desktop shortcut, and from **Snuffy's Launcher.vbs** in
      `%LOCALAPPDATA%\Snuffy's Launcher` (the folder with the apostrophe): both open it, no Notepad
- [ ] Run the installer again: it only opens the launcher
- [ ] **Make a desktop shortcut** (bottom of the window) remakes the shortcut

## When something's wrong

Inside the Sandbox, send these (Win+R, paste the path):

- `%TEMP%\snuffys-launcher.log`: everything the launcher did
- `%TEMP%\snuffys-launcher-error.log`: a problem it showed
- `%TEMP%\snuffys-launcher-setup-error.txt`: the installer's
- **Show log** in the launcher (the build and RuneLite's start)

Closing the Sandbox erases all of it: copy them out first (the Sandbox shares the clipboard).

## What's checked without Windows

`split_plugins.py --verify` builds every plugin on its own. The friends layout (the launcher's `runner/` with all
seven plugin repositories in `plugins/`, in a folder named `Snuffy's Launcher`) compiles together and loads all the
plugins in RuneLite's developer client (checked by hand with `gradlew runSnuffys` under xvfb; the game itself can't be
reached from there). The PowerShell and the installer only run on Windows, so they're tested in the Sandbox.

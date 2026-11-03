# Snuffy's Launcher

Runs [Snuffy's plugins](https://github.com/bburge14/snuffys-toolbelt) in RuneLite before (or alongside) the Plugin Hub:
it downloads each plugin's own repository, keeps them up to date, and starts RuneLite with the ones you pick.

## What you need

- **Windows**
- **RuneLite** ([runelite.net](https://runelite.net/))
- **Java 11 or newer** ([Temurin 17](https://adoptium.net/temurin/releases/?version=17) is a good one)
- **git** ([git-scm.com](https://git-scm.com/download/win)): how the launcher downloads and updates the plugins

## Getting it

Open PowerShell where you want it (your Documents, say) and run:

```
git clone https://github.com/bburge14/snuffys-launcher.git
```

Then open the `snuffys-launcher` folder and double-click **Launch-Snuffys.bat**. Its **Make a desktop shortcut** link
puts it on your desktop.

## Using it

- The first time it downloads every plugin (a minute or two), then updates them each time you open it, and every
  minute while it's open.
- Work down the **Setup** rows until they're ticked: RuneLite found, session saving turned on (the launcher can do it
  for you), a Jagex session saved (log in once through the Jagex Launcher), Java and git.
- Pick **Snuffy's Toolbelt** (everything in one) or the separate plugins you want, and press **Launch RuneLite**. The
  first launch downloads RuneLite and builds the plugins, so it takes a few minutes.
- When the chip under the name says RuneLite is behind, press **Relaunch** to get the update.

Your settings and plans are kept in RuneLite, per account, as with any plugin.

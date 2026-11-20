# FOCUS FLOW Desktop App (Windows)

One click on the **FOCUS FLOW** taskbar icon starts the server, waits until it is ready, and opens the app window (no address bar).
IntelliJ is not required.

## Install / reinstall
```
powershell -ExecutionPolicy Bypass -File desktop\install-desktop-app.ps1
```
- Builds `desktop\bin\FocusFlow.exe` (using the C# compiler built into Windows) and creates `FOCUS FLOW` shortcuts on the Desktop and in the Start menu.
- Run it again after moving the project folder or changing the launcher code. Close the FOCUS FLOW window first.
- `-TidyOldShortcuts`: moves the old Desktop shortcuts (the Chrome PWA `Sign in · FOCUS FLOW` and `start-focusflow - Shortcut`) into `%LOCALAPPDATA%\FocusFlow\old-shortcuts`. Nothing is deleted.

Windows does not allow scripts to pin apps to the taskbar. To pin it: right-click the **FOCUS FLOW** Desktop shortcut, choose **Show more options**, then **Pin to taskbar**.

## Behavior
| Situation | What happens |
|---|---|
| Server is not running | Checks MySQL (`DB_PORT`) → runs `gradlew bootJar` if the source changed → starts the server → waits for it to respond → opens the app window |
| Server is already running | Does not start a second server; only opens the window |
| Clicked several times quickly | A mutex processes the requests one at a time, and only one server is started |
| All app windows closed | Stops **only the server the launcher started**, with Ctrl+C (Spring graceful shutdown). Reopening within 5 seconds keeps it running |
| Server started from IntelliJ or `start-focusflow.bat` | Used but never stopped by the launcher |

- The server runs from the project folder, so it uses `application-local.properties`. The launcher reads only `DB_PORT` from it.
- The launcher runs a copy of the jar at `%LOCALAPPDATA%\FocusFlow\app\focus-flow.jar`, so it does not conflict with IntelliJ or Gradle builds.
- The app window is a dedicated Chrome profile (`%LOCALAPPDATA%\FocusFlow\browser`) opened in `--app` mode. If Chrome is not installed, Edge is used.

## Logs
`%LOCALAPPDATA%\FocusFlow\logs\`: `launcher.log`, `server.log`, `build.log`

## Command line
- `FocusFlow.exe --status`: server status
- `FocusFlow.exe --stop`: stop the server the launcher started

## Icons
- Sources: `icon\focus-flow-icon.svg` (master), `focus-flow-icon-small.svg` (simplified for 16–32 px), `focus-flow-icon-maskable.svg` (PWA)
- Generated files: `icon\png\*.png` (16–1024 px), `icon\focus-flow.ico`, and `src\main\resources\static\{favicon.ico,favicon.svg,icons\*,manifest.json}`
- Regenerate: `powershell -ExecutionPolicy Bypass -File desktop\icon\render-icons.ps1`

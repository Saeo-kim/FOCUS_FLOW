# Builds FocusFlow.exe and creates the "FOCUS FLOW" desktop + Start menu shortcuts.
# Safe to run again at any time (e.g. after moving the project or changing the launcher).
#
#   powershell -ExecutionPolicy Bypass -File desktop\install-desktop-app.ps1
#   powershell -ExecutionPolicy Bypass -File desktop\install-desktop-app.ps1 -TidyOldShortcuts
#
# -TidyOldShortcuts moves the old desktop shortcuts ("Sign in · FOCUS FLOW" Chrome PWA and
# "start-focusflow - Shortcut") into %LOCALAPPDATA%\FocusFlow\old-shortcuts so they cannot be
# confused with the new launcher. Nothing is deleted.
param([switch]$TidyOldShortcuts)
$ErrorActionPreference = 'Stop'

$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$bin  = Join-Path $here 'bin'
$exe  = Join-Path $bin 'FocusFlow.exe'
$ico  = Join-Path $here 'icon\focus-flow.ico'
New-Item -ItemType Directory -Force $bin | Out-Null

# 1. Compile the launcher with the C# compiler that ships with Windows.
$csc = Join-Path $env:WINDIR 'Microsoft.NET\Framework64\v4.0.30319\csc.exe'
if (-not (Test-Path $csc)) { $csc = Join-Path $env:WINDIR 'Microsoft.NET\Framework\v4.0.30319\csc.exe' }
if (-not (Test-Path $csc)) { throw '.NET Framework 4 C# compiler (csc.exe) not found.' }
$running = Get-Process FocusFlow -ErrorAction SilentlyContinue | Where-Object { $_.Path -eq $exe }
if ($running) { throw 'FocusFlow.exe is running (an app window is open). Close FOCUS FLOW first, then run this again.' }
& $csc /nologo /target:winexe /optimize+ /platform:anycpu "/win32icon:$ico" "/out:$exe" `
    /r:System.Management.dll /r:System.Windows.Forms.dll /r:System.Drawing.dll `
    (Join-Path $here 'launcher\FocusFlowLauncher.cs')
if ($LASTEXITCODE -ne 0) { throw 'Compiling FocusFlow.exe failed.' }
Write-Host "Built $exe"

# 2. Shortcuts (they carry the FOCUS FLOW icon and taskbar identity).
$desktop   = [Environment]::GetFolderPath('Desktop')
$startMenu = Join-Path ([Environment]::GetFolderPath('Programs')) 'FOCUS FLOW.lnk'
foreach ($lnk in @((Join-Path $desktop 'FOCUS FLOW.lnk'), $startMenu)) {
    $p = Start-Process -FilePath $exe -ArgumentList '--create-shortcut', "`"$lnk`"" -Wait -PassThru
    if ($p.ExitCode -ne 0 -or -not (Test-Path $lnk)) { throw "Creating $lnk failed." }
    Write-Host "Shortcut: $lnk"
}

# 3. Optional: move the old, confusing shortcuts out of the way (never deleted).
if ($TidyOldShortcuts) {
    $keep = Join-Path $env:LOCALAPPDATA 'FocusFlow\old-shortcuts'
    New-Item -ItemType Directory -Force $keep | Out-Null
    $dot = [char]0x00B7   # kept out of the literal so Windows PowerShell 5.1 reads this file correctly
    foreach ($name in @("Sign in $dot FOCUS FLOW.lnk", 'start-focusflow - Shortcut.lnk')) {
        $old = Join-Path $desktop $name
        if (Test-Path -LiteralPath $old) {
            Move-Item -LiteralPath $old -Destination (Join-Path $keep $name) -Force
            Write-Host "Moved old shortcut to $keep\$name"
        }
    }
}

# Refresh Explorer's icon cache so the new icon shows immediately.
& "$env:WINDIR\System32\ie4uinit.exe" -show 2>$null
Write-Host 'Done. Pin it: right-click the "FOCUS FLOW" desktop shortcut > Show more options > Pin to taskbar.'

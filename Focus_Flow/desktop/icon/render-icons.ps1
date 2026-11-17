# Renders the FOCUS FLOW icon SVGs to PNG / ICO / web assets with headless Chrome (or Edge) + GDI+.
# Run from anywhere: powershell -ExecutionPolicy Bypass -File desktop\icon\render-icons.ps1
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$here   = Split-Path -Parent $MyInvocation.MyCommand.Path
$root   = (Resolve-Path (Join-Path $here '..\..')).Path
$out    = Join-Path $here 'png'
$static = Join-Path $root 'src\main\resources\static'
New-Item -ItemType Directory -Force $out, (Join-Path $static 'icons') | Out-Null

$browser = @("$env:ProgramFiles\Google\Chrome\Application\chrome.exe",
             "${env:ProgramFiles(x86)}\Google\Chrome\Application\chrome.exe",
             "$env:LOCALAPPDATA\Google\Chrome\Application\chrome.exe",
             "${env:ProgramFiles(x86)}\Microsoft\Edge\Application\msedge.exe") | Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $browser) { throw 'Chrome or Edge is required to render the icons.' }
$tmp = Join-Path $env:TEMP ('ff-icon-' + [guid]::NewGuid())
New-Item -ItemType Directory $tmp | Out-Null

# Renders an SVG to a square PNG master with a transparent background.
function Render-Master([string]$svg, [int]$size) {
    $dest = Join-Path $tmp "$svg.png"
    $html = Join-Path $tmp "$svg.html"
    $uri  = ([Uri](Join-Path $here $svg)).AbsoluteUri
    "<html><body style='margin:0;background:transparent;overflow:hidden'><img src='$uri' width='$size' height='$size' style='display:block'></body></html>" |
        Set-Content -Encoding UTF8 $html
    $args = @('--headless', '--disable-gpu', '--hide-scrollbars', '--allow-file-access-from-files', '--force-device-scale-factor=1',
              "--user-data-dir=`"$tmp\profile`"", '--default-background-color=00000000', "--screenshot=`"$dest`"",
              "--window-size=$size,$size", ([Uri]$html).AbsoluteUri)
    Start-Process -FilePath $browser -ArgumentList $args -Wait -WindowStyle Hidden
    if (-not (Test-Path $dest)) { throw "Rendering $svg failed." }
    $img = [Drawing.Bitmap]::FromFile($dest)
    if ($img.Width -lt $size -or $img.Height -lt $size) { throw "Rendering $svg gave $($img.Width)x$($img.Height), expected $size." }
    return $img
}

function Save-Scaled([Drawing.Image]$src, [int]$size, [string]$dest) {
    $bmp = New-Object Drawing.Bitmap($size, $size, [Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [Drawing.Graphics]::FromImage($bmp)
    $g.InterpolationMode  = [Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.PixelOffsetMode    = [Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $g.CompositingQuality = [Drawing.Drawing2D.CompositingQuality]::HighQuality
    $g.SmoothingMode      = [Drawing.Drawing2D.SmoothingMode]::HighQuality
    $attr = New-Object Drawing.Imaging.ImageAttributes
    $attr.SetWrapMode([Drawing.Drawing2D.WrapMode]::TileFlipXY)
    $side = [Math]::Min($src.Width, $src.Height)
    $g.DrawImage($src, (New-Object Drawing.Rectangle(0, 0, $size, $size)), 0, 0, $side, $side, [Drawing.GraphicsUnit]::Pixel, $attr)
    $g.Dispose(); $bmp.Save($dest, [Drawing.Imaging.ImageFormat]::Png); $bmp.Dispose()
}

$master   = Render-Master 'focus-flow-icon.svg' 1024
$small    = Render-Master 'focus-flow-icon-small.svg' 512   # simplified drawing, used for 16-32 px
$maskable = Render-Master 'focus-flow-icon-maskable.svg' 1024
foreach ($s in 16, 20, 24, 32)                               { Save-Scaled $small  $s (Join-Path $out "focus-flow-$s.png") }
foreach ($s in 40, 48, 64, 96, 128, 180, 192, 256, 512, 1024) { Save-Scaled $master $s (Join-Path $out "focus-flow-$s.png") }
foreach ($s in 192, 512) { Save-Scaled $maskable $s (Join-Path $out "focus-flow-maskable-$s.png") }
$master.Dispose(); $small.Dispose(); $maskable.Dispose()

# ICO with PNG-compressed entries (supported since Windows Vista).
function Write-Ico([int[]]$sizes, [string]$dest) {
    $images = foreach ($s in $sizes) { ,[IO.File]::ReadAllBytes((Join-Path $out "focus-flow-$s.png")) }
    $ms = New-Object IO.MemoryStream; $w = New-Object IO.BinaryWriter($ms)
    $w.Write([uint16]0); $w.Write([uint16]1); $w.Write([uint16]$sizes.Count)
    $offset = 6 + 16 * $sizes.Count
    for ($i = 0; $i -lt $sizes.Count; $i++) {
        $d = if ($sizes[$i] -ge 256) { 0 } else { $sizes[$i] }
        $w.Write([byte]$d); $w.Write([byte]$d); $w.Write([byte]0); $w.Write([byte]0)
        $w.Write([uint16]1); $w.Write([uint16]32); $w.Write([uint32]$images[$i].Length); $w.Write([uint32]$offset)
        $offset += $images[$i].Length
    }
    foreach ($img in $images) { $w.Write($img) }
    $w.Flush(); [IO.File]::WriteAllBytes($dest, $ms.ToArray()); $w.Dispose()
}
Write-Ico @(16, 20, 24, 32, 40, 48, 64, 96, 128, 256) (Join-Path $here 'focus-flow.ico')
Write-Ico @(16, 32, 48) (Join-Path $static 'favicon.ico')

# Web app assets.
Copy-Item (Join-Path $here 'focus-flow-icon.svg')        (Join-Path $static 'favicon.svg') -Force
Copy-Item (Join-Path $out 'focus-flow-180.png')          (Join-Path $static 'icons\apple-touch-icon.png') -Force
Copy-Item (Join-Path $out 'focus-flow-32.png')           (Join-Path $static 'icons\favicon-32.png') -Force
Copy-Item (Join-Path $out 'focus-flow-192.png')          (Join-Path $static 'icons\icon-192.png') -Force
Copy-Item (Join-Path $out 'focus-flow-512.png')          (Join-Path $static 'icons\icon-512.png') -Force
Copy-Item (Join-Path $out 'focus-flow-maskable-192.png') (Join-Path $static 'icons\icon-maskable-192.png') -Force
Copy-Item (Join-Path $out 'focus-flow-maskable-512.png') (Join-Path $static 'icons\icon-maskable-512.png') -Force

Remove-Item -Recurse -Force $tmp -ErrorAction SilentlyContinue
Write-Host "Icons written to $out, $here\focus-flow.ico and $static"

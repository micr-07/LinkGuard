# Tap a view by its text, after dumping the UI hierarchy. ASCII-safe + retries.
# Usage: uitap.ps1 -Text "加入黑名单" [-Display 5] [-Index 0]
param(
    [Parameter(Mandatory = $true)][string]$Text,
    [int]$Display = 5,
    [int]$Index = 0,
    [string]$Device = "127.0.0.1:16384",
    [string]$Adb = "D:\adb\platform-tools\adb.exe",
    [string]$OutDir = "C:\Users\micr07\AppData\Local\Temp\lgsh",
    [switch]$DumpOnly
)

if (-not (Test-Path $OutDir)) { New-Item -ItemType Directory -Force -Path $OutDir | Out-Null }
$local = Join-Path $OutDir "ui-tap.xml"

$xml = $null
for ($try = 1; $try -le 3; $try++) {
    $r1 = & $Adb -s $Device shell uiautomator dump /sdcard/ui.xml 2>&1 | Out-String
    if ($r1 -notmatch "dumped") {
        Write-Host "  dump attempt $try : $($r1.Trim())"
        Start-Sleep -Milliseconds 1200
        continue
    }
    $r2 = & $Adb -s $Device pull /sdcard/ui.xml $local 2>&1 | Out-String
    if (Test-Path $local) {
        $xml = Get-Content -Raw -Encoding UTF8 $local
        break
    }
    Write-Host "  pull attempt $try : $($r2.Trim())"
    Start-Sleep -Milliseconds 1200
}

if (-not $xml) { Write-Host "FAILED: could not dump UI"; exit 1 }

# all nodes whose text attribute equals $Text
$rx = '<node[^>]*?text="' + [regex]::Escape($Text) + '"[^>]*?bounds="\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]"'
$ms = [regex]::Matches($xml, $rx)
if ($ms.Count -eq 0) {
    $rx2 = '<node[^>]*?bounds="\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]"[^>]*?text="' + [regex]::Escape($Text) + '"'
    $ms = [regex]::Matches($xml, $rx2)
}
if ($ms.Count -eq 0) {
    Write-Host "NOT FOUND: '$Text'"
    Write-Host "---- available text ----"
    [regex]::Matches($xml, 'text="([^"]+)"') | ForEach-Object { $_.Groups[1].Value } |
        Where-Object { $_ -ne '' } | Select-Object -Unique | ForEach-Object { Write-Host "  $_" }
    exit 2
}

$m = $ms[[Math]::Min($Index, $ms.Count - 1)]
$x1 = [int]$m.Groups[1].Value; $y1 = [int]$m.Groups[2].Value
$x2 = [int]$m.Groups[3].Value; $y2 = [int]$m.Groups[4].Value
$cx = [int](($x1 + $x2) / 2); $cy = [int](($y1 + $y2) / 2)
Write-Host "found '$Text' x$($ms.Count) bounds=[$x1,$y1][$x2,$y2] center=$cx,$cy"

if ($DumpOnly) { return }

& $Adb -s $Device shell input -d $Display tap $cx $cy 2>&1 | Out-Null
Write-Host "tapped $cx,$cy on display $Display"

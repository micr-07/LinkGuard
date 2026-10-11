# Walk the LinkGuard settings screens the way a user would (tap the buttons), dumping each page.
param(
    [string]$Device = "127.0.0.1:16384",
    [string]$Adb = "D:\adb\platform-tools\adb.exe",
    [string]$Tmp = "C:\Users\micr07\AppData\Local\Temp\lgsh"
)
$PKG = "com.linkguard.browser"
if (-not (Test-Path $Tmp)) { New-Item -ItemType Directory -Force -Path $Tmp | Out-Null }

function Sh([string]$c) { & $Adb -s $Device shell $c 2>&1 | Out-String }

function Get-Disp {
    $out = Sh "dumpsys window displays"
    $cur = $null
    foreach ($l in ($out -split "`n")) {
        if ($l -match 'Display:\s*mDisplayId=(\d+)') { $cur = $matches[1] }
        if ($l -match 'mCurrentFocus=' -and $l -match $PKG) { return $cur }
    }
    return $null
}

function Get-Xml {
    $local = Join-Path $Tmp "nav.xml"
    Remove-Item $local -Force -ErrorAction SilentlyContinue
    for ($i = 1; $i -le 4; $i++) {
        if ((Sh "uiautomator dump /sdcard/nav.xml") -match "dumped") {
            & $Adb -s $Device pull /sdcard/nav.xml $local 2>&1 | Out-Null
            if (Test-Path $local) { return (Get-Content -Raw -Encoding UTF8 $local) }
        }
        Start-Sleep -Milliseconds 900
    }
    return $null
}

function Show-Page([string]$title) {
    $xml = Get-Xml
    $texts = [regex]::Matches($xml, 'text="([^"]+)"') | ForEach-Object { $_.Groups[1].Value } | Where-Object { $_ -ne '' }
    Write-Host "---- $title ----"
    $texts | Select-Object -Unique | ForEach-Object { Write-Host "  $_" }
}

function Scroll-Down {
    $disp = Get-Disp
    if ($disp) { Sh "input -d $disp swipe 960 800 960 300 300" | Out-Null; Start-Sleep -Milliseconds 900 }
}

function Tap-Prefix([string]$prefix) {
    for ($s = 0; $s -lt 4; $s++) {
        $hit = Tap-PrefixOnce $prefix
        if ($hit) { return $true }
        Scroll-Down
    }
    Write-Host "  NOT FOUND after scrolling: $prefix*"
    return $false
}

function Tap-PrefixOnce([string]$prefix) {
    $xml = Get-Xml
    $ms = [regex]::Matches($xml, '<node[^>]*?text="([^"]*)"[^>]*?bounds="\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]"')
    foreach ($m in $ms) {
        if ($m.Groups[1].Value -like "$prefix*") {
            $cx = [int](([int]$m.Groups[2].Value + [int]$m.Groups[4].Value) / 2)
            $cy = [int](([int]$m.Groups[3].Value + [int]$m.Groups[5].Value) / 2)
            $disp = Get-Disp
            if (-not $disp) { Write-Host "  no display for $PKG"; return $false }
            Sh "input -d $disp tap $cx $cy" | Out-Null
            Write-Host "  tapped '$($m.Groups[1].Value)' at $cx,$cy (display $disp)"
            Start-Sleep -Seconds 2
            return $true
        }
    }
    return $false
}

Sh "am force-stop $PKG" | Out-Null
Start-Sleep -Seconds 1
Sh "am start -n $PKG/.MainActivity" | Out-Null
Start-Sleep -Seconds 3
Show-Page "主界面（上半）"

foreach ($btn in @("黑名单（", "白名单（", "已屏蔽的应用（", "浏览记录（")) {
    if (Tap-Prefix $btn) {
        Show-Page $btn
        $dd = Get-Disp; if ($dd) { Sh "input -d $dd keyevent 4" | Out-Null }
        Start-Sleep -Seconds 2
    }
}

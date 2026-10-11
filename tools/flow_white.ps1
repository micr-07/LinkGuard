# 验收新的白名单语义：
#   点「始终」= 只加入白名单、跟随默认浏览器，不再强制选浏览器；
#   想给某个网址单独指定浏览器，到白名单页里点那一行设置（特例）。
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
    $local = Join-Path $Tmp "fw.xml"
    Remove-Item $local -Force -ErrorAction SilentlyContinue
    for ($i = 1; $i -le 4; $i++) {
        if ((Sh "uiautomator dump /sdcard/fw.xml") -match "dumped") {
            & $Adb -s $Device pull /sdcard/fw.xml $local 2>&1 | Out-Null
            if (Test-Path $local) { return (Get-Content -Raw -Encoding UTF8 $local) }
        }
        Start-Sleep -Milliseconds 900
    }
    return $null
}

function Texts {
    $xml = Get-Xml
    if (-not $xml) { return @() }
    return ([regex]::Matches($xml, 'text="([^"]+)"') | ForEach-Object { $_.Groups[1].Value } | Where-Object { $_ -ne '' })
}

function Tap-Prefix([string]$prefix) {
    for ($try = 1; $try -le 3; $try++) {
        $xml = Get-Xml
        $ms = [regex]::Matches($xml, '<node[^>]*?text="([^"]*)"[^>]*?bounds="\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]"')
        foreach ($m in $ms) {
            if ($m.Groups[1].Value -like "$prefix*") {
                $cx = [int](([int]$m.Groups[2].Value + [int]$m.Groups[4].Value) / 2)
                $cy = [int](([int]$m.Groups[3].Value + [int]$m.Groups[5].Value) / 2)
                $disp = Get-Disp
                if (-not $disp) { Write-Host "  no display"; return $false }
                Sh "input -d $disp tap $cx $cy" | Out-Null
                Write-Host "  tapped '$($m.Groups[1].Value)' @$cx,$cy (d$disp)"
                Start-Sleep -Seconds 2
                return $true
            }
        }
        Start-Sleep -Milliseconds 900
    }
    Write-Host "  NOT FOUND: $prefix*"
    return $false
}

function Scroll-Down {
    $disp = Get-Disp
    if ($disp) { Sh "input -d $disp swipe 960 850 960 250 260" | Out-Null; Start-Sleep -Milliseconds 900 }
}

function Tap-PrefixScrolled([string]$prefix) {
    for ($i = 0; $i -lt 4; $i++) {
        if (Tap-Prefix $prefix) { return $true }
        Scroll-Down
    }
    return $false
}

function Top-Activity {
    # MuMu 把应用放在虚拟显示器上，dumpsys 里每个 display 各有一份焦点窗口；
    # 只取第一条会读到别的屏上的陈旧活动，所以把所有非空的焦点窗口拼起来判断。
    $out = Sh "dumpsys window displays"
    $acc = New-Object System.Collections.ArrayList
    foreach ($l in ($out -split "`n")) {
        if ($l -match 'mCurrentFocus=Window\{[^}]*\s(\S+/\S+)\}') { [void]$acc.Add($matches[1]) }
    }
    if ($acc.Count -eq 0) { return "?" }
    return ($acc -join " ")
}

function Wait-For([scriptblock]$cond, [int]$seconds = 10) {
    for ($i = 0; $i -lt ($seconds * 2); $i++) {
        if (& $cond) { return $true }
        Start-Sleep -Milliseconds 500
    }
    return $false
}

function Dialog-Showing {
    # 优先看窗口焦点（不受 uiautomator 抓取失败影响）
    if ((Top-Activity) -like "*InterceptActivity*") { return $true }
    return ((Texts) -contains "有应用想打开一个链接")
}

function Trigger([string]$url) {
    Sh "am force-stop $PKG" | Out-Null
    Start-Sleep -Milliseconds 700
    Sh "am start -n $PKG/.InterceptActivity -a android.intent.action.VIEW -d `"$url`"" | Out-Null
    [void](Wait-For { (Top-Activity) -like "*InterceptActivity*" } 8)
    Start-Sleep -Milliseconds 800
}

$ok = 0; $bad = 0
function Check([string]$name, [bool]$pass, [string]$detail) {
    if ($pass) { $script:ok++ } else { $script:bad++ }
    $tag = if ($pass) { "PASS" } else { "FAIL" }
    Write-Host ("[{0}] {1}  {2}" -f $tag, $name, $detail)
}

Write-Host "=== 白名单语义验收 ==="
Sh "pm clear $PKG" | Out-Null
Start-Sleep -Seconds 1

# --- 先在设置里选好默认浏览器（用系统默认的 Chrome） ---
Sh "am start -n $PKG/.MainActivity" | Out-Null
Start-Sleep -Seconds 3
[void](Tap-PrefixScrolled "修改默认打开方式")
[void](Tap-Prefix "Chrome")
Start-Sleep -Seconds 1
$t = Texts
$defaultSet = [bool]($t | Where-Object { $_ -like "*Chrome*" })
Check "0. 在设置里把 Chrome 设为默认浏览器" $defaultSet ("texts=" + ($t -join "|"))

# --- 点「始终」应该直接开、不再问浏览器 ---
Trigger "https://m.bilibili.com/video/1"
Check "1. 触发链接弹出询问窗" (Dialog-Showing) "url=m.bilibili.com"
[void](Tap-Prefix "始终")
Start-Sleep -Seconds 3
$top = Top-Activity
$showing = Dialog-Showing
$pickerText = (Texts) -join "|"
$noPicker = ($pickerText -notlike "*选择用哪个浏览器*")
Check "2. 点「始终」后没有再弹浏览器选择" $noPicker ("dialog=" + $showing)
Check "3. 直接用默认浏览器 Chrome 打开了" ($top -like "*chrome*") "top=$top"

# --- 再次触发同一网址：应静默直接用默认浏览器 ---
Trigger "https://m.bilibili.com/video/2"
$top = Top-Activity
$showing = Dialog-Showing
Check "4. 白名单网址不再询问，直接开" ((-not $showing) -and ($top -like "*chrome*")) "dialog=$showing top=$top"

# --- 白名单页里应该显示「跟随默认浏览器」 ---
Sh "am start -n $PKG/.MainActivity" | Out-Null
Start-Sleep -Seconds 2
[void](Tap-PrefixScrolled "白名单（")
$t = Texts
Check "5. 白名单条目显示「跟随默认浏览器」" (($t -contains "bilibili.com") -and ($t -contains "跟随默认浏览器")) ("texts=" + ($t -join "|"))

# --- 给这个网址单独指定浏览器（特例）---
[void](Tap-Prefix "bilibili.com")
[void](Tap-Prefix "指定浏览器")
[void](Tap-Prefix "Via")
Start-Sleep -Seconds 1
$t = Texts
Check "6. 指定后条目变成「固定使用 Via」" (($t -join "|") -like "*固定使用 Via*") ("texts=" + ($t -join "|"))

# --- 再触发：这次应该用指定的 Via，而不是默认的 Chrome ---
Trigger "https://m.bilibili.com/video/3"
Start-Sleep -Seconds 2
$top = Top-Activity
Check "7. 特例生效：用 Via 而不是默认的 Chrome" ($top -like "*via*") "top=$top"

Write-Host ""
Write-Host ("=== {0} passed, {1} failed ===" -f $ok, $bad)

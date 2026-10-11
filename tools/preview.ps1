# Preview helper: dump UI text from the emulator and capture the MuMu device window.
# Usage: preview.ps1 -Name main            (default device 127.0.0.1:16384)
param(
    [Parameter(Mandatory = $true)][string]$Name,
    [string]$Device = "127.0.0.1:16384",
    [string]$Adb = "D:\adb\platform-tools\adb.exe",
    [string]$OutDir = "C:\Users\micr07\AppData\Local\Temp\lgsh",
    [switch]$NoShot
)

if (-not (Test-Path $OutDir)) { New-Item -ItemType Directory -Force -Path $OutDir | Out-Null }

# ---- UI hierarchy (text + bounds), read from the Android side ----
$dump = & $Adb -s $Device shell uiautomator dump /sdcard/ui.xml 2>&1 | Out-String
if ($dump -notmatch "dumped") { Write-Host "dump failed: $dump" }
& $Adb -s $Device pull /sdcard/ui.xml (Join-Path $OutDir "ui-$Name.xml") 2>&1 | Out-Null

$xmlPath = Join-Path $OutDir "ui-$Name.xml"
if (Test-Path $xmlPath) {
    $xml = Get-Content -Raw -Encoding UTF8 $xmlPath
    $texts = [regex]::Matches($xml, 'text="([^"]+)"') | ForEach-Object { $_.Groups[1].Value } | Where-Object { $_ -ne '' }
    Write-Host "---- UI text ----"
    $texts | Select-Object -Unique | ForEach-Object { Write-Host "  $_" }
    $check = [regex]::Matches($xml, 'checked="true"[^>]*?text="([^"]*)"') | ForEach-Object { $_.Groups[1].Value }
    $check2 = [regex]::Matches($xml, 'text="([^"]*)"[^>]*?checked="true"') | ForEach-Object { $_.Groups[1].Value }
    if ($check.Count -gt 0 -or $check2.Count -gt 0) {
        Write-Host "---- checked ----"
        ($check + $check2) | Where-Object { $_ -ne '' } | ForEach-Object { Write-Host "  [x] $_" }
    }
}

if ($NoShot) { return }

# ---- Capture the MuMu device window ----
Add-Type -AssemblyName System.Drawing
if (-not ("WinCap2" -as [type])) {
Add-Type -TypeDefinition @"
using System;
using System.Text;
using System.Runtime.InteropServices;
public class WinCap2 {
  public delegate bool EnumProc(IntPtr h, IntPtr l);
  [DllImport("user32.dll")] public static extern bool EnumWindows(EnumProc cb, IntPtr l);
  [DllImport("user32.dll", CharSet=CharSet.Unicode)] public static extern int GetWindowText(IntPtr h, StringBuilder s, int n);
  [DllImport("user32.dll")] public static extern bool IsWindowVisible(IntPtr h);
  [DllImport("user32.dll")] public static extern uint GetWindowThreadProcessId(IntPtr h, out uint procId);
  [StructLayout(LayoutKind.Sequential)] public struct RECT { public int Left; public int Top; public int Right; public int Bottom; }
  [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr h, out RECT r);
  [DllImport("user32.dll")] public static extern bool PrintWindow(IntPtr h, IntPtr dc, uint flags);
}
"@ -ReferencedAssemblies System.Drawing
}

$target = [IntPtr]::Zero
$targetW = 0; $targetH = 0
$cb = [WinCap2+EnumProc]{
    param($h, $l)
    if ([WinCap2]::IsWindowVisible($h)) {
        $sb = New-Object System.Text.StringBuilder 512
        [void][WinCap2]::GetWindowText($h, $sb, 512)
        $procId = 0
        [void][WinCap2]::GetWindowThreadProcessId($h, [ref]$procId)
        $pn = (Get-Process -Id $procId -ErrorAction SilentlyContinue).ProcessName
        if ($pn -eq "MuMuNxDevice") {
            $r = New-Object WinCap2+RECT
            [void][WinCap2]::GetWindowRect($h, [ref]$r)
            $w = $r.Right - $r.Left
            $ht = $r.Bottom - $r.Top
            # keep the largest device window (MuMu also owns small helper windows)
            if ($w -gt 300 -and $ht -gt 300 -and ($w * $ht) -gt ($script:targetArea)) {
                $script:target = $h
                $script:targetW = $w
                $script:targetH = $ht
                $script:targetArea = $w * $ht
            }
        }
    }
    return $true
}
$script:target = [IntPtr]::Zero
$script:targetArea = 0
[void][WinCap2]::EnumWindows($cb, [IntPtr]::Zero)

if ($script:target -ne [IntPtr]::Zero -and $script:targetW -gt 0) {
    $bmp = New-Object System.Drawing.Bitmap($script:targetW, $script:targetH)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $dc = $g.GetHdc()
    [void][WinCap2]::PrintWindow($script:target, $dc, 2)
    $g.ReleaseHdc($dc)
    $g.Dispose()
    $file = Join-Path $OutDir ("shot-$Name.png")
    $bmp.Save($file, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
    Write-Host "shot -> $file ($script:targetW x $script:targetH)"
} else {
    Write-Host "shot: MuMuNxDevice window not found"
}

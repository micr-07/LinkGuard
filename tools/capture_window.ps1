# Capture MuMu host windows into PNGs (ASCII only, safe for PowerShell 5.1)
param([string]$OutDir = "D:\shots")

Add-Type -AssemblyName System.Drawing
Add-Type -TypeDefinition @"
using System;
using System.Text;
using System.Runtime.InteropServices;
public class WinCap {
  public delegate bool EnumProc(IntPtr h, IntPtr l);
  [DllImport("user32.dll")] public static extern bool EnumWindows(EnumProc cb, IntPtr l);
  [DllImport("user32.dll", CharSet=CharSet.Unicode)] public static extern int GetWindowText(IntPtr h, StringBuilder s, int n);
  [DllImport("user32.dll")] public static extern bool IsWindowVisible(IntPtr h);
  [DllImport("user32.dll")] public static extern uint GetWindowThreadProcessId(IntPtr h, out uint procId);
  [StructLayout(LayoutKind.Sequential)] public struct RECT { public int Left; public int Top; public int Right; public int Bottom; }
  [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr h, out RECT r);
  [DllImport("user32.dll")] public static extern bool PrintWindow(IntPtr h, IntPtr dc, uint flags);
  [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
  [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
}
"@ -ReferencedAssemblies System.Drawing

if (-not (Test-Path $OutDir)) { New-Item -ItemType Directory -Force -Path $OutDir | Out-Null }

$found = New-Object System.Collections.ArrayList
$cb = [WinCap+EnumProc]{
    param($h, $l)
    if ([WinCap]::IsWindowVisible($h)) {
        $sb = New-Object System.Text.StringBuilder 512
        [void][WinCap]::GetWindowText($h, $sb, 512)
        $title = $sb.ToString()
        $procId = 0
        [void][WinCap]::GetWindowThreadProcessId($h, [ref]$procId)
        $r = New-Object WinCap+RECT
        [void][WinCap]::GetWindowRect($h, [ref]$r)
        $w = $r.Right - $r.Left
        $ht = $r.Bottom - $r.Top
        if ($w -gt 200 -and $ht -gt 200 -and $title -like "*MuMu*") {
            [void]$found.Add([pscustomobject]@{ Title = $title; ProcId = $procId; W = $w; H = $ht; Handle = $h })
        }
    }
    return $true
}
[void][WinCap]::EnumWindows($cb, [IntPtr]::Zero)

$i = 0
foreach ($w in $found) {
    $i++
    $pname = (Get-Process -Id $w.ProcId -ErrorAction SilentlyContinue).ProcessName
    Write-Host ("[{0}] {1} ({2}) {3}x{4} hwnd={5}" -f $i, $w.Title, $pname, $w.W, $w.H, $w.Handle)
    $bmp = New-Object System.Drawing.Bitmap($w.W, $w.H)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $dc = $g.GetHdc()
    $ok = [WinCap]::PrintWindow($w.Handle, $dc, 2)
    $g.ReleaseHdc($dc)
    $g.Dispose()
    $file = Join-Path $OutDir ("mumu-{0}.png" -f $i)
    $bmp.Save($file, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
    Write-Host ("    PrintWindow={0} -> {1}" -f $ok, $file)
}

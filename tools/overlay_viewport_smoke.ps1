param(
    [string]$Serial = "",
    [string]$Package = "com.openlumen.debug",
    [string]$MainActivity = "com.openlumen.MainActivity",
    [string]$SecureActivity = "com.openlumen.OverlaySecureProbeActivity",
    [string]$InstallerApk = "app\build\outputs\apk\debug\app-debug.apk",
    [string]$OutputDir = "build\overlay-smoke"
)

$ErrorActionPreference = "Stop"

function Invoke-Adb {
    $adbArgs = @()
    if ($Serial) {
        $adbArgs += @("-s", $Serial)
    }
    $adbArgs += $args
    & adb @adbArgs
}

function Save-Text {
    param([string]$Path, [string[]]$Lines)
    $Lines | Set-Content -LiteralPath $Path -Encoding utf8
}

function Save-WindowDump {
    param([string]$Name)
    $path = Join-Path $OutputDir $Name
    Invoke-Adb shell dumpsys window | Set-Content -LiteralPath $path -Encoding utf8
    return $path
}

function Save-Logcat {
    param([string]$Name)
    $path = Join-Path $OutputDir $Name
    Invoke-Adb logcat -d -s OpenLumen/Overlay OpenLumen/LumenSvc WindowManager InputDispatcher |
        Set-Content -LiteralPath $path -Encoding utf8
    return $path
}

function Save-Screenshot {
    param([string]$Name)
    $path = Join-Path $OutputDir $Name
    $adbArgs = @()
    if ($Serial) {
        $adbArgs += @("-s", $Serial)
    }
    $adbArgs += @("exec-out", "screencap", "-p")
    & adb @adbArgs > $path
    return $path
}

function Get-UiXml {
    param([string]$Name)
    $remote = "/sdcard/openlumen-window.xml"
    $path = Join-Path $OutputDir $Name
    for ($attempt = 1; $attempt -le 4; $attempt++) {
        Invoke-Adb shell uiautomator dump $remote | Out-Null
        $content = (Invoke-Adb exec-out cat $remote) -join "`n"
        if ($content -match "<hierarchy") {
            $content | Set-Content -LiteralPath $path -Encoding utf8
            return [xml]$content
        }
        Start-Sleep -Milliseconds 750
    }
    "<hierarchy />" | Set-Content -LiteralPath $path -Encoding utf8
    return [xml]"<hierarchy />"
}

function Get-NodeCenter {
    param($Node)
    if (-not $Node) {
        return $null
    }
    $bounds = [string]$Node.bounds
    if ($bounds -notmatch "\[(\d+),(\d+)\]\[(\d+),(\d+)\]") {
        return $null
    }
    return @{
        X = [int](([int]$Matches[1] + [int]$Matches[3]) / 2)
        Y = [int](([int]$Matches[2] + [int]$Matches[4]) / 2)
    }
}

function Tap-NodeByText {
    param([xml]$Xml, [string]$Text, [switch]$Contains)
    $escaped = $Text.Replace("'", "&apos;")
    $query = if ($Contains) { "//*[contains(@text,'$escaped')]" } else { "//*[@text='$escaped']" }
    $node = $Xml.SelectSingleNode($query)
    $center = Get-NodeCenter $node
    if (-not $center) {
        return $false
    }
    Invoke-Adb shell input tap $center.X $center.Y | Out-Null
    Start-Sleep -Milliseconds 900
    return $true
}

function Test-WindowDump {
    param([string]$Path, [string]$Pattern)
    return (Select-String -LiteralPath $Path -Pattern $Pattern -Quiet)
}

function Get-ScreenSize {
    $match = (Invoke-Adb shell wm size | Select-String -Pattern "(\d+)x(\d+)").Matches[0]
    return @{
        Width = [int]$match.Groups[1].Value
        Height = [int]$match.Groups[2].Value
    }
}

function Tap-Fraction {
    param([double]$X, [double]$Y)
    $size = Get-ScreenSize
    Invoke-Adb shell input tap ([int]($size.Width * $X)) ([int]($size.Height * $Y)) | Out-Null
    Start-Sleep -Milliseconds 900
}

function Add-Result {
    param([string]$Name, [bool]$Passed, [string]$Detail)
    $script:Results += [pscustomobject]@{
        Check = $Name
        Result = if ($Passed) { "PASS" } else { "REVIEW" }
        Detail = $Detail
    }
}

New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null
$script:Results = @()

if (-not $Serial) {
    $devices = & adb devices | Select-String -Pattern "device$" | ForEach-Object { ($_ -split "\s+")[0] }
    if (@($devices).Count -ne 1) {
        throw "Specify -Serial when adb reports $(@($devices).Count) attached devices."
    }
    $Serial = @($devices)[0]
}

Invoke-Adb shell am force-stop com.android.settings | Out-Null
Invoke-Adb shell am force-stop com.google.android.packageinstaller | Out-Null
Invoke-Adb shell am force-stop $Package | Out-Null
Invoke-Adb shell input keyevent HOME | Out-Null
Start-Sleep -Seconds 1
Invoke-Adb shell appops set $Package SYSTEM_ALERT_WINDOW allow | Out-Null
Invoke-Adb shell pm grant $Package android.permission.POST_NOTIFICATIONS 2>$null | Out-Null
Invoke-Adb logcat -c | Out-Null
Invoke-Adb shell am start -n "$Package/$MainActivity" | Out-Null
Start-Sleep -Seconds 2

$xml = Get-UiXml "start.xml"
Tap-Fraction 0.70 0.93
$xml = Get-UiXml "driver.xml"
[void](Tap-NodeByText $xml "Overlay (rootless)")
$xml = Get-UiXml "driver-overlay.xml"

Tap-Fraction 0.30 0.93
$xml = Get-UiXml "schedule-before-active.xml"
Tap-Fraction 0.50 0.17
Start-Sleep -Milliseconds 900

$xml = Get-UiXml "schedule-always.xml"
Tap-Fraction 0.09 0.93
$xml = Get-UiXml "home.xml"
if (-not ($xml.OuterXml -like "*Filter is on*")) {
    Tap-Fraction 0.50 0.06
    Start-Sleep -Seconds 2
    $xml = Get-UiXml "home-after-toggle.xml"
}
Add-Result "filter-enabled" ($xml.OuterXml -like "*Filter is on*") "Home hierarchy captured."

$homeDump = Save-WindowDump "home-window.txt"
Save-Screenshot "home.png" | Out-Null
$overlayReady = (Test-WindowDump $homeDump "package=$Package appop=SYSTEM_ALERT_WINDOW") -and
    (Test-WindowDump $homeDump "ty=APPLICATION_OVERLAY") -and
    (Test-WindowDump $homeDump "mHasSurface=true")
Add-Result "overlay-window" $overlayReady $homeDump
Add-Result "system-bars" (Test-WindowDump $homeDump "ty=STATUS_BAR|ty=NAVIGATION_BAR|Taskbar") $homeDump

$oneHanded = (Invoke-Adb shell settings get secure one_handed_mode_enabled) -join ""
Add-Result "one-handed-setting" ($oneHanded -match "^(0|1)$") "secure one_handed_mode_enabled=$oneHanded"

Invoke-Adb shell am start -n "$Package/$SecureActivity" | Out-Null
Start-Sleep -Seconds 1
$secureDump = Save-WindowDump "secure-window.txt"
Save-Screenshot "secure.png" | Out-Null
$secureReady = (Test-WindowDump $secureDump $SecureActivity) -and
    (Test-WindowDump $secureDump "SECURE|FLAG_SECURE|pfl=.*SECURE") -and
    (Test-WindowDump $secureDump "package=$Package appop=SYSTEM_ALERT_WINDOW")
Add-Result "flag-secure" $secureReady $secureDump
$xml = Get-UiXml "secure-probe.xml"
[void](Tap-NodeByText $xml "Smoke input")
Start-Sleep -Seconds 1
$imeDump = Save-WindowDump "ime-window.txt"
Save-Screenshot "ime-dialog.png" | Out-Null
$imeReady = (Test-WindowDump $imeDump "ty=INPUT_METHOD") -and
    (Test-WindowDump $imeDump "mHasSurface=true") -and
    (Test-WindowDump $imeDump "package=$Package appop=SYSTEM_ALERT_WINDOW")
Add-Result "ime-open" $imeReady $imeDump
Invoke-Adb shell input keyevent BACK | Out-Null
Start-Sleep -Milliseconds 700
$imeClosedDump = Save-WindowDump "ime-closed-window.txt"
Add-Result "ime-close-captured" (Test-Path -LiteralPath $imeClosedDump) $imeClosedDump
Invoke-Adb shell input keyevent BACK | Out-Null
Start-Sleep -Milliseconds 700

$packageUri = "package:{0}" -f $Package
Invoke-Adb shell am start -a android.settings.action.MANAGE_OVERLAY_PERMISSION -d $packageUri | Out-Null
Start-Sleep -Seconds 2
$permissionDump = Save-WindowDump "permission-window.txt"
Save-Screenshot "permission.png" | Out-Null
$permissionSeen = Test-WindowDump $permissionDump "Window #.*(com\.android\.settings|permissioncontroller)|mFocusedApp=.*(settings|permissioncontroller)"
Add-Result "permission-surface" $permissionSeen $permissionDump
Invoke-Adb shell am force-stop com.android.settings | Out-Null
Invoke-Adb shell input keyevent HOME | Out-Null
Start-Sleep -Milliseconds 700

if (Test-Path -LiteralPath $InstallerApk) {
    Invoke-Adb push $InstallerApk /sdcard/Download/openlumen-smoke.apk | Out-Null
    $installerStart = Invoke-Adb shell am start --grant-read-uri-permission -a android.intent.action.VIEW -t application/vnd.android.package-archive -d content://com.android.shell.fileprovider/sdcard/Download/openlumen-smoke.apk 2>&1
    Start-Sleep -Seconds 2
    $installerDump = Save-WindowDump "installer-window.txt"
    Save-Screenshot "installer.png" | Out-Null
    $installerStartText = $installerStart -join " "
    $installerSeen = ($installerStartText -notmatch "Error:") -and
        (Test-WindowDump $installerDump "Window #.*(packageinstaller|PackageInstaller|permissioncontroller|com\.android\.settings)|mFocusedApp=.*(packageinstaller|permissioncontroller|settings)")
    Add-Result "installer-surface" $installerSeen "$installerDump; start=$installerStartText"
    Invoke-Adb shell am force-stop com.google.android.packageinstaller | Out-Null
} else {
    Add-Result "installer-surface" $false "$InstallerApk not found; build :app:assembleDebug first."
}

$logPath = Save-Logcat "overlay-logcat.txt"
$summaryPath = Join-Path $OutputDir "summary.txt"
$summary = @(
    "OpenLumen overlay viewport smoke",
    "serial=$Serial",
    "package=$Package",
    "output=$((Resolve-Path $OutputDir).Path)",
    ""
) + ($Results | ForEach-Object { "$($_.Result) $($_.Check): $($_.Detail)" }) + @(
    "",
    "logcat=$logPath"
)
Save-Text $summaryPath $summary
$summary | ForEach-Object { Write-Output $_ }

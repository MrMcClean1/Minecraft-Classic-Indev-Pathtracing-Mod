# Adds Classic PT to a MultiMC instance as its own component: the jar in the instance's libraries folder, a patch
# that puts it on the class path and passes --tweakClass to MCPHackers LaunchWrapper, and an entry in
# mmc-pack.json (backed up once as mmc-pack.json.before-classicpt). JVM arguments are not touched.
#   .\install.ps1 c0.30-s-1858      .\install.ps1 in-20100223      .\install.ps1 <instance> -Remove
param([Parameter(Mandatory)][string]$Instance, [string]$Version = '1.0.0', [switch]$Remove)
$ErrorActionPreference = 'Stop'
$inst = "%USERPROFILE%\Desktop\MultiMC\instances\$Instance"
if (-not (Test-Path "$inst\mmc-pack.json")) { throw "no instance $Instance" }
$uid = 'com.mrmcclean.classicpt'
$packPath = "$inst\mmc-pack.json"
if (-not (Test-Path "$packPath.before-classicpt")) { Copy-Item $packPath "$packPath.before-classicpt" }
$pack = Get-Content $packPath -Raw | ConvertFrom-Json
$pack.components = @($pack.components | Where-Object { $_.uid -ne $uid })
Remove-Item "$inst\patches\$uid.json", "$inst\libraries\classicpt-*.jar" -ErrorAction SilentlyContinue
if (-not $Remove) {
  New-Item -ItemType Directory -Force "$inst\libraries", "$inst\patches" | Out-Null
  Copy-Item "$PSScriptRoot\build\classicpt-$Version.jar" "$inst\libraries\classicpt-$Version.jar"
  @{
    formatVersion = 1; name = 'Classic PT (path tracer)'; uid = $uid; version = $Version; order = 10
    '+libraries' = @(@{ name = "com.mrmcclean:classicpt:$Version"; 'MMC-hint' = 'local' })
    '+tweakers' = @('com.mrmcclean.classicpt.tweak.ClassicPTTweak')
  } | ConvertTo-Json -Depth 5 | Set-Content "$inst\patches\$uid.json" -Encoding UTF8
  $pack.components += [pscustomobject]@{ uid = $uid; cachedName = 'Classic PT (path tracer)'; cachedVersion = $Version }
}
$pack | ConvertTo-Json -Depth 10 | Set-Content $packPath -Encoding UTF8
"$(if ($Remove) {'removed from'} else {'installed in'}) $Instance"

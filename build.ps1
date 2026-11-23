# Builds build/classicpt-<version>.jar with the JDK's javac for Java 8 (the instances run Java 8). No Gradle: the
# only libraries are LaunchWrapper, ASM and LWJGL 2, all already in MultiMC's library folder.
# The Vulkan side (vk/, NVIDIA DLSS, Ray Reconstruction, Frame Generation, Reflex) is built against LWJGL 3.3.6 from
# the Gradle cache (the Alpha/Beta/Tekkit builds put it there) and packed with LWJGL 3 as assets/classicpt/vk/*.bin;
# VkBridge loads it in a class loader of its own.
param([string]$Version = '1.0.0')
$ErrorActionPreference = 'Stop'
$jdk = 'C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot\bin'
$lib = '%USERPROFILE%\Desktop\MultiMC\libraries'
$cp = @("$lib\org\mcphackers\launchwrapper\1.3.0\launchwrapper-1.3.0.jar",
        "$lib\org\ow2\asm\asm\9.10.1\asm-9.10.1.jar",
        "$lib\org\ow2\asm\asm-tree\9.10.1\asm-tree-9.10.1.jar",
        "$lib\org\lwjgl\lwjgl\lwjgl\2.9.4-nightly-20150209\lwjgl-2.9.4-nightly-20150209.jar") -join ';'
$root = $PSScriptRoot
Remove-Item -Recurse -Force "$root\build\classes" -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force "$root\build\classes" | Out-Null
$src = Get-ChildItem -Recurse "$root\src" -Filter *.java | ForEach-Object FullName
& "$jdk\javac.exe" --release 8 -Xlint:-options -encoding UTF-8 -cp $cp -d "$root\build\classes" $src
if ($LASTEXITCODE -ne 0) { throw 'javac failed' }
Copy-Item -Recurse "$root\src\assets" "$root\build\classes\assets"

$g = "$env:USERPROFILE\.gradle\caches\modules-2\files-2.1\org.lwjgl"
function Find-Lwjgl3([string]$name, [string]$file) {
  $f = Get-ChildItem -Recurse "$g\$name\3.3.6" -Filter $file -ErrorAction SilentlyContinue | Select-Object -First 1
  if (-not $f) { throw "LWJGL 3.3.6 $file not in the Gradle cache (build alphapt or betapt once)" }
  $f.FullName
}
$l3 = [ordered]@{
  'lwjgl' = Find-Lwjgl3 'lwjgl' 'lwjgl-3.3.6.jar'
  'lwjgl-natives-windows' = Find-Lwjgl3 'lwjgl' 'lwjgl-3.3.6-natives-windows.jar'
  'lwjgl-vulkan' = Find-Lwjgl3 'lwjgl-vulkan' 'lwjgl-vulkan-3.3.6.jar'
  'lwjgl-opengl' = Find-Lwjgl3 'lwjgl-opengl' 'lwjgl-opengl-3.3.6.jar'
  'lwjgl-opengl-natives-windows' = Find-Lwjgl3 'lwjgl-opengl' 'lwjgl-opengl-3.3.6-natives-windows.jar'
}
$vkClasses = "$root\build\vkclasses"
Remove-Item -Recurse -Force $vkClasses -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force $vkClasses | Out-Null
$vkSrc = Get-ChildItem -Recurse "$root\vk" -Filter *.java | ForEach-Object FullName
& "$jdk\javac.exe" --release 8 -Xlint:-options -encoding UTF-8 -cp (($l3['lwjgl'], $l3['lwjgl-vulkan'], $l3['lwjgl-opengl']) -join ';') -d $vkClasses $vkSrc
if ($LASTEXITCODE -ne 0) { throw 'javac (vk) failed' }
$vkOut = "$root\build\classes\assets\classicpt\vk"
New-Item -ItemType Directory -Force $vkOut | Out-Null
& "$jdk\jar.exe" --create --file "$vkOut\classicpt-vk.bin" -C $vkClasses .
if ($LASTEXITCODE -ne 0) { throw 'jar (vk) failed' }
foreach ($k in $l3.Keys) { Copy-Item $l3[$k] "$vkOut\$k.bin" }
# Classic Bots (Alpha PT's bots, shared sources in bots\src from bots\sync.ps1): compiled against the game jar with
# Alpha's names (tools\Remap.java named + bots\<game>.mappings), then remapped back to the game's own names.
$asm = "$lib\org\ow2\asm"
$remapCp = (("$asm\asm\9.10.1\asm-9.10.1.jar", "$asm\asm-commons\9.10.1\asm-commons-9.10.1.jar", "$asm\asm-tree\9.10.1\asm-tree-9.10.1.jar") -join ';')
$lwjgl2 = "$lib\org\lwjgl\lwjgl\lwjgl\2.9.4-nightly-20150209\lwjgl-2.9.4-nightly-20150209.jar"
foreach ($g in @(@{ name = 'indev'; jar = "$lib\com\mojang\minecraft\in-20100223\minecraft-in-20100223-client.jar" },
                 @{ name = 'classic'; jar = "$lib\com\mojang\minecraft\c0.30-s-1858\minecraft-c0.30-s-1858-client.jar" })) {
  $n = $g.name
  $named = "$root\build\$n-named.jar"
  & "$jdk\java.exe" -cp $remapCp "$root\tools\Remap.java" named $g.jar "$root\bots\$n.mappings" $named 2>$null
  if ($LASTEXITCODE -ne 0) { throw "remap ($n, named) failed" }
  $bsrc = "$root\build\bots-$n-src"; $bcls = "$root\build\bots-$n-classes"
  Remove-Item -Recurse -Force $bsrc, $bcls -ErrorAction SilentlyContinue
  New-Item -ItemType Directory -Force $bsrc, $bcls | Out-Null
  # Alpha's shared files, then the bots' own for both games (bots\common), then this game's (a same-named file wins)
  foreach ($f in @(Get-ChildItem "$root\bots\src" -Recurse -Filter *.java) + @(Get-ChildItem "$root\bots\common" -Filter *.java) + @(Get-ChildItem "$root\bots\$n" -Recurse -Filter *.java)) { # with bots\<game>\shim (Alpha classes the game lacks)
    $t = [IO.File]::ReadAllText($f.FullName).Replace('package com.mrmcclean.classicpt.bots;', "package com.mrmcclean.classicpt.bots.$n;")
    [IO.File]::WriteAllText("$bsrc\$($f.Name)", $t) # a per-game file of the same name replaces the shared one
  }
  & "$jdk\javac.exe" --release 8 -nowarn -Xlint:-options -encoding UTF-8 -cp "$named;$lwjgl2" -d $bcls (Get-ChildItem $bsrc -Filter *.java | ForEach-Object FullName)
  if ($LASTEXITCODE -ne 0) { throw "javac (bots, $n) failed" }
  & "$jdk\java.exe" -cp $remapCp "$root\tools\Remap.java" back $g.jar "$root\bots\$n.mappings" $bcls "com/mrmcclean/classicpt/bots/$n/Compat" 2>$null
  if ($LASTEXITCODE -ne 0) { throw "remap ($n, back) failed" }
  Copy-Item -Recurse "$bcls\com" "$root\build\classes\" -Force
  # Classic PT's settings screen (gui\, the game's own screen class): built the same way, package gui.<game>
  $gsrc = "$root\build\gui-$n-src"; $gcls = "$root\build\gui-$n-classes"
  Remove-Item -Recurse -Force $gsrc, $gcls -ErrorAction SilentlyContinue
  New-Item -ItemType Directory -Force $gsrc, $gcls | Out-Null
  foreach ($f in Get-ChildItem "$root\gui" -Filter *.java) {
    $t = [IO.File]::ReadAllText($f.FullName).Replace('package com.mrmcclean.classicpt.gui;', "package com.mrmcclean.classicpt.gui.$n;")
    [IO.File]::WriteAllText("$gsrc\$($f.Name)", $t)
  }
  & "$jdk\javac.exe" --release 8 -nowarn -Xlint:-options -encoding UTF-8 -cp "$named;$lwjgl2;$root\build\classes" -d $gcls (Get-ChildItem $gsrc -Filter *.java | ForEach-Object FullName)
  if ($LASTEXITCODE -ne 0) { throw "javac (gui, $n) failed" }
  & "$jdk\java.exe" -cp $remapCp "$root\tools\Remap.java" back $g.jar "$root\bots\$n.mappings" $gcls "com/mrmcclean/classicpt/gui/$n/Compat"
  if ($LASTEXITCODE -ne 0) { throw "remap ($n, gui back) failed" }
  Copy-Item -Recurse "$gcls\com" "$root\build\classes\" -Force
}

$jar = "$root\build\classicpt-$Version.jar"
& "$jdk\jar.exe" --create --file $jar -C "$root\build\classes" .
if ($LASTEXITCODE -ne 0) { throw 'jar failed' }
"built $jar"

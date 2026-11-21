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
$jar = "$root\build\classicpt-$Version.jar"
& "$jdk\jar.exe" --create --file $jar -C "$root\build\classes" .
if ($LASTEXITCODE -ne 0) { throw 'jar failed' }
"built $jar"

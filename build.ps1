# Builds build/classicpt-<version>.jar with the JDK's javac for Java 8 (the instances run Java 8). No Gradle: the
# only libraries are LaunchWrapper, ASM and LWJGL 2, all already in MultiMC's library folder.
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
$jar = "$root\build\classicpt-$Version.jar"
& "$jdk\jar.exe" --create --file $jar -C "$root\build\classes" .
if ($LASTEXITCODE -ne 0) { throw 'jar failed' }
"built $jar"

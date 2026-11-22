# Copies the Classic Bots shared with Alpha PT (and through it Beta PT and the modpack) into bots\src, package renamed.
# Run after the bots change in alphapt; then .\build.ps1. Per-game files (Bots, BotsClient, Names, BotRenderer, BotTest,
# Compat, chat) live in bots\indev and bots\classic and are not touched. The few lines that reach Fabric are rewritten.
param([string]$From = 'C:\MinecraftBetaRaytracing\alphapt\src\main\java\com\mrmcclean\betapt\bots')
$ErrorActionPreference = 'Stop'
$to = "$PSScriptRoot\src\com\mrmcclean\classicpt\bots"
$shared = 'Blueprint', 'BotConfig', 'BotEntity', 'Brain', 'MachineDoctor', 'Model', 'Nav', 'Orders', 'TestQuiet', 'Town', 'Voice', 'VoiceChat', 'Guide', 'Want'
New-Item -ItemType Directory -Force $to | Out-Null
foreach ($n in $shared) {
  $t = [IO.File]::ReadAllText("$From\$n.java")
  $t = $t.Replace('com.mrmcclean.betapt.bots', 'com.mrmcclean.classicpt.bots')
  # Fabric's game folder: the game runs in .minecraft here
  $t = $t.Replace('net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("classicbots-test").resolve("debug").toFile().isFile()',
                  'new java.io.File("classicbots-test", "debug").isFile()')
  # a stub can't stand in for a constructor: Alpha's ItemEntity(World, double x3, ItemStack) is made by Compat
  $t = $t.Replace('new ItemEntity(', 'Compat.newItemEntity(')
  # Classic has no crafting: each game's Names says whether a bot may craft
  $t = $t.Replace('    boolean craft(int out, int outCount, int[] needs) {', "    boolean craft(int out, int outCount, int[] needs) {`n        if (!Names.CRAFTING) return false;")
  [IO.File]::WriteAllText("$to\$n.java", $t)
}
"synced $($shared.Count) files from $From"

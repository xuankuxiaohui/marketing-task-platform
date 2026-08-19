# design §7.10: @Disabled / assumeTrue / skip tags forbidden in *IT / *PropertyTest / *ArchTest
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$files = Get-ChildItem -Path (Join-Path $root "server") -Recurse -File |
    Where-Object { $_.Name -match '(IT|PropertyTest|ArchTest)\.java$' }
$pattern = '@Disabled|assumeTrue|@Tag\s*\(\s*"(skip|wip|disabled|ignore)"'
$hits = @()
foreach ($f in $files) {
    $hits += Select-String -Path $f.FullName -Pattern $pattern
}
if ($hits.Count -gt 0) {
    $hits | ForEach-Object { Write-Error $_.Line }
    throw "§7.10: @Disabled / assumeTrue / skip @Tag forbidden in *IT / *PropertyTest / *ArchTest"
}

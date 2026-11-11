# Prepare the shipped launcher scripts.
#
#   powershell -NoProfile -ExecutionPolicy Bypass -File _prepare.ps1 -Src <templates> -Dst <dist dir>
#
# Masters in dist-templates are kept UTF-8 with LF endings (easy to edit).
# For cmd.exe the files must be written as:
#   * GBK (codepage 936) - cmd reads batch files with the OEM codepage, so
#     UTF-8 Chinese text gets mis-decoded and swallows following bytes
#   * CRLF line endings - with LF only, cmd loses sync and drops characters
#     from commands (netstat becomes etstat)
# Do not edit the generated copies in the dist folder - edit dist-templates.
param(
    [Parameter(Mandatory = $true)][string]$Src,
    [Parameter(Mandatory = $true)][string]$Dst
)

$ErrorActionPreference = 'Stop'

$gbk  = [System.Text.Encoding]::GetEncoding(936)
$crlf = [string][char]13 + [string][char]10
$count = 0

Get-ChildItem (Join-Path $Src '*.bat') -File | ForEach-Object {
    $text = [System.IO.File]::ReadAllText($_.FullName, [System.Text.Encoding]::UTF8)
    $text = $text -replace ([string][char]13 + '?' + [string][char]10), $crlf
    [System.IO.File]::WriteAllText((Join-Path $Dst $_.Name), $text, $gbk)
    $count++
}

$helper = Join-Path $Src '_hidden.ps1'
if (Test-Path $helper) { Copy-Item -Path $helper -Destination (Join-Path $Dst '_hidden.ps1') -Force }

Write-Output ("prepared " + $count + " bat file(s) as GBK + CRLF, helper copied")
if ($count -lt 2) { exit 1 }

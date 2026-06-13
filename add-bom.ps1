$utf8 = New-Object System.Text.UTF8Encoding $true
$path = Join-Path (Split-Path -Parent $MyInvocation.MyCommand.Path) 'start.ps1'
$content = Get-Content $path -Raw -Encoding UTF8
[System.IO.File]::WriteAllText($path, $content, $utf8)

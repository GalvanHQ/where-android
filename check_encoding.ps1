Get-ChildItem -Path 'app\src\main\java\com\ovi\where\domain\model' -Filter '*.kt' -Recurse | ForEach-Object {
    $c = Get-Content $_.FullName -Raw
    $o = ([regex]::Matches($c, '/\*')).Count
    $cl = ([regex]::Matches($c, '\*/')).Count
    if ($o -ne $cl) {
        Write-Output ($_.Name + ' open=' + $o + ' close=' + $cl)
    }
}

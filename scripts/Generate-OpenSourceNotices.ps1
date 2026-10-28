param([string]$Project = (Split-Path -Parent $PSScriptRoot))
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$cache = Join-Path $env:USERPROFILE '.gradle/caches/modules-2/files-2.1'
$modules = Get-Content -LiteralPath (Join-Path $Project 'app/build/reports/runtime-modules.tsv')
$apache = (Invoke-WebRequest 'https://www.apache.org/licenses/LICENSE-2.0.txt').Content
$miuix = (Invoke-WebRequest 'https://raw.githubusercontent.com/compose-miuix-ui/miuix/main/LICENSE').Content
$material = (Invoke-WebRequest 'https://raw.githubusercontent.com/jordond/materialkolor/main/LICENSE').Content
$groups = [ordered]@{}
$projectUrls = @{
    'AndroidX · Jetpack Compose' = 'https://github.com/androidx/androidx'
    'Protocol Buffers' = 'https://github.com/protocolbuffers/protobuf'
    'Guava · ListenableFuture' = 'https://github.com/google/guava'
    'MaterialKolor' = 'https://github.com/jordond/MaterialKolor'
    'Okio' = 'https://github.com/square/okio'
    'Poko' = 'https://github.com/drewhamilton/Poko'
    'JetBrains · Kotlin / Compose Multiplatform' = 'https://github.com/JetBrains/compose-multiplatform'
    'JSpecify' = 'https://github.com/jspecify/jspecify'
    'Miuix' = 'https://github.com/compose-miuix-ui/miuix'
}
foreach($line in $modules) {
    $part = $line -split "`t"
    $coordinate = $part -join ':'
    $path = Join-Path $cache ($part -join '/')
    $files = @(Get-ChildItem -LiteralPath $path -Recurse -File)
    $pom = $files | Where-Object Extension -eq '.pom' | Select-Object -First 1
    [xml]$metadata = Get-Content -LiteralPath $pom.FullName
    $license = @($metadata.project.licenses.license | ForEach-Object { $_.name }) -join ', '
    $body = $apache; $spdx = 'Apache-2.0'
    $name = switch -Regex ($part[0]) {
        '^top\.yukonga' { $body = $miuix; 'Miuix'; break }
        '^com\.materialkolor' { $body = $material; $spdx = 'MIT'; 'MaterialKolor'; break }
        '^androidx\.datastore$' { if($part[1] -eq 'datastore-preferences-external-protobuf') { $spdx = 'BSD-3-Clause'; 'Protocol Buffers'; break }; 'AndroidX · Jetpack Compose'; break }
        '^androidx\.' { 'AndroidX · Jetpack Compose'; break }
        '^org\.jetbrains' { 'JetBrains · Kotlin / Compose Multiplatform'; break }
        '^com\.squareup' { 'Okio'; break }
        '^dev\.drewhamilton' { 'Poko'; break }
        '^org\.jspecify' { 'JSpecify'; break }
        '^com\.google\.guava' { 'Guava · ListenableFuture'; break }
        default { throw "Unclassified runtime dependency: $coordinate" }
    }
    if($license -and $license -notmatch 'Apache|apache|MIT|BSD-3') { throw "Unknown license: $coordinate $license" }
    $embedded = @()
    foreach($file in ($files | Where-Object { $_.Extension -in @('.jar','.aar') -and $_.Name -notmatch 'sources|javadoc' })) {
        $zip = [IO.Compression.ZipFile]::OpenRead($file.FullName)
        try {
            foreach($entry in $zip.Entries | Where-Object { $_.FullName -match '(?i)(^|/)(LICENSE|NOTICE|COPYING)(\.[^/]*)?$' -and $_.Length -gt 0 }) {
                $reader = [IO.StreamReader]::new($entry.Open())
                try { $embedded += $reader.ReadToEnd() } finally { $reader.Dispose() }
            }
        } finally { $zip.Dispose() }
    }
    if($spdx -eq 'BSD-3-Clause') {
        if(-not $embedded.Count) { throw 'Missing Protocol Buffers license text' }
        $body = $embedded -join "`n`n"
        $embedded = @()
    }
    if(-not $groups.Contains($name)) { $groups[$name] = @{ name=$name; license=$spdx; modules=@(); notices=@(); body=$body } }
    $groups[$name].modules += "$coordinate`n$($metadata.project.url)"
    $groups[$name].notices += $embedded
}
$result = foreach($group in $groups.Values) {
    $parts = @(($group.modules -join "`n"), [string]$group.body)
    $parts += @($group.notices | ForEach-Object { $_.Replace("`r", '').Trim() } | Select-Object -Unique)
    if(-not $projectUrls.ContainsKey($group.name)) { throw "Missing project URL: $($group.name)" }
    [ordered]@{ name=$group.name; license=$group.license; url=$projectUrls[$group.name]; text=($parts -join "`n`n") }
}
$applicationNotice = [ordered]@{
    name = 'Spectra'
    license = 'AGPL-3.0'
    url = 'https://github.com/Redlnn/Spectra'
    text = Get-Content -LiteralPath (Join-Path $Project 'LICENSE') -Raw
}
$result = @($applicationNotice) + @($result)
$directory = Join-Path $Project 'app/src/main/assets'
New-Item -ItemType Directory -Force -Path $directory | Out-Null
[IO.File]::WriteAllText((Join-Path $directory 'open_source_notices.json'), (ConvertTo-Json -InputObject @($result) -Depth 4), [Text.UTF8Encoding]::new($false))
Write-Output "Generated the project license and $($groups.Count) dependency groups for $($modules.Count) runtime modules."

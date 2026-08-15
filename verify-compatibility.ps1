[CmdletBinding()]
param(
	[string[]]$MinecraftVersions = @(
		'26.1',
		'26.1.1',
		'26.1.2',
		'26.3-snapshot-1',
		'26.3-snapshot-2',
		'26.3-snapshot-3',
		'26.3-snapshot-4',
		'26.3-snapshot-5',
		'26.3-snapshot-6',
		'26.3-snapshot-7',
		'26.3-snapshot-8',
		'26.2'
	)
)

$projectJdk = Get-ChildItem -LiteralPath "$PSScriptRoot\.toolchains\jdk25" -Directory -ErrorAction SilentlyContinue |
	Select-Object -First 1

if ($null -ne $projectJdk) {
	$env:JAVA_HOME = $projectJdk.FullName
}

foreach ($minecraftVersion in $MinecraftVersions) {
	Write-Host "Verifying Minecraft $minecraftVersion"
	& "$PSScriptRoot\gradlew.bat" clean build --no-daemon "-Pminecraft_version=$minecraftVersion"
	if ($LASTEXITCODE -ne 0) {
		Write-Error "Compatibility build failed for Minecraft $minecraftVersion"
		exit $LASTEXITCODE
	}
}

Write-Host "Compatibility matrix passed: $($MinecraftVersions -join ', ')"

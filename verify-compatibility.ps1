[CmdletBinding()]
param(
	[string[]]$MinecraftVersions = @(),
	[string]$LoaderVersion = '0.19.5'
)

$ErrorActionPreference = 'Stop'
$projectJdk = Get-ChildItem -LiteralPath "$PSScriptRoot\.toolchains\jdk25" -Directory -ErrorAction SilentlyContinue |
	Select-Object -First 1

if ($null -ne $projectJdk) {
	$env:JAVA_HOME = $projectJdk.FullName
}

if ($MinecraftVersions.Count -eq 0) {
	$metadata = Get-Content -LiteralPath "$PSScriptRoot\src\main\resources\fabric.mod.json" -Raw | ConvertFrom-Json
	$MinecraftVersions = @($metadata.custom.'hangul:tested_game_versions')
}

Push-Location -LiteralPath $PSScriptRoot
try {
	# Compile once on the stable target; every runtime check uses this exact JAR.
	& .\gradlew.bat clean stageCompatibilityJar --no-daemon "-Ploader_version=$LoaderVersion"
	if ($LASTEXITCODE -ne 0) {
		throw 'Stable candidate build failed.'
	}
	$candidate = Join-Path $PSScriptRoot 'build\compatibility\hangul.jar'
	$candidateHash = (Get-FileHash -LiteralPath $candidate -Algorithm SHA256).Hash
	foreach ($minecraftVersion in $MinecraftVersions) {
		Write-Host "Verifying the same candidate on Minecraft $minecraftVersion / Loader $LoaderVersion"
		& .\gradlew.bat verifyMixinApplication --no-daemon "-Pminecraft_version=$minecraftVersion" "-Ploader_version=$LoaderVersion" "-PcompatibilityJar=$candidate"
		if ($LASTEXITCODE -ne 0) {
			throw "Compatibility verification failed for Minecraft $minecraftVersion"
		}
		if ((Get-FileHash -LiteralPath $candidate -Algorithm SHA256).Hash -ne $candidateHash) {
			throw 'Candidate JAR changed during verification.'
		}
	}
	Write-Host "Compatibility matrix passed: $($MinecraftVersions -join ', ')"
	Write-Host "Candidate SHA-256: $candidateHash"
} finally {
	Pop-Location
}

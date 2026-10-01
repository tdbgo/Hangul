[CmdletBinding()]
param(
	[switch]$FullMatrix,
	[ValidateSet('fabric', 'neoforge', 'forge')]
	[string[]]$Platforms = @('fabric', 'neoforge', 'forge')
)

$ErrorActionPreference = 'Stop'
Push-Location -LiteralPath $PSScriptRoot
try {
	function Invoke-CheckedGradle([string[]]$Arguments) {
		& .\gradlew.bat @Arguments --no-daemon
		if ($LASTEXITCODE -ne 0) { throw "Gradle failed: $($Arguments -join ' ')" }
	}
	if ($Platforms.Count -ne @($Platforms | Select-Object -Unique).Count) { throw 'Duplicate platform selection' }
	$projectJdk = Get-ChildItem -LiteralPath "$PSScriptRoot/.toolchains/jdk25" -Directory -ErrorAction SilentlyContinue | Select-Object -First 1
	if ($null -ne $projectJdk) { $env:JAVA_HOME = $projectJdk.FullName }
	$candidates = Join-Path $PSScriptRoot 'build/compatibility'
	$artifactPaths = @()
	$fabricCandidate = Join-Path $candidates 'hangul.jar'
	if ('fabric' -in $Platforms) {
		Invoke-CheckedGradle -Arguments @('clean', 'stageCompatibilityJar', 'verifyQuiltApplication')
		$fabricHash = (Get-FileHash -LiteralPath $fabricCandidate -Algorithm SHA256).Hash
		$artifactPaths += $fabricCandidate
	}
	function Assert-FabricCandidate {
		if ((Get-FileHash -LiteralPath $fabricCandidate -Algorithm SHA256).Hash -ne $fabricHash) {
			throw 'Fabric/Quilt candidate changed during verification'
		}
	}
	$matrix = Get-Content -Raw platform-matrix.json | ConvertFrom-Json
	foreach ($platform in @('neoforge', 'forge') | Where-Object { $_ -in $Platforms }) {
		Invoke-CheckedGradle -Arguments @("-Pplatform=$platform", ":${platform}:clean", ":${platform}:build")
		$jar = @(Get-ChildItem "platforms/$platform/build/libs/*-$platform.jar")
		if ($jar.Count -ne 1) { throw "Expected one $platform release JAR" }
		$candidate = Join-Path $candidates "hangul-$platform.jar"
		New-Item -ItemType Directory -Path $candidates -Force | Out-Null
		Copy-Item -LiteralPath $jar[0].FullName -Destination $candidate
		$artifactPaths += $candidate
		$hash = (Get-FileHash -LiteralPath $candidate -Algorithm SHA256).Hash
		if ($FullMatrix) {
			foreach ($target in $matrix.$platform) {
				$property = if ($platform -eq 'neoforge') { 'neo_version' } else { 'forge_version' }
				$task = if ($platform -eq 'neoforge') { 'runVerification' } else { 'runVerificationClient' }
				Invoke-CheckedGradle -Arguments @("-Pplatform=$platform", ":${platform}:$task", "-P${property}=$($target.loader)", "-PcompatibilityJar=$candidate")
				if ((Get-FileHash -LiteralPath $candidate -Algorithm SHA256).Hash -ne $hash) {
					throw "$platform candidate changed during verification"
				}
			}
		}
	}
	if ($FullMatrix -and 'fabric' -in $Platforms) {
		$metadata = Get-Content -Raw src/main/resources/fabric.mod.json | ConvertFrom-Json
		foreach ($target in $metadata.custom.'hangul:tested_game_versions') {
			Invoke-CheckedGradle -Arguments @('verifyMixinApplication', 'verifyQuiltApplication', "-Pminecraft_version=$target", "-PcompatibilityJar=$candidates/hangul.jar")
			Assert-FabricCandidate
		}
		$minimumLoader = (Select-String -Path gradle.properties -Pattern '^loader_api_version=(.+)$').Matches.Groups[1].Value
		if (!$minimumLoader) { throw 'Missing minimum Fabric Loader version' }
		foreach ($target in @('26.1', '26.2', '26.3-pre-2', '26.3', '26.4-snapshot-1', '26.4-snapshot-2')) {
			Invoke-CheckedGradle -Arguments @('verifyMixinApplication', "-Pminecraft_version=$target", "-Ploader_version=$minimumLoader", "-PcompatibilityJar=$fabricCandidate")
			Assert-FabricCandidate
		}
	}
	& python scripts/verify-artifacts.py @artifactPaths
	if ($LASTEXITCODE -ne 0) { throw 'Release packaging checks failed' }
} finally {
	Pop-Location
}

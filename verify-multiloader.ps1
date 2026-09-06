[CmdletBinding()]
param([switch]$FullMatrix)

$ErrorActionPreference = 'Stop'
Push-Location -LiteralPath $PSScriptRoot
try {
	function Invoke-CheckedGradle([string[]]$Arguments) {
		& .\gradlew.bat @Arguments --no-daemon
		if ($LASTEXITCODE -ne 0) { throw "Gradle failed: $($Arguments -join ' ')" }
	}
	Invoke-CheckedGradle -Arguments @('clean', 'stageCompatibilityJar', 'verifyQuiltApplication')
	$candidates = Join-Path $PSScriptRoot 'build/compatibility'
	$fabricCandidate = Join-Path $candidates 'hangul.jar'
	$fabricHash = (Get-FileHash -LiteralPath $fabricCandidate -Algorithm SHA256).Hash
	function Assert-FabricCandidate {
		if ((Get-FileHash -LiteralPath $fabricCandidate -Algorithm SHA256).Hash -ne $fabricHash) {
			throw 'Fabric/Quilt candidate changed during verification'
		}
	}
	$matrix = Get-Content -Raw platform-matrix.json | ConvertFrom-Json
	foreach ($platform in @('neoforge', 'forge')) {
		Invoke-CheckedGradle -Arguments @("-Pplatform=$platform", ":${platform}:clean", ":${platform}:build")
		$jar = @(Get-ChildItem "platforms/$platform/build/libs/*-$platform.jar")
		if ($jar.Count -ne 1) { throw "Expected one $platform release JAR" }
		$candidate = Join-Path $candidates "hangul-$platform.jar"
		Copy-Item -LiteralPath $jar[0].FullName -Destination $candidate
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
	if ($FullMatrix) {
		$metadata = Get-Content -Raw src/main/resources/fabric.mod.json | ConvertFrom-Json
		foreach ($target in $metadata.custom.'hangul:tested_game_versions') {
			Invoke-CheckedGradle -Arguments @('verifyMixinApplication', 'verifyQuiltApplication', "-Pminecraft_version=$target", "-PcompatibilityJar=$candidates/hangul.jar")
			Assert-FabricCandidate
		}
		$minimumLoader = (Select-String -Path gradle.properties -Pattern '^loader_api_version=(.+)$').Matches.Groups[1].Value
		if (!$minimumLoader) { throw 'Missing minimum Fabric Loader version' }
		foreach ($target in @('26.1', '26.2', '26.3-pre-2')) {
			Invoke-CheckedGradle -Arguments @('verifyMixinApplication', "-Pminecraft_version=$target", "-Ploader_version=$minimumLoader", "-PcompatibilityJar=$fabricCandidate")
			Assert-FabricCandidate
		}
	}
	& python scripts/verify-artifacts.py "$candidates/hangul.jar" "$candidates/hangul-neoforge.jar" "$candidates/hangul-forge.jar"
	if ($LASTEXITCODE -ne 0) { throw 'Release packaging checks failed' }
} finally {
	Pop-Location
}

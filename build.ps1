$projectJdk = Get-ChildItem -LiteralPath "$PSScriptRoot\.toolchains\jdk25" -Directory -ErrorAction SilentlyContinue |
	Select-Object -First 1

if ($null -ne $projectJdk) {
	$env:JAVA_HOME = $projectJdk.FullName
}

& "$PSScriptRoot\gradlew.bat" clean build --no-daemon
exit $LASTEXITCODE

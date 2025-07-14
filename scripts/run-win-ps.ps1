# Check if java command exists
if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    Write-Host "Java is not installed."
    exit 1
}

# Get Java version string
$javaVersionOutput = & java -version 2>&1
$versionLine = $javaVersionOutput | Select-Object -First 1

# Extract version number using regex
if ($versionLine -match '"([\d._]+)"') {
    $version = $matches[1]
} else {
    Write-Host "Cannot parse Java version."
    exit 1
}

# Get major version
$major = $version.Split('.')[0]

# Handle versions like 1.8.x
if ($major -eq '1') {
    $major = $version.Split('.')[1]
}

# Check if major version is 21
if ($major -eq '21') {
    Write-Host "Java 21 is installed."
    & .\mvnw.cmd clean javafx:run
} else {
    Write-Host "Java 21 is not installed. Current version: $version"
    exit 1
}

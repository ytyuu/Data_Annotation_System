# Setup Maven environment and build
$env:JAVA_HOME = "C:\Program Files\Java\jdk-25.0.3"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

# Download Maven 3.9.6 if not present
$mavenDir = "C:\maven-3.9.6"
if (-not (Test-Path $mavenDir)) {
    Write-Host "Downloading Maven 3.9.6..." -ForegroundColor Cyan
    $mavenZip = "$env:temp\apache-maven-3.9.6-bin.zip"
    $url = "https://archive.apache.org/dist/maven/maven-3/3.9.6/binaries/apache-maven-3.9.6-bin.zip"

    try {
        [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
        Invoke-WebRequest -Uri $url -OutFile $mavenZip -UseBasicParsing
        Write-Host "Extracting Maven..." -ForegroundColor Cyan
        Expand-Archive -Path $mavenZip -DestinationPath "C:\" -Force
        Rename-Item -Path "C:\apache-maven-3.9.6" -NewName "C:\maven-3.9.6" -Force
        Remove-Item $mavenZip
        Write-Host "Maven installed successfully" -ForegroundColor Green
    } catch {
        Write-Host "Failed to download Maven: $_" -ForegroundColor Red
        exit 1
    }
}

$env:M2_HOME = $mavenDir
$env:PATH = "$mavenDir\bin;$env:PATH"

# Verify Maven and Java
Write-Host "Java version:" -ForegroundColor Yellow
& "$env:JAVA_HOME\bin\java.exe" -version
Write-Host ""
Write-Host "Maven version:" -ForegroundColor Yellow
& "$mavenDir\bin\mvn.cmd" -v

# Initialize Maven wrapper
Write-Host "Initializing Maven Wrapper..." -ForegroundColor Cyan
cd "C:\Code\数据标注网站\期末作业版"
& "$mavenDir\bin\mvn.cmd" -N io.takari:maven:wrapper@3.2.0 -Dmaven=3.9.6

Write-Host "Setup complete!" -ForegroundColor Green


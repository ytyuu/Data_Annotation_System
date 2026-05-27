# Maven Wrapper Bootstrap Script (PowerShell)
# 运行此脚本以初始化 Maven Wrapper

# 确保 Maven 已安装并在 PATH 中
if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
    Write-Host "Error: Maven is not installed or not in PATH" -ForegroundColor Red
    exit 1
}

Write-Host "Generating Maven Wrapper files..." -ForegroundColor Green

# 使用 Maven wrapper 插件原位初始化
mvn -N io.takari:maven:wrapper@3.2.0 -Dmaven=3.9.6

Write-Host "Maven Wrapper bootstrapped successfully!" -ForegroundColor Green
Write-Host "You can now use ./mvnw or mvnw.bat instead of 'mvn'" -ForegroundColor Cyan


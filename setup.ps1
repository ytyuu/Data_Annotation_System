# Project Setup Script (PowerShell)
# 为开发环境设置项目

Write-Host "======================================" -ForegroundColor Cyan
Write-Host "Javalin + Kotlin + Java 25 项目初始化" -ForegroundColor Cyan
Write-Host "======================================" -ForegroundColor Cyan
Write-Host ""

# 步骤 1：检查 Maven
Write-Host "[1/3] 检查 Maven 环装..." -ForegroundColor Yellow
if (Get-Command mvn -ErrorAction SilentlyContinue) {
    $mvnVersion = mvn -v | Select-Object -First 1
    Write-Host "✓ Maven 已安装: $mvnVersion" -ForegroundColor Green
    $hasMaven = $true
} else {
    Write-Host "⚠ Maven 未在 PATH 中，将跳过 Wrapper 初始化" -ForegroundColor Yellow
    $hasMaven = $false
}
Write-Host ""

# 步骤 2：检查 Java
Write-Host "[2/3] 检查 Java..." -ForegroundColor Yellow
if (Get-Command java -ErrorAction SilentlyContinue) {
    $javaVersion = java -version 2>&1 | Select-Object -First 1
    Write-Host "✓ Java 已安装: $javaVersion" -ForegroundColor Green
} else {
    Write-Host "⚠ 警告：Java 未在 PATH 中，请确保 JAVA_HOME 正确设置" -ForegroundColor Yellow
}
Write-Host ""

# 步骤 3：初始化 Maven Wrapper （如果有 Maven)
Write-Host "[3/3] 初始化项目..." -ForegroundColor Yellow
if ($hasMaven -and -not (Test-Path ".mvn/wrapper/maven-wrapper.jar")) {
    Write-Host "初始化 Maven Wrapper..." -ForegroundColor Cyan
    mvn -N io.takari:maven:wrapper@3.2.0 -Dmaven=3.9.6 -q
    if ($LASTEXITCODE -eq 0) {
        Write-Host "✓ Maven Wrapper 初始化成功" -ForegroundColor Green
    } else {
        Write-Host "✗ Maven Wrapper 初始化失败" -ForegroundColor Red
    }
} elseif (Test-Path ".mvn/wrapper/maven-wrapper.jar") {
    Write-Host "✓ Maven Wrapper 已存在" -ForegroundColor Green
} else {
    Write-Host "ℹ 跳过 Maven Wrapper 初始化 (需要本地 Maven)" -ForegroundColor Cyan
}
Write-Host ""

Write-Host "======================================" -ForegroundColor Cyan
Write-Host "初始化完成！" -ForegroundColor Green
Write-Host "======================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "下一步:" -ForegroundColor Cyan
Write-Host "  编译:   ./mvnw clean compile" -ForegroundColor White
Write-Host "  运行:   ./mvnw exec:java -Dexec.mainClass=app.AppKt" -ForegroundColor White
Write-Host "  测试:   curl http://localhost:7000/" -ForegroundColor White
Write-Host ""


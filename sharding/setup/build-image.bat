@echo off
echo [2/3] Building Spring Boot Docker image...
docker build -t sharding-app:latest .
if %ERRORLEVEL% neq 0 (
    echo Docker build failed!
    exit /b %ERRORLEVEL%
)
echo Loading Docker image into KIND nodes...
kind load docker-image sharding-app:latest
if %ERRORLEVEL% neq 0 (
    echo Failed to load image into KIND!
    exit /b %ERRORLEVEL%
)

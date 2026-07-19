@echo off
echo [1/3] Creating KIND cluster with NodePort mappings...
kind create cluster --config kind/kind-config.yaml
if %ERRORLEVEL% neq 0 (
    echo Failed to create cluster!
    exit /b %ERRORLEVEL%
)

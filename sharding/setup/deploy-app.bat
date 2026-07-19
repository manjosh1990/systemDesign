@echo off
echo [3/3] Deploying Kubernetes configurations...
kubectl apply -f k8s/
if %ERRORLEVEL% neq 0 (
    echo Failed to apply Kubernetes configurations!
    exit /b %ERRORLEVEL%
)
echo Watch the pods start up using this command: kubectl get pods -w

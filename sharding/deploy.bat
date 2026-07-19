@echo off
:menu
cls
echo =================================================
echo  Database Sharding POC - Setup Menu
echo =================================================
echo 1. Create KIND Cluster
echo 2. Build and Load Docker Image
echo 3. Deploy App to Kubernetes
echo 4. Run Full Pipeline (1, 2, 3)
echo 5. View Application Logs
echo 6. Destroy Cluster
echo 7. Exit
echo =================================================
set /p choice="Select an option (1-7): "

if "%choice%"=="1" call setup\create-cluster.bat
if "%choice%"=="2" call setup\build-image.bat
if "%choice%"=="3" call setup\deploy-app.bat
if "%choice%"=="4" (
    call setup\create-cluster.bat
    call setup\build-image.bat
    call setup\deploy-app.bat
)
if "%choice%"=="5" call setup\view-logs.bat
if "%choice%"=="6" call setup\destroy-cluster.bat
if "%choice%"=="7" exit /b

echo.
pause
goto menu

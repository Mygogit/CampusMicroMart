@echo off
chcp 65001 >nul
title 校园二手交易平台 - JAR打包

echo ============================================
echo   校园二手交易平台 - 一键打包
echo ============================================
echo.

echo [1/4] 清理旧构建...
call mvn clean -q
if %errorlevel% neq 0 (echo 清理失败！&& pause && exit /b 1)

echo [2/4] 编译项目（跳过测试）...
call mvn package -DskipTests -q
if %errorlevel% neq 0 (echo 编译失败！&& pause && exit /b 1)

echo [3/4] 收集产物到 dist/ ...
if not exist dist mkdir dist
if not exist dist\libs mkdir dist\libs

copy /Y campus-gateway\target\*-1.0.0.jar dist\libs\campus-gateway.jar >nul
copy /Y user-service\target\*-1.0.0.jar dist\libs\user-service.jar >nul
copy /Y product-service\target\*-1.0.0.jar dist\libs\product-service.jar >nul
copy /Y order-service\target\*-1.0.0.jar dist\libs\order-service.jar >nul
copy /Y payment-service\target\*-1.0.0.jar dist\libs\payment-service.jar >nul

echo [4/4] 复制前端构建...
if exist campus-frontend\dist (
    xcopy /E /Y campus-frontend\dist dist\frontend\ >nul
    echo   前端已复制到 dist\frontend\
) else (
    echo   前端未构建，请先执行: cd campus-frontend ^&^& npm run build
)

echo.
echo ============================================
echo   打包完成！产物在 dist\ 目录:
echo   dist\libs\campus-gateway.jar (:8080)
echo   dist\libs\user-service.jar   (:8084)
echo   dist\libs\product-service.jar (:8081)
echo   dist\libs\order-service.jar   (:8083)
echo   dist\libs\payment-service.jar (:8085)
echo   dist\frontend\               (静态文件)
echo ============================================
pause

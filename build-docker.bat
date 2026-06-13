@echo off
chcp 65001 >nul
title 校园二手交易平台 - Docker构建

echo ============================================
echo   Docker 镜像构建 (WSL2 环境下执行)
echo ============================================
echo.
echo 方式1 - 在 WSL2 中逐服务构建（推荐，可复用 Maven 缓存）:
echo   wsl bash -c "docker build -f Dockerfile.user-service -t campus-user-service ."
echo   wsl bash -c "docker build -f Dockerfile.product-service -t campus-product-service ."
echo   wsl bash -c "docker build -f Dockerfile.order-service -t campus-order-service ."
echo   wsl bash -c "docker build -f Dockerfile.payment-service -t campus-payment-service ."
echo   wsl bash -c "docker build -f Dockerfile.gateway -t campus-gateway ."
echo.
echo 方式2 - 一键构建全部:
echo   wsl bash build-all-images.sh
echo.
echo ============================================

cd /d "%~dp0"

if not exist deploy\images mkdir deploy\images

echo 正在使用 WSL2 Docker 构建镜像...
echo.

set SERVICES=user-service product-service order-service payment-service campus-gateway
set FAILED=0

for %%s in (%SERVICES%) do (
    echo --- 构建 %%s ---
    if "%%s"=="campus-gateway" (
        set TAG=campus-gateway
    ) else (
        set TAG=%%s
    )
    wsl docker build -f Dockerfile.%%s -t !TAG!:latest .
    if !errorlevel! neq 0 (
        echo [失败] %%s 构建失败！
        set /a FAILED+=1
    ) else (
        echo [成功] %%s 镜像已构建
        wsl docker save !TAG!:latest -o deploy\images\!TAG!.tar
    )
    echo.
)

echo ============================================
if %FAILED%==0 (
    echo   全部镜像构建成功！镜像已导出到 deploy\images\
) else (
    echo   有 %FAILED% 个镜像构建失败
)
echo ============================================
pause

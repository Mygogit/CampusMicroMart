#!/bin/bash
# WSL2 环境一键构建所有 Docker 镜像
set -e
cd "$(dirname "$0")"

SERVICES=("user-service" "product-service" "order-service" "payment-service" "gateway")
FAILED=0

for svc in "${SERVICES[@]}"; do
    echo "--- 构建 ${svc} ---"
    if [ "$svc" == "gateway" ]; then
        tag="campus-gateway"
        df="Dockerfile.gateway"
    else
        tag="${svc}"
        df="Dockerfile.${svc}"
    fi

    if docker build -f "$df" -t "${tag}:latest" . ; then
        echo "[OK] ${tag}:latest"
        mkdir -p deploy/images
        docker save "${tag}:latest" -o "deploy/images/${tag}.tar"
    else
        echo "[FAIL] ${svc}"
        ((FAILED++))
    fi
done

echo ""
if [ $FAILED -eq 0 ]; then
    echo "全部 5 个镜像构建成功！"
else
    echo "有 ${FAILED} 个失败"
fi

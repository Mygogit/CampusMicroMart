#!/bin/bash
# 将本地构建的 7 个镜像打 tag 并推送到 Docker Hub
# 用法: bash push-images.sh <你的DockerHub用户名>
set -e
USER="$1"
if [ -z "$USER" ]; then
  echo "用法: $0 <dockerhub-user>"
  exit 1
fi

# 本地 tag -> Docker Hub tag 映射（4 个业务服务本地无 campus- 前缀）
declare -A MAP=(
  [user-service]=campus-user-service
  [product-service]=campus-product-service
  [order-service]=campus-order-service
  [payment-service]=campus-payment-service
  [campus-gateway]=campus-gateway
  [campus-frontend]=campus-frontend
  [campus-mysql]=campus-mysql
)

for local in "${!MAP[@]}"; do
  remote="${MAP[$local]}"
  echo "--- tag & push $local -> $USER/$remote ---"
  docker tag "$local:latest" "$USER/$remote:latest"
  docker push "$USER/$remote:latest"
done

echo "ALL 7 IMAGES PUSHED TO $USER"

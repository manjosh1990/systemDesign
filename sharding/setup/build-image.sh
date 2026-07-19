#!/bin/bash
set -e
echo "[2/3] Building Spring Boot Docker image..."
docker build -t sharding-app:latest .
echo "Loading Docker image into KIND nodes..."
kind load docker-image sharding-app:latest

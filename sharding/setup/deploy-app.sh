#!/bin/bash
set -e
echo "[3/3] Deploying Kubernetes configurations..."
kubectl apply -f k8s/
echo "Watch the pods start up using this command: kubectl get pods -w"

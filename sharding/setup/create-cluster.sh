#!/bin/bash
set -e
echo "[1/3] Creating KIND cluster with NodePort mappings..."
kind create cluster --config kind/kind-config.yaml

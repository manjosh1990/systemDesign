#!/bin/bash
echo "Destroying KIND cluster..."
kind delete cluster || true

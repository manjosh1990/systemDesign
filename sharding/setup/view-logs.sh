#!/bin/bash
echo "Tailing logs for the Spring Boot application (Press Ctrl+C to exit)..."
kubectl logs -f deployment/sharding-app

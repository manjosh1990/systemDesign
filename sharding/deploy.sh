#!/bin/bash
while true; do
    clear
    echo "================================================="
    echo " Database Sharding POC - Setup Menu"
    echo "================================================="
    echo "1. Create KIND Cluster"
    echo "2. Build and Load Docker Image"
    echo "3. Deploy App to Kubernetes"
    echo "4. Run Full Pipeline (1, 2, 3)"
    echo "5. View Application Logs"
    echo "6. Destroy Cluster"
    echo "7. Exit"
    echo "================================================="
    read -p "Select an option (1-7): " choice
    
    case $choice in
        1) bash setup/create-cluster.sh ;;
        2) bash setup/build-image.sh ;;
        3) bash setup/deploy-app.sh ;;
        4) 
           bash setup/create-cluster.sh
           bash setup/build-image.sh
           bash setup/deploy-app.sh
           ;;
        5) bash setup/view-logs.sh ;;
        6) bash setup/destroy-cluster.sh ;;
        7) exit 0 ;;
        *) echo "Invalid option." ;;
    esac
    echo ""
    read -p "Press Enter to continue..."
done

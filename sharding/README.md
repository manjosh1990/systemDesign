# Database Sharding Proof of Concept (POC)

*This module is part of the broader **System Design Projects** collection.*

## Overview
This project is a complete Proof of Concept demonstrating how to scale a relational database horizontally using **Database Sharding**. It is built using **Spring Boot 3**, **Apache ShardingSphere**, **Flyway**, and **PostgreSQL**, fully containerized and deployable to a local **Kubernetes (KIND)** cluster.

### Architecture Diagram
```text
                             +-----------------------+
                             |  Client / Swagger UI  |
                             +-----------+-----------+
                                         |
                                   HTTP Requests
                                         |
                                         v
                             +-----------------------+
                             |    Spring Boot App    |
                             |      (port 18080)     |
                             +-----------+-----------+
                                         |
                                 Intercepted JDBC
                                         |
                                         v
                         +-------------------------------+
                         |   Apache ShardingSphere JDBC  |
                         +-------+---------------+-------+
                                 |               |
               (user_id % 2 == 0)|               |(user_id % 2 != 0)
                                 v               v
                +--------------------+   +--------------------+
                |  PostgreSQL ds_0   |   |  PostgreSQL ds_1   |
                |    (port 5432)     |   |    (port 5432)     |
                +--------------------+   +--------------------+
```

## The Problem It Solves
When an application's database grows too large to handle massive read/write volumes, companies traditionally scale *vertically* (buying bigger, more expensive servers). However, there is a physical hardware limit to vertical scaling. 

**Database Sharding** solves this by scaling *horizontally*—splitting the data across multiple cheaper, independent database servers.

The massive architectural challenge with sharding is that the application code now has to know exactly which server contains which piece of data. Writing and maintaining this routing logic manually creates immense technical debt.

## How It Works
This project uses **Apache ShardingSphere** as a transparent JDBC middleware proxy to solve the routing problem.

1.  **Transparency:** The Spring Boot application (using Hibernate/JPA) thinks it is talking to a single PostgreSQL database. It writes standard SQL and remains entirely unaware of the sharded architecture.
2.  **Interception & Routing:** ShardingSphere intercepts the SQL before it leaves the application. It parses the Abstract Syntax Tree (AST) looking for our configured **Shard Key** (`user_id`).
3.  **The Algorithm:** It applies an inline Modulo algorithm (`user_id % 2`). If the user ID is even, the query is routed physically to `ds_0` (Shard 0). If odd, it routes to `ds_1` (Shard 1).
4.  **Scatter-Gather:** If a query is executed *without* the Shard Key (e.g. `GET /orders`), ShardingSphere does not know which database holds the data. It will automatically rewrite the query, broadcast it to *all* shards simultaneously, merge the results in memory, and return them as a single response to the application.

### Spring Boot Configuration
The configuration bridging Spring Boot and ShardingSphere is handled seamlessly via the 12-Factor App methodology:
*   **`application.properties`**: We tell Spring Boot to use the ShardingSphere JDBC driver by setting `spring.datasource.url=jdbc:shardingsphere:classpath:shardingsphere.yaml?placeholder-type=environment`. This crucial parameter tells ShardingSphere to dynamically resolve environment variables.
*   **`shardingsphere.yaml`**: This file contains the actual sharding logic (`ds${user_id % 2}`). Instead of hardcoding database URLs, we use ShardingSphere's native environment variable syntax (e.g., `$${SHARD_0_HOST::localhost}`).
*   **Kubernetes Injection**: When deployed to Kubernetes, our `app.yaml` Deployment injects the actual internal cluster DNS names (`pg-shard-0`, `pg-shard-1`) into the pods. ShardingSphere picks these up instantly without any code changes!

## Core Dependencies
This Spring Boot application relies on a carefully selected stack of dependencies, defined in the `pom.xml`:

*   **`spring-boot-starter-webmvc`**: Provides the embedded Tomcat server and REST API routing capabilities.
*   **`spring-boot-starter-data-jpa`**: Brings in Hibernate and Spring Data to handle the Object-Relational Mapping (ORM) and abstract raw SQL queries.
*   **`shardingsphere-jdbc`**: The core Apache routing engine that intercepts JDBC calls and proxies them to the correct physical shards based on our algorithm.
*   **`flyway-core` & `flyway-database-postgresql`**: Database migration tools used to automatically execute the `V1__init.sql` script to create the database schemas on both shards during startup.
*   **`postgresql`**: The official JDBC driver required to establish the physical network connections to the PostgreSQL databases.
*   **`spring-boot-starter-actuator`**: Exposes `/actuator/health` endpoints, allowing the Kubernetes liveness and readiness probes to actively monitor the application's health.
*   **`springdoc-openapi-starter-webmvc-ui`**: Automatically generates the interactive Swagger UI and OpenAPI documentation for testing endpoints dynamically.

## Prerequisites
*   [Docker Desktop](https://www.docker.com/products/docker-desktop/)
*   [KIND (Kubernetes IN Docker)](https://kind.sigs.k8s.io/)
*   [kubectl](https://kubernetes.io/docs/tasks/tools/)
*   Java 21

## Deployment Instructions

We have provided a fully automated, menu-driven deployment script to launch this architecture locally using KIND.

1.  Open your terminal (Command Prompt, PowerShell, or Git Bash) in the project root.
2.  Run the deployment script:
    *   **Windows:** `.\deploy.bat`
    *   **Linux/Mac/Git Bash:** `./deploy.sh`
3.  Choose **Option 4 (Run Full Pipeline)**. This will automatically:
    *   Create a local Kubernetes cluster with 1 Control Plane and 2 Worker nodes.
    *   Map port `18080` from the cluster to your host machine.
    *   Build the Spring Boot Docker image.
    *   Load the image into the KIND nodes.
    *   Deploy the Kubernetes Secrets, PostgreSQL Databases, and the Spring Boot Application.
4.  Wait for the pods to become ready. You can monitor the status by running:
    ```bash
    kubectl get pods -w
    ```
    *(Wait until all pods show `1/1` READY and `Running`)*

## Verification & Testing

### 1. Interact via Swagger UI
Once all pods are running, open your web browser and navigate to:
**http://localhost:18080/api-docs**

From the UI, you can easily test the endpoints:
*   `POST /orders`: Create an order with `userId: 100`. Then create another with `userId: 101`.
*   `GET /orders`: Fetch all orders (Observe the console logs for the scatter-gather broadcast!).
*   `GET /orders/{orderId}/users/{userId}`: Fetch a specific order using targeted routing.

### 2. Verify Physical Sharding
To definitively prove that the data is actually being split across two separate database servers, we can bypass the Spring Boot application entirely and query the PostgreSQL pods directly!

1. Find the exact names of your database pods:
   ```bash
   kubectl get pods
   ```
2. Exec into Shard 0 (replace the pod name with yours) and query the data. You should **only** see orders with **even** `user_id`s:
   ```bash
   kubectl exec -it <pg-shard-0-pod-name> -- psql -U postgres -d ds_0 -c "SELECT * FROM t_order;"
   ```
3. Exec into Shard 1 and query the data. You should **only** see orders with **odd** `user_id`s:
   ```bash
   kubectl exec -it <pg-shard-1-pod-name> -- psql -U postgres -d ds_1 -c "SELECT * FROM t_order;"
   ```

## Clean Up
When you are finished testing, simply run `.\deploy.bat` and select **Option 5 (Destroy Cluster)** to instantly delete the Kubernetes environment and free up your system resources.

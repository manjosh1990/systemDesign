# System Design Projects Collection

Welcome to the **System Design Projects** repository. This monorepo is a comprehensive collection of Proof of Concepts (POCs), tutorials, and Low-Level Design (LLD) implementations geared toward mastering System Design for engineering interviews and production-grade architectures.

## Modules in this Repository

This repository is structured as a Maven Multi-Module project. 

### 1. Database Sharding (`/sharding`)
A complete, containerized Proof of Concept demonstrating horizontal database scaling using **Spring Boot 3**, **Apache ShardingSphere**, and **PostgreSQL**. It runs on a local Kubernetes (KIND) cluster and showcases dynamic query routing, 12-Factor App configuration, and scatter-gather mechanisms.
* [Read the Sharding Documentation](./sharding/README.md)

### 2. Consistent Hashing (`/consistent-hashing`)
A fully dynamic distributed database engine built from scratch. It demonstrates how systems like Amazon DynamoDB and Cassandra scale without downtime using a mathematical Hash Ring. Features Zero-Downtime scaling, algorithmic routing via Virtual Nodes, and automated background data migration.
* [Read the Consistent Hashing Documentation](./consistent-hashing/README.md)

### 3. Low-Level Design (`/systemDesign_lld`)
Implementations of various Object-Oriented Design (OOD) and Low-Level Design (LLD) problems.
* [Read the LLD Documentation](./systemDesign_lld)

### 4. DevOps Tutorials (`/devops_tutorials`)
Hands-on guides and infrastructure setups for mastering DevOps concepts and orchestration.
* [Read the DevOps Documentation](./devops_tutorials)

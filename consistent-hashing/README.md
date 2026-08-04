# Consistent Hashing — Proof of Concept

---

## Table of Contents

1. [What is Consistent Hashing?](#1-what-is-consistent-hashing)
2. [Why Modulo Sharding Fails](#2-why-modulo-sharding-fails)
3. [How the Ring Works](#3-how-the-ring-works)
4. [ConsistentHashRing.java — The Core](#4-consistenthashringjava--the-core)
5. [Dynamic Control Plane Architecture](#5-dynamic-control-plane-architecture)
6. [Startup Sequence & Bootstrapping](#6-startup-sequence--bootstrapping)
7. [Transaction Management](#7-transaction-management)
8. [Tech Stack](#8-tech-stack)
9. [Project Structure](#9-project-structure)
10. [Lab Guide: Real-World Data Migration](#10-lab-guide-real-world-data-migration)
11. [Key Learnings](#11-key-learnings)
12. [References](#12-references)

---

## 1. What is Consistent Hashing?

Consistent hashing is a technique for distributing data across multiple database nodes (shards) such that:

- Each key is always routed to the **same node** (deterministic)
- Adding or removing a node only remaps a **small fraction** of keys (~1/N)
- **Load is distributed evenly** across all nodes

It is used by systems like Amazon DynamoDB, Apache Cassandra, and Redis Cluster to scale horizontally without migrating all data every time a node is added.

---

## 2. Why Modulo Sharding Fails

The simplest way to split data across `N` nodes is modulo sharding:

```
node = user_id % N
```

With 2 nodes it works fine:
```
user_id = 1  →  1 % 2 = 1  →  ds1
user_id = 3  →  3 % 2 = 1  →  ds1
user_id = 4  →  4 % 2 = 0  →  ds0
```

**Now add a third node `ds2`. The formula becomes `% 3`:**
```
user_id = 1  →  1 % 3 = 1  →  ds1  (same)
user_id = 3  →  3 % 3 = 0  →  ds0  💥 (was ds1!)
user_id = 4  →  4 % 3 = 1  →  ds1  💥 (was ds0!)
```

Nearly every key routes to the wrong node. All data becomes unreachable until a full migration is complete.

**Consistent hashing limits this disruption to only `1/N` of keys.**

---

## 3. How the Ring Works

### 3.1 The ring is a number line that wraps

Imagine a number line from `0` to `MAX (2^64)`. The right end wraps back to the left — that's the ring.

```
0 ──── ds1(2000) ──── ds0(5000) ──── MAX
                                      │
                             (wraps back to 0)
```

### 3.2 Node positions are decided by hashing the node name

Nobody manually assigns positions. The **node name is hashed** → the output is its position on the ring:

```
hash("ds0") → 5000   →  ds0 placed at position 5000
hash("ds1") → 2000   →  ds1 placed at position 2000
```

### 3.3 Virtual Nodes — ensuring even load

With one position per node, one node might own a large arc and get more keys than others.
**Fix:** give each physical node 150 positions (virtual nodes) spread across the ring:

```
hash("ds0#0") = 1500  →  ring.put(1500, "ds0")
hash("ds0#1") = 4500  →  ring.put(4500, "ds0")
hash("ds1#0") = 500   →  ring.put(500,  "ds1")
hash("ds1#1") = 3500  →  ring.put(3500, "ds1")
```

Ring:
```
0 ─ ds1(500) ─ ds0(1500) ─ ds1(3500) ─ ds0(4500) ─ MAX
```

ds0 and ds1 now alternate across the ring → **even load** ✅

---

## 4. ConsistentHashRing.java — The Core

### `hash()` — FNV-1a + Murmur3

```java
private long hash(String key) {
    long h = fnv1a64(key);
    return murmur3Mix64(h);
}
```

Two-stage hash pipeline:
1. **FNV-1a**: Processes every character into a 64-bit hash. Zero allocation, pure arithmetic.
2. **MurmurHash3 fmix64**: Avalanche finalizer. Ensures small input differences produce wildly different outputs.

**Why not MD5?** MD5 allocates `MessageDigest` and `byte[]` on every call (~200ns). Our arithmetic hash takes ~2ns, zero allocation, and gives an equally uniform distribution on the ring.

---

## 5. Dynamic Control Plane Architecture

In a production system, you don't restart your application to add a new database. You add it dynamically. We implemented a **Control Plane Architecture** to manage this.

```
  ┌────────────────────────────────────────────┐
  │           Client (REST / Swagger)          │
  └─────────────────────┬──────────────────────┘
                        │
         ┌──────────────┴──────────────┐
         ▼                             ▼
  ┌──────────────┐             ┌──────────────┐
  │ Admin API    │             │ Order API    │
  │ /admin/shards│             │ /orders      │
  │ /admin/rebal │             └───────┬──────┘
  └──────┬───────┘                     │
         │ (Registers dynamically)     │ (@ShardTransactional routes to correct EMF)
         ▼                             ▼
  ┌────────────────────────────────────────────┐
  │               ShardRegistry                │
  │  (Thread-safe Map of Active Connections)   │
  └──────┬─────────────────────────────┬───────┘
         │                             │
         ▼                             ▼
  ┌────────────┐                ┌────────────┐
  │  ds_0      │                │  ds_1      │
  │ PostgreSQL │                │ PostgreSQL │
  │ :5432      │                │ :5433      │
  │ (Metadata) │                │ (Data Only)│
  └────────────┘                └────────────┘
```

1. **`ds_0` is the Master**: It holds a special table called `shard_registry` containing the URLs and credentials of all shards in the cluster.
2. **Dynamic Addition**: When you hit `POST /admin/shards`, the `ShardManagementService` saves the new DB credentials to the master `ds_0`, builds a new Connection Pool and `EntityManagerFactory` in memory, and adds it to the Hash Ring immediately!
3. **Rebalancing**: The `DataRebalancerService` scans all databases and migrates stranded data to the new node.

---

## 6. Startup Sequence & Bootstrapping

We completely disabled Spring Boot's auto-configuration for DataSources and JPA. Instead, `ShardManagementService.bootstrap()` runs on startup:

1. Connects to `ds_0` using raw JDBC.
2. Creates the `shard_registry` table if it doesn't exist.
3. Automatically inserts `ds_0` into the registry.
4. Reads all rows from `shard_registry`.
5. For each row, it dynamically:
   - Creates a `HikariDataSource`.
   - Runs `Flyway` to ensure the `t_order` table exists.
   - Builds a `LocalContainerEntityManagerFactoryBean`.
   - Adds the shard to the `ConsistentHashRing`.

---

## 7. Transaction Management

Spring's `@Transactional` binds to a static data source at compile time. Since our shard is chosen at runtime, we use a custom AOP aspect.

```java
// Service method
@ShardTransactional(routingKey = "#order.userId")
public Order createOrder(Order order) {
    EntityManager em = ShardEntityManagerHolder.get(); // set by the aspect
    em.persist(order);
    return order;
}
```

**How the aspect works:**
1. Intercepts method.
2. Evaluates SpEL `"#order.userId"` → e.g. `"42"`.
3. `ring.getNode("42")` → `"ds0"`.
4. Fetches the active `EntityManagerFactory` for `"ds0"` from the `ShardRegistry`.
5. Creates an `EntityManager`, begins a transaction, and sets it in a `ThreadLocal`.
6. Executes the method.
7. Commits and closes the connection.

---

## 8. Tech Stack

| Layer | Technology |
|---|---|
| Application | Java 21, Spring Boot 3 |
| Routing Logic | Custom `ConsistentHashRing` (TreeMap + FNV-1a + MurmurHash3) |
| Architecture | Dynamic Control Plane with Metadata DB |
| ORM | JPA / Hibernate (dynamic multi-datasource) |
| Transaction Mgmt | Custom `@ShardTransactional` AOP aspect |
| Database | PostgreSQL |
| Containerisation | Docker Compose / Kubernetes (KIND) |

---

## 9. Project Structure

```
consistent-hashing/
├── src/main/java/manjosh/labs/consistenthashing/
│   ├── config/         ← ShardProperties, ShardRegistry, OpenAPI config
│   ├── core/           ← ConsistentHashRing.java
│   ├── transaction/    ← @ShardTransactional aspect + ThreadLocal holder
│   ├── entity/         ← Order.java
│   ├── service/        ← OrderServiceImpl, ShardManagementService, DataRebalancerService
│   ├── controller/     ← AdminController, OrderController, RingDebugController
│   └── ConsistentHashingApplication.java
├── src/main/resources/
│   ├── application.properties    ← ONLY configures the master DB (ds0)
│   └── db/migration/             ← V1__init_schema.sql (t_order schema)
├── k8s/infra/                    ← postgres-ds0.yaml, postgres-ds1.yaml, postgres-ds2.yaml
└── docker-compose.yml
```

---

## 10. Lab Guide: Real-World Data Migration

Follow these steps to observe the cluster dynamically scaling and rebalancing data!

### Phase 1: Setup & Seeding (Kubernetes/KIND)
1. Build the application. Thanks to our Maven configuration, this will automatically compile the code and build the `consistent-hashing:latest` Docker image in one step:
   ```bash
   mvn clean package -DskipTests
   ```
2. Push the Docker image into your local KIND cluster:
   ```bash
   kind load docker-image consistent-hashing:latest
   ```
3. Deploy the databases and the application:
   ```bash
   kubectl apply -f k8s/infra/
   kubectl apply -f k8s/app/
   ```
4. *(Optional)* If you are redeploying a code change, force Kubernetes to restart the app pod to pick up the new image:
   ```bash
   kubectl rollout restart deployment/consistent-hashing-app
   ```
5. Follow the application logs to ensure it boots successfully:
   ```bash
   kubectl logs -f deployment/consistent-hashing-app
   ```
6. Open **Swagger UI** in your browser: `http://localhost:8081/api-docs` (The port `8081` is mapped directly to the app inside KIND).
7. Use `POST /orders` to create about 10 orders with random User IDs.
   *Since the app only bootstrapped `ds0`, 100% of these orders are physically stored on `ds0`.*


### Phase 2: Dynamic Scaling
1. We will now add a new database to the cluster without restarting the app!
2. Call `POST /admin/shards` in Swagger with the following payload. Notice the URL uses the internal Kubernetes service name `postgres-ds1`:
   ```json
   {
     "name": "ds1",
     "url": "jdbc:postgresql://postgres-ds1:5433/ds_1",
     "username": "postgres",
     "password": "password"
   }
   ```
3. Check your application logs. You will see Flyway instantly run on `ds1`, and `ds1` gets injected into the Hash Ring!

### Phase 3: The "Miss" (Migration Window)
1. In Swagger, use `GET /orders` to fetch the orders you created in Phase 1.
2. Some of them will succeed, but some will return **404 Not Found**.
   *Why? The Hash Ring updated instantly and now routes some keys to `ds1`, but the data is still physically sitting on `ds0`!*

### Phase 4: Rebalancing Data
1. Call `POST /admin/rebalance`.
2. Check your application logs. You will see the `DataRebalancerService` scanning `ds0`, finding the stranded records, and migrating them to `ds1`.
3. Try your `GET /orders` requests again from Phase 3. **They will all succeed!** 
   *The stranded data has successfully arrived at its new home.*

---

## 11. Key Learnings

1. **Static Configuration fails Distributed Systems**: Moving from hardcoded `app.shards[]` arrays to a dynamic `shard_registry` metadata table is what allows systems to scale at runtime without downtime.
2. **Routing Logic is Math**: Implementing our own Consistent Hash Ring using a `TreeMap` demystifies how keys are routed deterministically.
3. **Data Movement vs Routing Change**: The ring updates routing instantly, but moving the physical data takes time. This highlights the complexity of production systems which require dual-reads/proxying to avoid the 404s we see in Phase 3.
4. **AOP bridges the "Where" and the "What"**: By using a custom `@ShardTransactional` aspect and `ThreadLocal` context, the business logic (`OrderServiceImpl`) remains completely agnostic of the sharding infrastructure.

---

## 12. Cons & Limitations of Sharding

While Consistent Hashing provides infinite write-scaling, it introduces severe architectural tradeoffs that plague distributed systems:

### 1. The Cross-Shard Query Problem (Scatter-Gather)
Our routing logic relies entirely on hashing the Shard Key (e.g., `userId`). If you need to perform a query without the Shard Key—for example, *"Find all orders with status = PENDING"* or *"Get a list of all user IDs"*—you are flying blind. 
Because you cannot hash a key to find the database, the application must perform a **Scatter-Gather**. It must query *every single shard in the cluster* simultaneously and merge the results in memory. 
- **Industry Solution:** Cross-shard queries are considered an anti-pattern for transactional flows. To solve this, companies stream data from all shards via Change Data Capture (CDC tools like Debezium/Kafka) into a centralized, non-sharded **Global Secondary Index** (like Elasticsearch) for global searching, or into a **Data Warehouse** (like Snowflake/BigQuery) for analytical aggregations.

### 2. The Operational Complexity of Rebalancing
As demonstrated in Phase 3 of the lab, adding a node instantly changes the Hash Ring, but the physical data doesn't move instantly. This creates a window where data is "stranded" on the wrong node, resulting in 404s.
- **Industry Solution:** Production databases handle this by implementing complex proxies or dual-reads. If a node receives a query for data it doesn't have, it checks if a rebalance is occurring, and will internally forward the query to the old node on behalf of the user until the physical migration is complete.

### 3. Hotspots & Celebrity Problems
Even with 100+ Virtual Nodes guaranteeing uniform distribution of *keys*, you can still encounter severe skew in *traffic*. If `user:99` is a celebrity (e.g., Elon Musk) with millions of orders, the single database shard that owns `user:99` will be crushed under the load, while the other shards sit idle.
- **Industry Solution:** The industry handles this by caching the "celebrity" data heavily in distributed caches (Redis/Memcached), or by employing a composite shard key (e.g., `userId + orderId`) to force the celebrity's data to spread across multiple shards.

---

## 13. References

- [Consistent Hashing — Wikipedia](https://en.wikipedia.org/wiki/Consistent_hashing)
- [Amazon DynamoDB — Dynamo Paper (2007)](https://www.allthingsdistributed.com/files/amazon-dynamo-sosp2007.pdf)
- [Apache Cassandra — Token Ring](https://cassandra.apache.org/doc/latest/cassandra/architecture/dynamo.html)

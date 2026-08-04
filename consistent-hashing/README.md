# Consistent Hashing — Proof of Concept

---

## Table of Contents

1. [What is Consistent Hashing?](#1-what-is-consistent-hashing)
2. [Why Modulo Sharding Fails](#2-why-modulo-sharding-fails)
3. [How the Ring Works](#3-how-the-ring-works)
4. [ConsistentHashRing.java — The Core](#4-consistenthashringjava--the-core)
5. [Application Architecture](#5-application-architecture)
6. [Startup Sequence](#6-startup-sequence)
7. [Configuration Deep Dive](#7-configuration-deep-dive)
8. [Transaction Management](#8-transaction-management)
9. [Tech Stack](#9-tech-stack)
10. [Project Structure](#10-project-structure)
11. [How to Run](#11-how-to-run)
12. [Key Learnings](#12-key-learnings)
13. [References](#13-references)

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
user_id = 1  →  1 % 2 = 0  →  ds0
user_id = 3  →  3 % 2 = 1  →  ds1
user_id = 5  →  5 % 2 = 1  →  ds1
```

**Now add a third node `ds2`. The formula becomes `% 3`:**
```
user_id = 1  →  1 % 3 = 1  →  ds1  💥 (was ds0!)
user_id = 3  →  3 % 3 = 0  →  ds0  💥 (was ds1!)
user_id = 5  →  5 % 3 = 2  →  ds2  💥 (was ds1!)
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
hash("ds0") → MD5 → 5000   →  ds0 placed at position 5000
hash("ds1") → MD5 → 2000   →  ds1 placed at position 2000
```

The same name always produces the same position. No manual configuration needed.

### 3.3 Finding the right node — the clockwise walk

**Insert user_id = 1:**
```
Step 1: hash("user:1") = 3500

Step 2: Where is 3500 on the ring?

0 ──── ds1(2000) ──── 3500 ──── ds0(5000) ──── MAX
                        ↑
                   user:1 lands here

Step 3: Walk clockwise from 3500
        → first node hit = ds0 at 5000

Result: user_id=1 → ds0 ✅
```

**Insert user_id = 3:**
```
hash("user:3") = 6500

0 ──── ds1(2000) ──── ds0(5000) ──── 6500 ──── MAX
                                        ↑
                                   user:3 lands here

Walk clockwise from 6500
  → no more nodes ahead → hit MAX → wrap to 0
  → first node from 0 = ds1 at 2000

Result: user_id=3 → ds1 ✅
```

### 3.4 Adding a node — minimal disruption

```
hash("ds2") = 4000   →  ds2 inserted at position 4000

Ring becomes:
0 ──── ds1(2000) ──── ds2(4000) ──── ds0(5000) ──── MAX

user:1 was at 3500:
  Walk clockwise → first hit = ds2 at 4000
  user:1 now routes to ds2  (moved to new node)

user:3 was at 6500:
  Walk clockwise → wrap → ds1 at 2000
  user:3 still routes to ds1 ✅ (completely untouched)
```

Only the keys in the arc between ds1(2000) and ds2(4000) were affected. Everyone else is unaffected.

> ⚠️ The ring reroutes queries automatically, but **data does not migrate automatically**.
> If ds2 is new, it has no data yet. The application must handle the migration separately.
> Real systems (Cassandra, Redis Cluster) solve this with replication and slot migration.

### 3.5 Virtual Nodes — ensuring even load

With one position per node, one node might own a large arc and get more keys than others:

```
ds1 owns: 5000 → MAX → 0 → 2000  (large arc — too many keys)
ds0 owns: 2000 → 5000             (small arc — too few keys) ❌
```

**Fix:** give each physical node 150 positions (virtual nodes) spread across the ring:

```
hash("ds0#0") = 1500  →  ring.put(1500, "ds0")
hash("ds0#1") = 4500  →  ring.put(4500, "ds0")
hash("ds0#2") = 7500  →  ring.put(7500, "ds0")

hash("ds1#0") = 500   →  ring.put(500,  "ds1")
hash("ds1#1") = 3500  →  ring.put(3500, "ds1")
hash("ds1#2") = 6500  →  ring.put(6500, "ds1")
```

Ring:
```
0 ─ ds1(500) ─ ds0(1500) ─ ds1(3500) ─ ds0(4500) ─ ds1(6500) ─ ds0(7500) ─ MAX
```

ds0 and ds1 now alternate across the ring → **even load** ✅
The caller still just gets back `"ds0"` or `"ds1"` — virtual nodes are invisible to the caller.

**Comparison:**

| | Modulo Sharding (`% N`) | Consistent Hashing |
|---|---|---|
| Add node | ~75% keys remapped | ~1/N keys remapped |
| Remove node | ~67% keys remapped | ~1/N keys remapped |
| Load balance | Uneven if data isn't uniform | Even with virtual nodes |
| Complexity | Simple | Moderate |

---

## 4. ConsistentHashRing.java — The Core

### `hash()` — how a String becomes a position on the ring

```java
private long hash(String key) {
    MessageDigest md = MessageDigest.getInstance("MD5");
    byte[] digest = md.digest(key.getBytes());
    long hash = 0;
    for (int i = 0; i < 8; i++) {
        hash = (hash << 8) | (digest[i] & 0xFF);
    }
    return hash;
}
```

Trace with `"ds0#0"`:
```
Step 1: MD5("ds0#0") → 16 raw bytes: [A3, 7F, 02, B1, 44, 9C, D2, E8, ...]
Step 2: Take first 8 bytes:           [A3, 7F, 02, B1, 44, 9C, D2, E8]
Step 3: Combine into one Long:         0xA37F02B1449CD2E8 → 7263817263918273
Step 4: ring.put(7263817263918273, "ds0")
```

**Why MD5 and not `String.hashCode()`?**
- `String.hashCode()` can differ across JVM versions or restarts ❌
- MD5 always produces the same output everywhere, forever ✅
- MD5 gives a uniform, well-spread distribution across the ring ✅

**Why only 8 of the 16 MD5 bytes?**
- MD5 = 128 bits → too big for a Java `Long`
- `Long` = 64 bits → `2^64` positions on the ring — more than enough ✅

---

### `TreeMap<Long, String>` — the ring data structure

```java
private final TreeMap<Long, String> ring = new TreeMap<>();
```

| Key (Long) | Value (String) | Meaning |
|---|---|---|
| 500 | `"ds1"` | virtual node ds1#0 |
| 1500 | `"ds0"` | virtual node ds0#0 |
| 3500 | `"ds1"` | virtual node ds1#1 |
| 4500 | `"ds0"` | virtual node ds0#1 |

- **Key (Long)** = position on the ring (output of `hash()`)
- **Value (String)** = physical node name (`"ds0"` or `"ds1"`)
- **TreeMap** = auto-sorts by key → reading top to bottom = reading the ring left to right

```
0 ─ ds1(500) ─ ds0(1500) ─ ds1(3500) ─ ds0(4500) ─ MAX
```

---

### `addNode()` — placing a node on the ring

```java
public synchronized void addNode(String nodeName) {
    for (int i = 0; i < VIRTUAL_NODES; i++) {  // VIRTUAL_NODES = 150
        long position = hash(nodeName + "#" + i);
        ring.put(position, nodeName);
    }
}
```

`addNode("ds0")` loops 150 times:
```
i=0   → hash("ds0#0")   = 1500 → ring.put(1500, "ds0")
i=1   → hash("ds0#1")   = 4500 → ring.put(4500, "ds0")
i=149 → hash("ds0#149") = 9200 → ring.put(9200, "ds0")
```

After `addNode("ds0")` + `addNode("ds1")`:
- TreeMap has **300 entries** total
- But still only **2 physical nodes**

`synchronized` — the ring is shared state; prevents two threads from corrupting it simultaneously.

---

### `getNode()` — the clockwise walk

```java
public synchronized String getNode(String key) {
    long position = hash(key);
    SortedMap<Long, String> tail = ring.tailMap(position);
    long nodePosition = tail.isEmpty() ? ring.firstKey() : tail.firstKey();
    return ring.get(nodePosition);
}
```

`ring.tailMap(position)` returns all entries with key `>= position` — that's the clockwise portion.

**Normal case** — `hash("user:1") = 3500`:
```
tailMap(3500) → { 3500→"ds1", 4500→"ds0", ... }
tail.firstKey() = 3500 → "ds1"
Result: user:1 → ds1 ✅
```

**Wrap-around** — `hash("user:3") = 9500` (past all nodes):
```
tailMap(9500) → EMPTY

tail.isEmpty() = true
→ ring.firstKey() = 500 → "ds1"   ← wrap around to start
Result: user:3 → ds1 ✅
```

---

### `removeNode()` — a node goes down

```java
public synchronized void removeNode(String nodeName) {
    for (int i = 0; i < VIRTUAL_NODES; i++) {
        long position = hash(nodeName + "#" + i);
        ring.remove(position);
    }
}
```

Removes all 150 virtual nodes for that shard. After removal, any key that was routed to the removed node automatically reroutes to the next node clockwise.

---

## 5. Application Architecture

```
  ┌────────────────────────────────────────────┐
  │           Client (REST / Swagger)          │
  └─────────────────────┬──────────────────────┘
                        │  HTTP Request
                        ▼
  ┌────────────────────────────────────────────┐
  │              OrderController               │
  └─────────────────────┬──────────────────────┘
                        │
                        ▼
  ┌────────────────────────────────────────────┐
  │              OrderServiceImpl              │
  │                                            │
  │  1. ring.getNode(userId) → "ds0"           │
  │  2. emfMap.get("ds0")                      │
  │     .createEntityManager()                 │
  │  3. em.persist(order)                      │
  └──────┬──────────────────┬──────────────────┘
         │                  │
         ▼                  ▼
  ┌────────────┐     ┌────────────┐
  │  ds_0      │     │  ds_1      │
  │ PostgreSQL │     │ PostgreSQL │
  │ :5432      │     │ :5433      │
  └────────────┘     └────────────┘
```

### 5.1 End-to-End Request Flow
Let's trace a `POST /orders` request with `{ "orderId": 100, "userId": 42 }`:

1. **Controller**: `OrderController` receives the request and calls `orderService.createOrder(order)`.
2. **Aspect Intercepts**: Before the service method runs, `ShardTransactionalAspect` intercepts it.
    - Evaluates the SpEL routing key `"#order.userId"` → extracts `42`.
    - Asks the ring: `ring.getNode("42")` → returns `"ds0"`.
    - Fetches the `EntityManagerFactory` for `"ds0"`.
    - Creates an `EntityManager`, starts a database transaction, and binds it to the current thread (`ShardEntityManagerHolder`).
3. **Service Logic**: `OrderServiceImpl.createOrder()` runs. It has no idea which database it's talking to; it simply asks the `ThreadLocal` holder for the current `EntityManager` and calls `em.persist(order)`.
4. **Aspect Cleans Up**: Once the service method returns, the Aspect commits the transaction and closes the `EntityManager`.

**Why we don't use a framework for routing:**
A framework like ShardingSphere hides routing behind a virtual datasource. Here, we own the routing logic ourselves — the ring decides which node, the `EntityManagerFactory` map gives us the JPA session for that node.

---

## 6. Startup Sequence

```
Spring Boot starts
  │
  ├── DataSourceConfig       → creates HikariDataSource for ds0, ds1
  │                            (connection pools, no ring/flyway yet)
  │
  ├── JpaConfig              → wraps each DataSource in an EntityManagerFactory
  │                            emfMap = { "ds0" → EMF, "ds1" → EMF }
  │
  ├── RingInitializer        → reads dataSourceMap keys, calls ring.addNode() for each
  │   @PostConstruct           ring now has 300 virtual nodes
  │
  ├── FlywayMigrationRunner  → runs V1__init_schema.sql on ds_0 and ds_1
  │   @PostConstruct           t_order table created on both shards
  │
  └── App ready ✅
      ring:   500→ds1, 1500→ds0, ... (300 entries)
      emfMap: "ds0"→EMF(ds_0), "ds1"→EMF(ds_1)
```

---

## 7. Configuration Deep Dive

### `application.properties` — disabling Spring Boot auto-config

By default, Spring Boot auto-creates ONE `DataSource` and ONE `EntityManagerFactory`.
We need **two of each** (one per shard), so we disable the auto-config:

```properties
spring.autoconfigure.exclude=\
  org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,\
  org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration
```

We also define shard connection details under `app.shards[]`:

```properties
app.shards[0].name=ds0
app.shards[0].url=jdbc:postgresql://localhost:5432/ds_0
app.shards[0].username=postgres
app.shards[0].password=password

app.shards[1].name=ds1
app.shards[1].url=jdbc:postgresql://localhost:5433/ds_1
app.shards[1].username=postgres
app.shards[1].password=password
```

---

### `ShardProperties.java` — binding config to Java

`@ConfigurationProperties` maps `app.shards[]` directly into a `List<Shard>`:

```java
@ConfigurationProperties(prefix = "app")
public class ShardProperties {
    private List<Shard> shards;

    public static class Shard {
        private String name;      // "ds0" — the key used everywhere
        private String url;
        private String username;
        private String password;
    }
}
```

**The `name` field is the glue between all four config classes:**

```
application.properties    ShardProperties       RingInitializer        JpaConfig / emfMap
app.shards[0].name=ds0 →  shard.getName()="ds0" → ring.addNode("ds0") → emfMap.get("ds0")
```

All use `"ds0"` as the key. A mismatch anywhere → `NullPointerException` at runtime.

---

### `DataSourceConfig.java` — creating connection pools

Reads `ShardProperties` and creates one `HikariDataSource` per shard.
Does nothing else — no ring seeding, no Flyway.

```java
for (ShardProperties.Shard shard : shardProperties.getShards()) {
    HikariDataSource ds = new HikariDataSource();
    ds.setJdbcUrl(shard.getUrl());
    ds.setUsername(shard.getUsername());
    ds.setPassword(shard.getPassword());
    map.put(shard.getName(), ds);  // "ds0" → connection pool for ds_0
}
```

`HikariDataSource` is a connection pool — instead of opening a fresh DB connection for every request (expensive), it keeps a pool of reusable connections ready.

---

### `JpaConfig.java` — creating one EntityManagerFactory per shard

Takes `dataSourceMap` and wraps each `DataSource` in a JPA `EntityManagerFactory`.

```java
for (Map.Entry<String, DataSource> entry : dataSourceMap.entrySet()) {
    LocalContainerEntityManagerFactoryBean factoryBean = new LocalContainerEntityManagerFactoryBean();
    factoryBean.setDataSource(entry.getValue());
    factoryBean.setPackagesToScan("manjosh.labs.consistenthashing.entity");
    factoryBean.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
    factoryBean.setPersistenceUnitName(shardName);  // unique name — required for multiple EMFs
    factoryBean.afterPropertiesSet();               // initialise manually

    emfMap.put(shardName, factoryBean.getObject());
}
```

Result:
```
emfMap = {
  "ds0" → EntityManagerFactory (knows Order.java, connected to ds_0)
  "ds1" → EntityManagerFactory (knows Order.java, connected to ds_1)
}
```

**Why not use Spring Data JPA repositories?**
Spring Data JPA repositories are bound to a fixed `EntityManager` at compile time. Our shard is chosen at runtime (based on the hash of the key) — so we need to pick the right `EntityManagerFactory` per request manually.

---

### `RingInitializer.java` — seeding the ring at startup

Reads node names from `dataSourceMap.keySet()` and registers each into the ring.

```java
@PostConstruct
public void initRing() {
    for (String shardName : dataSourceMap.keySet()) {
        ring.addNode(shardName);  // places 150 virtual nodes per shard
    }
}
```

To add a new shard: update `application.properties` only. This class picks it up automatically.

---

### `FlywayMigrationRunner.java` — running schema migrations

Runs `V1__init_schema.sql` on each shard at startup.

```java
@PostConstruct
public void migrate() {
    for (Map.Entry<String, DataSource> entry : dataSourceMap.entrySet()) {
        log.info("Running Flyway migration for shard: {}", entry.getKey());
        Flyway.configure()
                .dataSource(entry.getValue())
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }
}
```

Spring Boot's built-in Flyway only runs against one datasource. We run it manually — once per shard. To add a new shard, update `application.properties` only.

---

## 8. Transaction Management

Spring's `@Transactional` works by picking a pre-configured `TransactionManager` at compile time.
Since our shard is decided at runtime, `@Transactional` doesn't know which shard's transaction to manage.

**Solution:** a custom `@ShardTransactional` annotation backed by an AOP aspect.

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
```
1. Intercept @ShardTransactional method
2. Evaluate SpEL "#order.userId" from method args → e.g. "42"
3. ring.getNode("42") → "ds0"
4. emfMap.get("ds0").createEntityManager() → em
5. em.getTransaction().begin()
6. ShardEntityManagerHolder.set(em)    ← ThreadLocal, visible inside the method
7. pjp.proceed()                       ← method runs, uses em from ThreadLocal
8. em.getTransaction().commit()   ✅
   or em.getTransaction().rollback() ❌ on exception
9. em.close(), ThreadLocal.clear()
```

---

## 9. Tech Stack

| Layer | Technology |
|---|---|
| Application | Java 17, Spring Boot 3 |
| Routing Logic | Custom `ConsistentHashRing` (TreeMap + MD5) |
| ORM | JPA / Hibernate (manual multi-datasource setup) |
| Transaction Mgmt | Custom `@ShardTransactional` AOP aspect |
| Database (shards) | PostgreSQL |
| Schema Migration | Flyway (per-shard) |
| Containerisation | Docker |
| Orchestration | Kubernetes (KIND) |

---

## 10. Project Structure

```
consistent-hashing/
├── src/main/java/manjosh/labs/consistenthashing/
│   ├── config/
│   │   ├── ShardProperties.java        ← binds app.shards[] from application.properties
│   │   ├── DataSourceConfig.java       ← creates HikariDataSource per shard
│   │   ├── JpaConfig.java              ← creates EntityManagerFactory per shard
│   │   ├── RingInitializer.java        ← seeds ConsistentHashRing at startup
│   │   ├── FlywayMigrationRunner.java  ← runs schema migration on each shard
│   │   └── OpenApiConfig.java          ← Swagger/OpenAPI config
│   ├── core/
│   │   └── ConsistentHashRing.java     ← TreeMap ring, hash(), addNode/removeNode/getNode
│   ├── transaction/
│   │   ├── ShardTransactional.java     ← custom @ShardTransactional annotation
│   │   ├── ShardEntityManagerHolder.java ← ThreadLocal EntityManager holder
│   │   └── ShardTransactionalAspect.java ← AOP: begin/commit/rollback/close
│   ├── entity/
│   │   └── Order.java                  ← JPA entity (orderId, userId, status)
│   ├── service/
│   │   ├── OrderService.java           ← interface
│   │   └── OrderServiceImpl.java       ← uses ring + @ShardTransactional
│   ├── controller/
│   │   └── OrderController.java        ← REST: POST /orders, GET /orders/{id}
│   ├── exception/
│   │   └── OrderNotFoundException.java
│   └── ConsistentHashingApplication.java
├── src/main/resources/
│   ├── application.properties
│   └── db/migration/
│       └── V1__init_schema.sql         ← CREATE TABLE t_order (runs on both shards)
├── k8s/
│   ├── infra/
│   │   ├── postgres-ds0.yaml       ← K8s manifest for shard 0
│   │   └── postgres-ds1.yaml       ← K8s manifest for shard 1
│   └── app/
│       └── deployment.yaml         ← K8s manifest for Spring Boot app
├── Dockerfile
├── docker-compose.yml
└── README.md
```

---

## 11. How to Run

### Option 1: Run Locally (IDE) + DB in KIND

1. Ensure your local KIND cluster is running:
   ```bash
   kind create cluster --config kind/kind-config.yaml
   ```
2. Deploy the databases into the cluster:
   ```bash
   kubectl apply -f k8s/infra/
   ```
3. Set your IDE Run Configuration for `ConsistentHashingApplication`:
   - Add VM Option: `-Duser.timezone=UTC` (Fixes Windows Asia/Calcutta JDBC bug)
4. Hit **Play** in your IDE.
5. Visit Swagger UI: `http://localhost:8081/api-docs`

### Option 2: Run Entire Stack in Kubernetes

1. Build the Docker image:
   ```bash
   docker build -t consistent-hashing:latest .
   ```
2. Load it into the KIND cluster:
   ```bash
   kind load docker-image consistent-hashing:latest
   ```
3. Deploy everything:
   ```bash
   kubectl apply -f k8s/infra/
   kubectl apply -f k8s/app/
   ```

---

## 12. Key Learnings

1. **Routing Logic is Math, Not Magic**: Unlike using ShardingSphere, implementing our own Consistent Hash Ring using a `TreeMap` and MD5 hashes demystifies how keys are routed deterministically.
2. **Spring Boot Auto-Configuration Conflicts**: When managing manual DataSources and JPA configurations (especially in newer Spring Boot versions), you must aggressively exclude downstream auto-configurations (`DataSourceAutoConfiguration`, `HibernateJpaAutoConfiguration`, etc.) directly on the `@SpringBootApplication` annotation using the exact version-specific package names.
3. **AOP bridges the "Where" and the "What"**: By using a custom `@ShardTransactional` aspect and `ThreadLocal` context, the business logic (`OrderServiceImpl`) remains perfectly clean and completely agnostic of the sharding infrastructure.
4. **Timezone Pitfalls**: The PostgreSQL JDBC driver aggressively validates timezones. Running on Windows with non-standard IANA mappings (like "Asia/Calcutta") will crash the application on startup. Standardizing backend systems to UTC or switching to Alpine-based database images avoids this.

---

## 13. References

- [Consistent Hashing — Wikipedia](https://en.wikipedia.org/wiki/Consistent_hashing)
- [Amazon DynamoDB — Dynamo Paper (2007)](https://www.allthingsdistributed.com/files/amazon-dynamo-sosp2007.pdf)
- [Apache Cassandra — Token Ring](https://cassandra.apache.org/doc/latest/cassandra/architecture/dynamo.html)

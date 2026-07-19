# HashMap Implementation

A custom HashMap built from scratch in Java to understand how hash maps work internally.

## How It Works

A HashMap stores key-value pairs using an **array of buckets**. Each key is hashed to determine which bucket it belongs in. When multiple keys hash to the same bucket (collision), they are chained together using a linked list.

## Core Components

### Entry
An inner class that holds:
- `key` — the lookup key
- `value` — the stored value
- `next` — pointer to the next entry in the chain (for collision handling)

### Bucket Array
An array of `Entry` nodes. Default capacity is 16.

## Operations

| Operation | Description | Time Complexity |
|-----------|-------------|-----------------|
| `put(key, value)` | Inserts or updates a key-value pair | O(1) amortized |
| `get(key)` | Returns the value for a key, or null | O(1) average |
| `remove(key)` | Removes a key and returns its value | O(1) average |
| `size()` | Returns the number of entries | O(1) |

## Collision Handling

When two keys hash to the same bucket index, entries are chained via a linked list:

```
Bucket[3]: (dog, 8) → (cat, 5) → null
```

## Resizing

When the number of entries exceeds `capacity * load factor (0.75)`, the bucket array doubles in size and all entries are rehashed. This keeps chains short and maintains O(1) average performance.

## hashCode and equals Contract

A HashMap relies on two methods from the key object: `hashCode()` and `equals()`. They must follow this contract:

> If two objects are equal (`a.equals(b)` is true), they **must** have the same `hashCode()`.

### What happens when hashCode is not overridden?

The default `hashCode()` from Object returns a value based on the memory address. So two objects with the same data will have **different** hash codes and land in **different** buckets.

```java
class Person {
    String name;
    Person(String name) { this.name = name; }

    @Override
    public boolean equals(Object o) {
        return o instanceof Person p && name.equals(p.name);
    }
    // hashCode NOT overridden
}

MyHashMap<Person, Integer> map = new MyHashMap<>();
map.put(new Person("Alice"), 1);
map.get(new Person("Alice")); // returns null!
```

Even though the two `Person` objects are equal, they hash to different buckets. The `get` looks in the wrong bucket and finds nothing.

### What happens when equals is not overridden?

The default `equals()` from Object uses `==` (reference comparison). So even if two objects hash to the same bucket, the key comparison will fail.

```java
class Person {
    String name;
    Person(String name) { this.name = name; }

    @Override
    public int hashCode() { return name.hashCode(); }
    // equals NOT overridden
}

MyHashMap<Person, Integer> map = new MyHashMap<>();
map.put(new Person("Alice"), 1);
map.get(new Person("Alice")); // returns null!
```

Both objects hash to the same bucket, but `current.key.equals(key)` uses reference equality — they are different objects, so it returns false. The entry is never found.

### The correct implementation

Always override **both** `hashCode()` and `equals()` together:

```java
class Person {
    String name;
    Person(String name) { this.name = name; }

    @Override
    public int hashCode() { return name.hashCode(); }

    @Override
    public boolean equals(Object o) {
        return o instanceof Person p && name.equals(p.name);
    }
}

MyHashMap<Person, Integer> map = new MyHashMap<>();
map.put(new Person("Alice"), 1);
map.get(new Person("Alice")); // returns 1 ✓
```

### Summary

| Scenario | hashCode | equals | Result |
|----------|----------|--------|--------|
| Both overridden | Same bucket ✓ | Matches key ✓ | Works correctly |
| hashCode missing | Different bucket ✗ | Never checked | Key not found |
| equals missing | Same bucket ✓ | Reference check fails ✗ | Key not found |
| Both missing | Different bucket ✗ | Reference check fails ✗ | Key not found |

## Usage

```java
MyHashMap<String, Integer> map = new MyHashMap<>();

map.put("cat", 5);
map.put("dog", 8);

map.get("cat");    // returns 5
map.remove("dog"); // returns 8
map.size();        // returns 1
```

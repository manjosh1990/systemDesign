package systemdesign.hashmap;

class MyHashMap<K,V>{

    private static final int DEFAULT_CAPACITY=16;
    private static final float LOAD_FACTOR=0.75f;
    private int size;

    static class Entry<K,V>{
        final K key;
        V value;
        Entry<K,V> next;

        Entry(K key,V value, Entry<K,V> next){
            this.key = key;
            this.value = value;
            this.next = next;
        }
    }

    private Entry<K,V>[] buckets;

    public MyHashMap(){
        buckets = new Entry[DEFAULT_CAPACITY];
    }

    //find index using hashcode
    private int getBucketIndex(K key){
        return Math.abs(key.hashCode() % buckets.length);
    }

    public void put(K key, V value){
        if(size >= buckets.length * LOAD_FACTOR){
            resize();
        }
        int index = getBucketIndex(key);
        Entry<K,V> current = buckets[index];

        //check if bucket has entries
        while(current != null){
            if(current.key.equals(key)){
                current.value = value; // replace value of existing key
                return;
            }
            current = current.next;
        }

        //if no chaining is found

        buckets[index] = new Entry<>(key,value, buckets[index]);
        size++;
    }

    public V get(K key){
        int index = getBucketIndex(key);
        Entry<K,V> current = buckets[index];

        while(current != null){
            if(current.key.equals(key)){
                return current.value;
            }
            current = current.next;
        }
        return null;
    }

    public int size(){
        return size;
    }

    public V remove(K key){
        int index = getBucketIndex(key);
        Entry<K,V> current = buckets[index];
        Entry<K,V> previous = null;
        while(current != null){
            if(current.key.equals(key)){
                if(previous == null){
                    buckets[index] = current.next;
                }else{
                    previous.next = current.next;
                }
                size--;
                return current.value;
            }
            previous = current;
            current = current.next;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private void resize() {
        Entry<K, V>[] oldBuckets = buckets;
        buckets = new Entry[oldBuckets.length * 2];
        size = 0;

        for (Entry<K, V> head : oldBuckets) {
            while (head != null) {
                put(head.key, head.value);
                head = head.next;
            }
        }
    }

}
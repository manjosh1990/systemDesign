package systemdesign.hashmap;

public class Main {
    public static void main(String[] args) {
        MyHashMap<String, Integer> map = new MyHashMap<>();

        // put
        map.put("cat", 5);
        map.put("dog", 8);
        map.put("bird", 3);
        System.out.println("Size after 3 puts: " + map.size());

        // get
        System.out.println("cat: " + map.get("cat"));
        System.out.println("dog: " + map.get("dog"));
        System.out.println("missing key: " + map.get("fish"));

        // update existing key
        map.put("cat", 10);
        System.out.println("cat after update: " + map.get("cat"));
        System.out.println("Size after update: " + map.size());

        // remove
        System.out.println("Removed dog: " + map.remove("dog"));
        System.out.println("dog after remove: " + map.get("dog"));
        System.out.println("Size after remove: " + map.size());
    }
}

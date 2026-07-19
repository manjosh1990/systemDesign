package meetingscheduler.model;

public class Room {
    private final int id;
    private final String name;
    private final int capacity;
    private final MeetingCalendar calendar;

    public Room(int id, String name, int capacity) {
        this.id = id;
        this.name = name;
        this.capacity = capacity;
        this.calendar = new MeetingCalendar();
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public int getCapacity() { return capacity; }
    public MeetingCalendar getCalendar() { return calendar; }
}

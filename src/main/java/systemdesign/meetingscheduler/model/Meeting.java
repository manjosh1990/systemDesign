package systemdesign.meetingscheduler.model;

import java.util.List;

public class Meeting {
    private final String subject;
    private final TimeInterval interval;
    private final List<User> attendees;
    private Room room;

    public Meeting(String subject, TimeInterval interval, List<User> attendees) {
        this.subject = subject;
        this.interval = interval;
        this.attendees = attendees;
    }

    public String getSubject() { return subject; }
    public TimeInterval getInterval() { return interval; }
    public List<User> getAttendees() { return attendees; }
    public Room getRoom() { return room; }
    public void setRoom(Room room) { this.room = room; }
}

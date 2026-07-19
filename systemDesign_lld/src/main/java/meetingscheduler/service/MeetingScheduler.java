package meetingscheduler.service;

import meetingscheduler.model.Meeting;
import meetingscheduler.model.Room;
import meetingscheduler.model.TimeInterval;
import meetingscheduler.model.User;

import java.util.List;
import java.util.Optional;

public class MeetingScheduler {
    private final List<Room> rooms;
    private final NotificationService notificationService;

    public MeetingScheduler(List<Room> rooms, NotificationService notificationService) {
        this.rooms = rooms;
        this.notificationService = notificationService;
    }

    public Optional<Meeting> book(String subject, TimeInterval interval, List<User> attendees) {
        int requiredCapacity = attendees.size();

        for (Room room : rooms) {
            if (room.getCapacity() >= requiredCapacity && room.getCalendar().isAvailable(interval)) {
                Meeting meeting = new Meeting(subject, interval, attendees);
                meeting.setRoom(room);
                room.getCalendar().addMeeting(meeting);
                notificationService.notify(meeting);
                return Optional.of(meeting);
            }
        }
        return Optional.empty();
    }

    public void cancel(Meeting meeting) {
        meeting.getRoom().getCalendar().removeMeeting(meeting);
    }

    public List<Room> findAvailableRooms(TimeInterval interval, int capacity) {
        return rooms.stream()
                .filter(r -> r.getCapacity() >= capacity && r.getCalendar().isAvailable(interval))
                .toList();
    }
}

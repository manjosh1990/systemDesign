package systemdesign.meetingscheduler;

import systemdesign.meetingscheduler.model.Meeting;
import systemdesign.meetingscheduler.model.Room;
import systemdesign.meetingscheduler.model.TimeInterval;
import systemdesign.meetingscheduler.model.User;
import systemdesign.meetingscheduler.service.EmailNotificationService;
import systemdesign.meetingscheduler.service.MeetingScheduler;

import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class Main {
    public static void main(String[] args) {
        // Create rooms
        Room room1 = new Room(1, "Conference-A", 5);
        Room room2 = new Room(2, "Conference-B", 10);
        List<Room> rooms = Arrays.asList(room1, room2);

        // Create scheduler with email notifications
        MeetingScheduler scheduler = new MeetingScheduler(rooms, new EmailNotificationService());

        // Create users
        User alice = new User(1, "Alice", "alice@example.com");
        User bob = new User(2, "Bob", "bob@example.com");
        User charlie = new User(3, "Charlie", "charlie@example.com");

        // Book a meeting: 10:00 - 11:00 with 2 attendees
        TimeInterval interval1 = new TimeInterval(LocalTime.of(10, 0), LocalTime.of(11, 0));
        Optional<Meeting> meeting1 = scheduler.book("Sprint Planning", interval1, Arrays.asList(alice, bob));
        meeting1.ifPresentOrElse(
                m -> System.out.println("Booked: " + m.getSubject() + " in " + m.getRoom().getName()),
                () -> System.out.println("No room available")
        );

        // Book another meeting at overlapping time: 10:30 - 11:30 with 3 attendees
        TimeInterval interval2 = new TimeInterval(LocalTime.of(10, 30), LocalTime.of(11, 30));
        Optional<Meeting> meeting2 = scheduler.book("Design Review", interval2, Arrays.asList(alice, bob, charlie));
        meeting2.ifPresentOrElse(
                m -> System.out.println("Booked: " + m.getSubject() + " in " + m.getRoom().getName()),
                () -> System.out.println("No room available")
        );

        // Cancel first meeting and rebook at same time in smaller room
        System.out.println("\nCancelling: " + meeting1.get().getSubject());
        scheduler.cancel(meeting1.get());

        TimeInterval interval3 = new TimeInterval(LocalTime.of(10, 0), LocalTime.of(11, 0));
        Optional<Meeting> meeting3 = scheduler.book("Quick Sync", interval3, Arrays.asList(alice, charlie));
        meeting3.ifPresentOrElse(
                m -> System.out.println("Booked: " + m.getSubject() + " in " + m.getRoom().getName()),
                () -> System.out.println("No room available")
        );

        TimeInterval slot = new TimeInterval(LocalTime.of(10, 0), LocalTime.of(11, 0));
        List<Room> available = scheduler.findAvailableRooms(slot, 3);
        available.forEach(r -> System.out.println("Available: " + r.getName() + " (capacity: " + r.getCapacity() + ")"));

    }
}

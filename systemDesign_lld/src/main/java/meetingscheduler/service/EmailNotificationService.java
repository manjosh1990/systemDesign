package meetingscheduler.service;


import meetingscheduler.model.Meeting;
import meetingscheduler.model.User;

public class EmailNotificationService implements NotificationService {

    @Override
    public void notify(Meeting meeting) {
        for (User user : meeting.getAttendees()) {
            System.out.println("Email sent to " + user.getEmail() +
                    " for meeting: " + meeting.getSubject() +
                    " in room: " + meeting.getRoom().getName());
        }
    }
}

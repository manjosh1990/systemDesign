package meetingscheduler.service;

import meetingscheduler.model.Meeting;

public interface NotificationService {
    void notify(Meeting meeting);
}

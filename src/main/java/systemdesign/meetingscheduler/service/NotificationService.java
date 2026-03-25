package systemdesign.meetingscheduler.service;

import systemdesign.meetingscheduler.model.Meeting;

public interface NotificationService {
    void notify(Meeting meeting);
}

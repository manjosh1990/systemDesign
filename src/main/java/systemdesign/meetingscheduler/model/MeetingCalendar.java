package systemdesign.meetingscheduler.model;

import java.util.ArrayList;
import java.util.List;

public class MeetingCalendar {
    private final List<Meeting> meetings;

    public MeetingCalendar() {
        this.meetings = new ArrayList<>();
    }

    public boolean isAvailable(TimeInterval interval) {
        return meetings.stream().noneMatch(m -> m.getInterval().overlaps(interval));
    }

    public void addMeeting(Meeting meeting) {
        meetings.add(meeting);
    }

    public void removeMeeting(Meeting meeting) {
        meetings.remove(meeting);
    }

    public List<Meeting> getMeetings() { return meetings; }
}

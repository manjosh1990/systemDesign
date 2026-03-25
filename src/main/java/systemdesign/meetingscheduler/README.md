# Meeting Scheduler - Low Level Design

## Problem Statement
1. Design a meeting scheduler with N meeting rooms. 
2. Book a meeting in any available room at a given time interval and capacity. 
3. Send notifications to all invited attendees once booked. 
4. Use a meeting room calendar to track meetings.

## Requirements
1. Book a meeting room for a given time slot and capacity
2. Prevent double-booking (conflict detection)
3. Cancel a meeting
4. Find all available rooms for a given time slot and capacity
5. Send notifications to attendees after booking

## Entities & Relationships

```
TimeInterval
├── startTime: LocalTime
├── endTime: LocalTime
└── overlaps(TimeInterval): boolean

User
├── id: int
├── name: String
└── email: String

Meeting
├── subject: String
├── interval: TimeInterval
├── attendees: List<User>
└── room: Room

MeetingCalendar
├── meetings: List<Meeting>
├── isAvailable(TimeInterval): boolean
├── addMeeting(Meeting): void
└── removeMeeting(Meeting): void

Room
├── id: int
├── name: String
├── capacity: int
└── calendar: MeetingCalendar

NotificationService (interface)
└── notify(Meeting): void

EmailNotificationService implements NotificationService
└── notify(Meeting): void

MeetingScheduler
├── rooms: List<Room>
├── notificationService: NotificationService
├── book(subject, interval, attendees): Optional<Meeting>
├── cancel(Meeting): void
└── findAvailableRooms(interval, capacity): List<Room>
```

### Relationships
- `Room` **has-a** `MeetingCalendar` (1:1 composition)
- `MeetingCalendar` **has-many** `Meeting` (1:N)
- `Meeting` **has-a** `TimeInterval` (1:1)
- `Meeting` **has-a** `Room` (1:1 — assigned room)
- `Meeting` **has-many** `User` as attendees (M:N)
- `MeetingScheduler` **depends on** `NotificationService` (interface)

## SOLID Principles Applied

| Principle | How |
|---|---|
| Single Responsibility | Each class has one job — Room holds room info, MeetingCalendar handles conflicts, Scheduler orchestrates booking |
| Open/Closed | NotificationService is an interface — add SMS, push notifications without modifying existing code |
| Liskov Substitution | Any NotificationService implementation (email, SMS) is swappable without breaking the scheduler |
| Interface Segregation | NotificationService has a single lean method |
| Dependency Inversion | MeetingScheduler depends on NotificationService abstraction, injected via constructor |

## How It Works

### Booking Flow
1. User requests a meeting with N attendees for a given time interval
2. Scheduler iterates through rooms where `room.capacity >= attendees.size()`
3. For each room, checks `room.calendar.isAvailable(interval)`
4. Availability check: no existing meeting's interval overlaps with the requested interval
5. Overlap formula: `start1 < end2 && start2 < end1`
6. First available room is assigned → meeting added to room's calendar → attendees notified
7. Returns `Optional.empty()` if no room is available

### Cancellation Flow
1. Remove the meeting from the assigned room's calendar

### Find Available Rooms
1. Filter rooms by capacity and calendar availability for the given interval

## Project Structure
```
systemdesign/meetingscheduler/
├── model/
│   ├── TimeInterval.java
│   ├── User.java
│   ├── Meeting.java
│   ├── MeetingCalendar.java
│   └── Room.java
├── service/
│   ├── NotificationService.java
│   ├── EmailNotificationService.java
│   └── MeetingScheduler.java
└── Main.java
```

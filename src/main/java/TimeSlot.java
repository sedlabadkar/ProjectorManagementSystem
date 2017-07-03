import java.time.*;

// NOTE: A recurring time slot is only indicated by the TimeSlot for its first occurence,
// all other occurences need to calculated from this one.
public class TimeSlot {
    private Instant startDateTime;  // Meeting start date and time
    private Duration duration; // Meeting duration
    private Duration recurInterval;
    private Instant recurEndDateTime; // Meeting end date
    private long teamID;

    public TimeSlot(){}

    public TimeSlot(Instant startDateTime,
                    Duration duration,
                    Duration recurInterval,
                    Instant recurEndDateTime,
                    long teamID) {
        this.startDateTime = startDateTime;
        this.duration = duration;
        this.recurInterval = recurInterval;
        this.recurEndDateTime = recurEndDateTime;
        this.teamID = teamID;
    }

    public TimeSlot(Instant startDateTime,
                    Duration duration,
                    Duration recurInterval,
                    Instant recurEndDateTime) {
        this.startDateTime = startDateTime;
        this.duration = duration;
        this.recurInterval = recurInterval;
        this.recurEndDateTime = recurEndDateTime;
    }

    public Instant getStartDateTime() {
        return startDateTime;
    }

    public void setStartDateTime(Instant startDateTime) {
        this.startDateTime = startDateTime;
    }

    public Duration getDuration() {
        return duration;
    }

    public void setDuration(Duration duration) {
        this.duration = duration;
    }

    public Duration getRecurInterval() {
        return recurInterval;
    }

    public void setRecurInterval(Duration recurInterval) {
        this.recurInterval = recurInterval;
    }

    public Instant getRecurEndDateTime() {
        return recurEndDateTime;
    }

    public void setRecurEndDateTime(Instant recurEndDateTime) {
        this.recurEndDateTime = recurEndDateTime;
    }

    public long getTeamID() {
        return teamID;
    }

    public void setTeamID(long teamID) {
        this.teamID = teamID;
    }
}


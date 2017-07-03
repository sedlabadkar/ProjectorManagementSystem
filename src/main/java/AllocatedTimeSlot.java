import java.time.Duration;
import java.time.Instant;

// A time is slot is allocated when it has an associated AllocatedID, ProjectorID, and TimeSlotID
public class AllocatedTimeSlot extends TimeSlot {
    private long allocatedID;
    private long projectorID;
    private long timeSlotID;

    public long getAllocatedID() {
        return allocatedID;
    }

    public void setAllocatedID(long allocatedID) {
        this.allocatedID = allocatedID;
    }

    public long getProjectorID() {
        return projectorID;
    }

    public void setProjectorID(long projectorID) {
        this.projectorID = projectorID;
    }

    public long getTimeSlotID() {
        return timeSlotID;
    }

    public void setTimeSlotID(long timeSlotID) {
        this.timeSlotID = timeSlotID;
    }

    public AllocatedTimeSlot(long allocatedID, long projectorID, long timeSlotID, long teamID,
                             Instant startDateTime, Duration duration, Duration recurInterval, Instant recurEndDateTime) {
        super(startDateTime, duration, recurInterval, recurEndDateTime, teamID);
        this.allocatedID = allocatedID;
        this.projectorID = projectorID;
        this.timeSlotID = timeSlotID;
    }
}

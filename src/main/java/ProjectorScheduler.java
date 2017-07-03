import com.google.common.collect.Range;
import com.google.common.collect.RangeSet;
import com.google.common.collect.TreeRangeSet;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static java.time.temporal.TemporalAdjusters.firstDayOfYear;

public class ProjectorScheduler {
    private static final int MINUTES_IN_A_YEAR = 525600 /*minutes*/;
    private static final int SUGGESTION_PERIOD_MINS = 120 /*minutes*/;
    private static final int PROJECTOR_COUNT = 3;
    private static ProjectorScheduler instance = null;

    // Logging
    private final Logger appLogger = LoggerFactory.getLogger(ProjectorScheduler.class);

    // The time slots for projector allocation can be thought of as intervals on the time-axis,
    // the key operation we are needed to do on these intervals is to check if two intervals overlap or not (meetings at the same time)
    // In addition, we also need a quick way to look up the next available interval (meeting start time for the same duration)
    // and we also need to be able to quickly add/remove intervals.
    // An interval tree is the best data structure to perform these operations efficiently.
    // For now we are considering a time-axis about the size of 1 year, and the points on this axis are all 1-minute apart
    // This results in an axis of 525600(minutes in a year) data points. The range of time-axis is [0, 525600)
    // We maintain a list of Interval Trees(RangeSet), one Interval tree for each available projector.
    // Assume a projector allocation to start at July 3rd 2017 at 1:00PM, the duration of this allocation is 1 hour
    // The interval on the time axis for this allocation would look something like this [263820, 263880)
    // where, 263820 = July 3rd 1:00PM and 263880 = July 3rd 2:00PM
    List<RangeSet<Integer>> projectorAvailableTimeSlots = new ArrayList<>();

    public static ProjectorScheduler getInstance() {
        if(instance == null) {
            instance = new ProjectorScheduler();
        }
        return instance;
    }

    private ProjectorScheduler() {
        for(int i = 0; i < PROJECTOR_COUNT; i++){
            projectorAvailableTimeSlots.add(TreeRangeSet.create());
        }
        loadData();
    }

    /**
     * Utility function to convert a given epoch time into the minute of current year
     * @param currInstant : Epoch time to be converted
     * @return minute of year
     */
    private int getMinuteOfYear(Instant currInstant){
        return (int)ChronoUnit.MINUTES.between(getStartOfYearEpochTime(), currInstant);
    }

    /**
     * Utility function to convert minute of current year to Epoch time (Instant)
     * @param minuteValue
     * @return
     */
    private Instant getInstantForMinute(int minuteValue){
        return getStartOfYearEpochTime().plus(Duration.ofMinutes(minuteValue));
    }

    /**
     * Function to get the instant of start of the year
     * @return Start of 01st January of the current year in Epoch Time
     */
    private Instant getStartOfYearEpochTime(){
        LocalDate firstDay = LocalDate.now().with(firstDayOfYear());
        return firstDay.atStartOfDay(ZoneId.systemDefault()).toInstant();
    }

    /**
     * Function to return the ID of the projector that can be allocated for the given time
     * This function checks each projector's interval tree for possible intersection with the given time range,
     * if no intersection exists then that projector's ID is returned.
     *
     * @param startMinutes : Allocation starting time
     * @param endMinutes : Allocation ending time
     * @return id of the first projector that is available for the duration
     * @return -1 if a projector cannot be allocated
     */
    private int getAvailableProjectorID(int startMinutes, int endMinutes){
        for (int i = 0; i < PROJECTOR_COUNT; i++) {
            if (!projectorAvailableTimeSlots.get(i).intersects(Range.closedOpen(startMinutes, endMinutes))) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Function to return the ID of the projector that can be allocated for the given time slot
     * @param timeSlotToAllocate : time slot to be allocated
     * @return id of the first projector that is available for the duration
     * @return -1 if a projector cannot be allocated
     */
    private int getAvailableProjectorID(TimeSlot timeSlotToAllocate){
        if (timeSlotToAllocate != null) {
            int startMinutes = getMinuteOfYear(timeSlotToAllocate.getStartDateTime());
            int endMinutes = getMinuteOfYear(timeSlotToAllocate.getStartDateTime().plus(timeSlotToAllocate.getDuration()));
            return getAvailableProjectorID(startMinutes, endMinutes);
        }
        return -1;
    }

    /**
     * Function to return the ID of the projector that can be allocated for the recurring meeting
     * @param timeSlotToAllocate : The first time slot of the recurring meeting
     * @return id of the first projector that is available for *all* occurences of this meeting
     * @return -1 if a projector cannot be allocated
     */
    private int getAvailableProjectorIDForRecurringMeeting(TimeSlot timeSlotToAllocate){
        boolean isSchedulable = true;
        if (timeSlotToAllocate.getStartDateTime().isAfter(timeSlotToAllocate.getRecurEndDateTime())) return -1;
        for(int i = 0; i < PROJECTOR_COUNT; i++) {
            Instant yearStartInstant = getStartOfYearEpochTime();
            Instant yearEndInstant = getStartOfYearEpochTime().plus(Duration.ofMinutes(MINUTES_IN_A_YEAR));
            Instant meetingStart  = timeSlotToAllocate.getStartDateTime();
            Instant meetingEnd = meetingStart.plus(timeSlotToAllocate.getDuration());
            while (meetingStart.isAfter(yearStartInstant) &&
                    meetingStart.isBefore(timeSlotToAllocate.getRecurEndDateTime()) &&
                    meetingEnd.isBefore(yearEndInstant)) {
                if (projectorAvailableTimeSlots
                        .get(i)
                        .intersects(Range.closedOpen(getMinuteOfYear(meetingStart), getMinuteOfYear(meetingEnd)))) {
                    isSchedulable = false;
                    break;
                }
                meetingStart = meetingStart.plus(timeSlotToAllocate.getRecurInterval());
                meetingEnd = meetingStart.plus(timeSlotToAllocate.getDuration());
            }
            if(isSchedulable){
                return i;
            } else {
                isSchedulable = true; // reset
            }
        }
        return -1;
    }

    /**
     * Function to mark the projector as taken for the given time interval
     * @param startMinutes : starting time
     * @param endMinutes : end time
     * @param projectorID : projector id
     * @return true if successfully added
     * @return false if adding failed
     */
    private boolean scheduleProjector(int startMinutes, int endMinutes, int projectorID){
        if (projectorID < 0 || projectorID >= PROJECTOR_COUNT) return false;
        projectorAvailableTimeSlots.get(projectorID).add(Range.closedOpen(startMinutes, endMinutes));
        return true;
    }

    /**
     * Function to mark the projector as taken for the given time interval
     * @param allocatedTimeSlot : Time slot allocated
     * @param projectorID : projector id
     * @return true if successfully added
     * @return false if failed
     */
    private boolean scheduleProjector(TimeSlot allocatedTimeSlot, int projectorID){
        if (allocatedTimeSlot != null) {
            return scheduleProjector(getMinuteOfYear(allocatedTimeSlot.getStartDateTime()),
                    getMinuteOfYear(allocatedTimeSlot.getStartDateTime().plus(allocatedTimeSlot.getDuration()))
                    , projectorID);
        }
        return false;
    }

    /**
     * Function to mark the projector as taken for all the occurences of the given recurring meeting
     * @param allocatedTimeSlot : First time slot of the recurring meeting
     */
    private void scheduleProjectorForRecurringMeeting(AllocatedTimeSlot allocatedTimeSlot){
        Instant yearStartInstant = getStartOfYearEpochTime();
        Instant yearEndInstant = getStartOfYearEpochTime().plus(Duration.ofMinutes(MINUTES_IN_A_YEAR));
        Instant meetingStart  = allocatedTimeSlot.getStartDateTime();
        Instant meetingEnd = meetingStart.plus(allocatedTimeSlot.getDuration());
        while (meetingStart.isAfter(yearStartInstant) && meetingStart.isBefore(allocatedTimeSlot.getRecurEndDateTime()) && meetingEnd.isBefore(yearEndInstant)){
            scheduleProjector(getMinuteOfYear(meetingStart),
                    getMinuteOfYear(meetingEnd), (int)allocatedTimeSlot.getProjectorID());
            meetingStart = meetingStart.plus(allocatedTimeSlot.getRecurInterval());
            meetingEnd = meetingStart.plus(allocatedTimeSlot.getDuration());
        }
    }


    /**
     * Mark the projector as available for the given time interval
     * @param startMinutes : start Time
     * @param endMinutes : end Time
     * @param projectorID
     * @return true if projector marked free successfully
     * @return false if projector id is incorrect
     */
    private boolean cancelProjector(int startMinutes, int endMinutes, int projectorID){
        if (projectorID < 0 || projectorID >= PROJECTOR_COUNT) return false;
        projectorAvailableTimeSlots.get(projectorID).remove(Range.closedOpen(startMinutes, endMinutes));
        return true;
    }

    /**
     * Mark the projector as available for the given time slot
     * @param allocatedTimeSlot
     * @param projectorID
     * @return true if projector marked free successfully
     * @return false if failed
     */
    private boolean cancelProjector(TimeSlot allocatedTimeSlot, int projectorID){
        if (allocatedTimeSlot != null) {
            return cancelProjector(getMinuteOfYear(allocatedTimeSlot.getStartDateTime()),
                    getMinuteOfYear(allocatedTimeSlot.getStartDateTime().plus(allocatedTimeSlot.getDuration())),
                    projectorID);
        }
        return false;
    }

    /**
     * Function to mark all occurences of a recurring meeting as free
     * @param firstTimeSlot : First time slot of the recurring meeting
     * @param projectorID
     * @return
     */
    private boolean cancelProjectorRecurring(TimeSlot firstTimeSlot, int projectorID){
        Instant yearStartInstant = getStartOfYearEpochTime();
        Instant yearEndInstant = getStartOfYearEpochTime().plus(Duration.ofMinutes(MINUTES_IN_A_YEAR));
        Instant meetingStart  = firstTimeSlot.getStartDateTime();
        Instant meetingEnd = meetingStart.plus(firstTimeSlot.getDuration());
        while (meetingEnd.isAfter(meetingStart) && meetingStart.isAfter(yearStartInstant) && meetingStart.isBefore(firstTimeSlot.getRecurEndDateTime()) && meetingEnd.isBefore(yearEndInstant)){
            cancelProjector(getMinuteOfYear(meetingStart),
                    getMinuteOfYear(meetingEnd), projectorID);
            meetingStart = meetingStart.plus(firstTimeSlot.getRecurInterval());
            meetingEnd = meetingStart.plus(firstTimeSlot.getDuration());
        }
        return true;
    }

    /**
     *  Function to load data based on the underlying database as the scheduler starts.
     *  The RangeSets for each of the projector are marked for the current year.
     *  Recurring meetings are marked accordingly
     */
    private void loadData(){
        String query = "SELECT * from time_slots, allocations " +
                " WHERE time_slots.id = allocations.time_slot_id AND " +
                " start >= " + getStartOfYearEpochTime().toEpochMilli() +
                " AND start <= " + getStartOfYearEpochTime().plus(Duration.ofMinutes(MINUTES_IN_A_YEAR)).toEpochMilli() +
                " OR  recur_every > 0";
        try {
            ResultSet queryResult = DataBase.getInstance().query(query);
            if (queryResult != null) {
                AllocatedTimeSlot allocatedTimeSlot;
                while (queryResult.next()) {
                    allocatedTimeSlot = new AllocatedTimeSlot(queryResult.getLong("id"),
                        queryResult.getLong("projector_id"),
                        queryResult.getLong("time_slot_id"),
                        queryResult.getLong("team_id"),
                        Instant.ofEpochMilli(queryResult.getLong("start")),
                        Duration.ofMillis(queryResult.getLong("duration")),
                        Duration.ofMillis(queryResult.getLong("recur_every")),
                        Instant.ofEpochMilli(queryResult.getLong("end")));

                    if (allocatedTimeSlot.getRecurInterval().equals(Duration.ZERO)) {
                        scheduleProjector(allocatedTimeSlot, (int)allocatedTimeSlot.getProjectorID());
                    } else {
                        scheduleProjectorForRecurringMeeting(allocatedTimeSlot);
                    }
                }
            }
        } catch (SQLException sqle){
            appLogger.info("Caught SQL Exception: " + sqle.getMessage());
        }
    }

    /**
     * Function to return AllocatedTimeSlot for the given allocation id
     * NOTE: Recurring meetings are identified by the time slot of their first occurence,
     * this time slots contains all the information about recurrence of the meeting.
     * All subsequent occurences are calculated as we go along
     * @param allocationID
     * @return The allocated time slot, if the record exists
     * @return null if the record doesn't exist
     */
    private AllocatedTimeSlot getTimeSlotForAllocationID(long allocationID) throws SQLException {
        String query = "SELECT * from time_slots, allocations " +
                " WHERE allocations.id = " + allocationID +
                " AND time_slots.id = allocations.time_slot_id";
        AllocatedTimeSlot allocatedTimeSlot = null;
        try {
            ResultSet queryResult = DataBase.getInstance().query(query);
            if (queryResult != null && queryResult.next()){
                allocatedTimeSlot = new AllocatedTimeSlot(allocationID,
                    queryResult.getLong("projector_id"),
                    queryResult.getLong("time_slot_id"),
                    queryResult.getLong("team_id"),
                    Instant.ofEpochMilli(queryResult.getLong("start")),
                    Duration.ofMillis(queryResult.getLong("duration")),
                    Duration.ofMillis(queryResult.getLong("recur_every")),
                    Instant.ofEpochMilli(queryResult.getLong("end")));
            }
        } catch (SQLException sqle) {
            appLogger.info("Caught SQL Exception: " + sqle.getMessage());
            throw new SQLException();
        }
        return allocatedTimeSlot;
    }

    /**
     * Function to find the next available time slot when the projector can be scheduled
     * This function looks for next available start time within the given SUGGESTION_PERIOD_MINS,
     * which is currently set to 120 minutes, or 2 hours.
     * NOTE: Currently this function only handles non-recurring allocations.
     * For Recurring allocations, if they can't be allocated, No next available time is suggested
     * @param requestedTimeSlot
     * @return An AllocatedTimeSlot indicating the start time and duration of the next available time slot
     * @return null if no next time can be suggested
     */
    private AllocatedTimeSlot getNextAvailableTimeSlot(TimeSlot requestedTimeSlot){
        Instant suggestionIntervalEnd = requestedTimeSlot.getStartDateTime().plus(Duration.ofMinutes(SUGGESTION_PERIOD_MINS));
        Instant meetingStart  = requestedTimeSlot.getStartDateTime();
        while (meetingStart.isBefore(suggestionIntervalEnd)){
            int allocatableProjectorID = getAvailableProjectorID(getMinuteOfYear(meetingStart),
                    getMinuteOfYear(meetingStart.plus(requestedTimeSlot.getDuration())));
            if (allocatableProjectorID != -1){
                return new AllocatedTimeSlot(-1, allocatableProjectorID, -1, -1, meetingStart, requestedTimeSlot.getDuration(), requestedTimeSlot.getRecurInterval(), requestedTimeSlot.getRecurEndDateTime());
            }
            meetingStart = meetingStart.plus(Duration.ofMinutes(1));
        }
        return null;
    }

    private List<TimeSlot> getAllocatedTimeSlotsForProjector(int projectorID){
        if (projectorID < 0 || projectorID >= PROJECTOR_COUNT) return null;
        Set<Range<Integer>> ranges = projectorAvailableTimeSlots.get(projectorID).asRanges();
        List<TimeSlot> takenSlots = new ArrayList<>();
        for(Range<Integer> range : ranges) {
            TimeSlot takenSlot = new TimeSlot(getInstantForMinute(range.lowerEndpoint()),
                    Duration.ofMinutes(range.upperEndpoint() - range.lowerEndpoint()),
                    Duration.ZERO,
                    getInstantForMinute(range.lowerEndpoint()));
            takenSlots.add(takenSlot);
        }
        return takenSlots;
    };

    /**
     * Utility function to reserve a projector for the given time slot
     * Takes care of finding out which projector can be scheduled, schedules it and updates the data base
     * @param requestedTimeSlot
     * @return AllocatedTimeSlot if one could be allocated
     *          null if no cannot art projector
     */
    private AllocatedTimeSlot reserveProjector(TimeSlot requestedTimeSlot) throws SQLException{
        if (requestedTimeSlot == null) return null;
        long allocatedProjectorId;

        if (requestedTimeSlot.getRecurInterval().equals(Duration.ZERO)) {
            allocatedProjectorId = getAvailableProjectorID(requestedTimeSlot);
        } else {
            allocatedProjectorId = getAvailableProjectorIDForRecurringMeeting(requestedTimeSlot);
        }

        if (allocatedProjectorId != - 1) {
            scheduleProjector(requestedTimeSlot, (int) allocatedProjectorId);
            try {
                //First column is the id, sqlite db will auto increment it so passing null
                String query = "INSERT INTO time_slots values(NULL, " +
                        requestedTimeSlot.getStartDateTime().toEpochMilli() + " , " +
                        requestedTimeSlot.getDuration().toMillis() + " , " +
                        requestedTimeSlot.getRecurInterval().toMillis() + " , " +
                        requestedTimeSlot.getRecurEndDateTime().toEpochMilli() +
                        ");";
                DataBase.getInstance().query(query);

                ResultSet queryResult = DataBase.getInstance().query("SELECT max(id) AS id FROM time_slots;");

                int timeSlotID = queryResult.getInt("id");

                //First columns is the id, sqlite db will auto increment it so passing null
                query = "INSERT INTO allocations values(NULL, " +
                        allocatedProjectorId + " , " +
                        timeSlotID + " , " +
                        requestedTimeSlot.getTeamID() +
                        " );";

                DataBase.getInstance().query(query);

                queryResult = DataBase.getInstance().query("SELECT max(id) AS id FROM allocations;");

                int allocationID = queryResult.getInt("id");

                AllocatedTimeSlot allocatedTimeSlot = getTimeSlotForAllocationID(allocationID);
                // if this is recurring meeting, let our data structure reflect that
                if (!requestedTimeSlot.getRecurInterval().equals(Duration.ZERO)) {
                    scheduleProjectorForRecurringMeeting(allocatedTimeSlot);
                }
                return allocatedTimeSlot;
            } catch (SQLException sqle) {
                appLogger.info("SQL Exception: " + sqle.getMessage());
                throw new SQLException();
            }
        }
        return null;
    }

    List<TimeSlot> getProjectorSchedule(int projectorID){
        return getAllocatedTimeSlotsForProjector(projectorID);
    }

    /**
     * Entry point for POST request to allocate a projector
     * @param requestedTimeSlot
     * @return An AllocatedTimeSlot if projector could be reserved or if next available time could be suggested
     *          null if a projector could not be reserved and no next available time can be suggested
     */
    AllocatedTimeSlot requestProjector(TimeSlot requestedTimeSlot) throws SQLException{
        AllocatedTimeSlot allocatedTimeSlot = reserveProjector(requestedTimeSlot);
        // if a time slot could not be allocated and the request is not recurring
        if (allocatedTimeSlot == null && requestedTimeSlot.getRecurInterval().equals(Duration.ZERO)){
            // get next available time
            allocatedTimeSlot = getNextAvailableTimeSlot(requestedTimeSlot);
        }
        return allocatedTimeSlot;
    }

    /**
     * Entry point to DELETE request to delete a previously scheduled projector allocation
     * @param allocationId
     * @return true if deleted
     * @return false if no such allocation existed
     */
    boolean deleteProjector(long allocationId) throws SQLException{
        AllocatedTimeSlot allocatedTimeSlot = getTimeSlotForAllocationID(allocationId);
        if (allocatedTimeSlot != null){
            if (allocatedTimeSlot.getRecurInterval().equals(Duration.ZERO))
                cancelProjector(allocatedTimeSlot, (int)allocatedTimeSlot.getProjectorID());
            else
                cancelProjectorRecurring(allocatedTimeSlot, (int)allocatedTimeSlot.getProjectorID());
            // Update datebase
            String query = "DELETE FROM allocations WHERE id = " + allocatedTimeSlot.getAllocatedID() + ";";
            DataBase.getInstance().query(query);

            query = "DELETE FROM time_slots WHERE id = " + allocatedTimeSlot.getTimeSlotID();
            DataBase.getInstance().query(query);
            return true;
        }
        return false;
    }

    /**
     * Entry point for a PUT request to update previously allocated projector
     * @param allocationID
     * @param allocatedTimeSlot
     * @return An AllocatedTimeSlot if update was successful
     *          null if cannot update
     *          null if doesn't exist
     */
    AllocatedTimeSlot updateProjector(long allocationID, TimeSlot allocatedTimeSlot) throws SQLException{
        // Delete the original entry - if it exists
        AllocatedTimeSlot oldAllocatedTimeSlot = getTimeSlotForAllocationID(allocationID);
        if (!deleteProjector(allocationID)) return null;
        // Allocate new
        AllocatedTimeSlot reservedTimeSlot = reserveProjector(allocatedTimeSlot);
        if(reservedTimeSlot != null){
            return reservedTimeSlot;
        } else {
            // if it can't be allocated reschedule the original one - return
            reserveProjector(oldAllocatedTimeSlot);
            return null;
        }
    }
}

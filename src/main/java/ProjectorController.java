import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;

import static java.net.HttpURLConnection.*;
import static spark.Spark.*;
import org.json.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProjectorController {

    // Logging
    private final Logger appLogger = LoggerFactory.getLogger(ProjectorController.class);

    public ProjectorController(final ProjectorScheduler projectorScheduler) {

        before((request, response) -> {
            // TODO: add basic auth
            // if (!authenticated) {
            //     halt(401, "You are not welcome here");
            // }
        });

        after((request, response) -> {
            // DataBase.getInstance().close(); // ref. DataBase#connect
        });


        get("/projector/status/:id", (req, res) ->{
            try {
                int projectorID = Integer.parseInt(req.params(":id"));
                List<TimeSlot> projectorSchedule = projectorScheduler.getProjectorSchedule(projectorID);
                if (projectorSchedule != null) {
                    JSONObject responseJSON = new JSONObject();
                    JSONArray timeSlotsArray = new JSONArray();
                    for (TimeSlot timeSlot : projectorSchedule) {
                        JSONObject timeSlotJSON = new JSONObject();
                        timeSlotJSON.put("startDate", timeSlot.getStartDateTime().toString());
                        timeSlotJSON.put("durationInMinutes", timeSlot.getDuration().toMinutes());
                        timeSlotsArray.put(timeSlotJSON);
                    }
                    responseJSON.put("schedule", timeSlotsArray);
                    res.status(HTTP_OK);
                    res.body(responseJSON.toString());
                } else {
                    res.status(HTTP_BAD_REQUEST);
                }
            } catch (JSONException je) {
                appLogger.error("HTTP_BAD_REQUEST: JSONException " + je.getMessage());
                res.status(HTTP_BAD_REQUEST);
            }
            return "";
        });

        post("/projector/request", (req, res) ->{
            if (req.contentLength() ==  0 || !req.contentType().equals("application/json") ){
                res.status(HTTP_BAD_REQUEST);
            } else {
                try {
                    JSONObject requestJSON = new JSONObject(req.body());
                    String startDateTime = requestJSON.getString("startDateTime");
                    long duration = requestJSON.getLong("duration");
                    long recurInterval = requestJSON.getLong("recurInterval");
                    long teamID = requestJSON.getLong("teamID");
                    String recurEndDateTime = startDateTime;
                    if (recurInterval != 0) {
                        recurEndDateTime = requestJSON.getString("recurEndDateTime");
                    }
                    TimeSlot requestedTimeSlot = new TimeSlot(Instant.parse(startDateTime),
                            Duration.ofMillis(duration),
                            Duration.ofMillis(recurInterval),
                            Instant.parse(recurEndDateTime),
                            teamID);

                    AllocatedTimeSlot allocatedTimeSlot = projectorScheduler.requestProjector(requestedTimeSlot);

                    if (allocatedTimeSlot != null) {
                        JSONObject responseJSON = new JSONObject();
                        if (allocatedTimeSlot.getAllocatedID() == -1) {
                            if(allocatedTimeSlot.getRecurInterval().equals(Duration.ZERO)) {
                                responseJSON.put("nextAvailableStartTime", allocatedTimeSlot.getStartDateTime().toString());
                                res.status(HTTP_OK);
                            }
                        } else {
                            res.status(HTTP_OK);
                            responseJSON.put("projectorID", allocatedTimeSlot.getProjectorID());
                        }
                        responseJSON.put("allocatedID", allocatedTimeSlot.getAllocatedID());
                        res.body(responseJSON.toString());
                    } else {
                        JSONObject responseJSON = new JSONObject();
                        responseJSON.put("allocatedID", -1);
                        res.status(HTTP_OK);
                        res.body(responseJSON.toString());
                    }
                } catch (SQLException sqle) {
                    appLogger.error("HTTP_INTERNAL_ERROR: SQLException " + sqle.getMessage());
                    res.status(HTTP_INTERNAL_ERROR);
                } catch (JSONException je) {
                    appLogger.error("HTTP_BAD_REQUEST: JSONException " + je.getMessage());
                    res.status(HTTP_BAD_REQUEST);
                } catch (DateTimeParseException dtpe){
                    appLogger.error("HTTP_BAD_REQUEST: DateTimeParseException " + dtpe.getMessage());
                    res.status(HTTP_BAD_REQUEST);
                }
            }
            return "";
        });

        delete("/projector/delete", (req, res) -> {
            JSONObject requestJSON = new JSONObject(req.body());
            long allocationID = requestJSON.getLong("allocationID");
            try {
                if (projectorScheduler.deleteProjector(allocationID))
                    res.status(HTTP_OK);
                else
                    res.status(HTTP_NOT_FOUND);
            } catch (SQLException sqle){
                appLogger.error("HTTP_INTERNAL_ERROR: SQLException " + sqle.getMessage());
                res.status(HTTP_INTERNAL_ERROR);
            } catch (JSONException je) {
                appLogger.error("HTTP_BAD_REQUEST: JSONException " + je.getMessage());
                res.status(HTTP_BAD_REQUEST);
            }
            return "";
        });

        put("/projector/update", (req, res) -> {
            if (req.contentLength() ==  0 || !req.contentType().equals("application/json") ){
                res.status(HTTP_BAD_REQUEST);
            } else {
                try {
                    JSONObject requestJSON = new JSONObject(req.body());
                    long allocationID = requestJSON.getLong("allocationID");
                    String startDateTime = requestJSON.getString("startDateTime");
                    long duration = requestJSON.getLong("duration");
                    long recurInterval = requestJSON.getLong("recurInterval");
                    String recurEndDateTime = startDateTime;
                    if (recurInterval != 0) {
                        recurEndDateTime = requestJSON.getString("recurEndDateTime");
                    }
                    TimeSlot timeSlotToUpdate = new TimeSlot(Instant.parse(startDateTime),
                            Duration.ofMillis(duration),
                            Duration.ofMillis(recurInterval),
                            Instant.parse(recurEndDateTime));

                    AllocatedTimeSlot updatedTimeSlot = projectorScheduler.updateProjector(allocationID, timeSlotToUpdate);
                    if (updatedTimeSlot != null) {
                        JSONObject responseJSON = new JSONObject();
                        if (updatedTimeSlot.getAllocatedID() == -1) {
                            responseJSON.put("nextAvailableStartTime", updatedTimeSlot.getStartDateTime().toString());
                            res.status(HTTP_OK);
                        } else {
                            res.status(HTTP_OK);
                            responseJSON.put("projectorID", updatedTimeSlot.getProjectorID());
                            responseJSON.put("allocatedID", updatedTimeSlot.getAllocatedID());
                        }
                        res.body(responseJSON.toString());
                    } else {
                        JSONObject responseJSON = new JSONObject();
                        responseJSON.put("allocatedID", -1);
                        res.status(HTTP_OK);
                        res.body(responseJSON.toString());
                    }
                } catch (SQLException sqle) {
                    appLogger.error("HTTP_INTERNAL_ERROR: SQLException " + sqle.getMessage());
                    res.status(HTTP_INTERNAL_ERROR);
                } catch (JSONException je) {
                    appLogger.error("HTTP_BAD_REQUEST: JSONException " + je.getMessage());
                    res.status(HTTP_BAD_REQUEST);
                } catch (DateTimeParseException dtpe){
                    appLogger.error("Incorrect date format " + dtpe.getMessage());
                    res.status(HTTP_BAD_REQUEST);
                }
            }
            return "";
        });
    }
}

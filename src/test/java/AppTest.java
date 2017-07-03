import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import spark.Spark;
import spark.utils.IOUtils;
import static java.net.HttpURLConnection.*;
import static org.junit.Assert.*;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// TODO: Testcases should not modify the database
// NOTE: For now we are just updating the database, this means that we need to delete the db file before running a test
// Or change the expected values in a test accordingly.
public class AppTest {
    // Logging
    private final Logger appLogger = LoggerFactory.getLogger(ProjectorScheduler.class);

    // We should not update the DB during test cases.
    // but since we are, we clear it before any tests.
    private void clearDatabase(){
        String[] clearDB = {
            "DELETE FROM allocations;",
            "DELETE FROM time_slots;",
        };
        try {
            DataBase.getInstance().update(clearDB);
        } catch (ClassNotFoundException cnfe){
            appLogger.info(cnfe.getMessage());
        }
    }

    @Before
    public void beforeClass(){
        clearDatabase();
        App.main(null);
        //Wait here to let the spark framework initialize and open port
        try {
            Thread.sleep(2000);
        } catch (Exception e){

        }
    }

    @After
    public void afterClass(){
        Spark.stop();
    }

    private void getProjectorBookings(int projectorID, int expectedResponseCode){
        String path = "/projector/status/" + projectorID;
        TestResponse response = request("GET", path, "");
        assertEquals(expectedResponseCode, response.status);
        if (expectedResponseCode == HTTP_OK) {
            appLogger.info("Received response: " + response.json());
            JSONObject responseJSON = response.json();
            assertNotEquals(0, responseJSON.length());
        }
    }


    private void updateProjectorBooking(int allocatedID, String startTime, long duration, long recurInterval, String recurEndDateTime, int expectedResponseCode, Integer expectedProjectorID, Integer expectedAllocationID){
        JSONObject reqJSON = new JSONObject();
        reqJSON.put("allocationID", allocatedID);
        reqJSON.put("startDateTime", startTime);
        reqJSON.put("duration", duration);
        reqJSON.put("recurInterval", recurInterval);
        reqJSON.put("recurEndDateTime", recurEndDateTime);
        TestResponse response = request("PUT", "/projector/update", reqJSON.toString());
        assertEquals(expectedResponseCode, response.status);
        if (response.status == HTTP_OK){
            JSONObject responseJson = response.json();
            appLogger.info("Received response: " + response.json());
            if (expectedProjectorID != null) assertEquals((long)expectedProjectorID, responseJson.getInt("projectorID"));
            if (expectedAllocationID != null)assertEquals((long)expectedAllocationID, responseJson.getInt("allocatedID"));
        }
    }

    private void deleteProjectorBooking(int allocatedID, int expectedResponseCode){
        JSONObject reqJSON = new JSONObject();
        reqJSON.put("allocationID", allocatedID);
        TestResponse response = request("DELETE", "/projector/delete", reqJSON.toString());
        assertEquals(expectedResponseCode, response.status);
    }

    private void requestProjectorBooking(String startTime, long duration, long recurInterval, long teamID, String recurEndDateTime, int expectedResponseCode, Integer expectedProjectorID, Integer expectedAllocationID, boolean nextAvailableTime){
        JSONObject reqJSON = new JSONObject();
        reqJSON.put("startDateTime", startTime);
        reqJSON.put("duration", duration);
        reqJSON.put("recurInterval", recurInterval);
        reqJSON.put("recurEndDateTime", recurEndDateTime);
        reqJSON.put("teamID", teamID);
        TestResponse response = request("POST", "/projector/request", reqJSON.toString());
        assertEquals(expectedResponseCode, response.status);
        if (response.status == HTTP_OK) {
            JSONObject responseJson = response.json();
            appLogger.info("Received response: " + response.json());
            if (expectedProjectorID != null)
                assertEquals((long) expectedProjectorID, responseJson.getInt("projectorID"));
            if (expectedAllocationID != null)
                assertEquals((long) expectedAllocationID, responseJson.getInt("allocatedID"));
            if (nextAvailableTime) assertNotEquals(0, responseJson.getString("nextAvailableStartTime").length());
        }
    }

    /**
     * Tests different post scenarios
     * ****NOTE: PLEASE DELETE THE DB FILE BEFORE RUNNING THIS TEST. OR CHANGE THE EXPECTED VALUES ACCORDINGLY.****
     * 1. Team 1 requests projector from Noon to 1:00PM on July 7th 2017 => System assigns P1(index:0)
     * 2. Team 2 requests projector from Noon to 1:00PM on July 7th 2017 recurring every 3 days => System assigns P2(index:1)
     * 3. Team 3 requests projector from Noon to 1:00PM on July 7th 2017 => System assigns P3(index:2)
     * 4. Team 4 requests projector from Noon to 1:00PM on July 7th 2017 => System can't assign any projector
     * 5. Team 4 requests projector from Noon to 1:00PM on July 4th 2017 recurring every 3 days => System can't assign any projector
     * 6. Team 4 requests projector but the starting date string is invalid => System can't assign any projector(400 Bad Request)
     */
    @Test
    public void postTest(){
        requestProjectorBooking("2017-07-07T12:00:00.00Z",
                3600000,
                0,
                1,
                "2017-07-05T14:00:00.00Z",
                HTTP_OK,
                0,
                1,
                false);

        requestProjectorBooking("2017-07-07T12:00:00.00Z",
                3600000,
                259200000,
                2,
                "2017-08-05T14:00:00.00Z",
                HTTP_OK,
                1,
                2,
                false);

        requestProjectorBooking("2017-07-07T12:00:00.00Z",
                3600000,
                0,
                3,
                "2017-08-05T14:00:00.00Z",
                HTTP_OK,
                2,
                3,
                false);

        // Unsuccessful booking
        requestProjectorBooking("2017-07-07T12:00:00.00Z",
                3600000,
                0,
                4,
                "2017-07-05T14:00:00.00Z",
                HTTP_OK,
                null,
                -1,
                true);

        // Unsuccessful recursive booking
        requestProjectorBooking("2017-07-04T12:00:00.00Z",
                3600000,
                259200000,
                4,
                "2017-08-08T14:00:00.00Z",
                HTTP_OK,
                null,
                -1,
                false);

        // Bad request
        requestProjectorBooking("kajsdkls",
                3600000,
                259200000,
                4,
                "2017-08-08T14:00:00.00Z",
                HttpURLConnection.HTTP_BAD_REQUEST,
                null,
                -1,
                false);
    }

    /**
     * Test basic get functionality
     * ****NOTE: PLEASE DELETE THE DB FILE BEFORE RUNNING THIS TEST. OR CHANGE THE EXPECTED VALUES ACCORDINGLY.****
     * 1. Get projector schedule for projector 1 => System returns schedule
     * 2. Get projector schedule for projector 2 => System returns schedule
     * 3. Get projector schedule for projector 3 => System returns schedule
     * 4. Get projector schedule for projector 5 => System says bad request becuase projector 5 doesn't exist
     * 5. Get projector schedule for projector -1 => System says bad request, no projector -1
     */
    @Test
    public void getTest(){
        requestProjectorBooking("2017-07-07T15:00:00.00Z",
                3600000,
                0,
                1,
                "2017-07-05T14:00:00.00Z",
                HTTP_OK,
                0,
                1,
                false);

        requestProjectorBooking("2017-07-07T15:00:00.00Z",
                3600000,
                259200000,
                2,
                "2017-08-05T14:00:00.00Z",
                HTTP_OK,
                1,
                2,
                false);

        requestProjectorBooking("2017-07-07T15:00:00.00Z",
                3600000,
                0,
                3,
                "2017-08-05T14:00:00.00Z",
                HTTP_OK,
                2,
                3,
                false);


        getProjectorBookings(0, HTTP_OK);

        getProjectorBookings(1, HTTP_OK);

        getProjectorBookings(2, HTTP_OK);

        getProjectorBookings(5, HTTP_BAD_REQUEST);

        getProjectorBookings(-1, HTTP_BAD_REQUEST);
    }

    /**
     * Test basic update functionality
     * ****NOTE: PLEASE DELETE THE DB FILE BEFORE RUNNING THIS TEST. OR CHANGE THE EXPECTED VALUES ACCORDINGLY.****
     * 1. Update #1 to July 3rd 2017 at 2PM to 3PM => System is able to change the booking (New booking number = 4)
     * 2. Update #2 to July 6th at 2PM to 3PM, recurring every 3 days => System is able to change the booking (New booking number = 5)
     * 3. Update #100 to July 2nd at 2PM to 3PM, recurring every 3 days => Can't be changed (100 doesn't exist)
     * 4. Malformed update request => Can't be changed (400 Bad request)
     * 5. Team 2 requests for projector July 8th 8PM to 9PM => System assigns P1 (booking number = 6)
     * 6. Team 1 requests for projector July 8th 8PM to 9PM => System assigns P2 (booking number = 7)
     * 7. Team 3 requests for projector July 8th 8PM to 9PM => System assigns P3 (booking number = 8)
     * 8. Update #1 => System is unable to change as the 1 was already modified before and has been assigned new ID (4)
     * 9. Update #5 Change recurring to non-recurring => System is able to update (booking number  = 9)
     */
    @Test
    public void putTest(){
        requestProjectorBooking("2017-07-07T09:00:00.00Z",
                3600000,
                0,
                1,
                "2017-07-05T14:00:00.00Z",
                HTTP_OK,
                0,
                1,
                false);

        requestProjectorBooking("2017-07-07T09:00:00.00Z",
                3600000,
                259200000,
                2,
                "2017-08-05T14:00:00.00Z",
                HTTP_OK,
                1,
                2,
                false);

        requestProjectorBooking("2017-07-07T09:00:00.00Z",
                3600000,
                0,
                3,
                "2017-08-05T14:00:00.00Z",
                HTTP_OK,
                2,
                3,
                false);

        // Update successfully (Non-recurring to non-recurring)
        updateProjectorBooking(1,
                "2017-07-03T14:00:00.00Z",
                3600000,
                0,
                "2017-08-02T14:00:00.00Z",
                HTTP_OK,
                null,
                null);

        // Update Recurring Successfully (recurring to recurring)
        updateProjectorBooking(2,
                "2017-07-20T14:00:00.00Z",
                3600000,
                86400000,
                "2017-07-25T14:00:00.00Z",
                HTTP_OK,
                null,
                5);

        // Update fail
        updateProjectorBooking(100,
                "2017-07-02T14:00:00.00Z",
                3600000,
                259200000,
                "2017-08-02T14:00:00.00Z",
                HTTP_OK,
                null,
                -1);

        // Update fail
        updateProjectorBooking(1,
                "2017-07-02T14:00kljasdlksajd:00.00Z",
                3600000,
                259200000,
                "2017-08-02T14:00:00.00Z",
                HTTP_BAD_REQUEST,
                null,
                null);

        requestProjectorBooking("2017-07-08T20:00:00.00Z",
                3600000,
                0,
                2,
                "2017-07-05T14:00:00.00Z",
                HTTP_OK,
                0,
                null,
                false);

        requestProjectorBooking("2017-07-08T20:00:00.00Z",
                3600000,
                0,
                1,
                "2017-07-05T14:00:00.00Z",
                HTTP_OK,
                1,
                null,
                false);

        requestProjectorBooking("2017-07-08T20:00:00.00Z",
                3600000,
                0,
                3,
                "2017-07-05T14:00:00.00Z",
                HTTP_OK,
                2,
                null,
                false);

        updateProjectorBooking(1,
                "2017-07-08T20:00:00.00Z",
                3600000,
                0,
                "2017-08-02T14:00:00.00Z",
                HTTP_OK,
                null,
                -1);


        //Recurring to non-recurring update
        updateProjectorBooking(5,
                "2017-07-09T20:00:00.00Z",
                3600000,
                259200000,
                "2017-08-02T14:00:00.00Z",
                HTTP_OK,
                null,
                9);

    }

    /**
     * Test basic delete functionality
     * ****NOTE: PLEASE DELETE THE DB FILE BEFORE RUNNING THIS TEST. OR CHANGE THE EXPECTED VALUES ACCORDINGLY.****
     * 1. Delete booking #3 => System is able to delete
     * 2. Delete booking #3 => System can't delete (404 Not Found)
     */
    @Test
    public void deleteTest(){
        requestProjectorBooking("2017-07-07T21:00:00.00Z",
                3600000,
                0,
                1,
                "2017-07-05T14:00:00.00Z",
                HTTP_OK,
                0,
                1,
                false);

        requestProjectorBooking("2017-07-07T21:00:00.00Z",
                3600000,
                259200000,
                2,
                "2017-08-05T14:00:00.00Z",
                HTTP_OK,
                1,
                2,
                false);

        requestProjectorBooking("2017-07-07T21:00:00.00Z",
                3600000,
                0,
                3,
                "2017-08-05T14:00:00.00Z",
                HTTP_OK,
                2,
                3,
                false);


        deleteProjectorBooking(3, HTTP_OK);

        deleteProjectorBooking(3, HTTP_NOT_FOUND);
    }

    /**
     * ****NOTE: PLEASE DELETE THE DB FILE BEFORE RUNNING THIS TEST. OR CHANGE THE EXPECTED VALUES ACCORDINGLY.****
     */
    @Test
    public void basicTest(){
        requestProjectorBooking("2017-07-05T14:00:00.00Z",
                3600000,
                0,
                1,
                "2017-07-05T14:00:00.00Z",
                HTTP_OK,
                0,
                1,
                false);

        requestProjectorBooking("2017-07-05T14:00:00.00Z",
                3600000,
                0,
                2,
                "2017-07-05T14:00:00.00Z",
                HTTP_OK,
                1,
                2,
                false);

        requestProjectorBooking("2017-07-05T14:00:00.00Z",
                3600000,
                0,
                3,
                "2017-07-05T14:00:00.00Z",
                HTTP_OK,
                2,
                3,
                false);

        updateProjectorBooking(3,
                "2017-07-02T14:00:00.00Z",
                3600000,
                259200000,
                "2017-08-02T14:00:00.00Z",
                HTTP_OK,
                2,
                null);

        requestProjectorBooking("2017-07-09T14:00:00.00Z",
                3600000,
                0,
                1,
                "2017-07-05T14:00:00.00Z",
                HTTP_OK,
                0,
                null,
                false);

        deleteProjectorBooking(1,
                HTTP_OK);

        updateProjectorBooking(30,
                "2017-07-02T14:00:00.00Z",
                3600000,
                259200000,
                "2017-08-02T14:00:00.00Z",
                HTTP_OK,
                null,
                -1);

        updateProjectorBooking(2,
                "2017-07-02T15:00:00.00Z",
                3600000,
                0,
                "2017-08-02T14:00:00.00Z",
                HTTP_OK,
                0,
                null);

        updateProjectorBooking(1,
                "2017-07-02T14:00:00.00Z",
                3600000,
                259200000,
                "2017-08-02T14:00:00.00Z",
                HTTP_OK,
                null,
                -1);

    }

    /**
     * ****NOTE: PLEASE DELETE THE DB FILE BEFORE RUNNING THIS TEST. OR CHANGE THE EXPECTED VALUES ACCORDINGLY.****
     */
    @Test
    public void nextAvailableStartTimeTest(){
        requestProjectorBooking("2017-07-10T11:00:00.00Z",
                3600000,
                0,
                1,
                "2017-07-05T14:00:00.00Z",
                200,
                0,
                null,
                false);

        requestProjectorBooking("2017-07-10T11:00:00.00Z",
                3600000,
                0,
                2,
                "2017-07-05T14:00:00.00Z",
                200,
                1,
                null,
                false);

        requestProjectorBooking("2017-07-10T11:00:00.00Z",
                3600000,
                0,
                3,
                "2017-07-05T14:00:00.00Z",
                200,
                2,
                null,
                false);

        requestProjectorBooking("2017-07-10T11:00:00.00Z",
                3600000,
                0,
                4,
                "2017-07-05T14:00:00.00Z",
                200,
                null,
                null,
                true);

    }

    /**
     * 1. Team1 wants a projector from 10:00 am to 12:00 pm => (System can assign P1)
     2. Team2 wants a projector from 10:30 am to 11:30 am => (System cannot assign P1 as it is
     overlapping with Team1’s reserved slot, but it can assign P2)
     3. Team3 wants a projector from 11:10 am to 12:00 pm => (System cannot assign P1 or P2 but can
     assign P3, etc.)
     4. Team4 wants a projector between 11:00 am - 11:30 am => (System cannot assign any projectors
     now since they’re all taken) - System suggests next available time
     5. Now, Team1 cancels their 10am - 12pm slot => System frees up P1
     6. Now, Team4 wants a projector between 10am and 11am => System assigns P1
     */
    @Test
    public void testScenario(){
        requestProjectorBooking("2017-07-07T10:00:00.00Z",
                7200000,
                0,
                1,
                "2017-07-05T14:00:00.00Z",
                HTTP_OK,
                0,
                1,
                false);

        requestProjectorBooking("2017-07-07T10:30:00.00Z",
                3600000,
                0,
                2,
                "2017-07-05T14:00:00.00Z",
                HTTP_OK,
                1,
                2,
                false);

        requestProjectorBooking("2017-07-07T11:10:00.00Z",
                3000000,
                0,
                3,
                "2017-07-05T14:00:00.00Z",
                HTTP_OK,
                2,
                3,
                false);

        requestProjectorBooking("2017-07-07T11:00:00.00Z",
                1800000,
                0,
                4,
                "2017-07-05T14:00:00.00Z",
                HTTP_OK,
                null,
                -1,
                true);

        deleteProjectorBooking(1, HTTP_OK);

        requestProjectorBooking("2017-07-07T10:00:00.00Z",
                3600000,
                0,
                4,
                "2017-07-05T14:00:00.00Z",
                HTTP_OK,
                0,
                null,
                false);

    }

    private TestResponse request(String method, String path, String json) {
        try {
            URL url = new URL("http://localhost:4567" + path);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod(method);
            connection.setDoOutput(true);
            if (json.length() != 0) {
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("Accept", "application/json");
                OutputStreamWriter streamWriter = new OutputStreamWriter(connection.getOutputStream());
                streamWriter.write(json);
                streamWriter.flush();
            }
            connection.connect();
            String body = null;
            if (connection.getResponseCode() == HTTP_OK)
                body = IOUtils.toString(connection.getInputStream());
            return new TestResponse(connection.getResponseCode(), body);
        } catch (IOException e) {
            e.printStackTrace();
            fail("Sending request failed: " + e.getMessage());
            return null;
        }
    }

    private static class TestResponse {

        public final String body;
        public final int status;

        public TestResponse(int status, String body) {
            this.status = status;
            this.body = body;
        }

        public JSONObject json() {
            return new JSONObject(body);
        }
    }
}
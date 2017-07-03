# Projector Management System

## What is it?

* A RESTful projector management system that allows teams to reserve projectors, update their reservations, cancel their reservations, and also check the availability of projectors.

* Projector bookings can be one time or recurring. A recurring booking is indicated by its first time slot, which contains the information about recurrence interval and the end time when recurrence should stop. For non-recurring bookings, the recurrence interval is set to 0, and end time field is irrelevant. 

* The projector only grants a recurring meeting, if all possible instances of the meeting (in the current year) can be scheduled on a single projector. 

* The implementation uses a list of RangeSets (or Interval Trees) to keep track of booked time slots for the current year. When a new POST request is received to reserve a slot, these datastructures are consulted, and if an allocation is possible then it is made. DB is updated accordingly. 

* If a projector cannot be assigned for the requested slot then the system will suggest next start time when a projector is available. However, next start time is only suggested for non-recurring meetings. If a recurring meeting slot cannot be assigned a projector, system will not suggest another time.

* Suppose Team 1 would like to reserve a projector starting from 1PM July 3rd 2017, and they'd like to reserve it for 1 hour. 
	
    * A projector can be reserved by sending a POST request to /projector/request with the following JSON content:
    
	```json
	{
	"startDateTime":"2017-07-03T13:00:00.00Z",
	"duration":3600000,
	"recurInterval":0,
	"recurEndDateTime":"2017-08-01T00:00:00.00Z",
	"teamID":1
	}
	```
    * The above booking would start on July 3rd 2017 at 1:00PM(`startDateTime`) and will last for 1 hour(`Duration` in milliseconds). `recurInterval = 0` indicates that this is a one time meeting. 
 * If the team would like to reserve this projector at this time every 3 days, the POST request should contain the following JSON:
    ```json
    {
	"startDateTime":"2017-07-03T13:00:00.00Z",
	"duration":3600000,
	"recurInterval":259200000,
	"recurEndDateTime":"2017-08-01T00:00:00.00Z",
	"teamID":1
	}
	```
    * The above booking will start on July 3rd 2017 at 1:00PM (as indicated by `startDateTime`), each session would last 1 hour(`duration` in milliseconds), and it would recur every 3 days(`recurInterval` in milliseconds) till 1st August 2017 (`recurEndDateTime`)

	* If the allocation was successful below JSON would be returned 
		```json
    	{
      		"projectorID":0,
      		"allocatedID":4
    	}
    	```
 	* if allocation fails, below JSON would be returned 
 		```json
 		{
            "allocatedID":-1
        }
         ```

* if the team would now like to update their booking then they can do so by sending a PUT request to `/projector/update` with the following JSON:
   ```json
   {
      "allocationID":7,
      "startDateTime":"2017-07-03T15:00:00.00Z",
      "duration":0,
      "recurInterval":259200000,
      "recurEndDateTime":"2017-08-01T00:00:00.00Z"
  }
  ```
	* Where allocationID points to the booking that needs to be changed
	
* To delete a booking send a DELETE request to '/projector/delete` with following JSON:
	```json
    {
		"allocationID":3
	}	
    ```
 * To get the schedule of a particular projector send a GET request to `/projector/status/0` .This will return the schedule of projector 1(index 0)

## Dependencies

Written in Java, using Spark framework(http://sparkjava.com/) and SQLite 3.3+ as DB

Other dependencies are:

* JDBC SQLite driver
* slf4j api
* org.json api
* Google guava RangeSet 
* JUnit

## Building and Executing

This a maven project, all the dependencies will be handled by maven. Use the following commands:

* `mvn test` - Executes basic tests on the app 

* `mvn -e exec:java -Dexec.mainClass="App"` - Will build and execute the app

* `mvn clean package` = Generates 2 jar files under target. jar-with-dependencies can be executed as it includes all the dependencies
	* The resulting jar can be executed as `java -cp target/LeanTaasCodingTest-1.0-SNAPSHOT-jar-with-dependencies.jar App`

* App will run on localhost:4567






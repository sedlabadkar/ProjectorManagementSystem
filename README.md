# Projector Management System

## What is it?

* A RESTful projector management system that allows teams to reserve projectors, update their reservations, cancel their reservations, and also check the availability of projectors.

* Projector bookings can be one time or recurring. A recurring booking is indicated by its first time slot, which contains the information about recurrence interval and the end time when recurrence should stop. For non-recurring bookings, the recurrence interval is set to 0, and end time field is irrelevant. 

* The projector only grants a recurring meeting, if all possible instances of the meeting (in the current year) can be scheduled on a single projector. 

* The implementation uses a list of RangeSets (or Interval Trees) to keep track of booked time slots for the current year. When a new POST request is received to reserve a slot, these datastructures are consulted, and if an allocation is possible then it is made. DB is updated accordingly. 

* If a projector cannot be assigned for the requested slot then the system will suggest next start time when a projector is available. However, next start time is only suggested for non-recurring meetings. If a recurring meeting slot cannot be assigned a projector, system will not suggest another time.

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




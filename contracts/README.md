# Component for managing contracts

Runs using quarkus. Dev mode with hot-replacement:

    mvn compile quarkus:dev

Persistence is in mysql.

Connecting via a mysql client:

    docker run -it --rm mysql mysql -h maxant.ch --port 30300 -u root -psecret contracts

Create DB:

    DROP DATABASE contracts;
    CREATE DATABASE contracts CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

Tables, etc. are created using Flyway.

H2 Web UI:

    java -cp /home/ant/.m2/repository/mysql/mysql-connector-java/8.0.17/mysql-connector-java-8.0.17.jar:/home/ant/Downloads/h2-1.4.199.jar org.h2.tools.GUIConsole

# TODO

- implement updating versions of contracts
- try putting further attributes into their own table
- consider backdated changes to attributes for both cases
- is doing so when all attributes are in own table, properly normalised?
- or is the problem that the attributes have different valid periods, and so belong in different tables?
- how the hell would one UI update just one attribute? I guess  like "change discount on contract", which leaves the payment frequency, and if that was updated to a given time, then it mustnt be over-written
- @QuarkusTest
- mysql client is showing localdatetime as timezoned, even tho java works...
- reading datetime from mysql is causing millis to be dropped when they are zero

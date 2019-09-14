# Component for managing contracts

Each time a customer signs a new contract, there is a new version.
The version is valid 'from' a given timestamp 'to' another one.
A version references an instance of a product. 
A product is instantiated by creating a row in the database containing specific values.
Many contracts contain many instances of a product, just like two customers shopping
carts can contain two different boxes of corn flakes. 
The difference between a box of corn flakes and an insurance product is that the latter can be tailored to the
customers exact needs, and that is done by setting specific attributes according to the customers wishes. 
So having two instances of an insurance product can be like thinking of having two boxes of corn flakes, 
one with some sugar added, the other with sweetner.
Initially, a product instance shares the same validity as the contract which it belongs to.
But as values are changed in the product instance, new versions of the product instance are created (new rows in the DB).
However there is only ever one which is valid at any instant in time.
Let's call each row in the contracts table a "big timeline".
Let's call each row in the products table a "small timeline".

## Running

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

- creating a contract version needs to first validate that there is no overlap with other versions with the same number!
- try putting further attributes into their own table => or even verticalised, to compare performance...
- or is the problem that the attributes have different valid periods, and so belong in different tables?
- @QuarkusTest
- reading datetime from mysql is causing millis to be dropped when they are zero
- mysql client is showing localdatetime as timezoned, even tho java and intellij plugin works... how to tell mysql client to behave?

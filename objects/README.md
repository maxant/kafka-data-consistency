# Objects

A component for managing objects linked to contracts.
Houses, cars, specially insured items, etc.

# Installation

The database must exist in order to get flyway to work:

    CREATE DATABASE if not exists objects CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

# Technical

This component uses quarkus with vertx and attempts to be entirely reactive (non-blocking).

# Development

    mvn compile quarkus:dev

    curl -v -X GET localhost:8086/objects/2098aa6c-f13b-4691-a0b3-0dfbaab990be | jq

# Take Aways

Notice how the logging is done on a different thread than the one that accepts the request:

    (executor-thread-30) starting request for id 2098aa6c-f13b-4691-a0b3-0dfbaab990be
    (vert.x-eventloop-thread-0) got 0 rows with the following columns: [installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success]
    (vert.x-eventloop-thread-0) row: [1, 1.001, create objects, SQL, V1.001__create_objects.sql, 1608972049, root, 2020-05-09T22:42:29, 753, 1]
    (vert.x-eventloop-thread-0) row: [2, 1.002, add name, SQL, V1.002__add_name.sql, 1396568508, root, 2020-05-09T23:48:40, 1485, 1]
    (vert.x-eventloop-thread-0) finished request for id 2098aa6c-f13b-4691-a0b3-0dfbaab990be in 28 ms

# TODO

- use the objects table - and add an api to add some objects!
- add pub/sub kafka
- add call to other rest service
- upgrade to quarkus 1.5 and see if we can get the mutiny version of the mysql pool to work and replace the completable future
                                           

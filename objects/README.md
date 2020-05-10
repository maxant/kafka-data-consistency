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

    http://localhost:8086/index.html

# Take Aways

Notice how the logging is done on a different thread than the one that accepts the request:

    (executor-thread-30) starting request for id 2098aa6c-f13b-4691-a0b3-0dfbaab990be
    (vert.x-eventloop-thread-0) got 0 rows with the following columns: [installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success]
    (vert.x-eventloop-thread-0) row: [1, 1.001, create objects, SQL, V1.001__create_objects.sql, 1608972049, root, 2020-05-09T22:42:29, 753, 1]
    (vert.x-eventloop-thread-0) row: [2, 1.002, add name, SQL, V1.002__add_name.sql, 1396568508, root, 2020-05-09T23:48:40, 1485, 1]
    (vert.x-eventloop-thread-0) finished request for id 2098aa6c-f13b-4691-a0b3-0dfbaab990be in 28 ms

## Context Propagation

See `ObjectResource#put` which uses a request scoped bean. It sets a username from the HTTP Header into that bean 
and runs a call to get some fruit. The response is processed and that might happen on a different thread. It writes 
the original thread name, and the new thread name as well as the incoming username into the result, so a test can 
check to see if there is a case where different threads process the request and fruit response, but the username is 
preserved. Sure enough, sometimes it's processed by two threads, but the username is indeed preserved. E.g.:

    got one with differing threads! ak-129412:executor-thread-4:executor-thread-3

The test used is `ContextPropagationSVT` which uses concurrent threads to force quarkus to start using more than just
one thread.

So if the `@Transactional` annotation is used, Quarkus will store the transaction into the context which is restored
when the completion stage completes so that the transaction is committed.

I wonder how rollbacks are handled? I guess similarly, when processing failed completion completable futures.

See https://quarkus.io/guides/context-propagation

# TODO

- add ping/pong to SSE because we don't clean up until we emit, as quarkus doesn't find out about the closed socket until it tries to write.
  - see https://github.com/quarkusio/quarkus/issues/9194
- use visualvm to checkout thread usage
- add mettrics to prometheus/grafana - https://quarkus.io/guides/microprofile-metrics
- use the objects table - and add an api to add some objects!
- add pub/sub kafka
- add call to other rest service
- upgrade to quarkus 1.5 and see if we can get the mutiny version of the mysql pool to work and replace the completable future

# Further Reading

- https://lordofthejars.github.io/quarkus-cheat-sheet/
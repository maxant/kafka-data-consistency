# Objects

A component for managing objects linked to contracts.
Houses, cars, specially insured items, etc.

# Installation

The database must exist in order to get flyway to work:

    CREATE DATABASE if not exists objects CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

# Technical

This component uses quarkus with vertx and attempts to be entirely reactive (non-blocking).

# Installing graalvm and maven

    cd /usr/local/
    wget https://github.com/graalvm/graalvm-ce-builds/releases/download/vm-19.3.2/graalvm-ce-java11-linux-amd64-19.3.2.tar.gz
    tar xzf graalvm-ce-java11-linux-amd64-19.3.2.tar.gz
    wget https://downloads.apache.org/maven/maven-3/3.6.3/binaries/apache-maven-3.6.3-bin.zip
    unzip apache-maven-3.6.3-bin.zip

    export JAVA_HOME=/usr/local/graalvm-ce-java11-19.3.2
    export GRAALVM_HOME=/usr/local/graalvm-ce-java11-19.3.2/
    export PATH=/usr/local/apache-maven-3.6.3/bin:$PATH

# Development

    mvn compile quarkus:dev

    curl -v -X GET localhost:8086/objects/2098aa6c-f13b-4691-a0b3-0dfbaab990be | jq

    http://localhost:8086/index.html

# Production

    cd objects
    export JAVA_HOME=/usr/local/graalvm-ce-java11-19.3.2
    export GRAALVM_HOME=/usr/local/graalvm-ce-java11-19.3.2/
    export PATH=/usr/local/apache-maven-3.6.3/bin:$PATH
    mvn clean package -Pnative -Dquarkus.native.container-build=true

    ./target/objects-1.0-SNAPSHOT-runner -Xmx32m -Dquarkus.profile=dev -Dquarkus.http.port=8086

    docker build -f src/main/docker/Dockerfile -t maxant/kdc-objects .
    docker run -it --rm -p 8086:8086 maxant/kdc-objects
    cd ..
    docker-compose -f dc-services.yml up -d

old stuff, that didnt work because the docker image doesnt contain docker which quarkus build uses:

    # build jar: docker run --name jdkmvn -it --rm -v  $(pwd):/project -v /root/.m2/repository:/root/.m2/repository maxant/jdkmvn mvn clean package
    # test jar: docker run -it --rm -p 8086:8086 -v $(pwd):/app openjdk:11.0.7-slim-buster java -jar /app/target/objects-1.0-SNAPSHOT-runner.jar
    # build exe: docker run --name jdkmvn -it --rm -v  $(pwd):/project -v /root/.m2/repository:/root/.m2/repository maxant/jdkmvn mvn clean package -Pnative -Dquarkus.native.container-build=true
    # test exe: docker run -it --rm -p 8086:8086 -v $(pwd):/app openjdk:11.0.7-slim-buster ./objects-1.0-SNAPSHOT-runner


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

So it looks like you can use JPA with all it's dependencies on TLS, in a reactive environment, but you need to 
add some unsexy code to do the propagation. I guess the other problem is that Hibernate uses jdbc under the hood 
and so it isn't entirely reactive, because the DB access will be blocking.

The alternative is to use a reactive DB connection, but then you have to handle transactions manually rather than 
declaratively with annotations.

See https://quarkus.io/guides/context-propagation

# TODO

- kafka monitoring + consumer lag in grafana
- add ping/pong to SSE because we don't clean up until we emit, as quarkus doesn't find out about the closed socket until it tries to write.
  - see https://github.com/quarkusio/quarkus/issues/9194
- use the objects table - and add an api to add some objects!
- add call to other rest service
- upgrade to quarkus 1.5 and see if we can get the mutiny version of the mysql pool to work and replace the completable future
- add fallback and timeout https://quarkus.io/quarkus-workshops/super-heroes/#fault-tolerance-fallbacks, https://quarkus.io/quarkus-workshops/super-heroes/#fault-tolerance-timeout
- add liveness / readiness https://quarkus.io/quarkus-workshops/super-heroes/#_adding_liveness / https://quarkus.io/quarkus-workshops/super-heroes/#_adding_readiness and add monitoring
- kafka integration: https://quarkus.io/quarkus-workshops/super-heroes/#messaging

# Further Reading

- https://lordofthejars.github.io/quarkus-cheat-sheet/
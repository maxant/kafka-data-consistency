# The Milk Factory :: Waiting Room

A component for dealing with delaying of messages so that they can be retried
in the case of a non-fatal error. See the functional docs and example logs below.

## Running in dev mode

From inside the waitingroom folder, so that changes to the library are also hot deployed:

```
mvn quarkus:dev
```

## Packaging and running the application

`mvn package` produces the `waitingroom-1.0-SNAPSHOT-runner.jar` file in the `/target` directory.
Be aware that it’s not an _über-jar_ as the dependencies are copied into the `target/lib` directory.
The application is now runnable using `java -jar target/waitingroom-1.0-SNAPSHOT-runner.jar`.

## Creating a native executable

You can create a native executable using: `./mvnw package -Pnative`.

Or, if you don't have GraalVM installed, 
you can run the native executable build in a container using: 
`./mvnw package -Pnative -Dquarkus.native.container-build=true`.

You can then execute your native executable with: `./target/waitingroom-1.0-SNAPSHOT-runner`

## Swagger-UI

    http://waitingroom:8085/swagger-ui

## Functional docs

This component offers two topics which records can be sent to. If you want to make the record wait at least 1 second,
send it to `waitingroom-01`, otherwise to wait for 10 seconds, send it to `waitingroom-10`. It MUST have a header named
`DELAY_UNTIL` set to the epoch millisecond timestamp containing the minimum time at which the record may again be sent
back to the original topic. It must also contain the header `ORIGINAL_TOPIC` with the name of it's ultimate destination.
Once sent back to the destination, it will contain the header `RETRY_COUNT` which is always incremented, should it 
already exist. 

**Note** that it makes no sense to set the `DELAY_UNTIL` value higher than the value for which the topic 
you send it to is responsible. But the producer must set this value, because otherwise the component will not know
how long to wait when processing subsequent records, which may have already been partially delayed as this component 
processes a previous record. 

**Note** that this component only uses the original topic name, key, value and headers when sending the record back
to the original topic, and does not set the timestamp or partition. While the partition is perhaps not so relevant
in cases where partitioning is done using the key, note that the timestamp is effectively reset - the record sent back
is NOT the original record, but a clone, containing the same key, value, etc.  It's timestamp is not that of the
original record. Indeed when the record was sent to the waiting room, it too was a clone, potentially with a different
timestamp, depending on how it was forwarded!

### Log Example one second delay

    curl -v -X POST http://waitingroom:8085/waitingroom/test/1

    2020-11-17 23:27:48,020 INFO  (executor-thread-2) POST /waitingroom/test/1 sent test message to waiting room 1
    2020-11-17 23:27:48,103 DEBUG (executor-thread-3) received 1 records requiring a delay in waiting room 1000
    2020-11-17 23:27:48,107 DEBUG (executor-thread-3) delay until 1605652068939
    2020-11-17 23:27:48,107 DEBUG (executor-thread-3) timeNow     1605652068107
    2020-11-17 23:27:48,108 DEBUG (executor-thread-3) timeToWait 832
    2020-11-17 23:27:48,108 INFO  (executor-thread-3) waiting 832 ms in waiting room 1000
    2020-11-17 23:27:48,964 DEBUG (executor-thread-3) sent record a on its way back to topic waiting-room-test from waiting room 1000

### Log Example ten second delay

    curl -v -X POST http://waitingroom:8085/waitingroom/test/10

    2020-11-17 23:46:40,757 INFO  (executor-thread-3) POST /waitingroom/test/10 sent test message to waiting room 10
    2020-11-17 23:46:40,790 DEBUG (executor-thread-2) received 1 records requiring a delay in waiting room 10000
    2020-11-17 23:46:40,794 DEBUG (executor-thread-2) delay until 1605653210682
    2020-11-17 23:46:40,794 DEBUG (executor-thread-2) timeNow     1605653200794
    2020-11-17 23:46:40,795 DEBUG (executor-thread-2) timeToWait 9888
    2020-11-17 23:46:40,795 INFO  (executor-thread-2) waiting 9888 ms in waiting room 10000
    2020-11-17 23:46:50,705 DEBUG (executor-thread-2) sent record a on its way back to topic waiting-room-test from waiting room 10000
    
## TODO

- shutdown isn't working correctly

# The Milk Factory :: Web

A microservice containing all SPAs as well as an SSE filter so that clients can receive asynchronous data from
downstream microservices.

## Running in dev mode

From inside the web folder, so that changes to the library are also hot deployed:

```
mvn quarkus:dev
```

## Packaging and running the application

`mvn package` produces the `web-1.0-SNAPSHOT-runner.jar` file in the `/target` directory.
Be aware that it’s not an _über-jar_ as the dependencies are copied into the `target/lib` directory.
The application is now runnable using `java -jar target/web-1.0-SNAPSHOT-runner.jar`.

## Creating a native executable

You can create a native executable using: `./mvnw package -Pnative`.

Or, if you don't have GraalVM installed, 
you can run the native executable build in a container using: 
`./mvnw package -Pnative -Dquarkus.native.container-build=true`.

You can then execute your native executable with: `./target/web-1.0-SNAPSHOT-runner`

## Swagger-UI

    http://localhost:8082/swagger-ui



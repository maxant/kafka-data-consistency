# The Milk Factory :: Pricing

A component for dealing with pricing of components in a contract.

## Running in dev mode

From inside the pricing folder, so that changes to the library are also hot deployed:

```
mvn quarkus:dev

# or on zeus:
mvn quarkus:dev \
-Dquarkus.datasource.jdbc.url=jdbc:mysql://192.168.1.215:30300/mfpricing \
-Dquarkus.log.handler.gelf.host=192.168.1.215 \
-Dkafka.bootstrap.servers=192.168.1.215:30001,192.168.1.215:30002
-Dquarkus.http.host=0.0.0.0
```

## Packaging and running the application

`mvn package` produces the `pricing-1.0-SNAPSHOT-runner.jar` file in the `/target` directory.
Be aware that it’s not an _über-jar_ as the dependencies are copied into the `target/lib` directory.
The application is now runnable using `java -jar target/pricing-1.0-SNAPSHOT-runner.jar`.

## Creating a native executable

You can create a native executable using: `./mvnw package -Pnative`.

Or, if you don't have GraalVM installed, 
you can run the native executable build in a container using: 
`./mvnw package -Pnative -Dquarkus.native.container-build=true`.

You can then execute your native executable with: `./target/pricing-1.0-SNAPSHOT-runner`

## Swagger-UI

    http://pricing:8081/swagger-ui

## TODO


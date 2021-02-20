# The Milk Factory :: DSC

A component for dealing with discounts, surcharges and conditions.

## Running in dev mode

From inside the dsc folder, so that changes to the library are also hot deployed:

```
mvn quarkus:dev
```

## Packaging and running the application

`mvn package` produces the `dsc-1.0-SNAPSHOT-runner.jar` file in the `/target` directory.
Be aware that it’s not an _über-jar_ as the dependencies are copied into the `target/lib` directory.
The application is now runnable using `java -jar target/dsc-1.0-SNAPSHOT-runner.jar`.

## Creating a native executable

You can create a native executable using: `./mvnw package -Pnative`.

Or, if you don't have GraalVM installed, 
you can run the native executable build in a container using: 
`./mvnw package -Pnative -Dquarkus.native.container-build=true`.

You can then execute your native executable with: `./target/dsc-1.0-SNAPSHOT-runner`

## Swagger-UI

    http://dsc:8088/swagger-ui

## TODO


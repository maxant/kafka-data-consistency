# The Milk Factory :: Contracts

A component for dealing with customers contracts, rather than purchasing contracts.

Contracts are based on a product. We create one contract per customer per product.

A Product defines the configurable parameters. When we create a contract we instantiate 
the product and set the parameters that are configured, saving each contract a as a row in our 
database. Parameters are stored using JSON.

## Running in dev mode

```
mvn quarkus:dev
```

## Packaging and running the application

`mvn package` produces the `contracts-1.0-SNAPSHOT-runner.jar` file in the `/target` directory.
Be aware that it’s not an _über-jar_ as the dependencies are copied into the `target/lib` directory.
The application is now runnable using `java -jar target/contracts-1.0-SNAPSHOT-runner.jar`.

## Creating a native executable

You can create a native executable using: `./mvnw package -Pnative`.

Or, if you don't have GraalVM installed, 
you can run the native executable build in a container using: 
`./mvnw package -Pnative -Dquarkus.native.container-build=true`.

You can then execute your native executable with: `./target/contracts-1.0-SNAPSHOT-runner`

## Swagger-UI

    http://localhost:8080/swagger-ui

## TODO

- add ui to display offer
- UI reacts to each event - need to add filtering in that component
- add changing config and recalculating price
- add purchase, which causes billing to run
- add billing component
- billing publishes event to world to inform contract component that the contract is active?
- add a structure rule, to ensure there is always a bottle or container in a milk product
- add duration, day in year to bill, billing frequency => make customer pay for first period until next billing period according to config
- use kafka as bus for entire application but use public topic for publishing billing, which is only losely coupled
- add daily billing job

## Blog

- sends a useful model around on the event bus, so that we have guaranteed order
- ui only uses event source for updating - we only update relevant part of ui
- MVC but with microservices
- error handling - timeouts? anything else?
- kafka interfaces are analagous to rest interfaces.
  - security? check jwt on incoming message?

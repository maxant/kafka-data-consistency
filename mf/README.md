# The Milk Factory

The hottest shake shop in town!

----

## What is The Milk Factory?

The Milk Factory is a venture where we allow customers to create contracts to order customised milk 
products, for example milkshakes. The customer can customise things like fat and sugar content, 
chocolate type, other extras and supplements.

Contracts can be one-off, subscriptions limited for a given duration, auto-extendable or not.

With a subscription you pay for say 10 per year and you can claim them when you want.

Depending upon the product, there are certain specialities, like our index based products which 
become more expensive year on year, depending upon raw product costs.

## Topcs, Command, Events

- Topics
  - event-bus (generic for all closely-coupled components)
  - cases-commands
  - cases-events
  - errors (for error propagation back to the client)
- Commands (all have the attribute "command" at root level)
 - CREATE_CASE (cases-commands)
 - CREATE_TASK (cases-commands)
 - UPDATE_TASK (cases-commands)
- Events (all have the attribute "event" at root level)
  - ERROR (errors)
  - CASE_CHANGED (cases-events)
  - OFFER_CREATED (event-bus)
  - UPDATED_PRICES (event-bus)

## TODO

- add a task based on a business rule and allow the user to do it
- add action to execute when task is completed or started (ie open UI, or do something)
- discounts
- billing
- output
- one html page per functional theme - contract viewer, search, partner view, etc.
- make offer ui sexier
- ability to change a config in the offer, which recals discounts and price
- fix offer => create pdf
- accept offer => event to billing
- partners
- additional info - to hang stuff external to the offer onto components
- billing publishes event to world to inform contract component that the contract is active?
- add a structure rule to components, to ensure there is always a bottle or container in a milk product
- add duration, day in year to bill, billing frequency => make customer pay for first period until next billing period according to config
- publishing billing as command => losely coupled
- add daily billing job

## Blog

- sends a useful model around on the event bus, so that we have guaranteed order
- ui only uses event source for updating - we only update relevant part of ui
- MVC but with microservices
- error handling - timeouts? anything else?
  - error topic - acts as a DLQ, but also one for sending errors back to the client
- kafka interfaces are analagous to rest interfaces.
  - security? check jwt on incoming message?

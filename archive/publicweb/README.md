# This folder contains the company's public site.

Used for example, for customer registration.

(Codepen: https://codepen.io/maxant/pen/abOZrwP)

Uses Vue with PrimeVue, and is an experiment into two-way binding.
See https://www.bennadel.com/blog/3538-on-the-irrational-demonization-of-two-way-data-binding-in-angular.htm
The idea here is to have a wizard with its own models which
emits an event when the user is finished.

This demonstrates how we can avoid shared state. The wizard has it's own view model. When it's done, we add this into
the main store.

## Running

    http-server

## TODO

- the initial registration data is actually public to the app => how could we make it read only, and still pass it 
into the wizard so that it can be passed into the form?

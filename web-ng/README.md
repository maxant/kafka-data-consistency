# What is this?

An example of how to build an Angular app which is compiled in the browser
so that you don't have to worry about the build process.

It still needs to be served by a web server, otherwise chrome has some
CORS issues.

Run like this:

    npm install && node node_modules/http-server/bin/http-server

Note that imports are different when doing this, e.g.:

    const { Component, NgModule } = ng.core;

And since all the Angular code is in the `module.ts` file, there is no need
to export classes.

Haven't been able to figure out how to get source maps generated,
so no chance of debugging in browser at the moment.


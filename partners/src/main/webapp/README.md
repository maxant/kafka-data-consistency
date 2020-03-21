# Partners UI

This is an Angular UI.

## Creation of the `ng-ui` folder

    sudo npm install -g @angular/cli
    
    sudo ng new ng-ui
    
    ? Would you like to add Angular routing? Yes
    ? Which stylesheet format would you like to use? (Use arrow keys)
    ❯ CSS 

    cd ng-ui
    
    ng serve
    
    # add a component
    ng generate component xyz
    
    # add angular material
    ng add @angular/material
    
    # add a dependency
    ng add _____
    
    # run and watch tests
    ng test

    # build for prod
    ng build --prod

    # add a service? without a test
    ng g s student --spec=false

## Adding a web component

Stick the web component javascript in e.g. `webcomponents` folder and modify 
the path `projects.ng-ui.architect.build.options.scripts` in the file `ng-ui\angular.json`:

            "scripts": [
              "../webcomponents/my-button.js"
            ]

Then, update `app.module.ts`:

    import { CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';

    @NgModule({
        ...
        schemas: [CUSTOM_ELEMENTS_SCHEMA]
    })

This allows custom elements.


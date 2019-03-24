var exports = {}; // this allows us to add "export" to components, so they can be more easily transferred to real Angular apps later

const { Component, NgModule, OnInit } = ng.core;
const { BrowserModule } = ng.platformBrowser;

@Component({
    selector: 'home',
    templateUrl: 'home.html',
})
export class HomeComponent implements OnInit {
    counter = 0;

    increment() {
        this.counter++;
    }

    ngOnInit() {
        document.getElementById("loading").style = "display: none;";
    }
}

@Component({
    selector: 'partner',
    templateUrl: 'partner.html',
})
export class PartnerComponent {
    name = 'Ant Kutschera';
    id = 'C-4837-4536';
    street = 'Ch. des chiens';
    streetNumber = 'Ch. des chiens 69';
    zip = '1000';
    city = 'Marbach';
    phone = '+41 77 888 99 00';
}

@Component({
    selector: 'contracts',
    templateUrl: 'contracts.html',
})
export class ContractsComponent {
    contracts = [{
        id: 'V-9087-4321',
        description1: 'House contents insurance',
        description2: 'incl. IT hardware'
    },{
        id: 'V-8374-3823',
        description1: 'Fully comprehensive car insurance',
        description2: 'incl. fire and theft'
    }];
}

@Component({
    selector: 'claims',
    templateUrl: 'claims.html',
})
export class ClaimsComponent {
    claims = [{id: 1}];

    // TODO subscribe to websocket and update model contents with responses from GET
    // TODO use redux
}

@NgModule({
    imports:      [ BrowserModule ],
    declarations: [ HomeComponent, PartnerComponent, ContractsComponent, ClaimsComponent ],
    bootstrap:    [ HomeComponent ]
})
class AppModule {
}

const { platformBrowserDynamic } = ng.platformBrowserDynamic;
platformBrowserDynamic().bootstrapModule(AppModule);

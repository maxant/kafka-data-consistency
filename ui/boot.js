import {} from './partner.js';
import {} from './contracts.js';
import {} from './claims.js';
import {} from './locations.js';
import {} from './tasks.js';
import {} from './documents.js';
import {} from './navigations.js';
import {} from './ruler.js';

import {PartnerView} from './partnerView.js';
import {ContractView} from './contractView.js';
import {SearchView} from './searchView.js';
import {GraphView} from './graphView.js';
import {ContractHistoryView} from './contractHistoryView.js';

Vue.use(VueRouter);
Vue.use(vuelidate.default);
Vue.use(VueRx);

const ClaimView = { template: `<div>TODO show screen for claim {{$route.params.id}}</div>` };

const LazyView = () => lazyImport("./lazy.js")

const router = new VueRouter({
    routes: [
        { path: '*',             redirect: '/contract/1568556465087' },
        { path: '/search',       name: 'search',   component: SearchView   },
        { path: '/graph',        name: 'graph',    component: GraphView    },
        { path: '/partner/:id',  name: 'partner',  component: PartnerView  },
        { path: '/contract/:cn', name: 'contract', component: ContractView },
        { path: '/lazy',         name: 'lazy',     component: LazyView     },
        { path: '/claim/:id',    name: 'claim',    component: ClaimView    },
        { path: '/contract-history', name: 'contract-history',    component: ContractHistoryView },
    ]
});

var app = new Vue({
    router,
    el: '#app'
});

window.onWSMessage = function(data) {
    app.$emit(data.topic);
}

function lazyImport(url) {
    return axios.get(url).then(response => {
        // sadly neither axios nor XMLHttpRequest put an object on the response even though the response
        // content type is application/javascript. they both make a string out of it. so...
        // 1) create a script element, 2) set its content to assign a global variable as the response,
        // 3) append the script to the document head so its executed, 4) get the object from the global variable,
        // 5) clean up, 6) return the object
        var injectedScript = document.createElement('script');
        injectedScript.innerHTML = "window.__$maxant$lazyLoadedObject = " + response.data;
        var head = document.getElementsByTagName('head');
        head[0].appendChild(injectedScript);
        var lazyLoadedObject = window.__$maxant$lazyLoadedObject;
        delete window.__lazyLoadedObject;
        head[0].removeChild(injectedScript);
        return lazyLoadedObject;
    });
}
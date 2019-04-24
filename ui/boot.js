import {} from './partner.js';
import {} from './contracts.js';
import {} from './claims.js';
import {} from './tasks.js';
import {} from './documents.js';
import {} from './navigations.js';
import {} from './ruler.js';

import {PartnerView} from './partnerView.js';
import {SearchView} from './searchView.js';

Vue.use(VueRouter);
Vue.use(vuelidate.default);
Vue.use(VueRx);

const ClaimView = { template: `<div>TODO show screen for claim {{$route.params.id}}</div>` };

const LazyView = () => lazyImport("./lazy.js")

const router = new VueRouter({
    routes: [
        { path: '/search',      name: 'search',  component: SearchView   },
        { path: '/partner/:id', name: 'partner', component: PartnerView  },
        { path: '/lazy',        name: 'lazy',    component: LazyView     },
        { path: '/claim/:id',   name: 'claim',   component: ClaimView    },
        { path: '*',            redirect: '/search' }
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
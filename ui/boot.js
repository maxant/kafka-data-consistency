import {} from './partner.js';
import {} from './contracts.js';
import {} from './claims.js';
import {} from './tasks.js';
import {} from './documents.js';
import {} from './navigations.js';
import {} from './ruler.js';

import {PartnerView} from './partnerView.js';

Vue.use(VueRouter);
Vue.use(vuelidate.default);
Vue.use(VueRx);

const HomeView = { template: `<router-link :to="{ name: 'partner', params: { id: 'C-4837-4536' }}">view partner</router-link>` };

const SearchView = { template: '<div>TODO</div>' };

const LazyView = () => lazyImport("./lazy.js")

const router = new VueRouter({
    routes: [
        { path: '/',            name: 'home',    component: HomeView     },
        { path: '/search',      name: 'search',  component: SearchView   },
        { path: '/partner/:id', name: 'partner', component: PartnerView  },
        { path: '/lazy',        name: 'lazy',    component: LazyView     },
        { path: '*',            redirect: '/' }
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
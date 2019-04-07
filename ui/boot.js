import {PartnerView} from './partnerView.js';

Vue.use(VueRouter);

const HomeView = { template: `<router-link :to="{ name: 'partner', params: { id: 'C-4837-4536' }}">view partner</router-link>` };

const SearchView = { template: '<div>TODO</div>' };

const router = new VueRouter({
    routes: [
        { path: '/',            name: 'home',    component: HomeView     },
        { path: '/search',      name: 'search',  component: SearchView   },
        { path: '/partner/:id', name: 'partner', component: PartnerView  },
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
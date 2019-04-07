import {model} from './model.js';
import {Store} from './store.js';
import {Controller} from './controller.js';

const store = new Store(model);
const controller = new Controller(store, model);

function buildNavigations() {
    return [
        { title: 'home',   name: 'home',   params: {} },
        { title: 'search', name: 'search', params: {} },
    ];
};

export const PartnerView = {
    data: function(){ return {model: model, store: store} },
    watch: { '$route': 'init' }, // if e.g. user changes it, reload
    created: function() { // this is an example of data fetching aka an angular resolver
        this.$parent.$on("task-created-event", function() {
            controller.loadTasks();
        });
        this.$parent.$on("claim-created-event", function() {
            controller.loadClaims();
        });
        this.init();
    },
    methods: {
        clearData() {
            var xhr = new XMLHttpRequest();
            xhr.open('DELETE', 'http://localhost:8081/claims/rest/claims', true);
            xhr.send();

            xhr = new XMLHttpRequest();
            xhr.open('DELETE', 'http://localhost:8082/tasks/rest/tasks', true);
            xhr.send();
            console.log("data is being cleared");
        },
        init() {
            this.$q.loading.show();
            var id = this.$route.params.id;
            console.log("loading partner " + id + "...");
            controller.loadClaims();
            controller.loadTasks();
// TODO replace with call to backend services to load model
// TODO also handle errors
            const self = this;
            return new Promise(function (resolve, reject) {
                setTimeout(function() {
                    self.$q.loading.hide();
                    controller.setMenu(buildNavigations());
                    resolve();
                }, 1000);
            });
        }
    },
    template: `
        <div>
            <table width="100%" height="100%">
                <tr>
                    <td colspan="2" align="center">
                        <h2><img height="50px" src="skynet.svg" style="vertical-align: text-bottom;">&nbsp;KAYS Insurance Ltd.</h2>
                    </td>
                    <td align="right" valign="top">
                        <div style="z-index=1;">
                            <small>
                                <a href='#' @click.prevent="clearData();">clear test data</a>
                                <br>
                                <a href='#' @click.prevent="store.timeTravelBack();">&lt;&lt;</a>
                                time travel
                                <a href='#' @click.prevent="store.timeTravelForward();">&gt;&gt;</a>
                                <br>
                                {{store.getCurrent().timestamp.toISOString()}}
                                <br>
                                Index {{store.getHistory().timeTravelIndex}} of {{store.getHistory().length - 1}}: {{store.getCurrent().message}}
                            </small>
                        </div>
                    </td>
                </tr>
                <tr><td colspan="3">
                    <hr>
                </td></tr>
                <tr>
                    <td width="20%" valign="top">
                        <navigations :navigations="model.navigations"></navigations>
                    </td>
                    <td width="60%" valign="top">
                        <partner :partner="model.partner"></partner>
                        <hr>
                        <contracts :contracts="model.contracts"></contracts>
                        <hr>
                        <claims :claims="model.claims"></claims>
                    </td>
                    <td width="20%" valign="top">
                        <tasks :tasks="model.tasks"></tasks>
                        <hr>
                        <documents></documents>
                    </td>
                </tr>
            </table>
        </div>
    `
};

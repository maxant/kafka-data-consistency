import {model} from './model.js';
import {Store} from './store.js';
import {Controller} from './controller.js';

const map = rxjs.operators.map;
const fromEvent = rxjs.fromEvent;

const store = new Store(model);
export const controller = new Controller(store, model);

function buildNavigations() {
    return [
        { title: 'home',   name: 'home',   params: {} },
        { title: 'search', name: 'search', params: {} },
        { title: 'lazy',   name: 'lazy',   params: {} },
    ];
};

export const PartnerView = {
    data(){ return {model: model, store: store} },
    watch: { '$route': 'init' }, // if e.g. user changes it, reload
    created() { // this is an example of data fetching aka an angular resolver
        this.$parent.$on("task-created-event", function() {
            controller.loadTasks();
        });
        this.$parent.$on("claim-created-event", function() {
            controller.loadClaims();
        });
        this.init();
    },
    subscriptions(){ // used by vue-rxjs
        const el = document.body;
        const time = new rxjs.Subject();
        setInterval(function(){time.next(new Date().toTimeString().substr(0,8))}, 1000);

        return {
            mouseMoves: fromEvent(el, 'mousemove')
                            .pipe(map(evt => `Coords: ${evt.clientX} X ${evt.clientY}`)),
            time: time
        };
    },
    methods: {
        showMenu() { return !this.$q.screen.xs && !this.$q.screen.sm; },
        clearData() {
            axios.delete('http://localhost:8081/claims/rest/claims');
            axios.delete('http://localhost:8082/tasks/rest/tasks');
            console.log("data is being cleared");
        },
        init() {
            this.$q.loading.show();
            var id = this.$route.params.id;
            console.log("loading partner " + id + "...");
            controller.loadClaims();
            controller.loadTasks();

            // simulate call to load partner data so user can see loading button
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
    // responsive design:
    //   lg:
    //      menu - claim - claim - tasks
    //   md:
    //      menu - claim - tasks
    //   sm:
    //      menu-as-a-button
    //      claim - tasks
    //   xs:
    //      menu-as-a-button
    //      claim
    //      task
    template:
    `
    <div>
        <div>Mouse {{mouseMoves}}</div>
        <div>Time: {{time}}</div>
        <!-- a ruler for checking responsiveness -->
        <div class="row">
            <ruler/>
        </div>
        <div class="row">
            <div class="col-10" style="padding-top: 20px; text-align: center;">
                <h2><img height="50px" src="skynet.svg" style="vertical-align: middle;">&nbsp;KAYS Insurance Ltd.</h2>
            </div>
            <div class="col-2" style="align: center; z-index=1; padding-top: 20px;">
                <small>
                    <a href='#' @click.prevent="clearData();">clear test data</a> |
                    <a href='./tests.html' target="_blank">run tests</a>
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
        </div>
        <div class="row">
            <div class="col-12">
                <hr>
            </div>
        </div>
        <div v-if="!showMenu()" class="row">
            <div class="col-2" style="">
                <q-btn round color="secondary">
                    <q-icon name="menu" />
                    <q-popover>
                        <div class="navigation-popup">
                            <navigations :navigations="model.navigations"></navigations>
                        </div>
                    </q-popover>
                </q-btn>
            </div>
        </div>
        <div class="row">
            <div v-if="showMenu()" class="col-3">
                <navigations :navigations="model.navigations"></navigations>
            </div>
            <div class="col-xs-12 col-sm-9 col-md-6">
                <partner :partner="model.partner"></partner>
                <contracts :contracts="model.contracts"></contracts>
                <claims :claims="model.claims"></claims>
            </div>
            <div class="col-xs-12 col-sm-3 col-md-3">
                <tasks :tasks="model.tasks"></tasks>
                <documents></documents>
            </div>
        </div>
    </div>
    `
};

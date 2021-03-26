/*
// prime components https://www.primefaces.org/primevue/
// (if unsure of global name, then check the javascript console using autocomplete)
Vue.component('Calendar', calendar);
Vue.component('InputText', inputtext);
Vue.component('Card', card);
Vue.component('Button', button);
Vue.component('ToggleButton', togglebutton);

// custom components
Vue.component('wizard', {
    props: ['model'],
    template: `
        <div class='wizard'>
            <div>
                <Button id='previous' label="< previous" v-on:click="previous" :disabled="!canPrevious()" />
                <Button id='next'     label="next >"     v-on:click="next"     :disabled="!canNext()"     />
                <Button id='finish'     label="finish"   v-on:click="finish"   :disabled="!canFinish()"   />
            </div>
            <registrationform :data="model.initialRegistrationFormData" v-show="shouldShow('RegistrationForm')" />
            <offers           :data="offers"                            v-show="shouldShow('Offer')"            />

            <div id='overlay' v-show="loading"></div>
        </div>
    `,
    data: function() {
        return {
            pageNames: ['RegistrationForm', 'Offer'],
            currentPage: 0,
            loading: false,
            offers: []
        }
    },
    methods: {
        previous: function(){
            this.$data.currentPage = Math.max(0, this.$data.currentPage - 1);
        },
        next: function(){
            let data = this.$data;
            data.currentPage = Math.min(data.pageNames.length - 1, data.currentPage + 1);
            if(data.pageNames[data.currentPage] === 'Offer') {
                data.loading = true;
                loadOffer(data.offers)
                .catch(e => alert(e))
                .finally(() => data.loading = false);
            }
        },
        finish: function() {
            console.log("done");
            let dob = this.$props.model.initialRegistrationFormData.dob;
            this.$emit('wizard-complete', {
                registrantName: this.$props.model.initialRegistrationFormData.name,
                registrantDob: dob.toISOString(),
                offerId: _.find(this.$data.offers, 'selected').id
            });
        },
        shouldShow: function(name) {
            return name === this.$data.pageNames[this.$data.currentPage]
        },
        canPrevious: function() {
            return this.$data.currentPage > 0;
        },
        canNext: function() {
            return this.$data.currentPage < this.$data.pageNames.length - 1;
        },
        canFinish: function() {
            return !this.canNext() && _.filter(this.$data.offers, o => o.selected).length === 1;
        }
    }
})

Vue.component('registrationform', {
    props: ['data'],
    template: `
        <div class='registrationform'>
            <span class="p-float-label">
                <InputText id="name" type="text" v-model="data.name" />
                <label for="name">Name</label>
            </span>
            <br>
            <br>
            <span class="p-float-label">
                <Calendar id="dob" v-model='data.dob' dateFormat="yy-mm-dd" :showButtonBar="true" :monthNavigator="true" :yearNavigator="true" yearRange="1900:2030" />
                <label for="dob">Date of birth</label>
            </span>
        </div>
    `
})

Vue.component('offers', {
    props: ['data'],
    template: `
        <div class='offers'>
            <Card style="width: 200px;" v-for="offer in data" :key="offer.id">
                <template slot="header">
                    header...
                </template>
                <template slot="title">
                    Offer #{{offer.id}}
                </template>
                <template slot="content">
                    Lorem ipsum dolor sit amet, consectetur adipisicing elit.
                </template>
                <template slot="footer">
                    <ToggleButton v-model="offer.selected" onIcon="pi pi-check" offIcon="pi pi-times" />
                </template>
            </Card>
        </div>
    `
})

// a central store; think redux
const store = {
    myCentralBusinessModel: null
};

// create the app
new Vue({
    el: '#app',
    data: {
        // we create some shared state which we can pass into the form, so that we can initialise it
        wizardViewModel: {
            initialRegistrationFormData: {
                                             name: 'John',
                                             dob: new Date()
                                         }
        },
        completed: false
    },
    methods: {
        // a callback registered declaritively in the template, called when the wizard emits its completed event
        wizardComplete: function(data) {
            // copy data from wizard into central model
            store.myCentralBusinessModel = {
                selectedOffer: data.offerId,
                name: data.registrantName,
                dob: data.registrantDob
            };
            this.$data.completed = true;
        },
        getMyCentralBusinessModelAsJson() {
            return JSON.stringify(store.myCentralBusinessModel);
        }
    }
})

// http simulator
function loadOffer(offers) {
    return new Promise(function(resolve, reject) {
        setTimeout(function() {
            offers.push(
                {
                    id: new Date().getTime(),
                    selected: false
                });
            resolve();
        }, 500);
    });
}
*/
console.log("asdf")
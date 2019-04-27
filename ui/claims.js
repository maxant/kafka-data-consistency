export const claimsComponentObject = {
    props: ['claims'],
    subscriptions() {
        return {
            entities: this.claims.entities$
        }
    },
    template: `
        <div id="claims" class="tile-group">
            Claims<br>
            <claim-form />
            <div v-if="claims.error" class="row">
                <q-banner type="warning" class="q-mb-sm" icon="priority_high">
                    {{claims.error}}
                </q-banner>
            </div>
            <div v-else-if="claims.loading" class="row"><q-spinner-hourglass size="32px"/></div>
            <div v-else-if="entities.length === 0" class="row"><i>No claims</i></div>
            <div v-else class="row">
                <div v-for="claim in entities" class="col-xs-12 col-sm-12 col-md-12 col-lg-6">
                    <claim :claim="claim" :showLabels="true" />
                </div>
            </div>
        </div>
    `
};

function buildEmptyClaim(){
    return {
       description: "",
       summary: "",
       reserve: 1000.0,
       date: new Date().toISOString().substr(0,10).replace(/-/g, "/")
    }
}

Vue.component('claims', claimsComponentObject);

export const claimsFormComponentObject = {
    data() {
        return  {
            form: buildEmptyClaim(),
            showingNewclaims: false
        }
    },
    inject: ['controller'],
    validations: {
        form: {
            description: {
                required: validators.required,
                minLength: validators.minLength(4),
                maxLength: validators.maxLength(40)
            }
        }
    },
    methods: {
        showForm() {
            this.showingNewclaims = true;
        },
        dateOptions(date) {
            let now = new Date();
            now = new Date(now.setMonth(now.getMonth() - 3)); // no claims older than three months are allowed
            return date >= now.toISOString().substr(0,10).replace(/-/g, "/");
        },
        createClaim() {
            this.$refs.summary.validate(); // causes hasError to be set
            this.$refs.date.validate();
            this.$refs.reserve.validate();
            this.$v.form.$touch(); // description is validated using vuelidate - the others use "internal validation" => see very bottom of q-input docs

            // check if form is valid
            if (this.$refs.summary.hasError || this.$refs.date.hasError || this.$refs.reserve.hasError || this.$v.form.$error) {
                this.$q.notify("Please review fields again");
            } else {
                // its valid :-)
                this.controller.createClaim(this.form);

                this.$refs.summary.resetValidation();
                this.$refs.date.resetValidation();
                this.$refs.reserve.resetValidation();
                this.$v.$reset();

                this.showingNewclaims = false;
                this.form = buildEmptyClaim();
            }
        }
    },
    template: `
                <q-btn v-if="!showingNewclaims" id="show-claims-form" label="create new claim..." color="primary" icon="create" @click="showForm()"/>
                <q-card v-else style="width: 100%;">
                    <q-card-section>
                        <div class="row">
                            <q-input
                                id="claims-form-summary"
                                class="col-12"
                                v-model="form.summary"
                                :rules="[ val => !!val || '* Required',
                                          val => val.length <= 20 || 'Please use maximum 20 character',
                                        ]"
                                lazy-rules
                                hint="Validation starts after first blur because it's lazy"
                                label="Summary"
                                counter
                                ref="summary"
                                autocomplete="off"
                            />
                        </div>
                        <div class="row">
                            <q-input ref="date" v-model="form.date" mask="date" :rules="['date']">
                                <template v-slot:append>
                                    <q-icon name="event" class="cursor-pointer">
                                        <q-popup-proxy>
                                            <q-date v-model="form.date"
                                                     :options="dateOptions"
                                                     today-btn
                                             />
                                        </q-popup-proxy>
                                    </q-icon>
                                </template>
                            </q-input>
                        </div>
                        <div class="row">
                            <q-input
                                v-model="form.reserve"
                                ref="reserve"
                                type="number"
                                :rules="[ val => !!val || '* Required',
                                          val => val >= 0 || 'Value must be positive',
                                        ]"
                                lazy-rules
                                hint="How much is this claim likely to cost?"
                            />
                        </div>
                        <div class="row">
                            <q-input
                                id="claims-form-description"
                                class="col-8"
                                required
                                v-model="form.description"
                                type="textarea"
                                label="Description"
                                rows="4"
                                ref="description"
                                @blur="this.$v.form.description.$touch"
                                :error="$v.form.description.$error"
                            />
                            <div class="col-4 error" v-if="$v.form.description.$dirty && !$v.form.description.required">Description is required</div>
                            <div class="col-4 error" v-else-if="$v.form.description.$dirty && !$v.form.description.minLength">Description must have at least {{$v.form.description.$params.minLength.min}} letters</div>
                            <div class="col-4 error" v-else-if="$v.form.description.$dirty && !$v.form.description.maxLength">Description must have at most {{$v.form.description.$params.maxLength.max}} letters</div>
                        </div>
                        <div class="row">
                            <q-btn label="create" id="claims-form-create" color="primary" @click="createClaim()" style="margin: 10px;"/>
                            <q-btn label="cancel" id="claims-form-cancel" color="secondary" @click="showingNewclaims = false" style="margin: 10px;"/>
                        </div>
                    </q-card-section>
                </q-card>
            </div>
    `
};

Vue.component('claim-form', claimsFormComponentObject);

export const claimComponentObject = {
    props: ['claim', 'showLabels'],
    methods: {
        goto(name, id) {
            this.$router.push({ name: name, params: {id: id } })
        }
    },
    template: `
        <div class="tile">
            <div class='tile-title'>
                <i class='fas fa-exclamation-circle'></i>
                <a href="#" :title="claim.id" @click.prevent="goto('claim', claim.id)">Claim</a>
            </div>
            <div v-if="claim.temp" class='tile-body'>
                <i>in progress...</i><br>
                {{claim.summary}}
            </div>
            <div v-else class='tile-body'>
                <span v-if="showLabels">Summary:</span> <span>{{claim.summary}}</span><br>
                <span v-if="showLabels">Description:</span> <span>{{claim.description}}</span><br>
                <span v-if="showLabels">Reserve:</span> <span>{{claim.reserve}} CHF,</span>
                    <span v-if="showLabels">Date:</span> <span>{{claim.date}}</span><br>
                <span v-if="showLabels"><i class="fas fa-user"></i></span> <span><a href="#" @click.prevent="goto('partner', claim.customerId)">{{claim.customerId}}</a></span>
            </div>
        </div>
    `
};
Vue.component('claim', claimComponentObject);

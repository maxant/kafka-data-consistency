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
                    <div class="tile">
                        <div class='tile-title'><i class='fas fa-exclamation-circle'></i>&nbsp;Claim</div>
                        <div v-if="claim.temp" class='tile-body'><i>in progress...</i><br>{{claim.description}}</div>
                        <div v-else class='tile-body'><i>{{claim.id}}</i><br>{{claim.description}}</div>
                    </div>
                </div>
            </div>
        </div>
    `
};

Vue.component('claims', claimsComponentObject);

export const claimsFormComponentObject = {
    data() {
        return  {
            form: {
                description: "",
                summary: ""
            },
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
        createClaim() {
            this.$v.form.$touch();
            if (this.$v.form.$error || !this.$refs.summary.validate()) {
                this.$q.notify("Please review fields again");
            } else {
                this.controller.createClaim(this.form.description);
                this.showingNewclaims = false;
                this.form.description = "";
                this.form.summary = "";
                this.$v.$reset();
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

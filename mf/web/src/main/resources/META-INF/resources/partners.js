(function(){
// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// select
// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
var template =
// start template
`
<div>
    <p-autocomplete
               id="partnerselectdropdown"
               v-model="partner"
               :suggestions="partners"
               @complete="search($event)"
               field="$name"
               placeholder="Select a partner"
               minLength=3
    >
    </p-autocomplete>
    <div v-if="partner && partner.id === 0">
        <h3>Add a new partner</h3>
        <div class="p-field">
            <label for="firstName">First Name</label>
            <p-inputtext id="firstName" v-model="newPartner.firstName" required />
            <small v-show="newPartner.$validationErrors.firstName && newPartner.$submitted" class="p-error"><br>First name is required.</small>
        </div>
        <div class="p-field">
            <label for="lastname">Last Name</label>
            <p-inputtext id="lastName" v-model="newPartner.lastName" />
            <small v-show="newPartner.$validationErrors.lastName && newPartner.$submitted" class="p-error"><br>Last name is required.</small>
        </div>
        <div class="p-field">
            <label for="dob">Date of birth</label>
            <p-calendar id="dob"
                        v-model="newPartner.dob"
                        :showIcon="true"
                        dateFormat="yy-mm-dd"
                        :minDate="minDob"
                        :maxDate="maxDob"
                        :yearNavigator="true"
                        :yearRange="dobYearRange"
                        selectOtherMonths="true"
            ></p-calendar>
            <small v-show="newPartner.$validationErrors.dob && newPartner.$submitted" class="p-error"><br>Date of birth is required.</small>
        </div>
        <div class="p-field">
            <label for="street">Street</label>
            <p-inputtext id="street" v-model="newPartner.addresses[0].street" />
            <small v-show="newPartner.$validationErrors.street && newPartner.$submitted" class="p-error"><br>Street is required.</small>
        </div>
        <div class="p-field">
            <label for="postcode">Postcode</label>
            <p-dropdown :options="['1000', '3000', '7000']"
                       v-model="newPartner.addresses[0].postcode"
                       placeholder="Select a postcode"
            >
            </p-dropdown>
            <small v-show="newPartner.$validationErrors.postcode && newPartner.$submitted" class="p-error"><br>Postcode is required.</small>
        </div>
        <div class="p-field">
            <span>
                <p-button id="newPartnerOk" label="ok" @click="createNewPartner()"/>
            </span>
            <span>
                <p-button id="newPartnerCancel" label="cancel" @click="partner = null"/>
            </span>
        </div>
    </div>
</div>
` // end template

window.mfPartnerSelect = {
    props: ['allowCreateNew' // if true, then there is an option to add a new partner, at the top of the list
    ],
    template,
    watch: {
        partner(newPartner, oldPartner) {
            if(newPartner && newPartner.id) {
                this.$emit('selected', newPartner);
            }
        }
    },
    data() {
        return {
            partners: [],
            partner: null,
            newPartner: {
                firstName: '',
                lastName: '',
                type: 'PERSON',
                dob: new Date(1980, 0, 1, 12),
                email: '',
                phone: '',
                addresses: [{
                    street: 'Surlough Street',
                    houseNumber: '4b',
                    postcode: '3000',
                    city: 'Llandudno',
                    state: 'Wales',
                    country: 'UK',
                    type: 'PRIMARY'
                }],
                $validationErrors: {},
                $submitted: false
            },
            requestId: uuidv4()
        }
    },
    created() {
        this.minDob = new Date();
        this.minDob.setFullYear(this.minDob.getFullYear() - 100);
        this.maxDob = new Date();
        this.maxDob.setFullYear(this.maxDob.getFullYear() - 18);
        this.dobYearRange = this.minDob.getFullYear() + ":" + this.maxDob.getFullYear();
    },
    methods: {
		search(event) {
            const self = this;
            return fetch(ELASTICSEARCH_BASE_URL + "/partners/_search?q=" + encodeURIComponent(event.query), {"method": "GET"})
            .then( r => {
                if(r.status >= 200 && r.status < 300) {
                    return r.json().then(body => {
                        console.log("got partners");
                        let ps = _.sortBy(body.hits.hits, ['lastName', 'firstName', 'dob']);
                        ps = _.map(ps, p => p._source);
                        _.forEach(ps, p => p.id = p.partnerId);
                        _.forEach(ps, p => p.$name = getName(p));
                        if(this.allowCreateNew) {
                            ps.unshift({$name: "create new...", id: 0});
                        }
                        self.partners = ps;
                    });
                } else {
                    return r.text().then(body => {
                        console.error("error getting search results. please try again"); // TODO handle this better
                    });
                }
            }).catch(error => {
                console.error("error getting search results: " + error + ". please try again"); // TODO handle this better
            });
		},
        createNewPartner() {
            // if we already created one, it has an ID which we need to remove, so that the server can start from scratch
            delete this.newPartner.id;
            delete this.newPartner.partnerId;

            this.newPartner.$submitted = true;
            if (this.validateForm()) {
                let self = this;
                let url = PARTNERS_BASE_URL + "/partners"
                return fetchIt(url, "POST", this, this.newPartner, true).then(r => {
                    if(r.ok) {
                        console.log("created new partner " + r.payload + " for requestId " + this.requestId);
                        // now select the new partner in the dropdown
                        self.newPartner.partnerId = r.payload;
                        self.newPartner.id = self.newPartner.partnerId;
                        self.newPartner.$name = getName(self.newPartner);
                        self.partner = self.newPartner;
                    } else {
                        let msg = "Failed to create partner: " + r.payload;
                        console.error(msg);
                        alert(msg);
                    }
                }).catch(error => {
                    console.error("received error: " + error);
                });
            }
        },
        validateForm() {
            if (!this.newPartner.firstName.trim())
                this.newPartner.$validationErrors.firstName = true;
            else
                delete this.newPartner.$validationErrors.firstName;

            if (!this.newPartner.lastName.trim())
                this.newPartner.$validationErrors.lastName = true;
            else
                delete this.newPartner.$validationErrors.lastName;

            if (!this.newPartner.dob)
                this.newPartner.$validationErrors.dob = true;
            else
                delete this.newPartner.$validationErrors.dob;

            if (!this.newPartner.addresses[0].street)
                this.newPartner.$validationErrors.street = true;
            else
                delete this.newPartner.$validationErrors.street;

            if (!this.newPartner.addresses[0].postcode)
                this.newPartner.$validationErrors.postcode = true;
            else
                delete this.newPartner.$validationErrors.postcode;

            return !Object.keys(this.newPartner.$validationErrors).length;
        }
    },
    components: {
        'p-dropdown': dropdown,
        'p-autocomplete': autocomplete,
        'p-calendar': calendar,
        'p-inputtext': inputtext,
        'p-button': button
    }
}

function getName(p) {
    let dob = p.dob;
    if(typeof dob == "object") {
        dob = dob.toISOString().substr(0, 10);
    }
    return p.firstName + " " + p.lastName + " (" + dob + " - " + p.id + ")";
}

// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// display partner tile
// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// TODO responsive, columns, etc.
template =
// start template
`
<div style="border: 1px solid #999999; box-shadow: 0 4px 4px 0 rgba(0, 0, 0, 0.2), 0 6px 10px 0 rgba(0, 0, 0, 0.2); width: 450px; margin: 10px 10px 20px 10px; padding: 5px;">
    <div v-if="error">
        Error loading partner<br>
        {{error}}
    </div>
    <div v-else-if="partner == null">
        loading...
    </div>
    <div v-else>
        <div>
            <i v-if="isContractHolder()" class="pi pi-user-plus"></i>
            <i v-else class="pi pi-user"></i>
            Partner: {{partnerId}}
        </div>
        <div v-if="isSalesRep()">
            Sales Representative: {{partner.firstName}} {{partner.lastName}}
        </div>
        <div v-else-if="isContractHolder()">
            Contract Holder: {{partner.firstName}} {{partner.lastName}}
        </div>
        <div v-else>
            {{partner.firstName}} {{partner.lastName}}
        </div>
        <div>
            Phone: {{partner.phone}}
        </div>
        <div>
            Email: {{partner.email}}
        </div>
        <div v-if="roles && roles.length >= 0">
            Roles: {{roles}}
        </div>
        <div v-if="clickable">
            <i class="pi pi-eye" @click="navigateToPartner()"></i>
        </div>
    </div>
    <slot></slot>
</div>
` // end template

window.mfPartnerTile = {
  props: ['partnerId', 'role', 'roles', 'clickable'], // roles is a string comma separated list of roles
  template,
  watch: {
    partnerId(newPartnerId, oldPartnerId) {
        this.loadPartner$();
    }
  },
  data() {
    return {
        partner: null,
        error: null,
        requestId: uuidv4()
    }
  },
  mounted() {
    this.loadPartner$();
  },
  methods: {
    loadPartner$() {
      this.partner = null;
      this.error = null;
      let self = this;
      let url = PARTNERS_BASE_URL + "/partners/" + this.partnerId;
      return fetchIt(url, "GET", this).then(r => {
        if(r.ok) {
            console.log("got partner " + self.partnerId + " for requestId " + self.requestId);
            self.partner = r.payload;
            self.$emit('loaded', self.partner);
        } else {
            let msg = "Failed to get partner " + self.partnerId + ": " + r.payload;
            self.error = msg;
            console.error(msg);
        }
      }).catch(error => {
        self.error = error;
        console.error("received error: " + error);
      });
    },
    isSalesRep() {
        return this.role == 'SALES_REP' ||
            (this.roles && this.roles.indexOf('SALES_REP') >= 0);
    },
    isContractHolder() {
        return this.role == 'CONTRACT_HOLDER' ||
            (this.roles && this.roles.indexOf('CONTRACT_HOLDER') >= 0);
    },
    navigateToPartner() {
        window.location.href = '/partner?id=' + this.partnerId;
    }
  }
}
})();

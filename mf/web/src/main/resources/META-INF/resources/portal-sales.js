(function(){
// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// sales widget
// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// TODO responsive, columns, etc.
var template =
// start template
`
<div style="font-size: small;">
    Drafted in {{ timeTaken }}ms
</div>
<div v-if="get(model, 'draft.contract')">
    <button @click="newDraft()">create a new draft</button>
</div>
<div v-if="get(model, 'draft.contract')">
    <mf-contract :contract="model.draft.contract"></mf-contract>
</div>
<div v-if="draftCreatedFor">
Offer created for partner {{draftCreatedFor.id}} ({{getNameOfPartner()}})
</div>
<div v-if="!!get(model, 'draft.pack.$price')">
    Product: {{ model.draft.pack.children[0].productId }} {{ model.draft.pack.$price.total }} CHF ({{ model.draft.pack.$price.tax }} CHF VAT)
</div>
<div v-if="!!get(model, 'draft.pack.$price')">
    <ul>
        <li>{{ model.draft.pack.componentDefinitionId }} {{ priceOf(model.draft.pack) }} CHF</li>
        <li v-for="child in model.draft.pack.children">
            {{ child.componentDefinitionId }}: {{ child.$price.total }} CHF
        </li>
    </ul>
</div>
<div>
    <button :disabled="!allowOffer" @click="offerDraftAndAcceptOffer()">accept offer</button>
</div>
<div v-if="get(model, 'draft.contract')">
    <mf-partner v-if="model.salesRep" :partner-id="model.salesRep" role="SALES_REP"></mf-partner>
</div>

` // end template

window.mfPortalSales = {
    template,
    data() {
        return {
            model: {"draft": {"prices": {}}, "startDate": new Date(), salesRep: null},
            start: 0,
            timeTaken: 0,
            requestId: uuidv4(),
            users,
            user: security.getCurrentUser(),
            draftCreatedFor: null, // the user it was created for, rather than the currently selected user, just as info
            allowOffer: false
        }
    },
    mounted() {
        window.model = this.model; // just for debugging purposes
        eventHub.on(LOGGED_IN, (username) => {
            this.user = security.getCurrentUser();
            // dont initialise, let the user click the button, so that they can
            // play with approving if they are a different user :-)
        });
        this.initialise();
    },
    methods: {
        initialise() {
            if(!this.model.draft.contract) {
                this.newDraft();
            } // else the user needs to hit the button (after optionally changing the user)
        },
        newDraft() {
            console.log("getting new draft for user " + this.user.id);
            this.allowOffer = false;
            let body = {
                "productId": "COOKIES_MILKSHAKE",
                "start": new Date(new Date().getTime() + 24*3600000).toISOString().substr(0,10),
                "partnerId": this.user.id
            }
            this.currentAction = {action: "newDraft"}
            this.draftCreatedFor = JSON.parse(JSON.stringify(this.user))

            // subscribe before sending request to server, to ensure we receive ALL of the events, and any errors
            sse(this.requestId, this);

            let self = this;
            let url = CONTRACTS_BASE_URL + "/drafts"
            fetchIt(url, "POST", this, body).then(r => {
                if(r.ok) console.log("got contract with id " + r.payload.id + ", for requestId " + self.requestId);
                else {
                    let msg = "Failed to offer contract: " + r.payload;
                    console.error(msg);
                    alert(msg);
                }
            }).catch(error => {
                console.error("received error: " + error);
                ameliorateCurrentAction(self)
            });
        },
        offerDraftAndAcceptOffer() {
            this.allowOffer = false;
            console.log("offering contract...")
            this.currentAction = {action: "offerDraftAndAcceptOffer"}
            let self = this;
            let url = CONTRACTS_BASE_URL + "/contracts/offerAndAccept/" + this.model.draft.contract.id;
            fetchIt(url, "PUT", this).then(r => {
                if(r.ok) {
                    console.log("offered and accepted contract with id " + r.payload.id + ", for requestId " + self.requestId);
                    this.$router.push({ name: "home"});
                } else {
                    if(r.payload.class == "ch.maxant.kdc.mf.partners.boundary.NoRelationshipsFoundValidationException") {
                        alert("No partner relationships exist, please create a new offer");
                    } else if(r.payload.class == "ch.maxant.kdc.mf.partners.boundary.NotEnoughRelationshipsForForeignIdTypeValidationException") {
                        alert("A partner relationship is missing: " + r.payload.data);
                    } else {
                        let msg = "Failed to offer contract: " + r.payload;
                        console.error(msg);
                        alert(msg);
                    }
                    ameliorateCurrentAction(self)
                }
            }).catch(error => {
                console.error("received error: " + error);
                ameliorateCurrentAction(self)
            });
        },
        get(obj, path) {
            // vue3 doesnt like seeing underscores in attribute values => so lets create an alias with this method
            return _.get(obj, path);
        },
        priceOf(component) {
            return (component.$price.total - _.sumBy(component.children, '$price.total')).toFixed(2);
        },
        getNameOfPartner() {
            return _.find(users, u => u.id == this.draftCreatedFor.id).name;
        }
    },
    components: {
        'mf-partner': mfPartnerTile,
        'mf-contract': mfContractTile
    }
}

function addPrice(component, id, price) {
    if(component.componentId == id) {
        component.$price = price
    } else {
        _.forEach(component.children, child => addPrice(child, id, price))
    }
}

function sse(requestId, self) {
    if(self.source && self.source.readyState != EventSource.CLOSED) self.source.close();
    self.source = new EventSource("/web/stream/" + requestId);
    self.source.onmessage = function (event) {
        console.log(event);
        let msg = JSON.parse(event.data);
        if(msg.event == "CREATED_DRAFT") {
            self.model.draft = msg.payload;
            initialiseDraft(self.model.draft.pack);
            // dont update any waiting or timing state, because this is just the first of many events to come
        } else if(msg.event == "UPDATED_PRICES_FOR_DRAFT") {
            self.model.draft.prices = msg.payload.priceByComponentId;
            _.forEach(self.model.draft.prices, (v,k) => {
                addPrice(self.model.draft.pack, k, v);
            });
            self.allowOffer = true;
            self.timeTaken = new Date().getTime() - self.start;
        } else if(msg.event == "CHANGED_PARTNER_RELATIONSHIP") {
            if(msg.payload.role == "SALES_REP") {
                self.model.salesRep = msg.payload.partnerId;
            }
        } else if(msg.event == "ERROR") {
            if(msg.payload.errorClass == "ch.maxant.kdc.mf.partners.control.PartnerHasNoPrimaryAddressValidationException") {
                alert(msg.payload.errorMessage);
                // TODO let user add a primary address
            } else {
                console.error("received error: " + JSON.stringify(msg));
                alert("an error occurred - see the console");
                ameliorateCurrentAction(self)
            }
        }
    };
    self.source.onerror = function (event) {
        console.log("sse error " + event);
        if(self.source && self.source.readyState != EventSource.CLOSED) self.source.close();
        self.source = null;
    }
}

function ameliorateCurrentAction(self) {
    if(self.currentAction.action == "newDraft") {
        // not much can be done => user has a button to go get a new one
        alert("Failed to create an offer. Please try refreshing the page.")
    } else if(self.currentAction.action == "offerDraftAndAcceptOffer") {
        self.allowOffer = true // so user can retry if they want to. it mustve been enabled in order to get here.
    } else {
        console.error("unexpected current action: " + self.currentaction.action)
    }
}

function initialiseDraft(component) {
    component.$configs = {}
    _.forEach(component.configs, c => component.$configs[c.name] = _.clone(c));
    _.forEach(component.children, child => initialiseDraft(child));
}


})();

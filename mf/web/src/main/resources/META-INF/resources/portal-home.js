(function(){
// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// home widget
// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// TODO responsive, columns, etc.
var template =
// start template
`
<div style="font-size: small;">
    Fetched in {{ timeTaken }}ms
</div>

<mf-partner :partner-id="model.id" :roles="model.partnerRoles"></mf-partner>

<div>
    Partner ID: {{model.id}}<br>
    First Name: {{model.partner.firstName}}<br>
    Last Name: {{model.partner.lastName}}<br>
    DoB: {{model.partner.dob}}<br>
</div>
<hr>
<mf-contract v-for="contractId in model.contractIds"
            :contract-id="contractId"
            allowAcceptOffer="true"
            hideDrafts="true"
            #default="sp"
            @loaded="contractLoaded">
    <div v-if="sp.theContract.contractState == 'RUNNING'">
        <p-button @click="details(sp.theContract)">details</p-button>
        &nbsp;
        <p-button @click="terminate(sp.theContract)">terminate</p-button>
        &nbsp;
        <p-button @click="order(sp.theContract)">order</p-button>
    </div>
</mf-contract>

` // end template

window.mfPortalHome = {
    template,
    data() {
        return {
            model: { id: security.getCurrentUser().id, partner: {} },
            start: 0,
            timeTaken: 0,
            sessionId
        }
    },
    mounted() {
        window.model = this.model; // just for debugging purposes
        eventHub.on(LOGGED_IN, (username) => {
            this.model.id = security.getCurrentUser().id
            // re-fetch when trying a different user, to e.g. re-render how tasks are shown for
            // that user, as well as re-check security
            this.fetchPartner();
        });
        this.fetchPartner();
    },
    methods: {
        fetchPartner() {
            this.sessionId = uuidv4();
            let self = this;
            let url = PARTNERS_BASE_URL + "/partners/" + this.model.id;
            let partner$ = fetchIt(url, "GET", this).then(r => {
                self.timeTaken = new Date().getTime() - self.start;
                if(r.ok) {
                    console.log("got partner with id " + r.payload.id + ", for sessionId " + self.sessionId);
                    self.model.partner = r.payload;
                } else {
                    let msg = "Failed to get partner: " + r.payload;
                    console.error(msg);
                    alert(msg);
                }
            }).catch(error => {
                alert("received error: " + error);
            });

            url = PARTNERS_BASE_URL + "/partner-relationships/allByPartnerId/" + this.model.id + "?idsOnly=true";
            let partnerRelationships$ = fetchIt(url, "GET", this).then(r => {
                self.timeTaken = new Date().getTime() - self.start;
                if(r.ok) {
                    console.log("got partner-relationships, for sessionId " + self.sessionId);
                    self.model.partnerRelationships = _.groupBy(r.payload, "role");
                    self.model.allContractIds = _(self.model.partnerRelationships["CONTRACT_HOLDER"]).map("foreignId").uniq().value();
                    self.model.contractIds = _(self.model.allContractIds).reverse().value();
                    self.model.partnerRoles = _(r.payload).map("role").uniq().value().join(",");
                } else {
                    let msg = "Failed to get partner relationships: " + r.payload;
                    console.error(msg);
                    alert(msg);
                }
            }).catch(error => {
                alert("received error: " + error);
            });

            return Promise.all([partner$, partnerRelationships$]);
        },
        details(contract){
            this.$router.push({ name: 'contract-details', params: {contractId: contract.id } });
        },
        order(contract){
            this.$router.push({ name: 'order', params: {contractId: contract.id } });
        },
        terminate(contract){
            alert("TODO - terminate " + contract.id);
        },
        contractLoaded(contract) {
            this.timeTaken = new Date().getTime() - this.start;
        }
    },
    components: {
        'p-button': button,
        'mf-partner': mfPartnerTile,
        'mf-contract': mfContractTile
    }
}


})();

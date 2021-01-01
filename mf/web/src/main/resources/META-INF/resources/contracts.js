(function(){

// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// display
// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// TODO responsive, columns, etc.
const template =
// start template
`
<div style="border: 1px solid #999999; width: 450px; margin-bottom: 5px;" v-if="fetchedContract && (fetchedContract.contractState != 'DRAFT' || !hideDrafts)">
    <div>
        <i class="pi pi-file"></i>
        <div v-if="fetchedContract">
            Contract: {{fetchedContract.id}}
        </div>
        <div v-else-if="contractId">
            Contract: {{contractId}}
        </div>
        <div v-else>
        </div>
    </div>
    <div>
        <div v-if="error">
            Error loading contract<br>
            {{error}}
        </div>
        <div v-else-if="fetchedContract == null">
            loading...
        </div>
        <div v-else>
            <div>
                Valid from {{fetchedContract.start.toString().substr(0,10)}} until {{fetchedContract.end.toString().substr(0,10)}}
            </div>
            <div>
                Created by {{fetchedContract.createdBy}} on {{fetchedContract.createdAt}}
            </div>
            <div>
                State: {{fetchedContract.contractState}}
                <i v-if="clickable" class="pi pi-eye" @click="navigateToContract()"></i>
            </div>
            <div v-if="allowAcceptOffer && fetchedContract.contractState == 'OFFERED'">
                <button @click="acceptOffer()">accept offer</button>
            </div>
        </div>
    </div>
    <slot :theContract="fetchedContract"></slot>
</div>
` // end template

window.mfContractTile = {
    props: ['contractId', // if set, then the contract is loaded
            'contract', // an object used to display the contract, in lieu of loading the contract from this widget
            'clickable', // if true, then the widget has an icon for clicking on, to open it in the contract view
            'allowAcceptOffer', // if true, then offers can be accepted with the click of a button
            'hideDrafts', // if true, then drafts are not shown
            'withDetails' // if true, then details are also fetched. none are displayed, but the slot can access them using "theContract"
        ],
    template,
    watch: {
        contractId(newContractId, oldContractId) {
            this.loadContract$();
        },
        contract(newContract, oldContract) {
            this.fetchedContract = newContract;
        }
    },
    data() {
        return {
            fetchedContract: null,
            error: null,
            requestId: uuidv4()
        }
    },
    mounted() {
        if(!!this.contract) {
            this.fetchedContract = this.contract;
        } else if(!this.contractId) {
            throw new Error("neither a contract nor a contractId was supplied to the contract widget");
        } else { // client provided an ID and no model, so lets load it
            return this.loadContract$();
        }
    },
    methods: {
        loadContract$: function() {
            this.fetchedContract = null;
            this.error = null;
            let self = this;
            let url = CONTRACTS_BASE_URL + "/contracts/" + this.contractId + "?withDetails=" + (this.withDetails?true:false);
            return fetchIt(url, "GET", this).then(r => {
                if(r.ok) {
                    console.log("got contract " + self.contractId + " for requestId " + self.requestId);
                    self.fetchedContract = r.payload;
                } else {
                    let msg = "Failed to get contract " + self.contractId + ": " + r.payload;
                    self.error = msg;
                    console.error(msg);
                }
            }).catch(error => {
                self.error = error;
                console.log("received error: " + error);
            });
        },
        navigateToContract() {
            window.location.href = '/contract?id=' + this.fetchedContract.id;
        },
        acceptOffer() {
            let self = this;
            let url = CONTRACTS_BASE_URL + "/contracts/accept/" + this.fetchedContract.id;
            fetchIt(url, "PUT", this).then(r => {
                if(r.ok) {
                    console.log("accepted contract " + this.fetchedContract.id + ", for requestId " + self.requestId);
                    self.fetchedContract = r.payload;
                } else {
                    let msg = "Failed to accept contract: " + r.payload;
                    console.error(msg);
                    alert(msg);
                }
            }).catch(error => {
                alert("received error: " + error);
            });
        }
    }
}

if(window.cases) { // not required in every UI which uses this library
    cases.registerActionHandler("APPROVE_CONTRACT", "Approve contract", (task) => {
        // navigate to contract details page, so that the user can assess the contract and approve it.
        // approval there is based on the state, and not simply a task
        window.location.href = '/contract?id=' + task.params["contractId"];
    });
}

})();

(function(){

// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// display
// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// TODO responsive, columns, etc.
const template =
// start template
`
<div style="border: 1px solid #999999; box-shadow: 0 4px 4px 0 rgba(0, 0, 0, 0.2), 0 6px 10px 0 rgba(0, 0, 0, 0.2); width: 450px; margin: 10px 10px 20px 10px; padding: 5px;"
     v-if="fetchedContract && (fetchedContract.contractState != 'DRAFT' || !hideDrafts)"
>
    <div>
        <div v-if="fetchedContract">
            <i class="pi pi-file"></i>
            Contract: {{fetchedContract.id}}
            <i v-if="clickable" id="viewContractIcon" class="pi pi-eye" @click="navigateToContract()" style="float: right;"></i>
        </div>
        <div v-else-if="contractId">
            <i class="pi pi-file"></i>
            Contract: {{contractId}}
            <i v-if="clickable" id="viewContractIcon" class="pi pi-eye" @click="navigateToContract()" style="float: right;"></i>
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
                Product: {{productId}}
            </div>
            <div>
                Created at: {{fetchedContract.createdAt}} by: {{fetchedContract.createdBy}}
            </div>
            <div>
                State: {{fetchedContract.contractState}}
                <span style="font-size: xxsmall; font-style: italic; float:right;">{{!!source?source:(usingES?"elastic":"master")}}</span>
            </div>
            <div v-if="allowAcceptOffer && fetchedContract.contractState == 'OFFERED'">
                <p-button id="acceptOfferButton" @click="acceptOffer()">accept offer</p-button>
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
            'withDetails', // if true, then details are also fetched. none are displayed, but the slot can access them using "theContract"
            'source' // the debug text to show to demonstrate what is being used to show the tile
        ],
    template,
    watch: {
        contractId(newContractId, oldContractId) {
            this.loadContract$();
        },
        contract(newContract, oldContract) {
            this.fetchedContract = newContract;
            this.patchElasticSearchContracts();
        }
    },
    data() {
        return {
            fetchedContract: null,
            usingES: false,
            error: null,
            requestId: uuidv4()
        }
    },
    computed: {
        productId: function() {
            if(!!this.fetchedContract.productId) {
                return this.fetchedContract.productId;
            } else if(!!this.fetchedContract.components){
                return _.find(this.fetchedContract.components, e => !!e.productId).productId;
            }
        }
    },
    mounted() {
        if(!!this.contract) {
            this.fetchedContract = this.contract;
            if(this.fetchedContract.metainfo) {
                this.usingES = true;
            }
            this.patchElasticSearchContracts();
        } else if(!this.contractId) {
            throw new Error("neither a contract nor a contractId was supplied to the contract widget");
        } else { // client provided an ID and no model, so lets load it
            return this.loadContract$();
        }
    },
    methods: {
        patchElasticSearchContracts() {
            if(this.usingES) {
                this.fetchedContract.id = this.fetchedContract.contractId; // elastic documents know the id as 'contractId' - without this, the click wont work
                this.fetchedContract.contractState = this.fetchedContract.state; // elastic documents know the contractState as 'state'
            }
        },
        loadContract$(forceUseMaster) {
            this.fetchedContract = null;
            this.error = null;
            let self = this;

            // if no details are required, lets get the data from ES and relieve our operative db
            let url = ELASTICSEARCH_BASE_URL + "/contracts/_doc/" + this.contractId;
            self.usingES = true;
            if(this.withDetails || forceUseMaster) {
                url = CONTRACTS_BASE_URL + "/contracts/" + this.contractId + "?withDetails=" + (this.withDetails?true:false);
                self.usingES = false;
            }
            return fetchIt(url, "GET", this).then(r => {
                if(r.ok) {
                    console.log("got contract " + self.contractId + " for requestId " + self.requestId);
                    if(self.usingES) {
                        self.fetchedContract = r.payload._source;
                        self.patchElasticSearchContracts();
                    } else {
                        self.fetchedContract = r.payload;
                    }
                    self.$emit('loaded', null);
                } else {
                    if(self.usingES) {
                        // if elastic cant find it, try the master, its obviously not yet in sync
                        console.log("retrying master, since ES failed");
                        return this.loadContract$(true);
                    } else {
                        let msg = "Failed to get contract " + self.contractId + ": " + r.payload;
                        self.error = msg;
                        console.error(msg);
                    }
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
                    self.$emit('accepted', self.fetchedContract);
                } else {
                    let msg = "Failed to accept contract: " + r.payload;
                    console.error(msg);
                    alert(msg);
                }
            }).catch(error => {
                alert("received error: " + error);
            });
        }
    },
    components: {
        'p-button': button
    }
}

if(window.cases) { // not required in every UI which uses this library
    cases.registerActionHandler("APPROVE_CONTRACT", "View and approve contract", (task) => {
        // navigate to contract details page, so that the user can assess the contract and approve it.
        // approval there is based on the state, and not simply a task
        window.location.href = '/contract?id=' + task.params["contractId"];
    });
}

})();

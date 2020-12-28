(function(){
// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// display
// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// TODO responsive, columns, etc.
const template =
// start template
`
<div style="border: 1px solid #999999; width: 350px; margin-bottom: 5px;">
    <div>
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
                <i class="pi pi-eye" @click="navigateToContract()"></i>
            </div>
        </div>
    </div>
</div>
` // end template

window.mfContractTile = {
  props: ['contractId', 'contract'],
  template,
  watch: {
    contractId(oldContractId, newContractId) {
        this.loadContract();
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
      let url = CONTRACTS_BASE_URL + "/contracts/" + this.contractId;
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

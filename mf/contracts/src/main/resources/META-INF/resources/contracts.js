// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// display
// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
template =
// start template
// TODO responsive, columns, etc.
`
<div style="border: 1px solid #999999; width: 350px; margin-bottom: 5px;">
    <div>
        <div v-if="myContract">
            Contract: {{myContract.id}}
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
        <div v-else-if="myContract == null">
            loading...
        </div>
        <div v-else>
            <div>
                Valid from {{myContract.start.toString().substr(0,10)}} until {{myContract.end.toString().substr(0,10)}}
            </div>
            <div>
                Created by {{myContract.createdBy}} on {{myContract.createdAt}}
            </div>
            <div>
                State: {{myContract.contractState}}
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
        myContract: null,
        error: null,
        requestId: uuidv4()
    }
  },
  mounted() {
    if(!!this.contract) {
        this.myContract = this.contract;
    } else if(!this.contractId) {
        throw new Error("neither a contract nor a contractId was supplied to the contract widget");
    } else { // client provided an ID and no model, so lets load it
        return this.loadContract$();
    }
  },
  methods: {
    loadContract$: function() {
      this.myContract = null;
      this.error = null;
      let self = this;
      let url = CONTRACTS_BASE_URL + "/contracts/" + this.contractId;
      return fetchIt(url, "GET", this).then(r => {
        if(r.ok) {
            console.log("got contract " + self.contractId + " for requestId " + self.requestId);
            self.myContract = r.payload;
        } else {
            let msg = "Failed to get contract " + self.contractId + ": " + r.payload.error;
            self.error = msg;
            console.error(msg);
        }
      }).catch(error => {
        self.error = error;
        console.log("received error: " + error);
      });
    },
    navigateToContract() {
        window.location.href = '/contract?id=' + self.myContract.id;
    }
  }
}
// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// display
// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
template =
// start template
// TODO responsive, columns, etc.
`
<div style="border: 1px solid #999999; width: 350px; margin-bottom: 5px;">
    <div v-if="error">
        Error loading contract<br>
        {{error}}
    </div>
    <div v-else-if="contract == null">
        loading...
    </div>
    <div v-else>
        <div>
            Contract: {{contract.id}}
        </div>
        <div>
            Valid from {{contract.start.toString().substr(0,10)}} until {{contract.end.toString().substr(0,10)}}
        </div>
        <div>
            State: {{contract.contractState}}
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
    },
    contract(oldContract, newContract) {
        console.log("CONTRACT HERE!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
    }
  },
  data() {
    return {
        error: null,
        requestId: uuidv4()
    }
  },
  mounted() {
    if(!this.contract) {
        this.loadContract();
    }
  },
  methods: {
    loadContract: function() {
      this.contract = null;
      this.error = null;
      let self = this;
      let url = CONTRACTS_BASE_URL + "/contracts/" + this.contractId;
      fetchIt(url, "GET", this).then(r => {
        if(r.ok) {
            console.log("got contract " + this.contractId + " for requestId " + this.requestId);
            this.contract = r.payload;
        } else {
            let msg = "Failed to get contract " + this.contractId + ": " + r.payload.error;
            this.error = msg;
            console.error(msg);
        }
      }).catch(error => {
        this.error = error;
        console.error("received error: " + error);
      });
    }
  }
}
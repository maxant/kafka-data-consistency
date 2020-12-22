// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// select
// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
var template =
// start template
`
<p-dropdown :options="partners"
           optionLabel="$name"
           v-model="partner"
           placeholder="Select a partner"
>
</p-dropdown>
` // end template

window.mfPartnerSelect = {
  template,
  data: function(){
    return {
        partners: [],
        partner: null,
        requestId: uuidv4(),
        initialised: false
    }
  },
  created() {
    eventHub.on(LOGGED_IN, this.loggedIn); // needs to be logged in in order to initialise,
                                           // otherwise we have no token to be able to call the backend
  },
  methods: {
    loggedIn() {
        if(!this.initialised) {
            this.initialise();
        }
    },
    initialise() {
      this.initialised = true;
      let self = this;
      let url = PARTNERS_BASE_URL + "/partners/search"
      fetchIt(url, "GET", this).then(r => {
          if(r.ok) {
              console.log("got partners for requestId " + this.requestId);
              let ps = _.sortBy(r.payload, ['lastName', 'firstName', 'dob']);
              _.forEach(ps, p => p.$name = p.firstName + " " + p.lastName + " (" + p.dob + " - " + p.id + ")")
              this.partners = ps;
          } else {
              let msg = "Failed to get partners: " + r.payload.error;
              console.error(msg);
              alert(msg);
          }
      }).catch(error => {
          console.error("received error: " + error);
      });
    },
  },
  components: {
    'p-dropdown': dropdown
  }
}

// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// display
// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
template =
// start template
// TODO responsive, columns, etc.
`
<div style="border: 1px solid #999999; width: 350px; margin-top: 3px; margin-bottom: 5px;">
    <div v-if="error">
        Error loading partner<br>
        {{error}}
    </div>
    <div v-else-if="partner == null">
        loading...
    </div>
    <div v-else>
        <div v-if="role == 'SALES_REP'">
            <div>
            Sales Representative: {{partner.firstName}} {{partner.lastName}}
            </div>
            <div>
            Phone: {{partner.phone}}
            </div>
            <div>
            Email: {{partner.email}}
            </div>
        </div>
        <div v-else>
            Partner {{partnerId}} <br>
            Unexpected role {{role}}
        </div>
    </div>
</div>
` // end template

window.mfPartnerTile = {
  props: ['partnerId', 'role'],
  template,
  watch: {
    partnerId(oldPartnerId, newPartnerId) {
        this.loadPartner();
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
    this.loadPartner();
  },
  methods: {
    loadPartner: function() {
      this.partner = null;
      this.error = null;
      let self = this;
      let url = PARTNERS_BASE_URL + "/partners/" + this.partnerId;
      fetchIt(url, "GET", this).then(r => {
        if(r.ok) {
            console.log("got partner " + this.partnerId + " for requestId " + this.requestId);
            this.partner = r.payload;
        } else {
            let msg = "Failed to get partner " + this.partnerId + ": " + r.payload.error;
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
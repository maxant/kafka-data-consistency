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

window.mfPartner = {
  template,
  data: function(){
    return {
        partners: [],
        partner: null,
        requestId: uuidv4(),
    }
  },
  created() {
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
  components: {
    'p-dropdown': dropdown
  }
}
(function(){
// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// display tasks
// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// TODO responsive, columns, etc.
var template =
// start template
`
<div v-for="task in case.tasks">
    <task :task="task"></task>
</div>
` // end template

window.mfCase = {
  props: ['caseReferenceId'],
  template,
  methods: {
    loadCase: function() {
      this.partner = null;
      this.error = null;
      let self = this;
      let url = PARTNERS_BASE_URL + "/partners/" + this.partnerId;
      fetchIt(url, "GET", this).then(r => {
        if(r.ok) {
            console.log("got partner " + self.partnerId + " for requestId " + self.requestId);
            self.partner = r.payload;
        } else {
            let msg = "Failed to get partner " + self.partnerId + ": " + r.payload.error;
            self.error = msg;
            console.error(msg);
        }
      }).catch(error => {
        self.error = error;
        console.error("received error: " + error);
      });
    }
  }
}

// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// display single task
// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// TODO responsive, columns, etc.
template =
// start template
`
<div style="border: 1px solid #999999; width: 350px; margin-bottom: 5px;">
    <div>
        {{task.title}}
    </div>
    <div>
        {{task.description}}
    </div>
    <div>
        {{task.user}}
    </div>
    <div>
        {{task.state}}
    </div>
</div>
` // end template

window.mfTask = {
  props: ['task'],
  template
}


})();

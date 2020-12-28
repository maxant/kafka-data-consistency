(function(){
// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// display single task
// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// TODO responsive, columns, etc.
var template =
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

// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// display tasks
// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// TODO responsive, columns, etc.
template =
// start template
`
<hr>
CASES:
<div v-for="task in tasks">
    <mf-task :task="task"></mf-task>
</div>
` // end template

window.mfCases = {
  props: ['caseReferenceIds'],
  template,
  watch: {
    caseReferenceIds(oldCaseReferenceIds, newCaseReferenceIds) {
        this.loadCases();
    }
  },
  data() {
    return {
        cases: [],
        tasks: [], // flattened
        error: null,
        requestId: uuidv4()
    }
  },
  mounted() {
    this.loadCases();
  },
  methods: {
    loadCases: function() {
      if(!this.caseReferenceIds || this.caseReferenceIds.length === 0) return;
      this.cases = [];
      this.tasks = [];
      this.error = null;
      let self = this;
      let url = CASES_BASE_URL + "/cases/?" + _.map(this.caseReferenceIds, crid => { return "referenceIds=" + crid; }).join("&");
      fetchIt(url, "GET", this).then(r => {
        if(r.ok) {
            console.log("got cases for requestId " + self.requestId);
            self.cases = r.payload;
            self.tasks = _(self.cases).map("tasks").flatten().value();
        } else {
            let msg = "Failed to get cases: " + r.payload;
            self.error = msg;
            console.error(msg);
        }
      }).catch(error => {
        self.error = error;
        console.error("received error: " + error);
      });
    }
  },
  components: {
    'mf-task': mfTask
  }
}

})();

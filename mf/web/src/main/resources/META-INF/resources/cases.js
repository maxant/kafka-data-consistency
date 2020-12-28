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
    <div v-if="isTaskAssignedToUser(task)">
        Assigned to you
    </div>
    <div v-else>
        Assigned to: {{task.userId}}
    </div>
    <div v-if="task.action && isTaskAssignedToUser(task)">
        <button @click="callActionHandler(task)">{{getActionText(task.action)}}</button>
    </div>
</div>
` // end template

window.mfTask = {
  props: ['task'],
  template,
  methods: {
    getActionText(action) {
        return cases.getActionText(action)
    },
    callActionHandler(task) {
        cases.callActionHandler(task)
    },
    isTaskAssignedToUser(task) {
        return security.getCurrentUser() == task.userId;
    }
  }
}

// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// display tasks
// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// TODO responsive, columns, etc.
template =
// start template
`
<hr>
Tasks:
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
        state: "OPEN", // ready for a toggle, should one want to view closed tasks at the push of a button
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
      let url = CASES_BASE_URL + "/cases/byReferenceIds/" + this.state + "?" + _.map(this.caseReferenceIds, crid => { return "referenceIds=" + crid; }).join("&");
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

// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// the cases application, which is responsible for this library, knows nothing about the implementation of
// tasks, i.e. the things that the UI can do with them. that is supplied by other components/teams which
// register their action handlers generically. when a task is displayed, it's action is used to determine
// the buttons and their text which are rendered. when you click on a button, this library simply delegates
// to the registered callback, which has knowledge of what to do. that way, we don't need to program contract
// logic here :-)
// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
window.cases = {
    state: {},
    registerActionHandler: function(action, actionText, callback) {
        this.state[action] = {
            action, actionText, callback
        };
    },
    callActionHandler: function(task) {
        this.state[task.action].callback(task);
    },
    getActionText: function(action) {
        return this.state[action].actionText;
    }
};

})();

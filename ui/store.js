// private fields
const _history = Symbol("history");
const _model = Symbol("model");

export class Store {

    constructor(model) {
        this[_history] = [];
        this[_model] = model;

        // initialise the history
        this.updateHistory("initial state saved in history");
    }

    /** get an array of the last 100 versions of the model */
    getHistory() {
        return this[_history];
    };

    /** get the version of the model which is currently being pointed to. time travel changes the pointer. */
    getCurrent() {
        const history = this.getHistory();
        return history[history.timeTravelIndex];
    };

    /** travel to the given index. */
    timeTravel(index) {
        const history = this.getHistory();
        Object.assign(this[_model], history[index].data);
        console.log("time  travelled to " + history[index].timestamp);
    };

    /** move one step backwards. */
    timeTravelBack() {
        const history = this.getHistory();
        if(history.timeTravelIndex < history.length - 1) {
            history.timeTravelIndex++;
            Object.assign(this[_model], this.getCurrent().data);
            console.log("time travelled to " + this.getCurrent().timestamp + " and at index " + history.timeTravelIndex + "/" + (history.length - 1));
        } else {
            console.log("no more time travel history available")
        }
    };

    /** move one step forwards. */
    timeTravelForward() {
        const history = this.getHistory();
        if(history.timeTravelIndex > 0) {
            history.timeTravelIndex--;
            Object.assign(this[_model], this.getCurrent().data);
            console.log("time travelled to " + this.getCurrent().timestamp + " and at index " + history.timeTravelIndex + "/" + (history.length - 1));
        } else {
            console.log("no more time travel history available")
        }
    };

    /** you MUST call this method in order to add the latest changes that you have just made, to the history */
    updateHistory(message) {
        const history = this.getHistory();
        if(message) console.log(message);
        try {
            // poor mans deep clone BUT has the advantage that only data is copied
            var copy = {};
            _.forEach(this[_model], function(v, k, o) {
                if(!k.startsWith('$') && !k.startsWith('_') && (typeof o[k] !== 'function')) copy[k] = v
            });

            //TODO fix this - we need to rewrite the clone and do it deeply
            //handleAllObservables(null, null, copy);
            copy = {};

            copy = JSON.parse(JSON.stringify(copy));
            history.unshift({data: copy, message: message, timestamp: new Date()});
            history.length = Math.min(history.length, 100);
        } catch(error) {
            console.error("unable to update history: " + error);
        }
        history.timeTravelIndex = 0;
    }
}

function handleAllObservables(parent, key, child) {
    //special treatement of rx observables. currently we only support BehaviourSubject

    if(parent && child && typeof child === 'object' &&
       typeof child.closed !== "undefined" &&
       typeof child.hasError !== "undefined" &&
       typeof child.isStopped !== "undefined" &&
       typeof child.observers !== "undefined" &&
       typeof child.getValue === "function") {

        // seems to be a BehaviourSubject
        parent[key] = child.getValue();
        if(!parent[key]) {
            parent[key] = [];
        }
        parent[key].__WAS__BEHAVIOUR_SUBJECT = true; //so that we can send value into original
    }

    if(typeof child === "object") {
        for (var key in child) {
            if (child.hasOwnProperty(key)) {
                handleAllObservables(child, key, child[key]);
            }
        }
    }
}
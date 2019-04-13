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
            var current = this.getCurrent().data;
            resetBehaviourSubjects(current);
            Object.assign(this[_model], current);
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
            var current = this.getCurrent().data;
            resetBehaviourSubjects(current);
            Object.assign(this[_model], current);
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
            var copy = deepClone(this[_model]);
            history.unshift({data: copy, message: message, timestamp: new Date()});
            history.length = Math.min(history.length, Vue.config.productionTip ? 100 : 1); // turned off in prod
        } catch(error) {
            console.error("unable to update history: " + error);
        }
        history.timeTravelIndex = 0;
    }
}

function resetBehaviourSubjects(o) {
    if(o && (_.isArray(o) || _.isObject(o))) {
        for(var k in o) {
            var v = o[k];
            if(v){
                var data = o["__observable_data_of_" + k];
                if(data) {
                    v.next(data);
                } else if(v.__original_observable) {
                    o[k] = v.__original_observable;
                    o[k].next(v);
                    o["__observable_data_of_" + k] = v; // so that when we go in the other direction, it still works
                    delete o[k].__original_observable;
                } else {
                    resetBehaviourSubjects(v);
                }
            }
        }
    }
}

function deepClone(source) {
    var target = _.isArray(source) ? [] : {};
    _.forEach(source, function(v, k, o) {
        if((typeof k === 'string' && (k.startsWith('$') || k.startsWith('_'))) || (typeof v === 'function')) {
            //skip
        } else if(typeof v === 'object') {
            target[k] = handleObservable(v);
            if(target[k] && !target[k].__original_observable) {
                target[k] = deepClone(v);
            }
        } else {
            target[k] = v;
        }
    });
    return target;
}

function handleObservable(object) {
    //special treatement of rx observables. currently we only support BehaviourSubject

    if(object && typeof object === 'object' &&
       typeof object.closed !== "undefined" &&
       typeof object.hasError !== "undefined" &&
       typeof object.isStopped !== "undefined" &&
       typeof object.observers !== "undefined" &&
       typeof object.getValue === "function") {

        // seems to be a BehaviourSubject
        var value = object.getValue();
        if(!value) {
            value = [];
        }
        value.__original_observable = object; //so that we can fix when we time travel
        object = value;
    }

    return object;
}
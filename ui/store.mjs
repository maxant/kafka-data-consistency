// ///////////////////////////////////////////////////////////
// a flux style store.
// ///////////////////////////////////////////////////////////
// vuex doesnt really look so good, because of the way state
// is mutated without calls to business methods. seems way to
// technical. i want a store which has business methods on it.
// this is it. see bottom of file for store history and time
// travel.
//
// NOTE: compare this to MVC - the model and controller are
//       a single object here, and the model does not emit
//       change events. here, controller methods (business
//       methods) are placed on the root of the model, and
//       should be used as the only way to modify the model.
//       they ensure a) the model is updated and b) the state
//       is stored into the history. vue then reacts to the
//       observables and re-renders the view.  you could of
//       course split the "controller" and "model", and probaby
//       that makes sense because the stuff in the lower half
//       of this file belongs in a library. TODO refactor it
//       into a library!
// ///////////////////////////////////////////////////////////

// ///////////////////////////////////////////////////////////
// initialise structure, because vue needs it to be all
// present so that it can set up observers
// ///////////////////////////////////////////////////////////
const store = (function(){

    const storeHistory = [];

    const store = {
        loading: false,
        partner: new LoadableData(),
        contracts: new LoadableData(),
        claims: new LoadableData(),
        tasks: new LoadableData(),
        navigations: []
    };

    // ///////////////////////////////////////////////////////////
    // initialise data
    // ///////////////////////////////////////////////////////////
    store.partner.entity = {
            id: 'C-4837-4536',
            name: 'Ant Kutschera',
            address: {
                street: 'Ch. des chiens',
                number: '69',
                zip: '1000',
                city: 'Marbach',
            },
            phone: '+41 77 888 99 00'
        };

    store.contracts.entities = [{
           id: 'V-9087-4321',
           title: 'House contents insurance',
           subtitle: 'incl. fire and theft'
        },{
           id: 'V-8046-2304',
           title: 'Main property building insurance',
           subtitle: 'incl. garden'
        }];

    // ///////////////////////////////////////////////////////////
    // claim modification methods
    // ///////////////////////////////////////////////////////////
    store.startLoadingClaims = function() {
        this.claims.loading = true;
        this.claims.error = null;
        // leave as is: this.claims.entities
        updateHistory(this, storeHistory, "loading claims...");
    };

    store.loadedClaims = function(newClaims) {
        this.claims.loading = false;
        this.claims.error = null;
        this.claims.entities = newClaims;
        updateHistory(this, storeHistory, "loaded claims");
    };

    store.unableToLoadClaims = function(error) {
        this.claims.error = error;
        this.claims.loading = false;
        this.claims.entities = [];
        updateHistory(this, storeHistory, "error loading claims: " + error);
    };

    // ///////////////////////////////////////////////////////////
    // task modification methods
    // ///////////////////////////////////////////////////////////
    store.startLoadingTasks = function() {
        this.tasks.loading = true;
        this.tasks.error = null;
        // leave as is: this.tasks.entities
        updateHistory(this, storeHistory, "loading tasks...");
    };

    store.loadedTasks = function(newTasks) {
        this.tasks.loading = false;
        this.tasks.error = null;
        this.tasks.entities = newTasks;
        updateHistory(this, storeHistory, "loaded tasks");
    };

    store.unableToLoadTasks = function(error) {
        this.tasks.error = error;
        this.tasks.loading = false;
        this.tasks.entities = [];
        updateHistory(this, storeHistory, "error loading tasks: " + error);
    };

    // ///////////////////////////////////////////////////////////
    // navigations / menu methods
    // ///////////////////////////////////////////////////////////
    store.setMenu = function(navigations) {
        this.navigations = navigations;
        updateHistory(this, storeHistory, "set menu");
    };

    updateHistory(store, storeHistory, "initial state saved in store history");

    // ///////////////////////////////////////////////////////////
    // helper stuff
    // ///////////////////////////////////////////////////////////
    function LoadableData() {
        this.loading = false;
        this.error = null;
        this.entities = [];
    }

    // ///////////////////////////////////////////////////////////
    // store history:
    // ///////////////////////////////////////////////////////////
    // view last changes by calling `store.getHistory()` in the
    // console. latest is first. each entry shows how the data was
    // after it was changed. to view diffs, see:
    // https://github.com/flitbit/diff
    // time travel works by calling the `store.timeTravel(index)`
    // method, or `store.timeTravelBack()` and
    // `store.timeTravelForward()`. Get the state of where time
    // time travel is currently pointing to, using
    // `store.getCurrent()`.
    // ///////////////////////////////////////////////////////////

    function updateHistory(store, storeHistory, message) {
        if(message) console.log(message);
        try {
            // poor mans deep clone BUT has the advantage that only data is copied
            var copy = {};
            _.forEach(store, function(v,k) { if(!k.startsWith('$') && !k.startsWith('_') && (typeof store[k] !== 'function')) copy[k] = v});
            copy = JSON.parse(JSON.stringify(copy));
            storeHistory.unshift({data: copy, message: message, timestamp: new Date()});
            storeHistory.length = Math.min(storeHistory.length, 100);
        } catch(error) {
            console.error("unable to update history: " + error);
        }
        storeHistory.timeTravelIndex = 0;
    }

    store.getHistory = function() {
        return storeHistory;
    };

    store.getCurrent = function() {
        return storeHistory[storeHistory.timeTravelIndex];
    };

    store.timeTravel = function(index) {
        Object.assign(this, storeHistory[index].data);
        console.log("time  travelled to " + storeHistory[index].timestamp);
    };

    store.timeTravelBack = function() {
        if(storeHistory.timeTravelIndex < storeHistory.length - 1) {
            storeHistory.timeTravelIndex++;
            Object.assign(this, this.getCurrent().data);
            console.log("time travelled to " + this.getCurrent().timestamp + " and at index " + storeHistory.timeTravelIndex + "/" + (storeHistory.length - 1));
        } else {
            console.log("no more time travel history available")
        }
    };

    store.timeTravelForward = function() {
        if(storeHistory.timeTravelIndex > 0) {
            storeHistory.timeTravelIndex--;
            Object.assign(this, this.getCurrent().data);
            console.log("time travelled to " + this.getCurrent().timestamp + " and at index " + storeHistory.timeTravelIndex + "/" + (storeHistory.length - 1));
        } else {
            console.log("no more time travel history available")
        }
    };

    return store;
}());

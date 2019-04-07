import {service} from './service.js';

// private fields
const _store = Symbol("store");
const _model = Symbol("model");

// /////////////////////////////////////////////////////////////
// a controller optionally uses a service. it manipulates a
// model and optionally emits events (yes, the controller,
// not the model!). no events are emitted here, because
// vue uses observables and so does not need events.
// the reason that the controller emits events is because
// it knows what is happening in the grand scheme of
// things. if the model emits events after each attribute is
// changed, they are too fine grained and that causes problems.
// /////////////////////////////////////////////////////////////
export class Controller {

    constructor(store, model) {
        this[_store] = store;
        this[_model] = model;
    }

    setMenu(navigations) {
        const store = this[_store];
        const model = this[_model];
        model.navigations = navigations;
        store.updateHistory("set menu");
    }

    // example implemented with promise
    loadTasks() {
        const store = this[_store];
        const model = this[_model];
        model.tasks.loading = true;
        model.tasks.error = null;
        // leave as is: model.tasks.entities
        store.updateHistory("loading tasks...");

        return service.loadTasks() // return otherwise callers cant chain!
        .then(function(newTasks){
            model.tasks.loading = false;
            model.tasks.error = null;
            model.tasks.entities = newTasks;
            store.updateHistory("loaded tasks");
        }).catch(function(error){
            model.tasks.error = error;
            model.tasks.loading = false;
            model.tasks.entities = [];
            store.updateHistory("error loading tasks: " + error);
        });
    }

    // example implemented with async awat
    async loadClaims() {
        const store = this[_store];
        const model = this[_model];
        model.claims.loading = true;
        model.claims.error = null;
        // leave as is: store.claims.entities
        store.updateHistory("loading claims...");

        try {
            const newClaims = await service.loadClaims();
            model.claims.loading = false;
            model.claims.error = null;
            model.claims.entities = newClaims;
            store.updateHistory("loaded claims");
        } catch (error) {
            model.claims.error = error;
            model.claims.loading = false;
            model.claims.entities = [];
            store.updateHistory("error loading claims: " + error);
        }
    }

    async createClaim(description) {
        const store = this[_store];
        const model = this[_model];
        model.claims.entities.push({
            description: description,
            temp: true
        });
        store.updateHistory("creating claim...");

        try {
            await service.createClaim(description, model.partner.entity.id);
        } catch (error) {
            model.claims.error = error;
            store.updateHistory("error creating claim: " + error);
        }
    }

}

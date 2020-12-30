/** inspired by https://github.com/vuejs/vue-rx - licence: MIT (http://opensource.org/licenses/MIT) */
(function(){

window.rxjsMixin = {
    created() {
        if(!rxjs) throw new Error("please add rxjs to your dependencies");
        if(!this.$options.subscriptions) throw new Error("please add a function called 'subscriptions' to the options object");
        let state = {
            subscriptions: []
        };
        this.$data.$rxjsMixinState = state;

        state.observables = this.$options.subscriptions
        if (typeof state.observables === 'function') {
            state.observables = state.observables.call(this);
        }
        if (state.observables) {
            _.forEach(state.observables, (v, k) => {
                if(!rxjs.isObservable(v)) throw new Error("'" + k + "' returned from 'subscriptions' is not an observable");
                this.$data[k] = null; // added to model, so that it can be referenced in the template without a warning
            });
        }
    },
    mounted() {
        // subscribe here, and unsubscribe on unmounted, because there is no destroy lifecycle hook where we
        // could unsubscribe
        let state = this.$data.$rxjsMixinState;
        if (state.observables) {
            _.forEach(state.observables, (v, k) => {
                let subscription = v.subscribe(data => {
                    this.$data[k] = data;
                });
                state.subscriptions.push(subscription);
            });
        }
    },
    unmounted() {
        let state = this.$data.$rxjsMixinState;
        _.forEach(state.subscriptions, (subscription) => {
            subscription.unsubscribe();
        });
    }
}

})();

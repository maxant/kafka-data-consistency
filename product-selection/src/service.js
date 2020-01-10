// this is a UI service (a place to put business logic)

import { model } from './model.js';

export let service = {

    incrementModelValue() {
        model.value++;
    }

};

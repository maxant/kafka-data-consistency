// this is an RxJS backed business model, owned by the application developer

class Model {
    _observable;
    observable = rxjs.Observable.create(o => this._observable = o);

    fire(name, value) {
        this._observable.next({name, value});
    }
}

class BusinessModel extends Model {
    _value = 0;

    set value(newValue) {
console.log(`updating model.value from ${this._value} to ${newValue}`)
        if(this._value != newValue) {
            this._value = newValue;
            this.fire('value', newValue);
        }
    }

    get value() {
        return this._value;
    }
}

export let model = new BusinessModel();

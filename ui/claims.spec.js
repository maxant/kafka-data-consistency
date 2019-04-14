const shallow = vueTestUtils.shallow;
const createLocalVue = vueTestUtils.createLocalVue;

import {claimsFormComponentObject} from './claims.js'

QUnit.test( "test claims form", function( assert ) {


    const localVue = createLocalVue()
    localVue.use(vuelidate.default);
    const wrapper = shallow(claimsFormComponentObject, { localVue })

    // check html
    assert.ok(!wrapper.find("#claims-form-create").exists(), "create button is hidden")
    assert.ok(wrapper.find("#show-claims-form").exists(), "form is closed")

    assert.ok(!wrapper.vm.showingNewclaims)
    wrapper.find("#show-claims-form").trigger("click")
    assert.ok(wrapper.vm.showingNewclaims)
    assert.ok(!wrapper.find("#show-claims-form").exists(), "form is open")
    assert.ok(wrapper.find("#claims-form-create").exists(), "create button is present")
    assert.ok(wrapper.find("#claims-form-cancel").exists(), "cancel button is present")
    assert.ok(wrapper.vm.$v.form.$invalid, "form is invalid")
    assert.ok(!wrapper.vm.$v.form.$error, "form error is not shown")
    assert.ok(wrapper.vm.$v.form.description.$invalid, "description is invalid")
    assert.ok(!wrapper.vm.$v.form.description.$error, "description error is not shown")
    assert.equals("", wrapper.find("#claims-form-description.q-input-area").element.value, "description is empty")
    wrapper.find("#claims-form-description.q-input-area").element.value = "asdf"

wrapper.find("#claims-form-description.q-input-area").trigger("blur")

    // TODO assert things on $v
    // TODO close form
    // TODO check its not reset when reopening
    // TODO etc...

    console.log(wrapper.html());
    assert.ok(false, "TODO");
});


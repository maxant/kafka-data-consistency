const shallow = vueTestUtils.shallow;
const createLocalVue = vueTestUtils.createLocalVue;

import {claimsFormComponentObject} from './claims.js'

QUnit.test( "test claims form", function( assert ) {

    // setup
    const wrapper = shallow(claimsFormComponentObject)

    // check html
    assert.ok(!wrapper.find("#claims-form-create").exists(), "create button is present")
    assert.ok(wrapper.find("#show-claims-form").exists(), "form is closed")

    wrapper.find("#show-claims-form").trigger("click")


    assert.ok(!wrapper.find("#claims-form-create").exists(), "TODO")

    console.log(wrapper.html());
    assert.ok(false, "TODO");
});


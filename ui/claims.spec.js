const mount = VueTestUtils.mount;
const createLocalVue = VueTestUtils.createLocalVue;

import {claimsFormComponentObject} from './claims.js'

QUnit.test( "test claims form", async function( assert ) {

    const controller = {
        createClaim(description) {
            console.log("mock controller received " + description + " in method createClaim")
        }
    }
    controller.createClaim = sinon.spy(controller.createClaim)

    const localVue = createLocalVue();
    localVue.use(vuelidate.default);

    const done = assert.async();

    const wrapper = mount(claimsFormComponentObject, { localVue, provide: { controller } } )

    // add it to the page - nice to watch, but also may help with events?
    document.getElementById("component").appendChild(wrapper.element);

    // check html
    assert.ok(!wrapper.find("#claims-form-create").exists(), "create button is hidden")
    assert.ok(wrapper.find("#show-claims-form").exists(), "form is closed")
    assert.ok(!wrapper.vm.showingNewclaims, "not showing new claim form")

    // open form
    wrapper.find("#show-claims-form").trigger("click")
    assert.ok(wrapper.vm.showingNewclaims, "showing new claim form after click")
    assert.ok(!wrapper.find("#show-claims-form").exists(), "show claim form button is now hidden because form is present")
    assert.ok(wrapper.find("#claims-form-create").exists(), "create button is present")
    assert.ok(wrapper.find("#claims-form-cancel").exists(), "cancel button is present")
    assert.ok(wrapper.vm.$v.form.$invalid, "form is invalid")
    assert.ok(!wrapper.vm.$v.form.$error, "form error is not shown")
    assert.ok(wrapper.vm.$v.form.description.$invalid, "description is invalid")
    assert.ok(!wrapper.vm.$v.form.description.$error, "description error is not shown")
    //TODO assert.ok(!wrapper.vm.$v.form.$invalid, "form is invalid")

    // the input MUST be focused so that when it later loses focus, the lazy-rules are validated
    document.querySelector("input#claims-form-summary").focus();
    wrapper.find("input#claims-form-summary").setValue("a summary that is too long");
    assert.equal(wrapper.vm.form.summary, "a summary that is too long", "summary value is set but too long")

    // check there is no validation error yet, because focus has not yet been lost
    assert.ok(_.isUndefined(wrapper.vm.$refs.summary.innerErrorMessage), "error on summary field is not shown yet because not blurred")

    // focus a different element, to cause lazy rules to be executed
    //     doesnt work: wrapper.find("#claims-form-summary").trigger("blur")
    //     doesnt work: wrapper.find("#claims-form-create").trigger("focus");
    //     doesnt work: wrapper.find("button#claims-form-create").trigger("focus")
    //     doesnt work: wrapper.find("input#claims-form-summary").trigger("blur")
    //     works but requires knowledge of textarea - i guess thats ok, we specify that under "type": wrapper.find("textarea#claims-form-description").element.focus()
    //     works but requires use of refs: wrapper.vm.$refs.description.focus();
    //     works for blurring, but focus doesnt end up in input!: wrapper.find("#claims-form-create").element.focus();
    //     works but really ugly: wrapper.find("#claims-form-description").vnode.componentInstance.$refs.input.focus()
    //     works for blurring, but focus doesnt end up in input!: document.getElementById("claims-form-create").focus(); // most elegant, doesnt require use of refs or knowledge of internal stuff
    //     works, but lets focus on the button instead: document.querySelector("textarea#claims-form-description").focus()
    document.querySelector("button#claims-form-create").focus()
    await nextTick(); // required because otherwise validation does not get executed the first time

    assert.equal(wrapper.vm.$refs.summary.innerErrorMessage, "Please use maximum 20 character", "focused on create button, so error on summary field is now shown")

    // try and submit the form, but it fails because form isnt valid yet
    wrapper.find("#claims-form-create").trigger("click")
    assert.ok(wrapper.vm.showingNewclaims, "form is still showing after click on create button because form is invalid")
    assert.equal(controller.createClaim.callCount, 0, "controller not called yet because form is invalid")

    // make the field valid by reducing the number of chars
    // validation is no done on the fly, not upon blur, so there is no need to refocus or call "nextTick"
    wrapper.find("input#claims-form-summary").setValue("a short summary");
    assert.equal(wrapper.vm.form.summary, "a short summary", "summary value is set to something valid ")

    // check there is no validation error
    assert.ok(_.isUndefined(wrapper.vm.$refs.summary.innerErrorMessage), "error is now gone from summary field because its valid")

    // set the description. TODO check validation on the description field too
    wrapper.find("textarea#claims-form-description").setValue("something happened and something broke");

    assert.equal(controller.createClaim.callCount, 0, "controller not called yet")

    // submit the form
    wrapper.find("#claims-form-create").trigger("click")

    assert.equal(controller.createClaim.callCount, 1, "controller was called after click on create button")
    assert.equal(controller.createClaim.args[0], "something happened and something broke", "controller was called with correct arguments")
    assert.ok(!wrapper.vm.showingNewclaims, "form is hidden after create button clicked")

    done();

    // TODO convert to page object model pattern
    // TODO assert things on $v
    // TODO reopen form and check its empty
    // TODO cancel and reopen form and check its not empty

    /** lets the event loop process stuff waiting on the message queue  */
    function nextTick(ms) {
        return new Promise(function(resolve){
            setTimeout(function(){
                resolve();
            }, ms);
        });
    }
});


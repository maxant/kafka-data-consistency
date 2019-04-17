const shallow = vueTestUtils.shallow;
const createLocalVue = vueTestUtils.createLocalVue;

import {claimsFormComponentObject} from './claims.js'

QUnit.test( "test claims form", async function( assert ) {

    const provide = {
        controller: {
            createClaim(description) {
                console.log("mock controller received " + description + " in method createClaim")
            }
        }
    };

//TODO    const localVue = createLocalVue()
//    localVue.use(vuelidate.default);
//    const wrapper = shallow(claimsFormComponentObject, { localVue, provide } )
Vue.use(vuelidate.default);

    const done = assert.async();

    const wrapper = shallow(claimsFormComponentObject, { provide } )

    // add it to the page - nice to watch, but also may help with events?
    document.getElementById("component").appendChild(wrapper.element);

    // check html
    assert.ok(!wrapper.find("#claims-form-create").exists(), "create button is hidden")
    assert.ok(wrapper.find("#show-claims-form").exists(), "form is closed")
    assert.ok(!wrapper.vm.showingNewclaims)

    // open form
    wrapper.find("#show-claims-form").trigger("click")
    assert.ok(wrapper.vm.showingNewclaims)
    assert.ok(!wrapper.find("#show-claims-form").exists(), "form is open")
    assert.ok(wrapper.find("#claims-form-create").exists(), "create button is present")
    assert.ok(wrapper.find("#claims-form-cancel").exists(), "cancel button is present")
    assert.ok(wrapper.vm.$v.form.$invalid, "form is invalid")
    assert.ok(!wrapper.vm.$v.form.$error, "form error is not shown")
    assert.ok(wrapper.vm.$v.form.description.$invalid, "description is invalid")
    assert.ok(!wrapper.vm.$v.form.description.$error, "description error is not shown")
    //TODO assert.equal("", wrapper.find("#claims-form-description.q-input-area").element.value, "description is empty")
    //TODO wrapper.find("#claims-form-description.q-input-area").element.value = "asdf"
    //TODO assert.ok(!wrapper.vm.$v.form.$invalid, "form is invalid")

    // focus the input first, because otherwise blur doesnt help
    document.querySelector("input#claims-form-other").focus();
    wrapper.vm.form.other = "fdas"; // simulate input => TODO why can't we call setValue on the wrapper?

    await nextTick();

    // check there is a validation error
    assert.ok(_.isUndefined(wrapper.vm.$refs.other.innerErrorMessage), "error on other field is not shown yet because not blurred")

    //
    // doesnt work: wrapper.find("#claims-form-other").trigger("blur")
    // doesnt work: wrapper.find("#claims-form-create").trigger("focus");
    // doesnt work: wrapper.find("input#claims-form-other").trigger("blur")
    // works but requires knowledge of textarea - i guess thats ok, we specify that under "type": wrapper.find("textarea#claims-form-description").element.focus()
    // works but requires use of refs: wrapper.vm.$refs.description.focus();
    // works for blurring, but focus doesnt end up in input!: wrapper.find("#claims-form-create").element.focus();
    // works but really ugly: wrapper.find("#claims-form-description").vnode.componentInstance.$refs.input.focus()
    // works for blurring, but focus doesnt end up in input!: document.getElementById("claims-form-create").focus(); // most elegant, doesnt require use of refs or knowledge of internal stuff
    // works, but lets focus on the button instead: document.querySelector("textarea#claims-form-description").focus()
    document.querySelector("button#claims-form-create").focus()

    await nextTick();

    assert.equal(wrapper.vm.$refs.other.innerErrorMessage, "Please use maximum 1 character", "error on other field is shown")

    // make it valid
    document.querySelector("input#claims-form-other").focus();
    wrapper.vm.form.other = "A";
    document.querySelector("button#claims-form-create").focus()
    await nextTick();
    // check there is no validation error
    assert.ok(_.isUndefined(wrapper.vm.$refs.other.innerErrorMessage), "no error on other field")

    done();
/*
TODO convert to page object model pattern
TODO test mock when clicking button at end
TODO must keep debug window closed :-( otherwise focus doesnt work => pain in the ass for lazy validation
*/


    // TODO assert things on $v
    // TODO close form
    // TODO check its not reset when reopening
    // TODO etc...
});

/** vue appears to need time to render */
function nextTick(ms) {
    return new Promise(function(resolve){
        setTimeout(function(){
            resolve();
        }, ms);
    });
}
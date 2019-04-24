const shallowMount = VueTestUtils.shallowMount;
const createLocalVue = VueTestUtils.createLocalVue;

import {componentObject} from './navigations.js'

var wrapper;

function click(id) { wrapper.find("#navigation-" + id).trigger("click")}

QUnit.test( "test navigations", function( assert ) {

    // setup
    const localVue = createLocalVue()
    localVue.use(VueRouter)
    const router = new VueRouter({ routes: [{ name: "search", path: '/'   },
                                            { name: 'n1',     path: '/n1' },
                                            { name: 'n2',     path: '/n2' }]
                                })
    const navigations = [{
                            title: "t1", name: "n1"
                        },{
                            title: "t2", name: "n2"
                        }]

    wrapper = shallowMount(componentObject, {
        localVue,
        router,
        propsData: { navigations }
    })


    // check html
    assert.ok( wrapper.html().indexOf("t1") > -1, "t1 present")
    assert.ok( wrapper.html().indexOf("t2") > -1, "t2 present")

    // check clicking
    wrapper.vm.$router.push({name: "search"}) // initial setup
    assert.equal(wrapper.vm.$router.currentRoute.name, "search", "router is search");
    click("n1")
    assert.ok(wrapper.vm.$router.currentRoute.name === "n1", "router is n1");
    click("n2")
    assert.ok(wrapper.vm.$router.currentRoute.name === "n2", "router is n2");
});


(function(){
// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// demo widget
// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

const { Subject } = rxjs;

var template =
// start template
`
<div>
    Demo using rxjs: count = {{subj}}
    <br>
    <slot :anInt="myInt"></slot>
</div>
` // end template

window.mfPortalDemoWithSlot = {
    mixins: [rxjsMixin],
    data() {
        return {
            subj$: new Subject(),
            myInt: 0
        };
    },
    template,
    subscriptions() {
        return {subj: this.subj$};
    },
    mounted() {
        let i = 0;
        let s = this.subj$;
        setInterval(() => {
            s.next(++i);
            this.myInt = i;
        }, 500);
    }
}

// /////////////////////////////////////////////////////////////////////////

var template =
// start template
`
<div>
    <mf-portaldemowithslot  v-slot:default="slotProps">
        asdf: {{slotProps.anInt }}
    </mf-portaldemowithslot>
</div>
` // end template

window.mfPortalDemo = {
    template,
    components: {
        'mf-portaldemowithslot': mfPortalDemoWithSlot
    }
}


})();

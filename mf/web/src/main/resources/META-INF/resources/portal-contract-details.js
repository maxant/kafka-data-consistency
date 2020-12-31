(function(){
// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// contract details widget
// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

var template =
// start template
`
<div>
Contract Details - {{ $route.params.contractId }}
</div>

<mf-contract :contract-id="$route.params.contractId"
             allowAcceptOffer="true"
             withDetails="true"
             #default="sp">
    <div>
        Offered by {{sp.theContract.offeredBy}} on {{sp.theContract.offeredAt}}
    </div>
    <div>
        Accepted by {{sp.theContract.acceptedBy}} on {{sp.theContract.acceptedAt}}
    </div>
    <div>
        Approved by {{sp.theContract.approvedBy}} on {{sp.theContract.approvedAt}}
    </div>
    <div>
        <ul>
            <li v-for="component in sp.theContract.components">
                <div>{{component.componentDefinitionId}}</div>
                <ul>
                    <li v-for="config in component.configs">{{config.name}} {{config.value}} {{config.units}}</li>
                </ul>
            </li>
        </ul>
    </div>
</mf-contract>

` // end template

window.mfPortalContractDetails = {
    template,
    components: {
        'mf-contract': mfContractTile
    }
}

})();

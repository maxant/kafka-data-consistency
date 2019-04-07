Vue.component('claims', {
    props: ['claims'],
    template: `
        <div id="claims" class="tile-group">
            Claims<br>
            <div class="row">
                <q-btn label="create new claim..." color="primary" icon="create" @click="createClaim()"/>
            </div>
            <div v-if="claims.error" class="error row">
                <q-alert :type="warning" class="q-mb-sm" icon="priority_high">
                    {{claims.error}}
                </q-alert>
            </div>
            <div v-else-if="claims.loading" class="row"><q-spinner-hourglass size="32px"/></div>
            <div v-else-if="claims.entities.length === 0" class="row"><i>No claims</i></div>
            <div v-else class="row">
                <div v-for="claim in claims.entities" class="col-xs-12 col-sm-12 col-md-12 col-lg-6">
                    <div class="tile">
                        <div class='tile-title'><i class='fas fa-exclamation-circle'></i>&nbsp;Claim</div>
                        <div class='tile-body'><i>{{claim.id}}</i><br>{{claim.description}}</div>
                    </div>
                </div>
            </div>
        </div>
    `,
    methods: {
        createClaim: function() {
            var xhr = new XMLHttpRequest();
            xhr.open('POST', 'http://localhost:8081/claims/rest/claims', true);
            xhr.setRequestHeader("Content-type", "application/json");
            xhr.onreadystatechange = function() {
                if (xhr.readyState == XMLHttpRequest.DONE) {
                    if (xhr.status === 202) {
                        console.log("claim creation request accepted");
                    } else {
                        alert("failed to create claim: " + xhr.status + "::" + xhr.responseText);
                    }
                }
            }
            xhr.send(JSON.stringify({"description" :"Nunc dictum tristique ex eu eleifend. Ut non massa ut libero imperdiet sollicitudin.", "customerId": "C-4837-4536"}));
        }
    }
});

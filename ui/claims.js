Vue.component('claims', {
    props: ['claims'],
    template: `
        <div id="claims" class="tile-group">
            <div>
                <q-btn label="create new claim..." color="primary" icon="create" @click="createClaim()" />
            </div>
            <div v-if="claims.error" class="error">
                <q-alert :type="warning" class="q-mb-sm" icon="priority_high">
                    {{claims.error}}
                </q-alert>
            </div>
            <div v-else-if="claims.loading"><q-spinner-hourglass size="32px"/></div>
            <div v-else-if="claims.entities.length === 0"><i>No claims</i></div>
            <table v-else>
                <tr v-for="claim in claims.entities">
                    <td class='tile'>
                        <div class='tile-title'><i class='fas fa-exclamation-circle'></i>&nbsp;Claim</div>
                        <div class='tile-body'><i>{{claim.id}}</i><br>{{claim.description}}</div>
                    </td>
                </tr>
            </table>
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

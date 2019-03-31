const PartnerView = {
    data: function(){ return model },
    watch: { '$route': 'loadPartner' },
    created: function() {this.loadPartner()}, // this is an example of data fetching aka an angular resolver
    methods: {
        clearData() {
            var xhr = new XMLHttpRequest();
            xhr.open('DELETE', 'http://localhost:8081/claims/rest/claims', true);
            xhr.send();

            xhr = new XMLHttpRequest();
            xhr.open('DELETE', 'http://localhost:8082/tasks/rest/tasks', true);
            xhr.send();
            console.log("data is being cleared");
        },
        loadPartner() {
            this.loading = true;
            var id = this.$route.params.id;
            console.log("loading partner " + id + "...");
            var self = this;
// TODO replace with call to backend services to load model
// TODO make each submodel have its own loading flag
// TODO also handle errors
// TODO make loading gif for claims/tasks non red
            return new Promise(function (resolve, reject) {
                setTimeout(function() {
                    self.loading = false;
                    console.log("partner was fetched!");
                    resolve();
                }, 1000);
            });
        }
    },
    template: `
        <div v-if="loading" style="position: fixed; width: 100%; height: 100%; top: 0; left: 0; right: 0; bottom: 0; background-color: rgba(0,0,0,0.5); z-index: 2; cursor: pointer;">loading...</div>
        <table v-else width="100%" height="100%">
            <tr>
                <td colspan="2" align="center">
                    <h2><img height="50px" src="k.jpg" style="vertical-align: text-bottom;">AYS Insurance Ltd.</h2>
                </td>
                <td align="right" valign="top">
                    <small><a href='#' @click.prevent="clearData();">clear test data</a></small>
                </td>
            </tr>
            <tr><td colspan="3">
                <hr>
            </td></tr>
            <tr>
                <td width="20%" valign="top">
                    <navigations v-bind:navigations="navigations"></navigations>
                </td>
                <td width="60%" valign="top">
                    <partner v-bind:partner="partner"></partner>
                    <hr>
                    <contracts v-bind:contracts="contracts"></contracts>
                    <hr>
                    <claims v-bind:claims="claims"></claims>
                </td>
                <td width="20%" valign="top">
                    <tasks v-bind:tasks="tasks"></tasks>
                    <hr>
                    <documents></documents>
                </td>
            </tr>
        </table>
    `
};

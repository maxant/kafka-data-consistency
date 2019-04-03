const PartnerView = (function() {
    function buildNavigations() {
        return [
            { title: 'home',   name: 'home',   params: {} },
            { title: 'search', name: 'search', params: {} },
        ];
    };

    return {
        data: function(){ return store },
        watch: { '$route': 'loadStore' }, // if e.g. user changes it, reload model
        created: function() {this.loadStore()}, // this is an example of data fetching aka an angular resolver
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
            loadStore() {
                this.startLoading();
                var id = this.$route.params.id;
                console.log("loading partner " + id + "...");
                loadClaims();
                loadTasks();
                var self = this;
// TODO replace with call to backend services to load model
// TODO also handle errors
                return new Promise(function (resolve, reject) {
                    setTimeout(function() {
                        self.completedLoading();
                        self.setMenu(buildNavigations());
                        resolve();
                    }, 1000);
                });
            }
        },
        template: `
            <div>
                <div :class="[loading ? '' : 'hidden']" style="position: fixed; width: 90%; height: 100%; top: 0; left: 0; right: 100px; bottom: 0; background-color: rgba(0,0,0,0.1); z-index: 2;">loading...</div>
                <table width="100%" height="100%">
                    <tr>
                        <td colspan="2" align="center">
                            <h2><img height="50px" src="k.jpg" style="vertical-align: text-bottom;">AYS Insurance Ltd.</h2>
                        </td>
                        <td align="right" valign="top">
                            <div style="z-index=1;">
                                <small>
                                    <a href='#' @click.prevent="clearData();">clear test data</a>
                                    <br>
                                    <a href='#' @click.prevent="timeTravelBack();">&lt;&lt;</a>
                                    time travel
                                    <a href='#' @click.prevent="timeTravelForward();">&gt;&gt;</a>
                                    <br>
                                    {{getCurrent().timestamp.toISOString()}}
                                    <br>
                                    Index {{getHistory().timeTravelIndex}} of {{getHistory().length - 1}}: {{getCurrent().message}}
                                </small>
                            </div>
                        </td>
                    </tr>
                    <tr><td colspan="3">
                        <hr>
                    </td></tr>
                    <tr>
                        <td width="20%" valign="top">
                            <navigations :navigations="navigations"></navigations>
                        </td>
                        <td width="60%" valign="top">
                            <partner :partner="partner"></partner>
                            <hr>
                            <contracts :contracts="contracts"></contracts>
                            <hr>
                            <claims :claims="claims"></claims>
                        </td>
                        <td width="20%" valign="top">
                            <tasks :tasks="tasks"></tasks>
                            <hr>
                            <documents></documents>
                        </td>
                    </tr>
                </table>
            </div>
        `
    };
})();


let model = {contractVersions: []}

export const ContractView = {
    data(){ return {model: model} },
    watch: { '$route': 'init' }, // if e.g. user changes it, reload
    created() { // this is an example of data fetching aka an angular resolver
        return this.init(); // TODO is return statement used? doesnt seem necessary...
    },
    methods: {
        showMenu() { return !this.$q.screen.xs && !this.$q.screen.sm; },
        init() {
            const self = this;
            this.$q.loading.show();
            var cn = this.$route.params.cn;
            console.log("loading contract " + cn + "...");
            axios.get(CONTRACTS_BASE_URL + 'product/versions/' + cn)
            .then(function(response) {
                window.contractVersions = response.data;
                model.contractVersions = response.data;
                console.log("contract loaded");

                let groups = new vis.DataSet();
                let contractIdsAlreadyEncountered = [];
                let container = document.getElementById('timeline');
                let items = new vis.DataSet(
                    _.map(response.data, row => {
                        if(_.indexOf(contractIdsAlreadyEncountered, row.contract.id) < 0) {
                            contractIdsAlreadyEncountered.push(row.contract.id);
                            groups.add({
                                id: row.contract.id,
                                content: 'contract version'
                                        + '<br>from ' + row.contract.from
                                        + '<br>to ' + row.contract.to
                            });
                        }
                        return {
                            id: row.id,
                            content: 'product instance version'
                                        + '<br>from ' + row.from
                                        + '<br>to ' + row.to
                                        + '<br>' + row.name
                                        + '<br>Insured ' + row.insuredSum + 'CHF'
                                        + '<br>Index ' + row.indexValue
                                    ,
                            group: row.contract.id,
                            start: row.from,
                            end: row.to
                        };
                    })
                );
                let options = {groupOrder: 'end', start: '2018-12-01', end: new Date(new Date().getTime() + (365*24*3600000) ) };
                let timeline = new vis.Timeline(container);
                timeline.setOptions(options);
                timeline.setGroups(groups);
                timeline.setItems(items);




            }).catch(function(error) {
                console.error("Failed to load contract: " + error);
            }).finally(() => self.$q.loading.hide() );
        }
    },
    template:
    `
    <div>
    <div id='timeline'></div>
    <table border="1" width="100%">
        <tr v-for="row in model.contractVersions">
            <td>{{row}}</td>
        </tr>
    </table>
    </div>
    `
};

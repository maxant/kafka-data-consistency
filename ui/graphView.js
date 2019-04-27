const map = rxjs.operators.map;
const filter = rxjs.operators.filter;
const debounceTime = rxjs.operators.debounceTime;
const distinctUntilChanged = rxjs.operators.distinctUntilChanged;
const switchMap = rxjs.operators.switchMap;

const driver = neo4j.v1.driver(NEO4J_URL, neo4j.v1.auth.basic("", ""));

const model = {
    searchResult: [],
    error: "",
    time: -1,
    searchTerm:
`MATCH (claim:Claim)<-[r:CLAIMANT]-(p:Partner)
WHERE claim.date > '2018-08-01'
WITH count(r) as cnt, p
WHERE cnt > 1
RETURN cnt, p.id`

}

export const GraphView = {
    data(){ return { model } },
    domStreams: ['searchRequest$'],
    subscriptions () {
        this.searchRequest$.pipe(
            map(e => e.event.target.value),
            map(text => {model.searchTerm = text;  return text}), // TODO is there anything like a peek?
            filter(text => text.length > 2),
            debounceTime(300),
            distinctUntilChanged(),
            switchMap(this.changed)
        ).subscribe(record => {
            _.forEach(record.keys, key => {
                model.searchResult.push({key: key, value: record.get(key)})
            });
            console.log(JSON.stringify(record));
        });
    },
    mounted(){
        this.$refs.search.focus(); // `autofocus` doesnt work unless you reload the page
    },
    methods: {
        changed(term) {
            const start = new Date().getTime();
            model.error = "";
            model.searchResult = [];

            this.draw(); // TODO at the moment we are doing two calls to neo4j => fix that!

            const self = this;
            const session = driver.session();
            //session.run('MERGE (alice:Person {name : {nameParam} }) RETURN alice.name AS name', {nameParam: 'Alice'})
            const observable = session.run(term, {});
            const subject = new rxjs.Subject();
            observable.subscribe({
                onNext: function (record) {
                    subject.next(record);
                },
                onCompleted: function(){
                    model.time = new Date().getTime() - start;
                    session.close();
                    subject.complete();
                },
                onError: function (error) {
                    model.error = error;
                }
            });
            return subject;
        },
        draw() {
            let config = {
                container_id: "viz",
                server_url: NEO4J_URL,
                server_user: "",
                server_password: "",
/*
                labels: {
                    "Claim": {
                        "caption": "date",
                        "size": "5"
                    },
                    "Partner": {
                        "caption": "id",
                        "size": "5"
                    },
                    "Contract": {
                    }
                },
                relationships: {
                    "CLAIMANT": {
                        "thickness": 1,
                        "caption": false
                    }
                },
*/
                initial_cypher: model.searchTerm
            };

            let viz = new NeoVis.default(config);
            viz.render();
        }
    },
    template:
    `
    <div>
        <div class="row">
            <div class="col-12" style="padding-top: 20px; text-align: center;">
                <h2><img height="50px" src="skynet.svg" style="vertical-align: middle;">&nbsp;KAYS Insurance Ltd.</h2>
            </div>
        </div>
        <div class="row">
            <div class="col-12">
                <hr>
            </div>
        </div>
        <div class="row">
            <div class="col-12 centred">
                <textarea ref='search' style="width: 60%;" rows="4" v-model="model.searchTerm" v-stream:keyup="searchRequest$"></textarea><br>
            </div>
        </div>
        <div class="row">
            <div class="col-12 centred" v-if="model.time >= 0" style="color: darkblue;">
                <small>Found {{model.searchResult.length}}
                        result {{model.searchResult.length===1?'':'s'}} in
                        {{model.time}}ms
                </small>
            </div>
        </div>
        <div class="row">
            <div class="col-12 centred" v-if="!!model.error" style="color: red;">
                <pre>{{model.error}}</pre>
            </div>
        </div>
        <div class="row">
            <div id="viz"></div>
            <div v-if="model.searchResult.length === 0">No results found.</div>
            <table v-else border="1">
                <tr v-for="row in model.searchResult">
                    <td>{{row.key}}</td>
                    <td>{{row.value}}</td>
                </tr>
            </table>
        </div>
    </div>
    `
};

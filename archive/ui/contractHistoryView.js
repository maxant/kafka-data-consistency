const map = rxjs.operators.map;
const filter = rxjs.operators.filter;
const debounceTime = rxjs.operators.debounceTime;
const distinctUntilChanged = rxjs.operators.distinctUntilChanged;
const switchMap = rxjs.operators.switchMap;

//const driver = neo4j.v1.driver(NEO4J_URL, neo4j.v1.auth.basic("", ""));
const driver = neo4j.v1.driver('bolt://localhost:7687', neo4j.v1.auth.basic("", ""));

const model = {
    searchResultColumns: {},
    searchResultRows: [],
    error: "",
    time: -1,
    searchTerm:
`match (n:Contract)-[r:replacedBy]-()
where n.created < '2018-06-01' AND n.expires >= '2018-06-01'
return *`
}

export const ContractHistoryView = {
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
            let row = {};
            _.forEach(record.keys, key => {
                model.searchResultColumns[key] = key;
                row[key] = record.get(key);
            });
            model.searchResultRows.push(row);
            console.log(JSON.stringify(record));
        });
    },
    mounted(){
        this.$refs.search.focus(); // `autofocus` doesnt work unless you reload the page
    },
    methods: {
        changed(term) {

            var ctx = document.getElementById("twoDView").getContext("2d");
            ctx.fillStyle = "#FFFFFF";
            ctx.fillRect(0, 0, 800, 400);

            const start = new Date().getTime();
            model.error = "";
            model.searchResultColumns = {};
            model.searchResultRows = [];

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
                    self.drawTwoDView();
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
                initial_cypher: model.searchTerm
            };

            let viz = new NeoVis.default(config);
            viz.render();
        },
        drawTwoDView() {
            console.log(model.searchResultRows.length);
            var ctx = document.getElementById("twoDView").getContext("2d");

            //row.n.properties.number.toString()


            ctx.moveTo(0, 0);
            ctx.lineTo(200, 100);
            ctx.stroke();
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
                <small>Found {{model.searchResultRows.length}}
                        result {{model.searchResultRows.length===1?'':'s'}} in
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
            <div v-if="model.searchResultRows.length === 0" class="col-12 centred" >No results found.</div>
            <div v-else class="col-12 centred">
                <table border="1" width="100%">
                    <tr>
                        <th v-for="col in model.searchResultColumns">{{col}}</th>
                    </tr>
                    <tr v-for="row in model.searchResultRows">
                        <td v-for="col in model.searchResultColumns">{{row[col]}}</td>
                    </tr>
                </table>
            </div>
        </div>
        <div>
            <canvas id="twoDView" width="800" height="400"></canvas>
        </div>
    </div>
    `
};

const map = rxjs.operators.map;
const filter = rxjs.operators.filter;
const debounceTime = rxjs.operators.debounceTime;
const distinctUntilChanged = rxjs.operators.distinctUntilChanged;
const switchMap = rxjs.operators.switchMap;

const model = {
    searchResult: {},
    searchTerm: ""
}

export const SearchView = {
    data(){ return { model } },
    domStreams: ['searchRequest$'],
    subscriptions () {
        this.searchRequest$.pipe(
            map(e => e.event.target.value),
            map(text => {model.searchTerm = text;  return text}),
            filter(text => text.length > 2),
            debounceTime(300),
            distinctUntilChanged(),
            switchMap(this.changed)
        ).subscribe(e => model.searchResult = e) // TODO naughty, this should be set via the controller!
    },
    mounted(){
        this.$refs.search.focus(); // `autofocus` doesnt work unless you reload the page
    },
    methods: {
        changed(term) {
            const self = this;
            return axios.get(ELASTIC_BASE_URL + "claims,partners/_search?q=" + encodeURIComponent(term)).then( response => {
                if(response.status === 200) {
                    return response.data;
                } else {
                    console.error("error getting search results. please try again"); // TODO handle this better
                    return {};
                }
            }).catch(error => {
                console.error("error getting search results: " + error + ". please try again"); // TODO handle this better
                return {};
            });
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
                <input ref='search' style="width: 60%;" v-model="model.searchTerm" v-stream:keyup="searchRequest$" /><br>
                <small>
                    Use * for wildcard searches e.g. when searching for an ID, or <code>reserve:>999"</code> to search for entities with that field <br>
                    greater than that value. You can even do crazy shit like this: <code>date:<2018-12-31 AND date:>2018-05-31</code>
                </small>
            </div>
        </div>
        <div class="row">
            <div class="col-12 centred" v-if="!!model.searchResult.timed_out">
                Timed out...
            </div>
            <div class="col-12 centred" v-if="!!model.searchResult.hits" style="color: darkblue;">
                <small>Found {{model.searchResult.hits.total.relation === 'eq' ? '' : 'more than'}}
                        {{model.searchResult.hits.total.value}}
                        result{{model.searchResult.hits.total.value===1?'':'s'}} in
                        {{model.searchResult.took}}ms
                </small>
            </div>
        </div>
        <div class="row" v-if="!!model.searchResult.hits">
            <div class="col-3" v-for="hit in model.searchResult.hits.hits">
                <claim v-if="hit._index === 'claims'" :claim="hit._source" :showLabels="true" />
                <div v-else class='tile'><b>Unexpected type</b><br>{{hit._source}}</div>
            </div>
        </div>

    </div>
    `
};

Vue.component('contracts', {
    props: ['contracts'],
    template: `
        <div id="contracts" class="tile-group">
            Contracts<br>
            <div class="row">
                <div v-for="contract in contracts.entities" class="col-xs-12 col-sm-12 col-md-12 col-lg-6">
                    <div class="tile">
                        <div class='tile-title'>
                            <i class="fas fa-file-contract"></i>&nbsp;<b>{{contract.id}}</b>
                        </div>
                        <div class='tile-body'>
                            {{contract.title}}<br>
                            {{contract.subtitle}}
                        </div>
                    </div>
                </div>
            </div>
        </div>
    `
});

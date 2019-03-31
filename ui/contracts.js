Vue.component('contracts', {
    props: ['contracts'],
    template: `
        <div id="contracts" class="tile-group">
            <table>
                <tr><td class='tile' v-for="contract in contracts">
                    <div class='tile-title'><i class="fas fa-file-contract"></i>&nbsp;<b>{{contract.id}}</b></div>
                    <div class='tile-body'>
                        {{contract.title}}<br>
                        {{contract.subtitle}}
                    </div>
                    </td></tr>
            </table>
        </div>
    `
});

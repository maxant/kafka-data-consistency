Vue.component('partner', {
    props: ['partner'],
    template: `
        <div id="partner" class="tile-group">
            <table>
                <tr><td class='tile'>
                    <div class='tile-title'><i class="fas fa-user"></i>&nbsp;<b>{{partner.name}}</b></div>
                    <div class='tile-body'>
                        {{partner.id}}<br>
                        {{partner.address.street}} {{partner.address.number}}<br>
                        {{partner.address.zip}} {{partner.address.city}}<br>
                        {{partner.phone}}
                    </div>
                    </td></tr>
            </table>
       </div>
    `
});


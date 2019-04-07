Vue.component('partner', {
    props: ['partner'],
    template: `
        <div id="partner" class="tile-group">
            Partner<br>
            <div class="row">
                <div class="col-xs-12 col-sm-12 col-md-12 col-lg-6">
                    <div class="tile">
                        <div class='tile-title'><i class="fas fa-user"></i>&nbsp;<b>{{partner.entity.name}}</b></div>
                        <div class='tile-body'>
                            {{partner.entity.id}}<br>
                            {{partner.entity.address.street}} {{partner.entity.address.number}}<br>
                            {{partner.entity.address.zip}} {{partner.entity.address.city}}<br>
                            {{partner.entity.phone}}
                        </div>
                    </div>
                </div>
            </div>
       </div>
    `
});


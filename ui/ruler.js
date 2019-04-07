Vue.component('ruler', {
    template: `
        <div>
            <div style="position: absolute; top: 0px; left:    0px; width: 576px; height: 14px; border: 1px solid grey; color: grey; text-align: center; font-size: 12px;">xs</div>
            <div style="position: absolute; top: 0px; left:  576px; width: 192px; height: 14px; border: 1px solid grey; color: grey; text-align: center; font-size: 12px;">sm</div>
            <div style="position: absolute; top: 0px; left:  768px; width: 224px; height: 14px; border: 1px solid grey; color: grey; text-align: center; font-size: 12px;">md</div>
            <div style="position: absolute; top: 0px; left:  992px; width: 208px; height: 14px; border: 1px solid grey; color: grey; text-align: center; font-size: 12px;">lg</div>
            <div style="position: absolute; top: 0px; left: 1200px; width: 100px; height: 14px; border: 1px solid grey; color: grey; text-align: left;   font-size: 12px;">xl &gt;&gt;&gt;</div>
        </div>
    `
});

{
    data: function(){
        return { name: "lazy" }
    },
    template: `
        <div>
            This page was loaded lazily.
            The javascript is only ever loaded once.
            Some data: "{{name}}"
        </div>
    `
}


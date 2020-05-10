if (!!window.EventSource) {
    var eventSource = new EventSource("/objects/changes/" + new Date().getTime() + "-" + Math.round(1000000*Math.random()));
    eventSource.onmessage = function (event) {
        var container = document.getElementById("container");
        var paragraph = document.createElement("p");
        paragraph.innerHTML = event.data;
        container.appendChild(paragraph);
    };

    // TODO handle errors
    // TODO Add ping pong to cope with broken connections

} else {
    window.alert("EventSource not available on this browser.")
}

function emit() {
    fetch('/objects/emit', {method: 'GET'}).then(r => r.json()).then(console.log);
}
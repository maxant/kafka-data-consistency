var socket;

function init() {
    var loc = window.location, newUri;
    if (loc.protocol === "https:") {
        newUri = "wss:";
    } else {
        newUri = "ws:";
    }
    newUri += "//" + loc.host;
    newUri += loc.pathname + "ws";

    socket = new WebSocket(newUri);
    socket.onmessage = onMessage;
    socket.onclose = onClose;
    socket.onerror = onError;
    socket.onopen = onOpen;
}

function onOpen() {
    console.log("ws opened");
}

function onMessage(event) {
    console.log(event);
}

function onClose(){
    console.log("ws closed");
    socket = null;
    setTimeout(init, 100);
};

function onError(e){
    console.log("ws error: " + e);
    if(socket) {
        socket.close();
    } else {
        socket = null;
        setTimeout(init, 100);
    }
};

init();

setInterval(function() {
    if(socket) {
        socket.send(JSON.stringify("heartbeat"));
    }
}, 1000);

console.log("STARTEDd");
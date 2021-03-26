///////////////////////////////////////////////
// setups and maintains a websocket.
// sends incoming JSON messages to a function
// named onWSMessage on the window object
///////////////////////////////////////////////

var socket;

function init() {
    socket = new WebSocket("ws://" + UI_HOST_PORT + "/web/ws");
    socket.onmessage = onMessage;
    socket.onclose = onClose;
    socket.onerror = onError;
    socket.onopen = onOpen;
}

function onOpen() {
    console.log("ws opened");
}

function onMessage(event) {
    window.onWSMessage(JSON.parse(event.data));
}

function onClose(){
    console.log("ws closed");
    socket = null;
    setTimeout(init, 100);
};

function onError(e){
    console.log("ws error: " + JSON.stringify(e));
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
        socket.send("heartbeat");
    }
}, 10000);

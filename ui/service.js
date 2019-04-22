const from = rxjs.from;
const map = rxjs.operators.map;

function createClaim(description, customerId) {
    return new Promise(function (resolve, reject) {
        setTimeout(function(){ // so that demo shows "in progress" message
            const claim = {"description": description, "customerId": customerId};
            axios.post('http://localhost:8081/claims/rest/claims', claim)
            .then(function(response) {
                if(response.status === 202) {
                    console.log("claim creation request accepted");
                    resolve();
                } else reject("Unexpected response code " + response.status);
            }).catch(function(call) {
                console.error("Failed to create claim: " + JSON.stringify(call));
                reject("Failed to create claim. See console for details. (" + (call.response?call.response.status:"-") + ")");
            });
        }, 1000);
    });
}

function loadClaims() {
    return from(axios.get('http://localhost:8081/claims/rest/claims'));
}

function loadTasks() {
    return new Promise(function (resolve, reject) {
        var xhr = new XMLHttpRequest();
        xhr.open('GET', 'http://localhost:8082/tasks/rest/tasks', true);
        xhr.onreadystatechange = function() {
            if (xhr.readyState == XMLHttpRequest.DONE) {
                if (xhr.status === 200) {
                    var tasks = JSON.parse(xhr.responseText);
                    resolve(tasks);
                } else {
                    reject("failed to load tasks: " + xhr.status + "::" + xhr.responseText);
                }
            }
        }
        xhr.send();
    });
}

export const service = {
    createClaim: createClaim,
    loadClaims: loadClaims,
    loadTasks: loadTasks
};
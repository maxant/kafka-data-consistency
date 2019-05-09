const from = rxjs.from;
const map = rxjs.operators.map;

function createClaim(form, partnerId) {
    var span = elasticApm.getCurrentTransaction().startSpan('createClaim', 'http')
    return new Promise(function (resolve, reject) {
        const claim = { "summary": form.summary,
                        "description": form.description,
                        "partnerId": partnerId,
                        "reserve": form.reserve,
                        "date": form.date.replace(/\//g, "-"),
                        "location": form.location
                      };
        //example of adding a header: axios.post(CLAIMS_BASE_URL + 'claims', claim, {headers: {"elastic-apm-traceparent": some value}})
        axios.post(CLAIMS_BASE_URL + 'claims', claim)
        .then(function(response) {
            span.end()
            if(response.status === 202) {
                console.log("claim creation request accepted");
                resolve();
            } else reject("Unexpected response code " + response.status);
        }).catch(function(error) {
            console.error("Failed to create claim: " + error);
            reject("Failed to create claim. See console for details. (" + (error.response?error.response.status:"-") + ")");
        });
    });
}

function loadClaims() {
    return from(axios.get(CLAIMS_BASE_URL + 'claims'));
}

function loadTasks() {
    return new Promise(function (resolve, reject) {
        var xhr = new XMLHttpRequest();
        xhr.open('GET', TASKS_BASE_URL + 'tasks', true);
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
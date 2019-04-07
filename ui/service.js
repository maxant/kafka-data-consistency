function loadClaims() {
    return new Promise(function (resolve, reject) {
        var xhr = new XMLHttpRequest();
        xhr.open('GET', 'http://localhost:8081/claims/rest/claims', true);
        xhr.onreadystatechange = function() {
            if (xhr.readyState == XMLHttpRequest.DONE) {
                if (xhr.status === 200) {
                    var claims = JSON.parse(xhr.responseText);
                    resolve(claims);
                } else {
                    reject("failed to get claims: " + xhr.status + "::" + xhr.responseText);
                }
            }
        }
        xhr.send();
    });
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
    loadClaims: loadClaims,
    loadTasks: loadTasks
};
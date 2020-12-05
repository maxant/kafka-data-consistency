// https://stackoverflow.com/a/38552302/458370
function parseJwt (token) {
    var base64Url = token.split('.')[1];
    var base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
    var jsonPayload = decodeURIComponent(atob(base64).split('').map(function(c) {
        return '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2);
    }).join(''));

    return JSON.parse(jsonPayload);
}

var currentJwt;

/**
 * returns a promise that will contain a JWT that will last at least 30 seconds
 */
function getJwt(username) {
    if(isJwtGoingToExpire()) {
        return fetch(ORGANISATION_BASE_URL + `/security/token/${username}`, {
            method: "POST",
            body: CryptoJS.SHA512("asdf").toString(CryptoJS.enc.Base64)
        })
        .then(r => r.text())
        .then(r => { currentJwt = r; return r; })
    } else {
        Promise.resolve(currentJwt);
    }
}

function isJwtGoingToExpire() {
    return currentJwt ? (new Date().getTime() > new Date(1000*parseJwt(currentJwt).exp).getTime() - 30000) : true;
}

function addJwt(header) {
    header["Authorization"] = "Bearer " + currentJwt
    return header
}

function getCurrentJwt() {
    return currentJwt ? parseJwt(currentJwt) : undefined
}
(function(){
    var currentJwt;

    // cached, so we dont need to ask the user or caller to re-enter the password. investigate keycloak that lets you re-issue a token based on an existing one
    var currentUsername;
    var currentEncryptedPassword;

    // https://stackoverflow.com/a/38552302/458370
    function parseJwt(token) {
        if(!token) return undefined;
        var base64Url = token.split('.')[1];
        var base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
        var jsonPayload = decodeURIComponent(atob(base64).split('').map(function(c) {
            return '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2);
        }).join(''));

        return JSON.parse(jsonPayload);
    }

    function isJwtGoingToExpire() {
        return currentJwt ? (new Date().getTime() > new Date(1000*parseJwt(currentJwt).exp).getTime() - 30000) : true;
    }

    function relogin$() {
        console.log("logging in and getting token");
        return fetch(ORGANISATION_BASE_URL + `/security/token/${currentUsername}`, {
            method: "POST",
            body: currentEncryptedPassword,
            headers: {"Content-Type": "application/json"}
        })
        .then(r => { console.log("got token"); return r.text()})
        .then(r => { console.log("token has been read"); currentJwt = r; return r; })
        .then(r => { eventHub.emit(LOGGED_IN, {r}); return r; })
    }

    window.security = {
        logout: function() {
            currentJwt = null;
        },

        login$: function(username, password) {
            currentUsername = username;
            currentEncryptedPassword = CryptoJS.SHA512(password).toString(CryptoJS.enc.Base64);
            return relogin$();
        },

        ensureJwtIsValid$: function() {
            console.log("ensuring token is valid");
            if(!currentJwt || isJwtGoingToExpire()) {
                console.log("token is not valid");
                return relogin$();
            } else {
                console.log("token is valid");
                return Promise.resolve(currentJwt);
            }
        },

        addJwt: function(header) {
            console.log("adding token to header");
            header["MFAuthorization"] = "Bearer " + currentJwt // we use a different header, because otherwise quarkus gets snotty
            return header
        },

        getCurrentJwt: function() {
            return currentJwt ? parseJwt(currentJwt) : undefined
        }
    };
})();

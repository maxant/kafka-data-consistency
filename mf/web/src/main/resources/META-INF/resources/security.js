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
        }).then(r => {
            return r.text().then(token => ({token, status: r.status, ok: r.ok, originalResponse: r}));
        }).then(r => {
            if(r.ok) {
                console.log("got token");
                currentJwt = r.token;
                setTimeout(() => eventHub.emit(LOGGED_IN, currentUsername), 1); // async otherwise errors that happen after login are caught below
                return r.token;
            } else if(r.status == 403) {
                throw new Error("Unknown user or password wrong");
            } else {
                throw new Error("Failed to login. Server Status was " + r.status);
            }
        }).catch(error => {
            let msg = "received error while getting token: " + error;
            console.error(msg);
            alert(error);
        });
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

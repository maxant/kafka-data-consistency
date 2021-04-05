(function(){
// https://stackoverflow.com/a/2117523/458370
window.uuidv4 = function() {
  return ([1e7]+-1e3+-4e3+-8e3+-1e11).replace(/[018]/g, c =>
    (c ^ crypto.getRandomValues(new Uint8Array(1))[0] & 15 >> c / 4).toString(16)
  );
}

window.getHeaders = function(sessionId, getDemoContext) {
    let ctx = security.addJwt({"Content-Type": "application/json", "Accept": "application/json", "request-id": uuidv4(), "session-id": sessionId })
    if(getDemoContext) {
        ctx["demo-context"] = getDemoContext()
    }
    return ctx
}

window.fetchIt = function(url, method, self, body, responseIsText) {
    return security.ensureJwtIsValid$().then(jwt => {
        self.start = new Date().getTime();
        let data = { "method": method,
                       body: JSON.stringify(body),
                       "headers": getHeaders(self.sessionId, self.getDemoContext)
                     };
        if(url.indexOf(ELASTICSEARCH_BASE_URL) >= 0) {
            // ES doesnt like all our custom headers. we could add them to dc-base.yml, but it doesnt use them, so lets not send them
            data = { "method": method,
                       body: JSON.stringify(body),
                       "headers": {"Content-Type": "application/json" }
                     };
        }
        return fetch(url, data)
        .then(r => {
            if(r.status >= 200 && r.status < 300) {
                if(responseIsText) {
                    return r.text().then(payload => ({payload, ok: r.ok, response: r}))
                } else {
                    return r.json().then(payload => ({payload, ok: r.ok, response: r}))
                }
            } else if(r.status == 401 || r.status == 403) {
                return r.text().then(payload => {
                    if(r.headers && r.headers.get("www-authenticate")) {
                        return {payload: r.headers.get("www-authenticate"), ok: r.ok, response: r};
                    } else {
                        return {payload, ok: r.ok, response: r};
                    }
                });
            } else {
                return r.text().then(payload => ({payload, ok: r.ok, response: r}))
            }
        })
    })
}

window.eventHub = mitt();

/** creates an sse connection and adds it to `self` as the field `source`, so that it can track it's own state.
    restarts on error.
 */
window.sse = function(sessionId, self, callback, regex) {
    return new Promise((resolve, reject) => {
        if(self.source && self.source.readyState != EventSource.CLOSED) {
            console.log("closing existing sse connection just before restarting");
            self.source.close();
        }
        console.log("(re)starting sse");
        self.source = new EventSource("/web/stream/" + sessionId + (regex ? "?regex=" + regex : ""));
        self.source.onopen = function (event) {
            console.log("sse open");
            resolve();
        }
        self.source.onmessage = function (event) {
            console.log("sse got event");
            let msg = JSON.parse(event.data);
            callback(msg);
        };
        self.source.onerror = function (event) {
            console.log("sse error " + JSON.stringify(event));
            if(self.source && (self.source.readyState != EventSource.CLOSED)) {
                console.log("closing unclosed sse because of error");
                self.source.close();
            }
            self.source = null;
            console.log("restarting sse in 500ms");
            setTimeout(() => {
                sse(sessionId, self, callback);
            }, 500);
        }
    });
}

})();

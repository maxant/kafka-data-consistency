(function(){
// https://stackoverflow.com/a/2117523/458370
window.uuidv4 = function() {
  return ([1e7]+-1e3+-4e3+-8e3+-1e11).replace(/[018]/g, c =>
    (c ^ crypto.getRandomValues(new Uint8Array(1))[0] & 15 >> c / 4).toString(16)
  );
}

window.getHeaders = function(requestId, getDemoContext) {
    let ctx = security.addJwt({"Content-Type": "application/json", "request-id": requestId })
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
                       "headers": getHeaders(self.requestId, self.getDemoContext)
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
                return r.text().then(payload => ({payload: r.headers.get("www-authenticate"), ok: r.ok, response: r}))
            } else {
                return r.text().then(payload => ({payload, ok: r.ok, response: r}))
            }
        })
    })
}

window.eventHub = mitt();
})();

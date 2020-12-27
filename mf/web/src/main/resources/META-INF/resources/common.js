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

window.fetchIt = function(url, method, self, body) {
    return security.ensureJwtIsValid$().then(jwt => {
        self.start = new Date().getTime();
        let data = { "method": method,
                       body: JSON.stringify(body),
                       "headers": getHeaders(self.requestId, self.getDemoContext)
                     };
        return fetch(url, data)
        .then(r => {
            return r.json().then(payload => ({payload, ok: r.ok}))
        })
    })
}

window.eventHub = mitt();
})();

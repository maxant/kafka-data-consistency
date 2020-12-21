// https://stackoverflow.com/a/2117523/458370
function uuidv4() {
  return ([1e7]+-1e3+-4e3+-8e3+-1e11).replace(/[018]/g, c =>
    (c ^ crypto.getRandomValues(new Uint8Array(1))[0] & 15 >> c / 4).toString(16)
  );
}

// cases each quarkus uService to reload, since it doesnt do that when a kafka event arrives
const CONTRACTS_BASE_URL    = "http://localhost:8080"; // http://178.199.206.170
const PRICING_BASE_URL      = "http://localhost:8081";
const WEB_BASE_URL          = "http://localhost:8082";
const PARTNERS_BASE_URL     = "http://localhost:8083";
const CASES_BASE_URL        = "http://localhost:8084";
const WAITINGROOM_BASE_URL  = "http://localhost:8085";
const ORGANISATION_BASE_URL = "http://localhost:8086";

function pingAll() {
    fetch(CONTRACTS_BASE_URL    + "/ping").then(r => r.text());
    fetch(PRICING_BASE_URL      + "/ping").then(r => r.text());
    fetch(WEB_BASE_URL          + "/ping").then(r => r.text());
    fetch(PARTNERS_BASE_URL     + "/ping").then(r => r.text());
    fetch(CASES_BASE_URL        + "/ping").then(r => r.text());
    fetch(WAITINGROOM_BASE_URL  + "/ping").then(r => r.text());
    fetch(ORGANISATION_BASE_URL + "/ping").then(r => r.text());
}

function getHeaders(requestId, getDemoContext) {
    let ctx = security.addJwt({"Content-Type": "application/json", "request-id": requestId })
    if(getDemoContext) {
        ctx["demo-context"] = getDemoContext()
    }
    return ctx
}

function fetchIt(url, method, self, body) {
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

// common event hub events
var LOGGED_IN = "loggedIn"; // emitted by security after a login

window.eventHub = mitt();
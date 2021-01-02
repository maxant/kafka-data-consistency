const fetch = require("node-fetch");
const EventSource = require('eventsource');
const _ = require('lodash');
const { v4: uuidv4 } = require('uuid');

// https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Math/random
// The maximum is exclusive and the minimum is inclusive
function getRandomInt(min, maxExcl) {
    min = Math.ceil(min);
    maxExcl = Math.floor(maxExcl);
    return Math.floor(Math.random() * (maxExcl - min) + min);
}

// https://stackoverflow.com/questions/31378526/generate-random-date-between-two-dates-and-times-in-javascript
function randomDate(start, end) {
    var startHour = 12;
    var endHour = 13
    var date = new Date(+start + Math.random() * (end - start));
    date.setHours(12);
    return date;
}

// https://stackoverflow.com/a/52174349/458370
var setTimeoutPromise = ms => new Promise(resolve => setTimeout(resolve, ( (ms < 0) ? 0 : ms) ));

var i = 0;

function createPartnerAndContract(){
    var requestId = uuidv4();
    var token;
    var partnerId;
    var contractId;
    var updatedDraft = 0;
    var updatedPrices = 0;
    var start = new Date().getTime();

    console.log("using requestId " + requestId);

    var source = new EventSource('http://web:8082/web/stream/' + requestId);
    source.onmessage = function (event) {
        let msg = JSON.parse(event.data);
        if(msg["request-id"] != requestId) {
            console.error(">>>>>>>>>> ERROR!!! - got event for request-id " + msg["request-id"]
                + "on source for requestId " + requestId + " so dumping it");
                return;
        }
        if(msg.event == "CREATED_DRAFT") {
            console.log("event: created draft");
            modifyFatContent(msg);
        } else if(msg.event == "UPDATED_DRAFT") {
            console.log("event: updated draft");
            updatedDraft++; // have to wait for updated prices before offering, otherwise we get a validation error because the contract isnt in sync
        } else if(msg.event == "UPDATED_PRICES") {
            console.log("event: updated prices");
            updatedPrices++;
            if(updatedDraft == 1 && updatedPrices == 2) {
                offerDraft();
            }
        } else if(msg.event == "OFFERED_DRAFT") {
            console.log("event: offered draft");
            acceptContract();
        } else if(msg.event == "CHANGED_PARTNER_RELATIONSHIP") {
            console.log("event: changed partner relationship");
        } else if(msg.event == "CHANGED_CASE") {
            console.log("event: changed case");
        } else if(msg.event == "ERROR") {
            console.log("event: error");
        } else {
            console.log("event: other");
        }
    };
    source.onerror = function (event) {
        console.log("sse error " + event);
        if(source && source.readyState != EventSource.CLOSED) source.close();
        source = null;
    }

    setTimeout(() => {
        if(source && !source.$completed) {
            // restart after timeout
            source.close();
            console.error(">>>>>>>>>> ERROR!!! - timeout on request, so restarting. rquestId: " + requestId);
            createPartnerAndContract();
        }
    }, 30000);

    function modifyFatContent(msg) {
        var componentId = _.find(msg.payload.pack.children[0].children, child => child.componentDefinitionId == "Milk").componentId;
        var fatContents = ['0.2', '1.8', '3.5'];
        var newFatContent = fatContents[getRandomInt(0, 3)];
        fetch("http://contracts:8080/drafts/" + contractId + "/" + componentId + "/FAT_CONTENT/" + newFatContent, {
          "headers": {
            "content-type": "application/json",
            "demo-context": "{\"forceError\":\"none\"}",
            "mfauthorization": "Bearer " + token,
            "request-id": requestId
          },
          "body": null,
          "method": "PUT"
        })
        .then(r => {
            console.log("modified content to " + newFatContent + ": " + r.status);
        });
    }

    function offerDraft() {
        console.log("offering draft " + contractId + " on requestId " + requestId);
        return fetch("http://contracts:8080/drafts/" + contractId + "/offer", {
          "headers": {
            "content-type": "application/json",
            "demo-context": "{\"forceError\":\"none\"}",
            "mfauthorization": "Bearer " + token,
            "request-id": requestId
          },
          "body": null,
          "method": "PUT"
        })
        .then(r => {
            console.log("offered draft: " + r.status);
            if(r.status == 400) {
                return offerDraft();
            }
        })
    }

    function acceptContract() {
        console.log("accepting contract");
        return fetch("http://contracts:8080/contracts/accept/" + contractId, {
          "headers": {
            "content-type": "application/json",
            "mfauthorization": "Bearer " + token,
            "request-id": requestId
          },
          "body": null,
          "method": "PUT"
        })
        .then(r => {
            console.log("accepted contract: " + r.status);
        })
        .then(approveContract);
    }

    function approveContract(){
        console.log("switching to jane in order to approve");
        return fetch("http://organisation:8086/security/token/jane.smith", {
          "headers": {
            "content-type": "application/json"
          },
          "body": "QBsJ6rPAE9TKVJIruAK+yP1TGBkrCnXyAdizcnQpCA+zN1kavT5ERTuVRVW3oIEuEIHDm3QCk/dl6ucx9aZe0Q==",
          "method": "POST"
        })
        .then(r => r.text())
        .then(t => {
            token = t;
            console.log("got token for jane, now approving");

            return fetch("http://contracts:8080/contracts/approve/" + contractId, {
              "headers": {
                "content-type": "application/json",
                "mfauthorization": "Bearer " + token,
                "request-id": requestId
              },
              "body": null,
              "method": "PUT"
            });
        })
        .then(r => {
            console.log("approved contract: " + r.status);
            console.log("reading contract");

            return fetch("http://contracts:8080/contracts/" + contractId, {
              "headers": {
                "content-type": "application/json",
                "mfauthorization": "Bearer " + token,
                "request-id": requestId
              },
              "body": null,
              "method": "GET"
            });
        })
        .then(r => r.json())
        .then(contract => {
            console.log("got contract: " + contract.contractState);
            if(contract.contractState != "RUNNING") {
                console.error("CONTRACT STATE IS NOT RUNNING!");
            }
            console.log("completed in " + (new Date().getTime() - start) + "ms");
            source.close();
            source.$completed = true;

            console.log("=============================================== " + i++);
            createPartnerAndContract();
        })
    }


    fetch("http://organisation:8086/security/token/john.smith", {
      "headers": {
        "content-type": "application/json",
        "request-id": requestId
      },
      "body": "QBsJ6rPAE9TKVJIruAK+yP1TGBkrCnXyAdizcnQpCA+zN1kavT5ERTuVRVW3oIEuEIHDm3QCk/dl6ucx9aZe0Q==",
      "method": "POST"
    })
    .then(r => r.text())
    .then(t => {
        token = t;
        console.log("got token for john");
        return fetch("http://partners:8083/partners", {
          "headers": {
            "content-type": "application/json",
            "mfauthorization": "Bearer " + token,
            "request-id": requestId
          },
          "body": "{\"firstName\":\"Anton" + new Date().toISOString().substr(0,19)
                    + "\",\"lastName\":\"Kutschera\",\"type\":\"PERSON\",\"dob\":\""
                    + randomDate(new Date(1910, 0, 1), new Date(2000, 0, 1)).toISOString()
                    + "\",\"email\":\"\",\"phone\":\"\",\"addresses\":[{\"street\":\"asdf road\","
                    + "\"houseNumber\":\"4b\",\"postcode\":\"3000\","
                    + "\"city\":\"Llandudno\",\"state\":\"Wales\",\"country\":\"UK\",\"type\":\"PRIMARY\"}]}",
          "method": "POST"
        });
    })
    .then(r => r.text())
    .then(pid => {
        partnerId = pid;
        console.log("created partner " + partnerId);
        return fetch("http://contracts:8080/drafts", {
          "headers": {
            "content-type": "application/json",
            "demo-context": "{\"forceError\":\"none\"}",
            "mfauthorization": "Bearer " + token,
            "request-id": requestId
          },
          "body": "{\"productId\":\"COOKIES_MILKSHAKE\",\"start\":\""
                    + randomDate(new Date(), new Date(new Date().getTime() + 365*24*3600000)).toISOString().substr(0,10)
                    + "\",\"partnerId\":\"" + partnerId + "\"}",
          "method": "POST"
        });
    })
    .then(r => r.json())
    .then(draft => {
        contractId = draft.id;
        console.log("got draft " + contractId);
    });
}

createPartnerAndContract();


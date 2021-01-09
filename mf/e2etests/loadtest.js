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
var numErrors = 0;
var numWarns = 0;

function createPartnerAndContract(){
    var requestId = uuidv4();
    var token;
    var partnerId;
    var contractId;
    var createdDraft = 0;
    var updatedDraft = 0;
    var updatedPrices = 0;
    var relationshipCreated = 0;
    var changedCase = 0;
    var modifiedFatContent = false;
    var offeredDraft = false;
    var offeredDraftEvent = 0;
    var componentIdWithFatContent;
    var start = new Date().getTime();

    function getMsSinceStart() {
        return new Date().getTime() - start;
    }

    console.log("using requestId " + requestId);

    var source = new EventSource('http://web:8082/web/stream/' + requestId);
    source.onmessage = function (event) {
        let msg = JSON.parse(event.data);
        if(msg["request-id"] != requestId) {
            console.error(">>>>>>>>>> ERROR!!! - got event " + msg.event + " for request-id " + msg["request-id"]
                + " on source for requestId " + requestId + " so dumping it");
            numErrors++;
            return;
        }
        if(msg.event == "CREATED_DRAFT") {
            createdDraft++;
            console.log("event: created draft " + createdDraft + " - " + getMsSinceStart() + "msFromStart");
            componentIdWithFatContent = getComponentIdWithFatContent(msg);

            // see note below - prices can indeed arrive before, because we handle messages in web in parallel!
            if(createdDraft >= 1 && updatedPrices >= 1 && !modifiedFatContent) {
                modifyFatContent(componentIdWithFatContent);
            }
            if(createdDraft >= 2) numWarns++; // this happens just once
        } else if(msg.event == "UPDATED_DRAFT") {
            updatedDraft++; // have to wait for updated prices before offering, otherwise we get a validation error because the contract isnt in sync
            console.log("event: updated draft " + updatedDraft + " - " + getMsSinceStart() + "msFromStart");
            if(updatedDraft >= 2) numWarns++; // this happens once
        } else if(msg.event == "UPDATED_PRICES_FOR_DRAFT") {
            updatedPrices++;
            console.log("event: updated prices " + updatedPrices + " - " + getMsSinceStart() + "msFromStart");

            // TODO hmmm the following isnt true! ah, because the web now handles these in parallel
            // prices are always updated after draft creation/update, and are published on the same topic, so arrive
            // AFTER those events. but before we continue with the process, lets just check everything is as expected
            if(createdDraft >= 1 && updatedPrices >= 1 && !modifiedFatContent) {
                modifyFatContent(componentIdWithFatContent);
            } else if(updatedDraft >= 1 && updatedPrices >= 2 && relationshipCreated >= 2 && !offeredDraft) {
                offerDraft();
            }
            if(updatedPrices >= 3) numWarns++; // created, updated
        } else if(msg.event == "OFFERED_DRAFT") {
            offeredDraftEvent++;
            console.log("event: offered draft" + " - " + getMsSinceStart() + "msFromStart");
            acceptContract();
            if(offeredDraftEvent >= 2) numWarns++; // this just happens once
        } else if(msg.event == "CHANGED_PARTNER_RELATIONSHIP") {
            relationshipCreated++;
            console.log("event: changed partner relationship " + relationshipCreated + " - " + getMsSinceStart() + "msFromStart");
            if(updatedDraft >= 1 && updatedPrices >= 2 && relationshipCreated >= 2 && !offeredDraft) {
                offerDraft();
            }
            if(relationshipCreated >= 3) numWarns++; // sales rep, contract holder
        } else if(msg.event == "CHANGED_CASE") {
            changedCase++;
            console.log("event: changed case" + " - " + getMsSinceStart() + "msFromStart");
            if(changedCase >= 4) numWarns++; // create case, create task, complete task
        } else if(msg.event == "ERROR") {
            console.log(">>>>>> event: error" + " - " + getMsSinceStart() + "msFromStart");
            numErrors++;
        } else {
            console.log(">>>>>> event: other" + " - " + getMsSinceStart() + "msFromStart");
            numErrors++;
        }
    };
    source.onerror = function (event) {
        console.log("sse error " + event + " - " + getMsSinceStart() + "msFromStart");
        if(source && source.readyState != EventSource.CLOSED) source.close();
        source = null;
    }

    setTimeout(() => {
        if(source && !source.$completed) {
            // restart after timeout
            source.close();
            console.error(">>>>>>>>>> ERROR!!! - timeout on request, so restarting. rquestId: " + requestId + " - " + getMsSinceStart() + "msFromStart");
            numErrors++;
            console.info("");
            console.info("restarting...");
            console.info("");
            createPartnerAndContract();
        }
    }, 15000);

    function getComponentIdWithFatContent(msg) {
        return _.find(msg.payload.pack.children[0].children, child => child.componentDefinitionId == "Milk").componentId;
    }

    function modifyFatContent(componentId) {
        modifiedFatContent = true;
        var fatContents = ['0.2', '1.8', '3.5'];
        var newFatContent = fatContents[getRandomInt(0, 3)];
        var go = new Date().getTime();
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
            console.log("modified content to " + newFatContent + ": " + r.status + " - " + getMsSinceStart() + "msFromStart - " + (new Date().getTime() - go) + "ms");
        });
    }

    function offerDraft() {
        offeredDraft = true;
        console.log("offering draft " + contractId + " on requestId " + requestId + " - " + getMsSinceStart() + "msFromStart");
        var go = new Date().getTime();
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
            console.log("offered draft: " + r.status + " - " + getMsSinceStart() + "msFromStart - " + (new Date().getTime() - go) + "ms");
            if(r.status == 400) {
                return offerDraft();
            }
        })
    }

    function acceptContract() {
        console.log("accepting contract" + " - " + getMsSinceStart() + "msFromStart");
        var go = new Date().getTime();
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
            console.log("accepted contract: " + r.status + " - " + getMsSinceStart() + "msFromStart - " + (new Date().getTime() - go) + "ms");
        })
        .then(approveContract);
    }

    function approveContract(){
        console.log("switching to jane in order to approve" + " - " + getMsSinceStart() + "msFromStart");
        var go = new Date().getTime();
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
            console.log("got token for jane, now approving" + " - " + getMsSinceStart() + "msFromStart - " + (new Date().getTime() - go) + "ms");
            go = new Date().getTime();
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
            console.log("approved contract: " + r.status + " - " + getMsSinceStart() + "msFromStart - " + (new Date().getTime() - go) + "ms");
            go = new Date().getTime();
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
            console.log("got contract: " + contract.contractState + " - " + getMsSinceStart() + "msFromStart - " + (new Date().getTime() - go) + "ms");
            if(contract.contractState != "RUNNING") {
                console.error(">>>>>>>>>>>>>> CONTRACT STATE IS NOT RUNNING!" + " - " + getMsSinceStart() + "msFromStart");
                numErrors++;
            }
            console.log("completed in " + (new Date().getTime() - start) + "ms");
            source.close();
            source.$completed = true;

            console.log("=============================================== " + (i++) + " :: warnings=" + numWarns + " :: errors=" + numErrors);
            createPartnerAndContract();
        })
    }


    var go = new Date().getTime();
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
        console.log("got token for john" + " - " + getMsSinceStart() + "msFromStart - " + (new Date().getTime() - go) + "ms");
        go = new Date().getTime();
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
        console.log("created partner " + partnerId + " - " + getMsSinceStart() + "msFromStart - " + (new Date().getTime() - go) + "ms");
        go = new Date().getTime();
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
        console.log("got draft " + contractId + " - " + getMsSinceStart() + "msFromStart - " + (new Date().getTime() - go) + "ms");
    });
}

createPartnerAndContract();


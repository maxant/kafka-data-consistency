for (var i = 0; i < 1 ; i++) {

describe('Create Partner and Contract', () => {
    it('creates a partner!', () => {

        cy.intercept('GET', '**/partners/_search*').as('searchPartner')

        let dob = new Date(2003-(80*Math.random()).toFixed(), 12*Math.random(), 30*Math.random());
        dob = dob.toISOString().substr(0, 9); // TODO strange, somehow theres a problem - it keeps adding a 1 at the end...
        console.log("using dob " + dob);
        cy.visit('http://localhost:8082/sales')
        cy.get('#partnerselectdropdown').type("qwer")

        cy.wait('@searchPartner').its('response.statusCode').should('be.oneOf', [200])

        cy.contains('create new...').click()
        cy.get('#firstName').click().type("Anton")
        cy.get('#lastName').click().type("Kutschera" + (new Date().toISOString().substring(0, 19)))

        // force, otherwise calendar popup doesnt hide
        cy.get('#dob').clear().type(dob)
        cy.get('.p-datepicker-year').select(dob.substring(0,4))
        for(var i = 0; i < 12*Math.random(); i++) {
            cy.get('.p-datepicker-prev-icon').click()
        }
        cy.get(':nth-child(' + getRandomInt(1,5) + ') > :nth-child(' + getRandomInt(1,8) + ') > [draggable="false"]').click()

        cy.intercept('POST', '**/partners').as('createPartner')

        // test validation...
/*
        cy.get('#street').click({force: true})
        cy.get('#street').clear()
        cy.get('#street').should('have.value', '')
        cy.get('#newPartnerOk').click()
        cy.contains("Street is required.")
*/
        cy.get('#street').type("asdf road")
        cy.get('#newPartnerOk').click()

        cy.wait('@createPartner').its('response.statusCode').should('be.oneOf', [201])

        let startDate = new Date(new Date().getTime() + (300*24*3600000*Math.random()));
        startDate = startDate.toISOString().substr(0, 9); // TODO strange, somehow theres a problem - it keeps adding a 1 at the end...
        console.log("using startDate " + startDate);
        cy.get('#startDate').clear().type(startDate)
        cy.get('.p-datepicker-next-icon').click({force: true})
        cy.get(':nth-child(' + getRandomInt(1,5) + ') > :nth-child(' + getRandomInt(1,8) + ') > [draggable="false"]').click({force: true})

        cy.intercept('POST', '**/drafts*').as('createDraft')
        cy.intercept('GET', '**/partners/*').as('getPartner') // for sales rep

        cy.contains('get new draft').click({force: true})

        cy.wait('@createDraft').its('response.statusCode').should('be.oneOf', [201])
        cy.wait('@getPartner').its('response.statusCode').should('be.oneOf', [200])

        cy.contains('FAT_CONTENT').click({force: true}) // just to make sure its really there before we edit it

        cy.get("#FAT_CONTENT-config-param > .p-dropdown-label").click()
        let contents = ['0.2', '1.8', '3.5']
        let toSelect = contents[getRandomInt(0, contents.length)]
        cy.contains(toSelect).click()

        cy.intercept('PUT', '**/drafts/*/offer').as('createOffer')
        cy.intercept('GET', '**/contracts/*').as('getContract') // after redirecting to partner page
        cy.get("#offerDraft").click()
        cy.wait('@createOffer').its('response.statusCode').should('be.oneOf', [201])
        cy.wait('@getContract').its('response.statusCode').should('be.oneOf', [200])

        cy.get("#viewContractIcon").click() // on the partner page, to go to the contract page
        cy.wait('@getContract').its('response.statusCode').should('be.oneOf', [200])

        cy.intercept('PUT', '**/contracts/accept/*').as('acceptContract')
        cy.get("#acceptOfferButton").click()
        cy.wait('@acceptContract').its('response.statusCode').should('be.oneOf', [200])

        cy.contains("John").click()
        cy.contains("Jane").click()
        cy.intercept('PUT', '**/contracts/approve/*').as('approveContract')
        cy.get("#approveContractButton").click()
        cy.wait('@approveContract').its('response.statusCode').should('be.oneOf', [200])
    })
})

}

// https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Math/random
// The maximum is exclusive and the minimum is inclusive
function getRandomInt(min, maxExcl) {
    min = Math.ceil(min);
    maxExcl = Math.floor(maxExcl);
    return Math.floor(Math.random() * (maxExcl - min) + min);
}


describe('Create Partner and Draft', () => {
    it('Does not do much!', () => {
        let dob = new Date(2003-(80*Math.random()).toFixed(), 12*Math.random(), 30*Math.random());
        dob = dob.toISOString().substr(0, 9); // TODO strange, somehow theres a problem - it keeps adding a 1 at the end...
        console.log("using dob " + dob);
        cy.visit('http://localhost:8082/sales')
        cy.get('#partnerselectdropdown').click()
        cy.contains('create new...').click()
        cy.get('#firstName').click().type("Anton")
        cy.get('#lastName').click().type("Kutschera" + (new Date().toISOString().substring(0, 19)))

        // force, otherwise calendar popup doesnt hide
        cy.get('#dob').clear().type(dob)
        cy.get('.p-datepicker-year').select(dob.substring(0,4))
        for(var i = 0; i < 12*Math.random(); i++) {
            cy.get('.p-datepicker-prev-icon').click()
        }
        cy.get(':nth-child(4) > :nth-child(1) > [draggable="false"]').click()
 //       cy.contains((28*Math.random()).toFixed(0)).click()

        // test validation...
        cy.get('#street').click({force: true})
        cy.get('#street').clear()
        cy.get('#street').should('have.value', '')
        cy.get('#newPartnerOk').click()
        cy.contains("Street is required.")
        cy.get('#street').type("asdf road")
        cy.get('#newPartnerOk').click()

        let startDate = new Date(new Date().getTime() + (365*24*3600000*Math.random()));
        startDate = startDate.toISOString().substr(0, 9); // TODO strange, somehow theres a problem - it keeps adding a 1 at the end...
        console.log("using startDate " + startDate);
        cy.get('#startDate').clear().type(startDate)
        cy.get(':nth-child(4) > :nth-child(1) > [draggable="false"]').click()
   //     cy.contains((28*Math.random()).toFixed(0)).click()

        cy.wait(2000).then(console.log)

        cy.contains('get new draft').click({force: true})
        cy.wait(2000)

        cy.get("#FAT_CONTENT-config-param > .p-dropdown-label").click()
        let contents = ['0.2', '1.8', '3.5']
        let toSelect = contents[parseInt((3*Math.random()).toFixed(0))]
        cy.contains(toSelect).click()

        cy.get("#offerDraft").click()
    })
})
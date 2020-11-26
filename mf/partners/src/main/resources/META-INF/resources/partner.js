var template =
// start template
`

<p-dropdown :options="partners"
           optionLabel="firstName"
           v-model="partner"
           placeholder="Select a partner"
>
</p-dropdown>

` // end template

window.mfPartner = {
  template,
  data: function(){
    return {
        partners: [
            {"firstName": "John", "lastName": "Smith", "id": "1"},
            {"firstName": "Jane", "lastName": "Smith", "id": "2"},
            {"firstName": "Janet", "lastName": "Smith", "id": "3"}
        ],
        partner: null
    }
  },
  components: {
    'p-dropdown': dropdown
  }
}
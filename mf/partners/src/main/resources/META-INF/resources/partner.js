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
            {"firstName": "John", "lastName": "Smith", "id": "3cd5b3b1-e740-4533-a526-2fa274350586"},
            {"firstName": "Jane", "lastName": "Smith", "id": "6c5aa3cd-0a07-4055-9cec-955900c6bea0"},
            {"firstName": "Janet", "lastName": "Smith", "id": "c1f1b7ee-ed4e-4342-ac68-199fba9fe50d"}
        ],
        partner: null
    }
  },
  components: {
    'p-dropdown': dropdown
  }
}
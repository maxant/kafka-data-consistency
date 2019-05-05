export const locationFormComponentObject = {
    data() {
        return  {
            zipOptions: [],
            zip: null,
            houseOptions: [],
            house: null,
            pleaseSelectZip: false
        }
    },
    props: ['model'],
    watch: {
        zip: function (val) {
            this.pleaseSelectZip = !val
            this.model.zip = val.zip
            this.model.city = val.city27
        },
        house: function (val) {
            this.model.number = val.houseNumber
            this.model.street = val.street.streetName
        }
    },
    methods: {
        searchZip(val, update, abort) {
            if (val.length < 2) { abort(); return }
            const self = this;
            axios.get(LOCATIONS_BASE_URL + "locations/zips/" + val)
                .then(function(response) {
                    update(() => {
                        _.forEach(response.data.zips, (z)=> z.desc = z.zip + " " + z.city27)
                        self.zipOptions = response.data.zips
                    })
                }).catch(function(error) {
                    console.log("error getting locations: " + error)
                    update(() => self.zipOptions = [])
                });
        },
        searchHouse(val, update, abort) {
            if (!this.zip) {
                this.pleaseSelectZip = true;
            }
            if (!this.zip || val.length < 4) {
                abort()
                return
            }
            const self = this;
            axios.get(LOCATIONS_BASE_URL + "locations/houses/" + this.zip.city27 + "/" + val)
                .then(function(response) {
                    update(() => {
                        self.houseOptions = _.chain(response.data.houses)
                                            .filter(h => h.street && h.street.streetValidation && h.street.streetValidation.isStreetNameValid)
                                            .map(h => {let o = {}; Object.assign(o, h); o.desc = h.street.streetName + " " + h.houseNumber; return o;})
                                            .value()
                    })
                }).catch(function(error) {
                    console.log("error getting houses: " + error)
                    update(() => self.houseOptions = [])
                });
        }
    },
    template: `
        <div>
            <q-select
                v-model="zip"
                use-input
                hide-selected
                input-debounce="500"
                :options="zipOptions"
                option-label="desc"
                @filter="searchZip"
                hint="search zip"
                style="width: 250px; padding-bottom: 32px"
            >
            </q-select>
            <q-select
                v-model="house"
                use-input
                hide-selected
                input-debounce="500"
                :options="houseOptions"
                option-label="desc"
                @filter="searchHouse"
                hint="search street"
                style="width: 250px; padding-bottom: 32px"
            >
            </q-select>
            <div v-if="pleaseSelectZip" class="error">Please choose a zip first</div>
            {{model}}
        </div>
    `
};
Vue.component('location-form', locationFormComponentObject);

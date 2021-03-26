export const componentObject =
{
    props: ['navigations'],
    methods: {
        goto(navigation) {
            this.$router.push({ name: navigation.name, params: navigation.params })
        }
    },
    template: `
        <div>
            <div v-for="navigation in navigations"
                 class="navigation"
                 @click="goto(navigation)"
                 :id="'navigation-' + navigation.name">

                {{navigation.title}}

            </div>
        </div>
    `,
};

Vue.component('navigations', componentObject);

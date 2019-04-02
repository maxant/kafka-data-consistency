Vue.component('navigations', {
    props: ['navigations'],
    template: `
        <ul class="navigation">
            <li v-for="navigation in navigations">
                <router-link :to="{ name: navigation.name, params: navigation.params }">{{navigation.title}}</router-link>
            </li>
        </ul>
    `,
});

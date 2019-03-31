Vue.component('navigations', {
    props: ['navigations'],
    template: `
        <ul class="navigation">
            <li v-for="navigation in navigations">
                <router-link v-bind:to="navigation.to">{{navigation.title}}</router-link>
            </li>
        </ul>
    `,
});

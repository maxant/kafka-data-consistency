//////////////////////////////////////////////////////////////////////////////////////////////
// test functionality allowing the user to select their identity
//////////////////////////////////////////////////////////////////////////////////////////////

const template =
`
        User: <p-dropdown :options="users"
                          v-model="user"
                          option-label="name"
                          @change="userChanged()">
              </p-dropdown>
` // end template


const LOGGED_IN = "loggedIn"; // emitted by security after a login

window.mfUsers = {
    template,
    props: ['users'],
    data() {
        return {
            user: users[1],
        }
    },
    mounted() {
        security.login$(this.user.un, this.user.password);
    },
    methods: {
        userChanged() {
            security.login$(this.user.un, this.user.password);
        }
    },
    components: {
        'p-dropdown': dropdown
    }
};
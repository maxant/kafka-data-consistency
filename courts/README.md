# Courts

An application dealing with court cases releated to claims.

## Run

    http-server -p 8100 .

goto: http://localhost:8100/demo.html

## Technology

- Vue 3
  - https://v3.vuejs.org/guide/installation.html#release-notes
  - installed chrome dev tools
  - `npm install vue@next`
  - `npm install -g @vue/cli`
  - `vue upgrade --next`
  - Nomenclature
    - text interpolation: `{{ counter }}`
    - bind element attributes: `<span v-bind:title="message">`
    - directive: for example the `v-bind` in the above binding
      - they are prefixed with `v-`
    - mounting the app `const vm = Vue.createApp(options).mount('#idOfHtmlElement')`
    - the object returned by Vue can be used to set data on the model: `v.todoItems.push({...})`
    - the `options` object contains attributes & functions:
      - `data`: a function returning an object that is watched
      - `methods`: an object containing methods which can be called from listeners, e.g. `reverseMessage` in `<button v-on:click="reverseMessage">Reverse Message</button>`
      - `mounted`: a method called by vue, when the app is mounted
      - `computed`: allows you to derive data based on other data in the model
    - angular pipes: were vuejs filters, no longer supported => use computed
    - two-way binding is done with the `v-model` directive which creates bindings between form input and app state: `input v-model="message" />`
    - conditionals: `<span v-if="seen">Now you see me</span>`
    - loops: `<li v-for="todo in todos"> {{ todo.text }} </li>`
    - composition with components: `const app = Vue.createApp({data: function(){return {items: [{text: "a"}]}}}); app.component('todo-item', { props: ['todo'], template: ``<li>{{todo.text}}</li>``}); app.mount("#app")` and then use the component: `<todo-item v-for="item in items" v-bind:todo="item" v-bind:key="item.id"></todo-item>`
      - `props` are one-way-down bindings, which normally down affect the original, unless the prop is an object or array
    - `v-bind:title` shortcut: `:title`
    - `v-on:click` shortcut: `@click`
    - camelCase vs kebab-case: html attribute names are case insensitive, props names can be camelCase and you can refer to the kebabCase equivalent in your html

- Quarkus + Kotlin




Vue.component('tasks', {
    props: ['tasks'],
    template: `
        <div id="tasks" class="tile-group">
            <div v-if="tasks.error" class="error">{{tasks.error}}</div>
            <div v-else-if="tasks.loading"><i>loading...</i></div>
            <div v-else-if="tasks.entities.length === 0"><i>No tasks</i></div>
            <table v-else>
                <tr v-for="task in tasks.entities">
                    <td class='tile'>
                        <div class='tile-title'><i class='fas fa-tasks'></i>&nbsp;Task</div>
                        <div class='tile-body'>{{task}}</div>
                    </td>
                </tr>
            </table>
        </div>
    `
});

Vue.component('tasks', {
    props: ['tasks'],
    template: `
        <div id="tasks" class="tile-group">
            <div v-if="tasks.error" class="error">
                <q-alert :type="warning" class="q-mb-sm" icon="priority_high">
                    {{tasks.error}}
                </q-alert>
            </div>
            <div v-else-if="tasks.loading"><q-spinner-hourglass size="32px"/></div>
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

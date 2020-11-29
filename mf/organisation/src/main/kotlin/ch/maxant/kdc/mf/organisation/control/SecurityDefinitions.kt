package ch.maxant.kdc.mf.organisation.control

import ch.maxant.kdc.mf.library.Role
import ch.maxant.kdc.mf.organisation.control.ProcessSteps.*
import ch.maxant.kdc.mf.organisation.control.StaffRole.*
import ch.maxant.kdc.mf.organisation.control.PartnerRole.*
import javax.enterprise.context.Dependent

@Dependent
class SecurityDefinitions {

    fun getDefinitions(): SecurityDefinitionResponse {
        return SecurityDefinitionResponse(
                buildTree(Processes.values(), ""),
                Processes.values().map { P(it, it.processSteps) },
                ProcessSteps.values().map { PS(it, it.fqMethodNames) },
                RoleMappings.values().map { RM(it, it.role, it.processStep ) }
        )
    }

    private fun buildTree(ps: Array<Processes>, parentKey: String): List<Node> {
        return ps.map {
            val key = parentKey + "::" + it.name
            Node(key, Data(it.name, null, "Process"), buildTree(it.processSteps, key))
        }
    }
    private fun buildTree(pss: Set<ProcessSteps>, parentKey: String): List<Node> {
        return pss.map { ps ->
            val key = parentKey + "::" + ps.name
            val rms = RoleMappings.values()
                    .filter { rm -> rm.processStep == ps }
            Node(key, Data(ps.name, null, "Step", rms.joinToString { "${it.role}" }), buildTree(ps.fqMethodNames, key, ps))
        }
    }
    private fun buildTree(ps: Set<String>, parentKey: String, step: ProcessSteps): List<Node> {
        val users: MutableList<User> = Partner.values().toMutableList()
        users.addAll(Staff.values())
        val rms = RoleMappings.values()
                .filter { it.processStep == step }
        val raus = rms.map { rm -> RoleWithUsers(rm.role, users.filter { u-> u.roles.contains(rm.role) }) }
        return ps.map {
            val key = "$parentKey::$it"
            Node(key, Data(it, raus.flatMap { it.users }.joinToString { it.un }, "Service Method"), emptyList())
        }
    }
}

data class SecurityDefinitionResponse(val root: List<Node>, val processes: List<P>, val processSteps: List<PS>, val roleMappings: List<RM>)
data class Node(val key: String, val data: Data, val children: List<Node>)
data class Data(val name: String, val users: String?, val type: String, val roleMappings: String? = null)
data class RoleWithUsers(val role: Role, val users: List<User>)
data class P(val process: Processes, val processSteps: Collection<ProcessSteps>)
data class PS(val processStep: ProcessSteps, val methods: Collection<String>)
data class RM(val name: RoleMappings, val role: Role, val processStep: ProcessSteps)

enum class StaffRole(description: String): Role {
    SALES_REP("A person who sells stuff"),
    SUPPLY_CHAIN_SPECIALIST("Someone specialising in sourcing products"),
    ORDER_COMPLETION_CONSULTANT("Someone who completes bespoke orders"),
    FINANCE_SPECIALIST("Someone who works with finances")
}

enum class PartnerRole(description: String): Role {
    CUSTOMER("Someone with a contract"),
    SUPPLIER("Supplies MF with materials"),
}

enum class ProcessSteps(val fqMethodNames: Set<String>) {
    DRAFT(setOf(
        "ch.maxant.kdc.mf.contracts.boundary.DraftsResource.create",
        "ch.maxant.kdc.mf.contracts.boundary.DraftsResource.updateConfig",
        "ch.maxant.kdc.mf.contracts.boundary.ContractResource.getById"
    )),
    OFFER(setOf(
        "ch.maxant.kdc.mf.contracts.boundary.ContractResource.getById",
        "ch.maxant.kdc.mf.contracts.boundary.DraftsResource.offerDraft"
    )),
    ACCEPT(setOf(
        "ch.maxant.kdc.mf.contracts.boundary.ContractResource.getById",
        "ch.maxant.kdc.mf.contracts.boundary.ContractResource.acceptOffer"
    ))
}

enum class Processes(val processSteps: Set<ProcessSteps>) {
    SALES(setOf(DRAFT, OFFER, ACCEPT))
}

enum class RoleMappings(val role: Role, val processStep: ProcessSteps) {
    SALES_REP___DRAFT(SALES_REP, DRAFT),
    SALES_REP___OFFER(SALES_REP, OFFER),
    CUSTOMER___ACCEPT(CUSTOMER, ACCEPT)
}


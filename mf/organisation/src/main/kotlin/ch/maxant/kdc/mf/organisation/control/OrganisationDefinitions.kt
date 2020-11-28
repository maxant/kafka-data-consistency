package ch.maxant.kdc.mf.organisation.control

import ch.maxant.kdc.mf.organisation.control.Staff.Companion.JANE
import ch.maxant.kdc.mf.organisation.control.Staff.Companion.JANET
import ch.maxant.kdc.mf.organisation.control.Staff.Companion.JOHN
import com.fasterxml.jackson.annotation.JsonIgnore
import java.util.*
import java.util.UUID.fromString

object OUs {
    val HEAD_OFFICE = OU(DN("Head Office", listOf("ch", "maxant", "mf")), listOf(JOHN), listOf())
    val FINANCES = OU(DN("Finances", listOf("finances")), listOf(
        // note how john is here, but also in head office
        JOHN
    ), listOf(), HEAD_OFFICE)
    val BERN = OU(DN("Bern", listOf("bern")), listOf(
        JANE
    ), listOf(), HEAD_OFFICE)
    val LAUSANNE = OU(DN("Lausanne", listOf("lausanne")), listOf(
        JANET
    ), listOf(), HEAD_OFFICE)

    /** returns all staff, optionally matching the given role (null means all roles),
     * operating recursively down thru the kids of the given OU, or HEAD_OFFICE if none is provided */
    fun getAllStaff(staffRole: StaffRole?, ou: OU = HEAD_OFFICE): MutableList<Staff> {
        val staffList: MutableList<Staff> = mutableListOf()
        ou.getAllStaff(staffRole, staffList)
        return staffList
    }
}

/** distinguished name - this refers to the name that uniquely identifies an entry in the directory
 * @param CN common name
 * @param dcs domain components, this refers to each component of the domain
 */
class DN(val CN: String, val dcs: List<String>)

class Staff(val dn: DN, val partnerId: UUID, val staffRoles: List<StaffRole>) {
    @get:JsonIgnore lateinit var ou: OU

    fun getAllStaffRoles(): List<StaffRole> {
        val roles = mutableListOf(*staffRoles.toTypedArray())
        roles.addAll(ou.getAllInheritableRoles())
        return roles
    }

    companion object {
        val JOHN = Staff(DN("John", listOf("")), fromString("3cd5b3b1-e740-4533-a526-2fa274350586"),
                listOf(StaffRole.SALES_REP, StaffRole.FINANCE_SPECIALIST)
        )
        val JANE = Staff(DN("Jane", listOf("")), fromString("6c5aa3cd-0a07-4055-9cec-955900c6bea0"),
                listOf(StaffRole.SALES_REP, StaffRole.ORDER_COMPLETION_CONSULTANT)
        )
        val JANET = Staff(DN("Janet", listOf("")), fromString("c1f1b7ee-ed4e-4342-ac68-199fba9fe50d"),
                listOf(StaffRole.ORDER_COMPLETION_CONSULTANT, StaffRole.SUPPLY_CHAIN_SPECIALIST)
        )

    }
}

/**
 * organizational unit - this refers to the organizational unit (or sometimes the user group) that the
 * user is part of. If the user is part of more than one group, you may specify as such,
 * e.g., OU= Lawyer,OU= Judge.<br>
 * <br>
 * ous form a tree, linked in both directions.<br>
 * <br>
 *
 * @param dn distinguised name of the ou. note that dcs are inherited by children.
 * @param inheritableRoles roles that are inherited by all child-ous and staff
 */
class OU(val dn: DN, val staff: List<Staff>, val inheritableRoles: List<StaffRole>, @get:JsonIgnore val parent: OU? = null) {
    val children = mutableListOf<OU>()

    init {
        parent?.children?.add(this)
        staff.forEach { it.ou = this }
    }

    fun getAllInheritableRoles(): Collection<StaffRole> {
        val roles = mutableListOf(*inheritableRoles.toTypedArray())
        roles.addAll(parent?.getAllInheritableRoles()?: emptyList())
        return roles
    }

    /** puts all staff into the given list, optionally matching the given role (null means all roles),
     * operating recursively down thru the kids */
    fun getAllStaff(staffRole: StaffRole?, staffList: MutableList<Staff> = mutableListOf()) {
        staffList.addAll(
            if(staffRole == null) {
                staff
            } else {
                staff.filter { it.getAllStaffRoles().contains(staffRole) }
            }
        )
        children.forEach { it.getAllStaff(staffRole, staffList) }
    }
}

enum class StaffRole(description: String) {
    SALES_REP("A person who sells stuff"),
    SUPPLY_CHAIN_SPECIALIST("Someone specialising in sourcing products"),
    ORDER_COMPLETION_CONSULTANT("Someone who completes bespoke orders"),
    FINANCE_SPECIALIST("Someone who works with finances")
}

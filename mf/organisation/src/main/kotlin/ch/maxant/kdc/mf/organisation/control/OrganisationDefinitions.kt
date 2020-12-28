package ch.maxant.kdc.mf.organisation.control

import ch.maxant.kdc.mf.library.Role
import ch.maxant.kdc.mf.organisation.control.Staff.Companion.JANE
import ch.maxant.kdc.mf.organisation.control.Staff.Companion.JANET
import ch.maxant.kdc.mf.organisation.control.Staff.Companion.JOHN
import com.fasterxml.jackson.annotation.JsonIgnore
import java.util.*
import java.util.UUID.fromString
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaType

object OUs {
    val HEAD_OFFICE = OU(DN("Head Office", listOf("ch", "maxant", "mf")), listOf(JOHN), listOf(), listOf())
    val FINANCES = OU(DN("Finances", listOf("finances")), listOf(
        // note how john is here, but also in head office
        JOHN
    ), listOf(StaffRole.FINANCE_SPECIALIST), listOf(), HEAD_OFFICE)
    val BERN = OU(DN("Bern", listOf("bern")), listOf(
        JANE
    ), listOf(), listOf("3000"), HEAD_OFFICE)
    val LAUSANNE = OU(DN("Lausanne", listOf("lausanne")), listOf(
        JANET
    ), listOf(), listOf("1000", "1007"), HEAD_OFFICE)

    /** returns all staff, optionally matching the given role (null means all roles),
     * operating recursively down thru the kids of the given OU, or HEAD_OFFICE if none is provided */
    fun getAllStaff(staffRole: StaffRole? = null, ou: OU = HEAD_OFFICE): MutableSet<Staff> {
        val staffList = mutableSetOf<Staff>()
        ou.getAllStaff(staffRole, staffList)
        return staffList
    }
}

/**
 * distinguished name - this refers to the name that uniquely identifies an entry in the directory
 * @param cn common name
 * @param dcs domain components, this refers to each component of the domain
 */
class DN(val cn: String, val dcs: List<String>)

/**
 * @param un username
 */
abstract class User(val dn: DN, val partnerId: UUID, val un: String, val pswd: String, val roles: List<Role>)

class Staff(dn: DN, partnerId: UUID, staffRoles: List<StaffRole>, un: String, pswd: String): User(dn, partnerId, un, pswd, staffRoles) {
    @get:JsonIgnore val ous = mutableListOf<OU>() // ignored since its a relationship back up to the parent which could cause infinite loops during serialisation

    /** keys of OUs where staff works */
    val ouDnCns = mutableListOf<String>()

    fun getAllStaffRolesInclInherited(): Set<Role> {
        val allRoles = mutableSetOf(*roles.toTypedArray())
        ous.forEach { allRoles.addAll(it.getAllInheritableRoles()) }
        return allRoles
    }

    fun getEmail(): String = "$un@mf.maxant.ch"

    companion object {
        val JOHN = Staff(DN("John", listOf("")), fromString("3cd5b3b1-e740-4533-a526-2fa274350586"),
                listOf(StaffRole.SALES_REP), "john.smith", "asdf"
        )
        val JANE = Staff(DN("Jane", listOf("")), fromString("6c5aa3cd-0a07-4055-9cec-955900c6bea0"),
                listOf(StaffRole.SALES_REP, StaffRole.ORDER_COMPLETION_CONSULTANT), "jane.smith", "asdf"
        )
        val JANET = Staff(DN("Janet", listOf("")), fromString("c1f1b7ee-ed4e-4342-ac68-199fba9fe50d"),
                listOf(StaffRole.ORDER_COMPLETION_CONSULTANT, StaffRole.SUPPLY_CHAIN_SPECIALIST), "janet.smith", "asdf"
        )
        fun values() = (Companion::class.memberProperties as ArrayList)
                .filter { it.returnType.javaType.typeName == Staff::class.java.name }
                .map { it.get(Companion) as Staff }
    }
}

/** a partner (e.g. a customer, or supplier) with access to some part of the landscape */
class Partner(dn: DN, partnerId: UUID, partnerRoles: List<PartnerRole>, un: String, pswd: String): User(dn, partnerId, un, pswd, partnerRoles) {
    @get:JsonIgnore val ous = mutableListOf<OU>()

    companion object {
        val AUGUSTUS = Partner(DN("Augustus", listOf("")), fromString("331e5c18-e330-4204-95a1-371e54a12f5c"),
                listOf(PartnerRole.CUSTOMER), "180000032", "asdf"
        )
        val TIBERIUS = Partner(DN("Tiberius", listOf("")), fromString("c642e4c8-bdcf-4b34-96d4-6a45df2cbf22"),
                listOf(PartnerRole.CUSTOMER), "180000033", "asdf"
        )
        val GAIUS = Partner(DN("Gaius", listOf("")), fromString("8599d3f0-3807-4b57-9770-6864cb3fddbd"),
                listOf(PartnerRole.CUSTOMER), "180000034", "asdf"
        )
        fun values() = (Companion::class.memberProperties as ArrayList)
                .filter { it.returnType.javaType.typeName == Partner::class.java.name }
                .map { it.get(Companion) as Partner }
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
class OU(val dn: DN,
         val staff: List<Staff>,
         val inheritableRoles: List<StaffRole>,
         val postcodes: List<String>,
         @get:JsonIgnore val parent: OU? = null) {
    val children = mutableListOf<OU>()

    init {
        parent?.children?.add(this)
        staff.forEach { it.ous.add(this) }
        staff.forEach { it.ouDnCns.add(getId()) }
    }

    fun getAllInheritableRoles(): Collection<StaffRole> {
        val roles = mutableSetOf(*inheritableRoles.toTypedArray())
        roles.addAll(parent?.getAllInheritableRoles()?: emptyList())
        return roles
    }

    fun getId(): String = (if(parent != null) parent.getId() + "." else "").plus(dn.dcs.joinToString("."))

    /** puts all staff into the given list, optionally matching the given role (null means all roles),
     * operating recursively down thru the kids */
    fun getAllStaff(staffRole: StaffRole?, staffList: MutableSet<Staff> = mutableSetOf()) {
        staffList.addAll(
            if(staffRole == null) {
                staff
            } else {
                staff.filter { it.getAllStaffRolesInclInherited().contains(staffRole) }
            }
        )
        children.forEach { it.getAllStaff(staffRole, staffList) }
    }
}


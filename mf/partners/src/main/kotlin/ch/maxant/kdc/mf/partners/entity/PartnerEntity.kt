package ch.maxant.kdc.mf.partners.entity

import org.hibernate.annotations.Type
import java.time.LocalDate
import java.util.*
import javax.persistence.*
import ch.maxant.kdc.mf.partners.entity.PartnerEntity.NqSelectByFirstNameOrLastNameOrDobOrEmailOrPhone as NqSByFNOrLNOrDobOrEOrP

@Entity
@Table(name = "T_PARTNERS")
@NamedQueries(
        NamedQuery(name = NqSByFNOrLNOrDobOrEOrP.name,
                query = NqSByFNOrLNOrDobOrEOrP.query)
)
class PartnerEntity(

    @Id
    @Column(name = "ID")
    @Type(type = "uuid-char")
    var id: UUID = UUID.randomUUID(),

    @Column(name = "FIRST_NAME", nullable = false)
    var firstName: String,

    @Column(name = "LAST_NAME", nullable = false)
    var lastName: String,

    @Column(name = "TYPE", nullable = false)
    @Enumerated(EnumType.STRING)
    var type: PersonType,

    @Column(name = "DOB", nullable = false)
    var dob: LocalDate,

    @Column(name = "EMAIL")
    var email: String,

    @Column(name = "PHONE")
    var phone: String
) {
    constructor() : this(UUID.randomUUID(), "", "", PersonType.PERSON, LocalDate.now(), "", "")

    object NqSelectByFirstNameOrLastNameOrDobOrEmailOrPhone {
        const val name = "selectPartnerByFirstNameOrLastNameOrDobOrEmailOrPhone"
        const val firstNameParam = "firstName"
        const val lastNameParam = "lastName"
        const val dobParam = "dob"
        const val emailParam = "email"
        const val phoneParam = "phone"
        const val query = """
                from PartnerEntity p
                where upper(p.firstName) like concat('%',upper(:$firstNameParam),'%')
                   or upper(p.lastName)  like concat('%',upper(:$lastNameParam) ,'%')
                   or p.dob = :$dobParam
                   or upper(p.email)  like concat('%',upper(:$emailParam) ,'%')
                   or upper(p.phone)  like concat('%',upper(:$phoneParam) ,'%')
                """
    }

    object Queries {
        fun selectByFirstNameOrLastNameOrDobOrEmailOrPhone(em: EntityManager,
          firstName: String?,
          lastName: String?,
          dob: LocalDate?,
          email: String?,
          phone: String?): List<PartnerEntity> {
            return em.createNamedQuery(NqSByFNOrLNOrDobOrEOrP.name, PartnerEntity::class.java)
                    .setParameter(NqSByFNOrLNOrDobOrEOrP.firstNameParam, firstName?:"")
                    .setParameter(NqSByFNOrLNOrDobOrEOrP.lastNameParam,  lastName?:"")
                    .setParameter(NqSByFNOrLNOrDobOrEOrP.dobParam,       dob?:LocalDate.now())
                    .setParameter(NqSByFNOrLNOrDobOrEOrP.emailParam,     email?:"")
                    .setParameter(NqSByFNOrLNOrDobOrEOrP.phoneParam,     phone?:"")
                    .resultList
        }
    }
}

enum class PersonType {
    PERSON,
    COMPANY
}
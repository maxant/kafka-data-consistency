package ch.maxant.kdc.partners;

import java.time.LocalDate;
import java.util.UUID;

public class Partner {

    private String id;
    private String firstname;
    private String lastname;
    private int nationality; //https://en.wikipedia.org/wiki/List_of_ISO_3166_country_codes
    private LocalDate dateOfBirth;
    private int age;

    /** for frameworks that need a default constructor */
    public Partner() {
        this( null, null, -1, null);
    }

    /** new */
    public Partner(String firstname, String lastname, int nationality, LocalDate dateOfBirth) {
        this(UUID.randomUUID().toString(), firstname, lastname, nationality, dateOfBirth);
    }

    /** copy */
    public Partner(String id, String firstname, String lastname, int nationality, LocalDate dateOfBirth) {
        this.id = id;
        this.firstname = firstname;
        this.lastname = lastname;
        this.nationality = nationality;
        this.dateOfBirth = dateOfBirth;
    }

    public String getId() {
        return id;
    }

    public String getFirstname() {
        return firstname;
    }

    public String getLastname() {
        return lastname;
    }

    public int getNationality() {
        return nationality;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }
}

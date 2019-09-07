package ch.maxant.kdc.contracts;

import org.hibernate.annotations.Type;

import javax.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "contracts")
public class Contract {

    @Id
    @Column(name = "id")
    @Type(type="uuid-char")
    private UUID id = UUID.randomUUID();

    @Column(updatable = false, nullable = false, name = "contractnumber")
    private String contractNumber;

    @Version
    @Column(nullable = false, name = "version")
    private Integer version;

    @Column(nullable = false, name = "a")
    private String a;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getContractNumber() {
        return contractNumber;
    }

    public void setContractNumber(String contractNumber) {
        this.contractNumber = contractNumber;
    }

    public Integer getVersion() {
        return version;
    }

    // TODO it would be nice to get rid of this, but without it, jsonb doesnt set the version number on incoming requests. check out how to configure jsonb like jackson
    public void setVersion(Integer version) {
        this.version = version;
    }

    public String getA() {
        return a;
    }

    public void setA(String a) {
        this.a = a;
    }
}

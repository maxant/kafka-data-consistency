package ch.maxant.kdc.objects;

import java.util.UUID;

public class AnObject {
    private UUID id;
    private String name;

    public AnObject() {
        this(UUID.randomUUID(), null);
    }

    public AnObject(UUID id, String name) {
        this.id = id;
        this.name = name;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}

package ch.maxant.kdc.claims;

public class Task {

    private String foreignReference;
    private String description;

    public Task(String foreignReference, String description) {
        this.foreignReference = foreignReference;
        this.description = description;
    }

    public String getForeignReference() {
        return foreignReference;
    }

    public void setForeignReference(String foreignReference) {
        this.foreignReference = foreignReference;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}

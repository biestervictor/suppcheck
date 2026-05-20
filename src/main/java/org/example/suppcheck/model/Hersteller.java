package org.example.suppcheck.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Represents a supplement manufacturer (Hersteller) stored in MongoDB.
 * Replaces the hardcoded {@link Shop} enum for dynamic management.
 */
@Document(collection = "hersteller")
public class Hersteller {

    @Id
    private String id;

    @Indexed(unique = true)
    private String name;

    public Hersteller() {}

    public Hersteller(String name) {
        this.name = name;
    }

    public String getId()               { return id; }
    public void   setId(String id)      { this.id = id; }
    public String getName()             { return name; }
    public void   setName(String name)  { this.name = name; }
}

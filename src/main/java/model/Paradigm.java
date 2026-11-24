package model;

public enum Paradigm {
    // Defines the paradigms
    PROCEDURAL("Procedural Programming"),
    OBJECT_ORIENTED("Object-Oriented Programming"),
    FUNCTIONAL("Functional Programming"),
    LOGICAL("Logic Programming"),
    RECURSIVE("Recursive Programming"),
    EVENT_DRIVEN("Event-Driven Programming"),
    CONCURRENT("Concurrent Programming"),
    IMPERATIVE_DECLARATIVE("Imperative and Declarative Programming"),
    COMPONENT_MAPPING("Component Mappings Between Paradigms");

    private final String displayName;

    Paradigm(String displayName) {
        this.displayName = displayName;
    }

    /** Readable name of this paradigm. */
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}

package model.character;

import java.util.Objects;

/**
 * One piece of the countermeasure device the player's father left blueprints for. One Component per era, dropped by that era's boss
 * and collected into the Player's inventory until all pieces are gathered.
 */
public class Component {

    private String componentId;
    private String name;
    private String description;
    private String iconAssetPath;
    private int originEraNumber;
    private boolean collected;

    public Component(String componentId, String name, String description,
                      String iconAssetPath, int originEraNumber) {
        this.componentId = Objects.requireNonNull(componentId, "componentId must not be null");
        this.name = name;
        this.description = description;
        this.iconAssetPath = iconAssetPath;
        this.originEraNumber = originEraNumber;
        this.collected = false;
    }

    /** Marks this component as gathered into the player's inventory. */
    public void collect() {
        this.collected = true;
    }

    public String getComponentId() {
        return componentId;
    }

    public void setComponentId(String componentId) {
        this.componentId = Objects.requireNonNull(componentId, "componentId must not be null");
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getIconAssetPath() {
        return iconAssetPath;
    }

    public void setIconAssetPath(String iconAssetPath) {
        this.iconAssetPath = iconAssetPath;
    }

    /** The number (1-7) of the era whose boss drops this component. */
    public int getOriginEraNumber() {
        return originEraNumber;
    }

    public void setOriginEraNumber(int originEraNumber) {
        this.originEraNumber = originEraNumber;
    }

    public boolean isCollected() {
        return collected;
    }

    public void setCollected(boolean collected) {
        this.collected = collected;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Component)) return false;
        Component component = (Component) o;
        return componentId.equals(component.componentId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(componentId);
    }

    @Override
    public String toString() {
        return "Component{componentId='" + componentId + "', name='" + name + "', collected=" + collected + '}';
    }
}
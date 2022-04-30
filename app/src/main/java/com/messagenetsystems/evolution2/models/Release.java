package com.messagenetsystems.evolution2.models;

/** Release
 * Model class for version release information.
 *
 * Revisions:
 *  2020.03.11      Chris Rider     Created.
 */

import java.util.ArrayList;
import java.util.List;

public class Release {
    private String date;
    private String version;
    private String author;
    private List<Component> components;

    // Normal constructor
    public Release() {
        this.date = "";
        this.version = "";
        this.author = "";
        this.components = new ArrayList<>();
    }

    // Copy constructor...
    // To use: Release someReleaseToCopyTo = new Release(someReleaseToCopyFrom)
    public Release(Release releaseToCopy) {
        date = releaseToCopy.date;
        version = releaseToCopy.version;
        author = releaseToCopy.author;
        components = releaseToCopy.components;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public List<Component> getComponents() {
        return components;
    }

    public void setComponents(List<Component> components) {
        this.components = components;
    }


    public static class Component {
        private String type;
        private String author;
        private String summary;
        private String description;

        // Normal constructor
        public Component() {
            this.type = "";
            this.author = "";
            this.summary = "";
            this.description = "";
        }

        // Copy constructor
        // To use: Component someComponentToCopyTo = new Component(someComponentToCopyFrom)
        public Component(Component componentToCopy) {
            type = componentToCopy.type;
            author = componentToCopy.author;
            summary = componentToCopy.summary;
            description = componentToCopy.description;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getAuthor() {
            return author;
        }

        public void setAuthor(String author) {
            this.author = author;
        }

        public String getSummary() {
            return summary;
        }

        public void setSummary(String summary) {
            this.summary = summary;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }
}

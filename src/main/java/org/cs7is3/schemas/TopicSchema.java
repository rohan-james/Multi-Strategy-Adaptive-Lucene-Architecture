package org.cs7is3.schemas;

public class TopicSchema {
    private String number = "";
    private String title = "";
    private String description = "";
    private String narrative = "";

    public static final String topicStartTag = "<top>";
    public static final String topicEndTag = "</top>";
    public static final String numberTag = "<num>";
    public static final String titleTag = "<title>";
    public static final String descriptionTag = "<desc>";
    public static final String narrativeTag = "<narr>";

    public String getNumber() {
        return this.number;
    };

    public String getTitle() {
        return this.title;
    };

    public String getDescription() {
        return this.description;
    };

    public String getNarrative() {
        return this.narrative;
    };

    public void setNumber(String newNumber) {
        this.number = newNumber;
    }

    public void setTitle(String newTitle) {
        this.title = newTitle;
    }

    public void setDescription(String newDescription) {
        this.description = newDescription;
    }

    public void setNarrative(String newNarrative) {
        this.narrative = newNarrative;
    }
}
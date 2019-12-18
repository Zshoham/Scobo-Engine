package gui;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;

import java.util.Comparator;

public class DictionaryEntry {

    public static Comparator<DictionaryEntry> comparator = Comparator.comparing(DictionaryEntry::getTerm);

    private final SimpleStringProperty term;
    private final SimpleIntegerProperty frequency;

    public DictionaryEntry(String term, int frequency) {
        this.term = new SimpleStringProperty(term);
        this.frequency = new SimpleIntegerProperty(frequency);
    }

    public String getTerm() {
        return term.get();
    }

    public int getFrequency() {
        return frequency.get();
    }

    public void setTerm(String term) {
        this.term.set(term);
    }

    public void setFrequency(int frequency) {
        this.frequency.set(frequency);
    }
}

package indexer;

import java.util.HashMap;

public class Document {

    public String name;
    public HashMap<String, Integer> terms;

    public Document(String doc, HashMap<String, Integer> terms) {
        this.name = doc;
        this.terms = terms;
    }
}

package indexer;

import util.Configuration;
import util.Logger;

import java.io.*;

public class SemanticAnalyzer implements Runnable {

    private String GloSimPath;
    private String dictSimPath;
    private Dictionary dictionary;

    public SemanticAnalyzer(Dictionary dictionary) {
        this.dictionary = dictionary;
        this.GloSimPath = "";
        if (Configuration.getInstance().getUseStemmer())
            GloSimPath = Configuration.getInstance().getGloVeStemmedPath();
        else
            GloSimPath = Configuration.getInstance().getGloVeUnStemmedPath();

        dictSimPath = Configuration.getInstance().getDictSimPath();
    }

    @Override
    public void run() {
        try {
            BufferedReader reader = new BufferedReader(new FileReader(GloSimPath));
            BufferedWriter writer = new BufferedWriter(new FileWriter(dictSimPath));
            String line;
            while ((line = reader.readLine()) != null) {
                String word = line.split(",")[0];
                if (dictionary.lookupTerm(word).isPresent())
                    writer.append(line).append("\n");
            }
        }
        catch (IOException e) {
            Logger.getInstance().error(e);
        }
    }

}

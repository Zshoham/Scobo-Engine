package indexer;

import util.Configuration;
import util.Logger;

import java.io.*;

/**
 * This class creates the semantic word similarity vectors for the given dictionary.
 * The created similarity vectors will contain <word, similar word vector> where word
 * can be any word in the dictionary, but the similar words might be words that are not
 * contained in the dictionary.
 */
public class SemanticAnalyzer implements Runnable {

    private String dictSimPath;
    private Dictionary dictionary;

    /**
     * Initialize the analyzer with a given dictionary for which the
     * vectors will be computed.
     * @param dictionary the engines dictionary.
     */
    public SemanticAnalyzer(Dictionary dictionary) {
        this.dictionary = dictionary;
        dictSimPath = Configuration.getInstance().getDictSimPath();
    }

    @Override
    public void run() {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(Configuration.getInstance().getGloVe()));
            BufferedWriter writer = new BufferedWriter(new FileWriter(dictSimPath));
            String line;
            while ((line = reader.readLine()) != null) {
                String word = line.substring(0, line.indexOf(","));
                if (dictionary.lookupTerm(word).isPresent())
                    writer.append(line).append("\n");
            }

            writer.close();
        }
        catch (IOException e) {
            Logger.getInstance().error(e);
        }
    }

}

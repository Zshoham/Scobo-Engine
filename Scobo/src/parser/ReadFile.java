package parser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Manages the reading and separating of files into documents
 */
class ReadFile {

    private Parser parser;

    // this pattern catches all text between <DOC> and </DOC> tags, which is a document.
    private static final Pattern splitPattern = Pattern.compile(Pattern.quote("<DOC>") + "(.+?)" + Pattern.quote("</DOC>"), Pattern.DOTALL);

    private volatile AtomicInteger fileCount;

    /**
     * Creates a readfile that will read from the specified path and is
     * associated with the given parser.
     * @param corpusPath the path of the corpus.
     * @param parser the parser in charge of this readfile.
     */
    protected ReadFile(String corpusPath, Parser parser) {
        String[] files = new File(corpusPath).list();
        if (files == null)
            throw new InvalidPathException(corpusPath, "could not find corpus files");

        this.parser = parser;
        this.fileCount = new AtomicInteger(files.length);

        // create the file paths for each file.
        for (int i = 0; i < files.length; i++)
            files[i] = corpusPath + "/" + files[i] + "/" + files[i];

        // open the io group and add all the reading tasks.
        parser.IOTasks.openGroup();
        for (int i = 0; i < files.length; i += parser.getBatchSize()) {
            final int index = i;
            parser.IOTasks.add(() -> read(files, index, Math.min(index + parser.getBatchSize(), files.length)));
        }
        parser.IOTasks.closeGroup();
    }

    /*
    Reads a batch of files from 'batch', start reading from the
    'start' index of batch up to the 'end' index.
     */
    private void read(String[] batch, int start, int end) {
        if (fileCount.get() == 0)
            parser.CPUTasks.openGroup();

        byte[][] files = new byte[end - start][];

        try {
            for (int i = start; i < end; i++)
                files[i - start] = Files.readAllBytes(Paths.get(batch[i]));

        } catch (IOException e) {
            parser.LOG.error(e);
        }

        // the batch is read now separate the files in the batch
        // into documents.
        parser.CPUTasks.add(() -> separate(files));
        parser.IOTasks.complete();
    }

    /*
    Separates the given files into documents, the files are given
    as an array of byte arrays where each byte array represents
    a files bytes.
     */
    private void separate(byte[][] batch) {
        for (int i = 0; i < batch.length; i++) {
            Matcher docMatcher = splitPattern.matcher(new String(batch[i]));
            batch[i] = null;
            while (docMatcher.find())
                parser.CPUTasks.add(new Parse(docMatcher.group(), parser));

        }

        // we close the group once all the files have been separated
        // and all CPU tasks added.
        if (fileCount.addAndGet(-batch.length) == 0)
            parser.CPUTasks.closeGroup();

        parser.CPUTasks.complete();
    }
}

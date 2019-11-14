package parser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

public class ReadFile {

    private Parser parser;

    private AtomicInteger fileCount;

    private static final Pattern splitPattern = Pattern.compile("\\|<DOC>|</DOC>\n\n");

    public ReadFile(String corpusPath, Parser parser) {
        String[] files = new File(corpusPath).list();
        if (files == null) throw new InvalidPathException(corpusPath, "could not find corpus files");
        this.parser = parser;
        this.fileCount = new AtomicInteger(files.length);


        for (int i = 0; i < files.length; i++)
            files[i] = corpusPath + "/" + files[i] + "/" + files[i];

        for (int i = 0; i < files.length; i += parser.getBatchSize()) {
            final int index = i;
            parser.executeIOTask(() -> read(files, index, Math.min(index + parser.getBatchSize(), files.length)));
        }

       parser.readLatch.countDown();
    }

    private void read(String[] batch, int start, int end) {
        byte[][] files = new byte[end - start][];

        try {
            for (int i = start; i < end; i++)
                files[i - start] = Files.readAllBytes(Paths.get(batch[i]));

        } catch (IOException e) {
            e.printStackTrace();
        }

        parser.executeCPUTask(() -> separate(files));
    }

    private void separate(byte[][] batch) {
        for (int i = 0; i < batch.length; i++) {
            String[] docs = splitPattern.split(new String(batch[i]));
            batch[i] = null;
            for (String doc : docs)
                parser.executeCPUTask(new Parse(doc, parser.getUniqueTerms()));
        }

        if (fileCount.addAndGet(-batch.length) == 0)
            parser.parseLatch.countDown();
    }
}

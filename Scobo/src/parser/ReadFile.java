package parser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicInteger;

public class ReadFile {
    private Parser parser;

    private AtomicInteger fileCount;

    public ReadFile(String corpusPath, Parser parser) {
        String files[] = new File(corpusPath).list();
        this.parser = parser;
        this.fileCount = new AtomicInteger(files.length);


        for (int i = 0; i < files.length; i++)
            files[i] = corpusPath + "/" + files[i] + "/" + files[i];

        for (int i = 0; i < files.length; i += parser.getBatchSize()) {
            final int index = i;
            parser.executeIOTask(() -> read(files, index, Math.min(index + parser.getBatchSize(), files.length)));
        }

        synchronized (parser.readWaiter) {
            parser.readWaiter.notifyAll();
            parser.readWaiter = Boolean.TRUE;
        }
    }

    private void read(String[] batch, int start, int end) {
        byte files[][] = new byte[end - start][];

        try {
            for (int i = start; i < end; i++) {
                files[i - start] = Files.readAllBytes(Paths.get(batch[i]));
                batch[i] = null;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        parser.executeCPUTask(() -> separate(files));
    }

    private void separate(byte[][] batch) {
        for (int i = 0; i < batch.length; i++) {
            //TODO: if the documents need to be saved separably add the <DOC> tag after split.
            String docs[] = new String(batch[i]).split("\\|<DOC>|</DOC>\n\n");
            for (String doc : docs)
                parser.executeCPUTask(new Parse(doc, parser.getUniqueTerms()));
        }

        if (fileCount.addAndGet(-batch.length) == 0)
            synchronized (parser.parseWaiter) {
            parser.parseWaiter.notifyAll();
            parser.parseWaiter = Boolean.TRUE;
        }
    }
}

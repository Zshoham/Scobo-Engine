package parser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Parser {

    private ExecutorService IOPool;
    private ExecutorService CPUPool;

    private String corpusPath;

    private static final int BATCH_SIZE = 10;

    public Parser(String path) {
        IOPool = Executors.newFixedThreadPool(1);
        CPUPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

        this.corpusPath = path;
    }

    public void start() {
        String files[] = new File(corpusPath).list();

        for (int i = 0; i < files.length; i++)
            files[i] = corpusPath + "/" + files[i] + "/" + files[i];


        for (int i = 0; i < files.length; i += BATCH_SIZE) {
            final int index = i;
            IOPool.execute(() -> read(files, index, Math.min(index + BATCH_SIZE, files.length)));
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

       CPUPool.execute(() -> separate(files));
    }

    private void separate(byte[][] batch) {
        for (int i = 0; i < batch.length; i++) {
            //TODO: if the documents need to be saved separably add the <DOC> tag after split.
            String docs[] = new String(batch[i]).split("<DOC>|</DOC>");
            for (String doc : docs) CPUPool.execute(new DocumentProcessor(doc));
        }
    }
}

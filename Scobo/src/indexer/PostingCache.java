package indexer;


import util.Configuration;
import util.Logger;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class PostingCache {

    private static final String postingPath = Configuration.getInstance().getIndexPath() + "/postings/";
    private static final String invertedFilePath = Configuration.getInstance().getIndexPath() + "/InvertedFile.txt";

    private static Cache cache;
    private static volatile AtomicInteger runningID;

    static void initCache(Indexer indexer) {
        File postingDir = new File(postingPath);

        if (!postingDir.exists())
            postingDir.mkdirs();

        runningID = new AtomicInteger(0);
        cache = new Cache(indexer);
    }

    static Optional<PostingFile> newPostingFile() {
        if (cache == null) {
            Logger.getInstance().warn("trying to use PostingCache when not initialized");
            return Optional.empty();
        }

        PostingFile res = new PostingFile(runningID.getAndIncrement());
        return Optional.of(res);
    }

    static void queuePostingFlush(PostingFile postingFile) {
        final int postingFileID = postingFile.getID();
        final TermPosting[] postings = postingFile.getPostings();
        cache.indexer.IOTasks.add(() -> flushPosting(postingFileID, postings));
    }

    static void flushPosting(int postingFileId, TermPosting[] postings) {
        try {
            String path = getPostingFilePath(postingFileId);
            BufferedWriter writer = new BufferedWriter(new FileWriter(path));

            for (TermPosting termPosting : postings)
                writer.append(termPosting.dump());

            writer.close();

        } catch (IOException e) {
            Logger.getInstance().error(e);
        }
        finally {
            cache.indexer.IOTasks.complete();
        }
    }

    static String getPostingFilePath(int postingFileID) {
        return postingPath + postingFileID + ".txt";
    }

    public static void merge(Dictionary dictionary) {
        try {
            int postingFileCount = runningID.get();
            BufferedWriter invertedFileWriter = new BufferedWriter(new FileWriter(invertedFilePath));
            BufferedReader[] postingReaders = new BufferedReader[postingFileCount];
            boolean isFirstRead = true;

            int lineNumber = 0;
            // find min term of all files.
            ArrayList<String> lines = new ArrayList<>();
            LinkedList<Integer> minLines = new LinkedList<>();

            int countNull = 0;
            while (countNull < postingFileCount) {
                String minTerm = null;
                countNull = 0;
                for (int i = 0; i < postingFileCount; i++) {
                    if (isFirstRead) {
                        postingReaders[i] = new BufferedReader(new FileReader(getPostingFilePath(i)));
                        lines.add(i, postingReaders[i].readLine());
                    }

                    String line = lines.get(i);
                    if (line != null) {
                        String term = line.substring(0, line.indexOf("|"));
                        if (minTerm == null) {
                            minTerm = term;
                            minLines.addLast(i);
                        } else if (term.compareTo(minTerm) < 0) {
                            minTerm = term;
                            minLines.clear();
                            minLines.addLast(i);
                        } else if (term.compareTo(minTerm) == 0)
                            minLines.addLast(i);
                    }
                    else countNull++;
                }
                if (countNull >= postingFileCount)
                    break;

                isFirstRead = false;
                int firstMinLine = minLines.removeFirst();
                StringBuilder docsStr = new StringBuilder(lines.get(firstMinLine));
                for (int line : minLines) {
                    String lineStr = lines.get(line);
                    docsStr.append(lineStr.substring(lineStr.indexOf("|")));
                    lines.set(line, postingReaders[line].readLine());
                }
                lines.set(firstMinLine, postingReaders[firstMinLine].readLine());
                docsStr.append("\n");
                invertedFileWriter.write(docsStr.toString());

                // update dictionary pointer.
                Optional<Term> optionalTerm = dictionary.lookupTerm(minTerm);
                if (!optionalTerm.isPresent())
                    throw new IllegalStateException("term does not exist in dictionary");

                optionalTerm.get().pointer = lineNumber;

                minLines.clear();
                lineNumber++;
            }
            invertedFileWriter.close();
            for (int i = 0; i < postingReaders.length; i++)
                postingReaders[i].close();

        } catch (IOException e) {
            Logger.getInstance().error(e);
        }
    }

    public static void clean() {
        File postingsDir = new File(postingPath);
        for (File file : postingsDir.listFiles()) {
            file.delete();
        }
        postingsDir.delete();
    }

    /**
     * Deletes the inverted file.
     *
     * @throws IOException if there is a problem deleting the file.
     */
    public static void deleteInvertedFile() throws IOException {
        Files.deleteIfExists(Paths.get(invertedFilePath));
    }


    private static class Cache {

        public Indexer indexer;

        public Cache(Indexer indexer) {
            this.indexer = indexer;
        }
    }
}
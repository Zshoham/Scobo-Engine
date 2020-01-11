package indexer;

import util.Configuration;
import util.Logger;
import util.TaskGroup;
import util.TaskManager;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages the creation and deletion of posting files and
 * the inverted file
 */
public final class PostingCache {

    private static Indexer indexer;
    private static volatile AtomicInteger runningID;

    private PostingCache() {}

    /**
     * Initializes the cache, after this method is called
     * it is possible to start using the cache to create posting files
     * and later an inverted file.
     * @param cacheIndexer the indexer using the cache.
     */
    static void initCache(Indexer cacheIndexer) {
        File postingDir = new File(getPostingPath());

        if (!postingDir.exists())
            postingDir.mkdirs();

        runningID = new AtomicInteger(0);
        indexer = cacheIndexer;
    }

    /**
     * Posting file factory, creates new posting files.
     * @return a new posting file.
     */
    static Optional<PostingFile> newPostingFile() {
        if (indexer == null) {
            Logger.getInstance().warn("trying to use PostingCache when not initialized");
            return Optional.empty();
        }

        PostingFile res = new PostingFile(runningID.getAndIncrement());
        return Optional.of(res);
    }

    /**
     * Queues a flush of a posting file, this will write the
     * posting file to the disk under a name matching it's id.
     * @param postingFile the posting file to be written.
     * @see #flushPosting(int, TermPosting[])
     */
    static void queuePostingFlush(PostingFile postingFile) {
        final int postingFileID = postingFile.getID();
        final TermPosting[] postings = postingFile.getPostings();
        indexer.IOTasks.add(() -> flushPosting(postingFileID, postings));
    }

    /**
     * Flushes the posting file to the disk.
     * @param postingFileId the id of the posting file to be flushed.
     * @param postings the postings that need to be written to the file.
     * @see #queuePostingFlush(PostingFile)
     */
    private static void flushPosting(int postingFileId, TermPosting[] postings) {
        try {
            String path = getPostingFilePath(postingFileId);
            BufferedWriter writer = new BufferedWriter(new FileWriter(path));

            for (TermPosting termPosting : postings)
                writer.append(termPosting.toString());

            writer.close();

        } catch (IOException e) {
            Logger.getInstance().error(e);
        }
        finally {
            indexer.IOTasks.complete();
        }
    }

    /**
     * Merges all the posting files into an inverted file.
     * @param dictionary the dictionary that will map into the newly created
     *                   inverted file.
     */
    static void merge(Dictionary dictionary, DocumentMap documentMap) {
        try {
            int postingFileCount = runningID.get();
            BufferedWriter invertedFileWriter = new BufferedWriter(new FileWriter(getInvertedFilePath()));
            BufferedReader[] postingReaders = new BufferedReader[postingFileCount];
            boolean isFirstRead = true;

            //line number of the inverted file.
            long linePointer = 0;
            // number of readers who have finished reading their files.
            int countNull = 0;

            //the latest line each reader read.
            ArrayList<String> lines = new ArrayList<>();
            // the alphabetically minimal lines of the above lines.
            LinkedList<Integer> minLines = new LinkedList<>();

            // create task group to execute the entity updates in parallel.
            TaskGroup entityUpdate = TaskManager.getTaskGroup(TaskManager.TaskType.COMPUTE);
            entityUpdate.openGroup();

            // while there are readers who haven't finished reading their file.
            while (countNull < postingFileCount) {
                //the minimal term of terms this iteration.
                String minTerm = null;
                //reset number of null readers.
                countNull = 0;

                //for each reader
                for (int i = 0; i < postingFileCount; i++) {
                    if (isFirstRead) { // initialize the reader if this is the first read.
                        postingReaders[i] = new BufferedReader(new FileReader(getPostingFilePath(i)));
                        lines.add(i, postingReaders[i].readLine());
                    }
                    String line = lines.get(i);

                    if (line != null) { // if the reader could read a new line.
                        String term = line.substring(0, line.indexOf("|"));
                        if (minTerm == null) { // the min term hasn't been updated this iteration yet.
                            minTerm = term;
                            minLines.addLast(i);
                        } else if (term.compareTo(minTerm) < 0) { // we found a new min term.
                            minTerm = term;
                            minLines.clear();
                            minLines.addLast(i);
                        } else if (term.compareTo(minTerm) == 0) // we found a new term equal to the min.
                            minLines.addLast(i);
                    }
                    else countNull++; // the reader couldn't read another line, meaning it finished reading it's file.
                }

                // if non of the readers read a new line we are finished.
                if (countNull >= postingFileCount)
                    break;

                isFirstRead = false;

                // merge all the min lines from the files into one line and read the next line.
                int firstMinLine = minLines.removeFirst();
                StringBuilder termPostingStr = new StringBuilder(lines.get(firstMinLine));
                for (int line : minLines) {
                    String lineStr = lines.get(line); // line of the format t(|d,f)+
                    termPostingStr.append(lineStr.substring(lineStr.indexOf("|"))); // get only the (|d,f)+ part
                    lines.set(line, postingReaders[line].readLine()); // read the next line
                }
                lines.set(firstMinLine, postingReaders[firstMinLine].readLine());

                // get term for minTerm from dictionary.
                Optional<Term> optionalTerm = dictionary.lookupTerm(minTerm);
                if (!optionalTerm.isPresent())
                    throw new IllegalStateException("term does not exist in dictionary");

                // update pointer.
                optionalTerm.get().pointer = linePointer;
                if(dictionary.isEntity(minTerm)) {
                    // if the term we added is an entity update the document map.
                    String postingString = termPostingStr.toString();
                    entityUpdate.add(() -> documentMap.updateEntity(postingString, entityUpdate::complete));
                }

                termPostingStr.append("\n"); // append line ending.
                invertedFileWriter.append(termPostingStr); // write the merged line.

                minLines.clear();
                linePointer += termPostingStr.length();
            }

            // all entity updates have been sent.
            entityUpdate.closeGroup();

            // release all the files held by the readers and writers.
            invertedFileWriter.close();
            for (BufferedReader postingReader : postingReaders)
                postingReader.close();

            // wait for all the entity updates to complete.
            entityUpdate.awaitCompletion();

        } catch (IOException e) {
            Logger.getInstance().error(e);
        }
    }

    /**
     * Deletes all the posting files.
     */
    static void clean() {
        File postingsDir = new File(getPostingPath());
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
        Files.deleteIfExists(Paths.get(getInvertedFilePath()));
    }

    // get path to Posting file directory.
    private static String getPostingPath() {
        return Configuration.getInstance().getPostingFilePath();
    }

    // get path to inverted file.
    private static String getInvertedFilePath() {
        return Configuration.getInstance().getInvertedFilePath();
    }

    // get path to the posting file with the given id.
    // note: this method does not guarantee that the file exists.
    private static String getPostingFilePath(int postingFileID) {
        return getPostingPath() + postingFileID + ".txt";
    }
}
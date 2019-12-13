package indexer;

import util.Configuration;
import util.Logger;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

public final class PostingCache {

    private static int FIRST_FLUSH_THRESHOLD = 16384; //2^12
    private static final String postingPath = Configuration.getInstance().getIndexPath() + "/postings/";


    private static Cache cache;
    private static volatile AtomicInteger runningID;
    private static PostingFile newPostingFile = null;

    static void initCache(Indexer indexer) {
        File postingDir = new File(postingPath);

        if (!postingDir.exists())
            postingDir.mkdirs();

        runningID = new AtomicInteger(0);
        cache = new Cache(indexer);
    }

    synchronized static Optional<PostingFile> getPostingFileByID(int postingFile) {
        if (cache == null) {
            Logger.getInstance().warn("trying to use PostingCache when not initialized");
            return Optional.empty();
        }

        return Optional.ofNullable(cache.postingFiles.get(postingFile));
    }

    synchronized static Optional<PostingFile> newPostingFile() {
        if (cache == null) {
            Logger.getInstance().warn("trying to use PostingCache when not initialized");
            return Optional.empty();
        }
        if (newPostingFile != null)
            return Optional.of(newPostingFile);

        PostingFile res = new PostingFile(runningID.getAndIncrement());
        cache.postingFiles.put(res.getID(), res);
        return Optional.of(res);
    }

    synchronized static void handleFirstFlush(PostingFile postingFile) {
        if (newPostingFile != null && newPostingFile != postingFile)
            throw new IllegalStateException("using a new posting file before flushing the previous");

        if (postingFile.getPostingCount() >= FIRST_FLUSH_THRESHOLD) {
            postingFile.flush();
            newPostingFile = null;
        }
        else newPostingFile = postingFile;
    }

    synchronized static void queuePostingFileUpdate(int postingFileID, ConcurrentHashMap<String, TermPosting> postings) {
        cache.indexer.IOTasks.add(() -> updatePostingFile(postingFileID, postings));
    }

    private synchronized static void updatePostingFile(int postingFileID, ConcurrentHashMap<String, TermPosting> postings) {
        try {
            String path = getPostingFilePath(postingFileID);
            List<String> postingFile = new LinkedList<>();
            if (!isFirstWrite(postingFileID))
                postingFile = Files.readAllLines(Paths.get(path));
            BufferedWriter writer = new BufferedWriter(new FileWriter(path));

            // for all the existing postings in the file
            // update their values with the cached postings.
            for (String posting : postingFile) {
                TermPosting loaded = TermPosting.loadPosting(posting);

                if (postings.containsKey(loaded.getTerm())) {
                    // get the corresponding posting from the cached posting file.
                    TermPosting cachedPosting = postings.get(loaded.getTerm());
                    // added the new posting data to the loaded posting.
                    loaded.addAll(cachedPosting.getDocuments());
                    // remove the posting from the cached posting in order to
                    // not iterate over it again.
                    postings.remove(loaded.getTerm());
                }

                writer.append(loaded.dump()).append("\n");
            }

            // now for all the postings that are cached but do not exists in the file
            // we will add new lines to the file with those postings.
            // this loop should only be entered if this is a new posting file
            for (TermPosting termPosting : postings.values()) {
                writer.append(termPosting.dump()).append("\n");
            }

            writer.close();
            updateFirstWrite(postingFileID);

        } catch (IOException e) {
            Logger.getInstance().error(e);
        }
        finally {
            cache.indexer.IOTasks.complete();
        }
    }

    private static String getPostingFilePath(int postingFileID) {
        String basePath = Configuration.getInstance().getIndexPath() + "/postings/";
        return basePath + postingFileID + ".txt";
    }

    private static boolean isFirstWrite(int postingFileID) {
        PostingFile pf = cache.postingFiles.get(postingFileID);
        return pf == null || !pf.isWritten;
    }

    private static void updateFirstWrite(int postingFileID) {
        PostingFile pf = cache.postingFiles.get(postingFileID);
        if (pf != null)
            pf.isWritten = true;
    }

    public static void updateAll() {
        for (PostingFile postingFile : cache.postingFiles.values()) {
            postingFile.flush();
        }
    }

    public static void onFinishedParse() {
        FIRST_FLUSH_THRESHOLD = 0;
    }

    private static class Cache {

        public ConcurrentHashMap<Integer, PostingFile> postingFiles;
        public Indexer indexer;

        public Cache(Indexer indexer) {
            this.postingFiles = new ConcurrentHashMap<>();

            this.indexer = indexer;
        }
    }
}

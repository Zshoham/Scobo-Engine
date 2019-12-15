package indexer;


import util.Configuration;
import util.Logger;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class PostingCache {

    private static final String postingPath = Configuration.getInstance().getIndexPath() + "/postings/";


    private static Cache cache;
    private static volatile AtomicInteger runningID;

    static void initCache(Indexer indexer) {
        File postingDir = new File(postingPath);

        if (!postingDir.exists())
            postingDir.mkdirs();

        runningID = new AtomicInteger(0);
        cache = new Cache(indexer);
    }

    static Optional<PostingFile> getPostingFileByID(int postingFile) {
        if (cache == null) {
            Logger.getInstance().warn("trying to use PostingCache when not initialized");
            return Optional.empty();
        }

        return Optional.ofNullable(cache.postingFiles.get(postingFile));
    }

    static Optional<PostingFile> newPostingFile() {
        if (cache == null) {
            Logger.getInstance().warn("trying to use PostingCache when not initialized");
            return Optional.empty();
        }

        PostingFile res = new PostingFile(runningID.getAndIncrement());
        //cache.postingFiles.put(res.getID(), res);
        return Optional.of(res);
    }

    static void flushPosting(PostingFile postingFile) {
        try {
            String path = getPostingFilePath(postingFile.getID());
            BufferedWriter writer = new BufferedWriter(new FileWriter(path));

            for (TermPosting termPosting : postingFile.postings.values()) {
                writer.append(termPosting.dump()).append("\n");
            }

            writer.close();

        } catch (IOException e) {
            Logger.getInstance().error(e);
        }
    }

    static String getPostingFilePath(int postingFileID) {
        String basePath = Configuration.getInstance().getIndexPath() + "/postings/";
        return basePath + postingFileID + ".txt";
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

package indexer;

import parser.Document;
import util.Logger;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;

/*
Posting File is no longer treated as a buffer, rather it is filled with
postings and then flushed right away and not loaded until the end of the inverting
phase.
once all the documents have been inverted we load pairs of posting files and
merge them together.
aa | ab | aa
ac | ac | ab
dg | bf | ac1 + ac2
          bf
          dg
 */

public class PostingFile {

    private final int postingFileID;

    ArrayList<TemporaryPosting> postings;

    public PostingFile(int postingFileID) {
        this.postingFileID = postingFileID;
        this.postings = new ArrayList<>();
    }

    public void addTerm(String term, int documentID, int documentFrequency) {
        postings.add(new TemporaryPosting(term, documentID, documentFrequency));
    }

    public int getID() {
        return postingFileID;
    }

    public void flush() {
        PostingCache.flushPosting(this);
    }

    static class TemporaryPosting {
        public String term;
        public int docID;
        public int docFrequency;

        public TemporaryPosting(String term, int docID, int docFrequency) {
            this.term = term;
            this.docID = docID;
            this.docFrequency = docFrequency;
        }

        public String dump() {
            return term + ", " + docID + ", " + docFrequency + "\n";
        }
    }
}

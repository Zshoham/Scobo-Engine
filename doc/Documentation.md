# Scobo-Engine Documentation

This document contains implementation level documentation for the engine. In order to get an overview of how the engine operates and how the classes described here operate together see the Architecture document.

## Indexer Package

### Dictionary Class

Maps string representation of a term to a [`Term`](file:///home/shoham/Dev/Scobo-Engine/doc/JavaDoc/indexer/Term.html) instance holding the relevant term statistics and posting file pointer.  

Dictionary file format: Each line in the file represents a single entry in the Dictionary, each line will look like so: [term]|[document frequency]|[posting file]\n 

- term - string representation of the term
- term frequency - number of times the term appears in the corpus
- document frequency - number of documents the term appears in
- posting pointer - index of the posting file this terms posting appears in

*`Dictionary` is externally immutable meaning that it is immutable outside of the scope of its package (indexer)* 

* `protected Dictionary()` : 
  Constructs a `Dictionary` with default parameters. This creates a *mutable* reference.
* `private Dictionary(int termCount, float loadFactor, int concurrencyLevel)` :
  Constructs a `Dictionary` with the given parameters.
* `protected void addNumberFromDocument(String number, int frequency)` : 
  Adds the number to the dictionary, if the number was already contained its statistics will be updated, otherwise the number will be added to the dictionary.
* `protected void addTermFromDocument(String term, int frequency)` : 
  Adds the term to the dictionary, if the word was already contained its statistics will be updated, otherwise the term will be added to the dictionary.
* `protected void addEntityFromDocument(String entity, int frequency)` :
  Adds the entity to the dictionary, if entity term was already contained its statistics will be updated, otherwise the entity will be added to the dictionary.
* `private void addTerm(String term, int count, int frequency)` :
  helper function to add any term to the dictionary. where count is the number of new documents the term appears in and frequency is the number of times the term appears in said documents
* `public Optional<Term> lookupTerm(String term)` : 
  Retrieves information about a term via a `Term` object
* `Optional<Term> lookupEntity(String term)` : 
  Retrieves information about an entity via a `Term` object
* `public boolean contains(String term)` : 
  returns true if the term exists in the dictionary, false otherwise.
* `public int size()` : 
  Returns the number of key-value mappings in this map.  If the map contains more than `Integer.MAX_VALUE` elements, returns `Integer.MAX_VALUE`.
* `public Collection<Term> getTerms()` :
  returns Collection of all the terms in the dictionary.
* `public void save()` :
  Saves the `Dictionary` to the directory specified by`Configuration`.
* `public void clear() throws IOException` :
  Removes all of the entries from the dictionary, and deletes the dictionary file.
* `public static Dictionary loadDictionary() throws IOException` :
  Loads the Dictionary into memory from the directory specified by `Configuration`  and returns a reference to it.
* `private static String getPath()` :  
  returns the path to the dictionary file as specified by `Configuration`.

### DocumentBuffer Class

Represents a buffer of documents that builds up until a term limit is reached (or exceeded) at which point the buffer is flushed and queued to be inverted.

* `DocumentBuffer(Indexer indexer)` : constructs a new document buffer.
* `void addToBuffer(Document document)` : adds a document to the buffer.
* `void flush()` : flushes the document buffer.

### DocumentMap Class

Maps document names to document ID's and generates said IDs. `DocumentMap` can be in one of two modes: 

- ADD mode - meant to be used internally by the indexer, when in ADD mode it is possible to add documents to the map and receive their IDs 
- LOOKUP mode - is meant to be used externally after indexing, when LOOKUP mode it is impossible to add new documents but looking up document names by ID is available

 The distinction between the modes is made because while indexing there is no need to be able to lookup documents after adding them, hence when in ADD mode after reaching a certain threshold the documents added to the map will be saved to the map file. While in LOOKUP all the mappings are available in memory and thus it is possible to preform lookups for any document.  

 Document Map file format: Each line in the file represents a document ID -> document data mapping each line line will look like so: [document ID]|[(document data)]\n 

- document ID - id given to the document by the map
- document data - csv  data about the document including document name 

*`DocumentMap` is externally immutable meaning that it is immutable outside of the scope of its package (indexer)*  

* `protected DocumentMap(Indexer indexer)` : 
  Creates a `DocumentMap` in ADD mode. This creates a *mutable* reference.
* `private DocumentMap(MODE mode, int mapSize, float loadFactor)` :
  private initialization constructor used by the package constructor and the external loadDocumentMap function.
* `protected int addDocument(Document document)` : Adds a document to the map, giving it a unique ID.
* `public Optional<DocumentMapping> lookup(int docID)` : 
  Gets the document mapping of the given document ID.
* `void dumpNow()` : dumps the document map into the document map file.
* `public void clear() throws IOException` :
  Removes all of the document mappings from the map, and deletes the document map file.
* `public static DocumentMap loadDocumentMap() throws IOException` :
  Loads the document map in LOOKUP mode into memory and returns a reference to it.
* `private static void queueDump(Indexer indexer, 
  Map<Integer, DocumentMapping> documents, 
  BufferedWriter writer)` :
  queues an IO task for dumping the new document mappings to the file.
* `private static void dump(Indexer indexer, `
  `Map<Integer, DocumentMapping> documents, `
  `BufferedWriter writer)` : 
  Writes the newly added mappings to the file according to the file format specified in the class documentation.
* `private static String getPath()` : 
  returns the path to the dictionary file as specified by Configuration.

### DocumentMapping Class

Holds the data of the documents mapping: 

- name - the name of the document (DOCNO)
- maxFrequency - frequency of the term or entity that is most frequent in the document
- length - number of terms or entities that appear the document (not unique)

### Indexer Class

Manages the indexing of the documents produced by the `Parser`

 indexing is done in two phases: 

- first a batch of documents is taken from the parser and then is inverted into into term -> document mappings and then those mappings are written into a single posting file.     
- after all the documents have been inverted they are all merged into a single inverted file where each line is a term -> documents mapping during this process a `Dictionary` and`DocumentMap`are created     in order to later retrieve information from the inverted file.



* `public void onFinishParser()` : 
  Callback meant to be used by the parser to notify the indexer that the last of the documents has been parsed and the indexer can now start entering it's second phase.
* `public void awaitIndex()` :
  Waits until all indexing is done. when this method returns all posting files are gone and the the inverted file, dictionary, document map are ready to be used.
* `public void index(Document document)` : Adds a document to the inverted index.
* `void queueInvert(LinkedList<Document> documents)` : 
  Queues an invert task for the given list of documents.
* `private void invert(LinkedList<Document> documents)` : 
  Inverts the given document list as described above and then queues a task to write the resulting map into a posting file.
  * `private void invertNumbers(int docId, PostingFile newPosting, Documetn document)` :
    inverts number terms.
  * `private void invertTerms(int docId, PostingFile newPosting, Documetn document)`:
    inverts terms that are not numbers or entities.
  * `private void invertEntities(int docId, PostingFile newPosting, Documetn document)` :
    inverts entity terms.
* `public int getTermCount()` : returns the number of terms in the dictionary.

### PostingCache Class

Manages the creation and deletion of posting files and the inverted file

* `static void initCache(Indexer cacheIndexer)` :
  Initializes the cache, after this method is called it is possible to start using the cache to create posting files and later an inverted file.
* `static Optional<PostingFile> newPostingFile()` : Posting file factory, creates new posting files.
* `static void queuePostingFlush(PostingFile postingFile)` : 
  Queues a flush of a posting file, this will write the posting file to the disk under a name matching it's id.
* `private static void flushPosting(int postingFileId, TermPosting[] postings)` :
  Flushes the posting file to the disk.
* `static void merge(Dictionary dictionary)` : Merges all the posting files into an inverted file. 
* `static void clean()` : Deletes all the posting files.
* `public static void deleteInvertedFile() throws IOException` : Deletes the inverted file.
* `private static String getPostingPath()` : get path to Posting file directory.
* `private static String getInvertedFilePath()` : get path to inverted file.
* `private static String getPostingFilePath(int postingFileID)` :
  get path to the posting file with the given id. 
  *note: this method does not guarantee that the file exists.*

### PostingFile Class

Represents a posting file while its in memory.

* `PostingFile(int postingFileID)` : Creates a posting file with the given id
* `public void addTerm(String term, int documentID, int documentFrequency)` :
  Adds a term -> document mapping to the posting file.
* `TermPosting[] getPostings()` : 
  returns an alphabetically sorted array of the term postings in the posting file.
* `public int getID()` : returns posting file id.
* `public void flush()` : Writes the posting file to a file.

### Term Class

Holds information about a term.

* `term`- string representation of the term.
* `termDocumentFrequency` - number of documents the term has appeared in.
* `termFrequency` : number of times the term occurred in the corpus.
* `pointer` : pointer to the terms line in the inverted file.

### TermPosting Class

Represents a terms posting (a line) in a posting file.

* `public TermPosting(String term)` : Constructs a term posting with the given term.
* `public void addDocument(int documentID,int termFrequency)` : 
  Add a document to the posting.
* `public String getTerm()` :  return the term of the posting.
* `public String toString()` : return a string containing all the contents of this term posting.



## Gui Package

### GUI Class

The GUI class is the application's entry point and not much more then that, it creates the stage and scene and then launches the application, the controller class handles the run of the application.

- `public void start(Stage primaryStage) throws Exception`
  the only method in this class, it sets up the application and launches it.

### Controller Class

This class *controls* the application during its runtime, most of its functionality is event handling, launching the engine and displaying its data.

*  `public void setStage(Stage stage)` :
  called when the application starts and the stage is initialized, serves as an init method for the controller.
* `private void updateOptions()` : updates the configurations of the application
* `public void onClickBrowseCorpus()` : 
  event, triggered when the user browses for the corpus path, updates the text field as well as updating the configuration with the selected corpus path.
* `public void onClickBrowseIndex()` :
  event, triggered when the user browses for the index path, updates the text field as well as updating the configuration with the selected index path.
* `public void onClickBrowseLog()` : 
  event, triggered when the user browses for the log path, updates the text field as well as updating the configuration with the selected log path.
* `public void onClickSaveOptions()` :
  event, triggered when the user clicks the save button, this saves the currently selected configuration to disk so it will be available in the next run of the application.
* `public void onClickRun()` : 
  event, triggered when the user clicks the run button , runs the parser/indexer and creates the inverted index files.
* `public void onClickReset()` : 
  event, triggered when the user clicks the reset button, clears the memory and disk of the dictionary and inverted file. 
* `public void onClickLoadDict()` : 
  event, triggered when the user clicks the load dictionary button, loads the dictionary into memory.
* `public void onClickShowDict()` : 
  event, triggered when the user clicks the show dictionary button, opens a window with a table view containing two columns, all the terms as they appear in the dictionary, and their frequencies in the corpus.
* `private void makeViewable()` : creates a sorted view of the dictionary.
* `private void showAlert(String title, String message)` : shows alert with given text and message.

### DictionaryEntry Class

This class represents a table entry in the dictionary table window

* `public DictionaryEntry(String term, int frequency)` : 
  Constructor, creates new entry with the given term and frequency.
* `public String getTerm()` : get the term in the entry.
* `public String getFrequency()` : get the frequency in the entry.
* `public void setTerm(String term)` : change the entry's term to a given term.
* `public void setFrequency(String term)` : change the entry's frequency to a given frequency.

## Util Package

### Configuration 

Scobo Engine Configuration manager. Handles the creation, loading, and updating of the engine's configuration.

* `public static Configuration getInstance()` : returns an instance of Configuration.
* `private void loadConfiguration()` : loads configuration file.
* `private void initConfiguration()` : initialize configuration file.
* `public void updateConfig()` :  
  In order for the configuration changes to persist this method must be called explicitly otherwise the changed configuration will only apply to the current run.
* `public void setCorpusPath(String path)` : 
  Changes the corpus path, this change only applies to the current run of the engine, and will not persist unless the `updateConfig()` method is called.
* `public void setIndexPath(String indexPath)` : 
  Changes the index path, this change only applies to the current run of the engine, and will not persist unless the `updateConfig()` method is called.
* `public void setParserBatchSize(int parserBatchSize)` : 
  Changes the parsers batch size, this change only applies to the current run of the engine, and will not persist unless the `updateConfig()` method is called.
* `public void setLogPath(String logPath)` : 
  Changes the path to the log file, this change only applies to the current run of the engine, and will not persist unless the `updateConfig()` method is called.
* `public void setUseStemmer(boolean useStemmer)` : 
  Changes weather or not the engine will use a stemmer, this change only applies to the current run of the engine, and will not persist unless the `updateConfig()` method is called.
* The following methods are getters for all the configurations :
  * `public String getCorpusPath()`
  * `public String getIndexPath()`
  * `public int getParserBatchSize()`
  * `public String getLogPath()`
  * `public boolean getUseStemmer()`
  * `public String getDictionaryPath()`
  * `public String getDocumentMapPath()`
  * `public String getInvertedFilePath()`
* `private String getUseStemmerPath()` : 
  returns the correct folder name for the index according to the value of `useStemmer` .

### CountLatch Class

A synchronization aid that allows one or more threads to wait until a set of operations being performed in other threads completes.  

The `CountLatch` is very similar to the `CountDownLatch` it provides the same functionality only adding the ability to `countUp()` in addition to to `CountDownLatch.countDown()`. 

The `CountLatch` is initialized to some count, the count may be thought of as the number of tasks/threads that need to complete before some other waiting threads can continue. The count can change through the `countDown()` and `countUp()`, whenever the count reaches 0 all the threads waiting using the `await()` method will be notified. 

 *The maximum count is `Long.MAX_VALUE`*.

* `public CountLatch(long initialCount)` : 
  Creates a `CountLatch` with a specified initial count.

* `public void await() throws InterruptedException` :Causes the current thread to wait until the latch has counted down to zero, unless the thread is interrupted.  

  If the current count is zero then this method returns immediately.  

  If the current count is greater than zero then the current thread becomes disabled for thread scheduling purposes and lies dormant until one of two things happen: 

  - The count reaches zero due to invocations of the `countDown()` method; or 
  - Some other thread interrupts the current thread. 

  If the current thread: 

  - has its interrupted status set on entry to this method; or 
  - is interrupted while waiting, 

   then `InterruptedException` is thrown and the current thread's interrupted status is cleared. 

* `public long countUp()` : Increments the count of the latch.
* `public long countUp(int delta)`  : Adds `delta` to the count of the latch.
* `public long countDown()`  : 
  Decrements the count of the latch, releasing all waiting threads if the count reaches zero.

### Logger Class

Simplistic Logger class. This class is a singleton, as such in order to use it call [`getInstance()`](file:///home/shoham/Dev/Scobo-Engine/doc/JavaDoc/util/Logger.html#getInstance()) Supports Three logging levels : 

- MESSAGE - use `message(String)` to log message
- WARNING - use `warn(String)` to log warning
- ERROR - use `error(String)` to log error

The logs are not written to the log file instantly, this is done to improve performance and not force consecutive IO when it could be avoided. The logs will be written into the file when they reach a size of 1KB, otherwise to force the logger to write to the log file use `flushLog()`.

* `public static Logger getInstance()` : get instance of the logger class.

* `public void message(String message)` : 
  Logs a message in the format: [ TIME ] [ MESSAGE ]: message
  Messages are intended as part of the regular operation of the program, and as a tool to output information such as debug data timings and more. 

* `public void warn(String warning)` : 
  Logs a warning in the format: [ TIME ] [ WARNING ]: warning
  Warnings are intended as errors or exceptions that the program can recover from and continue execution.

* `public void warn(Exception exception)`  :

  Logs a warning in the format: [ TIME ] [ WARNING ]: warning
  Warnings are intended as errors or exceptions that the program can recover from and continue execution.

* `public void error(String error)` : 

  Logs a error in the format: [ TIME ] [ ERROR ]: error
  Errors are intended as problems or exceptions that the program can *not* recover from.

* `public void error(Exception exception)` :

  Logs a error in the format: [ TIME ] [ ERROR ]: error
  Errors are intended as problems or exceptions that the program can *not* recover from.

* `private void logException(Exception exception, StringBuilder stringBuilder)` :
  formats the exception into a string and adds it to the log buffer.
* `public void flushLog()` : 
  Flushes the log buffer into the log file, this method should be called when the logger is about to be destroyed or othera comparable runnable that can have a priority of execution.wise become unavailable.
* `private void tryFlushLog(int logSizeBytes)` : 
  checks if the log buffer has become large enough to flush if so flush it.
* `private String getTime()` : creates a formatted string of the current date and time.

### TaskExecutor Class

Thread pool used to execute tasks with varying priority. This implementation is practically identical to `ThreadPoolExecutor` apart from the ability to execute tasks with a given priority.

* `TaskExecutor(int poolSize, int initialSize)` : 
  Creates new `TaskExecutor` with the given pool size and initial size.
* `public void execute(Runnable runnable)` :
  Enqueues a task with default priority to be executed when there is an available thread.
* `public void execute(Runnable runnable, int priority)` : 
  Enqueues a task with default priority to be executed when there is an available thread.
* `private static Task taskOf(Runnable runnable, int priority)` :
  creates a task from the given runnable and priority.
* `Task` interface - a comparable runnable that can have a priority of execution.

### TaskGroup Class

Encapsulates a group of tasks in order to be able to reason about them as one unit. Task Groups should be used when there is a clear grouping of tasks that can all be added to the group before all the added tasks are completed.
 Example Usage: 

```
     //some CPU tasks that need to be executed
     Runnable[] CPUTasks = getCPU();
     //some IO tasks that need to be executed
     Runnable[] IOTasks = getIO();

     // create CPU task group
     TaskGroup CPUGroup = TaskManager.getTaskGroup(GroupType.IO);
     //create IO task group
     TaskGroup IOGroup = TaskManager.getTaskGroup(GroupType.COMPUTE);

     // open the group to ensure that awaitCompletion works as intended.
     CPUGroup.openGroup();
     // add the CPU tasks to the CPU group
     for (Runnable r : CPUTasks)
          CPUGroup.add(r);
     CPUGroup.closeGroup();

     // open the group to ensure that awaitCompletion works as intended.
     IOGroup.openGroup();
     // add the IO tasks to the IO group
     for (Runnable r : IOTasks)
          IOGroup.add(r);
     CPUGroup.closeGroup();

     CPUGroup.awaitCompletion();
     System.out.println("all CPU tasks have been completed");

     IOGroup.awaitCompletion();
     System.out.println("all IO tasks have been completed");
 
```



* `TaskGroup(TaskManager manager, TaskType type, TaskPriority priority)` :
  Creates a task group that will be managed by the provided manager with the given type and priority.
* `public void add(Runnable task)`  :
  Adds a task to the group and schedules it through the `TaskManager` as a task matching the groups type.
* `public void add(Collection<? extends Runnable> tasks)` :
  Adds a collection of tasks to the group and schedules them through the`TaskManager` as a task matching the groups type.  
  Calling `awaitCompletion()` after using this method ensures that all the tasks added will complete before the thread calling `awaitCompletion()` will be notified.
* `public void openGroup()` : 
  Ensures that if a threads calls `awaitCompletion()` on this group, it will not be notified before `closeGroup()` is called. 
   This is intended in order to ensure that a batch of tasks that cannot be executed using `add(Collection)` will all be executed before a thread calling `awaitCompletion()` is notified.  This is done by calling `openGroup` before calling `awaitCompletion()` then adding the tasks to the group, and calling `closeGroup()` when all tasks have been added. (see the example in the class documentation) 
* `public void closeGroup()` : 
  Notifies the group that all tasks have been added and threads that are waiting using `awaitCompletion()` will be notified once the tasks group is empty. 
  Calling `closeGroup` more than once, has no effect.
* `public void complete()` : 
  notifies the task group that a task has completed. calling this method is required when a task finishes in order to update the `awaitCompletion()` 
* `public void awaitCompletion()` : 
  Causes the calling thread to wait until every task that was added to the group using `add(Runnable)` has called the  `complete()` method.      
  *The calling thread may be notified before all the planned tasks for this group have completed.* 
  for example if there was a delay in adding tasks and tasks 1,2,3 were completed before task 4 could be added to the group. When using Task Group take care to have a clear batch of tasks that can be executed. 
  In order to be assured this issue does not arise use `openGroup()`

  ### TaskManager Class

Simple task manager, servers as basically a wrapper for Executor service. This class implements the singleton pattern, in order to attain an instance use `getInstance()`.

You may also attain an instance of `TaskGroup` using  it is recommended to use TaskGroups when it is needed to treat a group of tasks as one unit e.g - needing to wait for a batch of task to complete. 

* `public static TaskManager getInstance()` : return instance of `TaskManager` class.
* `public static TaskGroup getTaskGroup(TaskType type)` : 
  Creates a Task Group that can be used to execute tasks as part of group and treat all the tasks executed through the group as a single unit. 
   *The Task group will have MEDIUM priority*
* `public static TaskGroup getTaskGroup(TaskType type, TaskPriority priority)` : Creates a Task Group that can be used to execute tasks as part of group and treat all the tasks executed through the group as a single unit. 
   *The Task group will have MEDIUM priority*
* `public void executeIO(Runnable task, int priority)` :
  Enqueues the task into the IO task queue, the task will execute when its turn arrives.
* `public void executeCPU(Runnable task, int priority)` :
  Enqueues the task into the CPU task queue, the task will execute when its turn arrives.
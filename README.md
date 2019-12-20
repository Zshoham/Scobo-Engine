# Scobo-Engine

authors: shoham zarfati, hod twito

This is our project for the course Information Retrieval 

## User Guide

To launch the application go to `Scobo/bin` there you will find runnable files for Linux and windows, to run the application on windows double click the `windows_runnable.sh` file, to run the application on Linux enter the command `./linux_runnable.sh`

*note: in order to run the application on windows java 1.8.131 must be installed, on Linux newer java versions are supported only if javafx matching the java version is installed in `usr/share/openjfx/lib`*

After launching the application you will see a user interface that is split into two parts - Options in the upper area and actions in th lower area.

Options include :

* corpus path - the path to a folder containing the corpus the engine will be run on, the folder must contain 
  * a folder called "corpus" containing folders in which the files that will be parsed are found.
  * a file called "stop_words.txt" containing a line break separated list of words the engine should ignore 
* index path - path to a folder where the engine will create its inverted index (at the end of the run this will include three files - dictionary file, document map file, and inverted index) this folder must be able to hold data equal to about 50% of the original corpus size.
* use stemmer - if selected the engine will use a stemmer to try and normalize the terms it creates.
* log path - path to a file that will contain the engine run log
* parser batch size - the number of files the engine will read from disk at once for parsing 
* save - will save the selected configuration to be loaded at the next run of the application.

Actions include :

* run - runs the parser/indexer with the selected options.
* reset - deletes all files the engine created in the run and clears the RAM.
* load dictionary - loads the dictionary into memory.
* show dictionary - opens a window that will show a table of terms and their frequencies sorted alphabetically by the terms.
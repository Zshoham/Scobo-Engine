# Scobo Engine Architecture

Scobo can be divided into four modules - three core modules and a UI modules, these modules have a mostly hierarchical relationship and thus we will look at them as layers.

The core modules are **Parse**, **Index**, **Query**, where the first two modules are separate as they are used in the initial setup of the engine in order to create the necessary data to later be searched, and the Query module is used to search the data and retrieve results. finally the **UI module** is used to facilitate user communication with the other layers.

![](diagrams/Abstract Layers.png)

## Parse Module

The Parse module receives the path to the corpus as input and is responsible for creating all the terms and their statistics that will be used later for indexing and for Querying.

The Parse module is divided into two main parts, the first is the ReadFile submodule that is responsible for asynchronously taking multiple files from the corpus and splitting them up into documents these documents are then given to the DocumentParse submodule that takes a document and updates all the terms appearing in it and creates new terms accordingly.

![](diagrams/Parse Module.png)



## Index Module

We receive a `<Document, TermList>` pair, we put the pair into a document buffer that will hold up to some k of these pairs, once there are k pairs in the buffer, we dispatch k tasks to the task manager to invert these documents.

The inverters each have a `<Document, TermList>` they iterate the terms, if the term is in the dictionary then  we load the corresponding posting file and add the new document to the list, for all the terms that do not appear in the Dictionary we construct a new posting file and put all the new terms in it.
This approach grants the guarantee of at most T/k posting files, T being the number of terms in the corpus later if we find that there are abnormally small posting files they can be merged, additionally a small posting file could be wait in memory for another small file to be created and they would be merged immediately.


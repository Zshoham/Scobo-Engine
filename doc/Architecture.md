# Scobo Engine Architecture

Scobo can be divided into four modules - three core modules and a UI modules, these modules have a mostly hierarchical relationship and thus we will look at them as layers.

The core modules are **Parse**, **Index**, **Query**, where the first two modules are separate as they are used in the initial setup of the engine in order to create the necessary data to later be searched, and the Query module is used to search the data and retrieve results. finally the **UI module** is used to facilitate user communication with the other layers.

<img src="E:\Scobo-Engine\doc\diagrams\Abstract Layers.png" style="zoom:150%;" />

## Parse Module

The Parse module receives the path to the corpus as input and is responsible for creating all the terms and their statistics that will be used later for indexing and for Querying.

The Parse module is divided into two main parts, the first is the ReadFile submodule that is responsible for asynchronously taking multiple files from the corpus and splitting them up into documents these documents are then given to the DocumentParse submodule that takes a document and updates all the terms appearing in it and creates new terms accordingly.

<img src="E:\Scobo-Engine\doc\diagrams\Parse Module.png" style="zoom:100%;" />
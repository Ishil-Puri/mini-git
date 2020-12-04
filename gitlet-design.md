# Gitlet Design Document

**Ishil Puri**:

## Classes and Data Structures
#### Main
Input handler. Calls relevant functions  from repository.

####Repository
#####The driver class which executes input commands
* Static path variables
* HEAD string keeps track of head commit hash
* StagingAdd Hashmap for added items (name, sha id)
* StagingRm TreeSet for removed items
 

#### Commit
##### Instance Variables
* Message – contains the message of commit
* Timestamp – time at which a commit was created. Assigned by the constructor.
* Parent – the parent commit of the commit object

## Algorithms
#### Commits Class
1. method that uses sha 1 cryptographic hash (known for low collisions) and assigns to the commit
2. Must save state of new files to .gitlet folder for historical storage

#### Main Class
1. Role of input handler. Accept commands:
<br>`init` which creates a new version control system in the current working directory. There mustn't already exist a VCS in CWD. 
<br>`add [file name]` adds a copy of the file in current state to the staging area. If the file already on staging area, overwrite it.
<br>`commit [message]` save a snapshot of current files that have been placed in the staging area. Once committed, clear staging area. Only file to be modified by this operation is .gitlet. Commit must contain metadata (date and time, log message) 
<br>`rm [file name]` Un-stage a file if it is currently being staged for addition then remove the file from current working directory.
<br>`log` Display info for each commit
<br>`global-log` display all commits and corresponding information
<br> `find [commit message]` prints all IDs of every commit that have specified commit message
<br> `status` display current branch and all other branches
<br> `checkout (([commit id])-- [file name]) ([branch name])` Overwrites current file with different version.
<br> `branch [branch name]` create new branch with given name
<br> `rm-branch [branch name]` delete given branch
<br> `reset [commit id]` checkout all files in given commit. Move current branch head to that commit
<br> `merge [branch name]` merge files from given branch to current branch

## Persistence
In gitlet, we must retain information from previous commits as well as store it after the program terminates.
<br><br>To persist every commit made, including all versions of file contents. To do so, we must serialize 
1. Write the contents of staged files to disk. Serialize the file into a byte stream that will have an ID. This ID will be referenced in the future to retrieve if needed for deserialization.
2. Write the blob to disk. Use write object method from the Utils class to serialize the blob object and write to disk. This requires implementing the serializable interface.

In order to retrieve previous states, or commits, we must search for the serialized version of the file in the current working directory to load the objects in case of checkouts or resetting etc. ReadObject method will allow for deserialization of the object.
package gitlet;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.TreeSet;
import java.util.HashSet;
import java.util.Deque;
import java.util.ArrayDeque;
import java.util.Arrays;

/** Class that implements gitlet commands.
 * @author Ishil Puri
 */
public class Repository implements Serializable {

    /** Initializes gitlet repo. */
    public void init() throws IOException {
        Commit initial = new Commit("initial commit", "");
        if (!GITFOLDER.mkdir()) {
            throw Utils.error("A Gitlet version-control system"
                    + " already exists in the current directory.");
        }
        setupPersistence();
        setHead("master", initial.saveCommit());
        _currentBranch = "master";
    }

    /** Setup file system.
     * .gitlet/ -- top level folder for all persisting data
     *   - branches/ -- folder containing all data for branches
     *     - master -- text file containing String hash for current commit
     *   - head -- text file containing String hash for current commit
     *   - commits/ -- folder containing all data for commits
     * */
    private void setupPersistence() {
        try {
            BRANCHESFOLDER.mkdir();
            master.createNewFile();
            COMMITSFOLDER.mkdir();
            STAGINGAREA.mkdir();
            BLOBS.mkdir();
            repoObj.createNewFile();
        } catch (IOException e) {
            System.out.println("file or folder already exists");
        }
    }

    /** Add file for staging.
     * @param fileName File to be staged
     */
    public void add(String fileName) {
        File fileToAdd = Utils.join(CWD, fileName);
        if (!fileToAdd.exists()) {
            throw Utils.error("File does not exist.");
        }
        byte[] contents = Utils.readContents(fileToAdd);
        String fileUid = getUID(fileToAdd);
        Commit head = commitFromFile(getHEAD());
        boolean currCommitHas = head.getTracking().containsKey(fileName)
                && head.getTracking().get(fileName).equals(fileUid);

        if (currCommitHas && _stagingAdd.containsKey(fileName)) {
            Utils.join(STAGINGAREA, fileName).delete();
            _stagingAdd.remove(fileName);
            return;
        } else if (currCommitHas && _stagingRm.contains(fileName)) {
            _stagingRm.remove(fileName);
            return;
        } else if (currCommitHas || (_stagingAdd.containsKey(fileName)
                && _stagingAdd.get(fileName).equals(fileUid))) {
            return;
        }
        Utils.writeContents(Utils.join(STAGINGAREA, fileName), contents);
        _stagingAdd.put(fileName, fileUid);
    }

    /** Remove file.
     * @param fileName name of file
     */
    public void rm(String fileName) {
        if (!_stagingRm.contains(fileName) && !_stagingAdd.containsKey(fileName)
                && !commitFromFile(getHEAD()).getTracking().
                containsKey(fileName)) {
            throw Utils.error("No reason to remove the file.");
        }
        if (_stagingAdd.containsKey(fileName)) {
            Utils.join(STAGINGAREA, fileName).delete();
            _stagingAdd.remove(fileName);
        }
        if (commitFromFile(getHEAD()).getTracking().containsKey(fileName)) {
            Utils.restrictedDelete(fileName);
            _stagingRm.add(fileName);
        }
    }

    /** Commit staged files.
     * @param message Commit message
     */
    public void commit(String message) throws IOException {
        if (Utils.plainFilenamesIn(STAGINGAREA).size() == 0
                && _stagingRm.size() == 0) {
            throw Utils.error("No changes added to the commit.");
        } else if (message.length() == 0) {
            throw Utils.error("Please enter a commit message.");
        }
        Commit commitObj = new Commit(message, getHEAD());
        commitObj.getTracking().putAll(commitFromFile(getHEAD()).getTracking());
        for (File f : STAGINGAREA.listFiles()) {
            commitObj.getTracking().put(f.getName(), getUID(f));
            Utils.writeContents(Utils.join(BLOBS, getUID(f)),
                    Utils.readContents(f));
        }
        for (String rmFile : _stagingRm) {
            commitObj.getTracking().remove(rmFile);
        }
        clearStagingArea();
        setHead(_currentBranch, commitObj.saveCommit());
    }

    /** @param args arguments from main
     * 1. get file from head commit
     * 2. get file from specified commit
     * 3. switch branch
     */
    public void checkout(String[] args) {
        if (args.length == 2 && args[0].equals("--")) {
            updateCWD(getHEAD(), args[1]);
        } else if (args.length == 3 && args[1].equals("--")) {
            updateCWD(lazySearch(args[0]), args[2]);
        } else if (args.length == 1) {
            Commit curr = commitFromFile(getHEAD());
            validateSwitch(curr, args[0]);
            Commit branchHeadObj = commitFromFile(Utils.readContentsAsString(
                    Utils.join(BRANCHESFOLDER, args[0])));
            checkoutFullCommit(curr, branchHeadObj);
            _currentBranch = args[0];
        } else {
            throw Utils.error("Incorrect operands.");
        }
    }

    /** Checks out all files from a specific commit.
     * @param branchHead branch commit obj
     * @param curr current commit obj
     */
    private void checkoutFullCommit(Commit curr, Commit branchHead) {
        for (String fileName: curr.getTracking().keySet()) {
            if (!branchHead.getTracking().containsKey(fileName)) {
                Utils.restrictedDelete(Utils.join(CWD, fileName));
            }
        }
        for (String fileName : branchHead.getTracking().keySet()) {
            updateCWD(branchHead.getCommitUID(), fileName);
        }
        clearStagingArea();
    }

    /** Ensure it is okay to switch branches, else throw errors.
     * @param curr Current commit obj
     * @param bName Name of given branch
     */
    private void validateSwitch(Commit curr, String bName) {
        if (bName.equals(_currentBranch)) {
            throw Utils.error("No need to checkout the current branch.");
        } else if (!Utils.plainFilenamesIn(BRANCHESFOLDER).contains(bName)) {
            throw Utils.error("No such branch exists.");
        }
        Commit branchHeadObj = commitFromFile(Utils.readContentsAsString(
                Utils.join(BRANCHESFOLDER, bName)));
        checkUntracked(curr, branchHeadObj);
    }

    /** Check for untracked files.
     * @param curr Current commit obj
     * @param dest Given commit obj
     */
    private void checkUntracked(Commit curr, Commit dest) {
        for (File f : CWD.listFiles()) {
            if (!curr.getTracking().containsKey(f.getName())) {
                if (dest.getTracking().containsKey(f.getName())) {
                    throw Utils.error("There is an un-tracked file in the way;"
                            + " delete it, or add and commit it first.");
                }
            }
        }
    }

    /** Helper method for checkout.
     * @param cID commit id for proper version
     * @param fileName name of file
     */
    private void updateCWD(String cID, String fileName) {
        File f = Utils.join(CWD, fileName);
        String blobID = getBlobID(cID, fileName);
        Utils.restrictedDelete(f);
        Utils.writeContents(f, Utils.readContents(Utils.join(BLOBS, blobID)));
    }

    /** Check out all files tracked by given commit.
     * @param cID Commit id
     */
    public void reset(String cID) {
        Commit obj = commitFromFile(lazySearch(cID));
        checkUntracked(commitFromFile(getHEAD()), obj);
        checkoutFullCommit(commitFromFile(getHEAD()), obj);
        setHead(_currentBranch, obj.getCommitUID());
    }

    /** Creates new branch with given name.
     * @param branchName branch name
     */
    public void branch(String branchName) {
        File b = Utils.join(BRANCHESFOLDER, branchName);
        if (b.exists()) {
            throw Utils.error("A branch with that name already exists.");
        }
        Utils.writeContents(b, getHEAD());
    }

    /** Display info for each commit starting at head going backwards. */
    public void log() {
        Commit curr = commitFromFile(getHEAD());
        while (!curr.getParent().equals("")) {
            printLog(curr);
            curr = commitFromFile(curr.getParent());
        }
        printLog(curr);
    }

    /** Display info of every commit ever made. */
    public void globalLog() {
        for (String cID : Utils.plainFilenamesIn(COMMITSFOLDER)) {
            printLog(commitFromFile(cID));
        }
    }

    /** Print out IDs of commits with given message.
     * @param message Given commit message
     */
    public void find(String message) {
        boolean found = false;
        for (String cID : Utils.plainFilenamesIn(COMMITSFOLDER)) {
            if (commitFromFile(cID).getMessage().equals(message)) {
                System.out.println(cID);
                found = true;
            }
        }
        if (!found) {
            throw Utils.error("Found no commit with that message.");
        }
    }

    /** Display current status of repository. */
    public void status() {
        branchStatus();
        addFileStatus();
        rmFileStatus();
        modificationsStatus();
        untrackedStatus();
    }

    /** Delete branch with given name.
     * @param branchName Name of branch
     */
    public void rmBranch(String branchName) {
        File branch = Utils.join(BRANCHESFOLDER, branchName);
        if (!branch.exists()) {
            throw Utils.error("A branch with that name does not exist.");
        } else if (branchName.equals(_currentBranch)) {
            throw Utils.error("Cannot remove the current branch.");
        }
        branch.delete();
    }

    /** Print branch status. */
    private void branchStatus() {
        printHeader("Branches");
        for (String branchName : Utils.plainFilenamesIn(BRANCHESFOLDER)) {
            if (branchName.equals(_currentBranch)) {
                System.out.print("*");
            }
            System.out.println(branchName);
        }
        System.out.println();
    }

    /** Print status of files staged for addition. */
    private void addFileStatus() {
        printHeader("Staged Files");
        for (String fileName : Utils.plainFilenamesIn(STAGINGAREA)) {
            System.out.println(fileName);
        }
        System.out.println();
    }

    /** Print status of files staged for removal. */
    private void rmFileStatus() {
        printHeader("Removed Files");
        for (String elem : _stagingRm) {
            System.out.println(elem);
        }
        System.out.println();
    }

    /** Print modification status. */
    private void modificationsStatus() {
        printHeader("Modifications Not Staged For Commit");
        Commit curr = commitFromFile(getHEAD());
        TreeSet<String> fileNamesInCommit = new TreeSet<>(curr.
                getTracking().keySet());
        for (String key : fileNamesInCommit) {
            if (!Utils.join(CWD, key).exists() && !_stagingRm.contains(key)) {
                System.out.println(key + " (deleted)");
                continue;
            }
            if (Utils.join(CWD, key).exists() && !getBlobID(curr.
                    getCommitUID(), key).equals(getUID(Utils.join(CWD, key)))) {
                System.out.println(key + " (modified)");
            }
        }
        System.out.println();
    }

    /** Print status of untracked files. */
    private void untrackedStatus() {
        printHeader("Untracked Files");
        Commit curr = commitFromFile(getHEAD());
        for (File f : CWD.listFiles()) {
            if (!f.isDirectory()) {
                boolean inCurr = curr.getTracking().containsKey(f.getName());
                if (!inCurr && (!_stagingAdd.containsKey(f.getName())
                        || _stagingRm.contains(f.getName()))) {
                    System.out.println(f.getName());
                }
            }
        }
        System.out.println();
    }

    /** Merge files from given branch into current branch.
     * @param otherName Given branch to be merged
     */
    public void merge(String otherName) throws IOException {
        Commit curr = commitFromFile(getHEAD());
        Commit other = validateMerge(curr, otherName);
        Commit split = findSplitPt(curr, other);
        conflict = false;
        if (split.getCommitUID().equals(getHEAD())) {
            checkout(new String[] {otherName});
            System.out.println("Current branch fast-forwarded");
            return;
        } else if (split.getCommitUID().equals(Utils.readContentsAsString(Utils.
                join(BRANCHESFOLDER, otherName)))) {
            System.out.println("Given branch is an ancestor of the current"
                    + " branch.");
            return;
        }
        noConflict(curr, other, split);

        HashSet<String> allFiles = new HashSet<>();
        allFiles.addAll(other.getTracking().keySet());
        allFiles.addAll(curr.getTracking().keySet());
        allFiles.addAll(split.getTracking().keySet());
        for (String fileName : allFiles) {
            boolean currHasFile = curr.getTracking().containsKey(fileName);
            boolean otherHasFile = other.getTracking().containsKey(fileName);
            boolean splitHasFile = split.getTracking().containsKey(fileName);
            boolean scenario1 = !currHasFile && otherHasFile;
            boolean scenario2 = !otherHasFile && currHasFile;
            boolean scenario3 = otherHasFile && currHasFile;

            if (!splitHasFile) {
                if (scenario3) {
                    if (isModified(fileName, other, curr)) {
                        replaceConflict(fileName, curr, other);
                    }
                }
            } else {
                if (scenario1) {
                    if (isModified(fileName, split, other)) {
                        replaceConflict(fileName, null, other);
                    }
                }
                if (scenario2) {
                    if (isModified(fileName, split, curr)) {
                        replaceConflict(fileName, curr, null);
                    }
                }
                if (scenario3) {
                    if (isModified(fileName, split, curr)
                            && isModified(fileName, curr, other)) {
                        replaceConflict(fileName, curr, other);
                    }
                }
            }
        }
        mergeCommit("Merged " + otherName + " into " + _currentBranch
                + ".", other.getCommitUID());
        if (conflict) {
            System.out.println("Encountered a merge conflict.");
        }
    }

    /** Check if blobs btwn 2 commits differ.
     * @param fileName Name of file to be checked from blobs of a and b
     * @param a Commit a
     * @param b Commit b
     * @return Whether the two blobs differ
     */
    private boolean isModified(String fileName, Commit a, Commit b) {
        byte[] contentA = Utils.readContents(Utils.
                join(BLOBS, getBlobID(a.getCommitUID(), fileName)));
        byte[] contentB = Utils.readContents(Utils.
                join(BLOBS, getBlobID(b.getCommitUID(), fileName)));
        return !Arrays.equals(contentA, contentB);
    }

    /** Do stuff required for merge with no conflicts.
     * @param curr current branch head
     * @param other given branch head
     * @param split split point
     */
    private void noConflict(Commit curr, Commit other, Commit split) {
        for (String fileName : other.getTracking().keySet()) {
            boolean splitHasFile = split.getTracking().containsKey(fileName);
            boolean currHasFile = curr.getTracking().containsKey(fileName);
            if (!splitHasFile && !currHasFile) {
                setAndStage(other, fileName);
            } else if (currHasFile && !isModified(fileName, curr, split)
                && isModified(fileName, other, split)) {
                setAndStage(other, fileName);
            }
        }
        for (String fileName : split.getTracking().keySet()) {
            boolean currHasFile = curr.getTracking().containsKey(fileName);
            boolean otherHasFile = other.getTracking().containsKey(fileName);
            if (currHasFile && !isModified(fileName, curr, split)
                    && !otherHasFile) {
                rm(fileName);
            }
        }
    }

    /** Specialized commit for merge.
     * @param message commit msg
     * @param pID parent id
     * @throws IOException for save commit
     */
    private void mergeCommit(String message, String pID) throws IOException {
        if (Utils.plainFilenamesIn(STAGINGAREA).size() == 0
                && _stagingRm.size() == 0) {
            throw Utils.error("No changes added to the commit.");
        } else if (message.length() == 0) {
            throw Utils.error("Please enter a commit message.");
        }
        Commit commitObj = new Commit(message, getHEAD());
        commitObj.getTracking().putAll(commitFromFile(getHEAD()).getTracking());
        for (File f : STAGINGAREA.listFiles()) {
            commitObj.getTracking().put(f.getName(), getUID(f));
            Utils.writeContents(Utils.join(BLOBS, getUID(f)),
                    Utils.readContents(f));
        }
        for (String rmFile : _stagingRm) {
            commitObj.getTracking().remove(rmFile);
        }
        commitObj.setMergeParent(pID);
        clearStagingArea();
        setHead(_currentBranch, commitObj.saveCommit());
    }

    /** Replace conflicting files with appropriate format.
     * @param fileName File to be replaced
     * @param a Commit obj a
     * @param b Commit obj b
     */
    private void replaceConflict(String fileName, Commit a, Commit b) {
        String contentA = a == null ? "" : Utils.readContentsAsString(Utils.
                join(BLOBS, getBlobID(a.getCommitUID(), fileName)));
        String contentB = b == null ? "" : Utils.readContentsAsString(Utils.
                join(BLOBS, getBlobID(b.getCommitUID(), fileName)));
        String concatContent = "<<<<<<< HEAD\n" + contentA + "=======\n"
                + contentB + ">>>>>>>\n";
        Utils.writeContents(Utils.join(CWD, fileName), concatContent);
        add(fileName);
        conflict = true;
    }


    /** Checkout and stage for addition.
     * @param obj Commit where file is pulled from
     * @param fileName File to be set and staged
     */
    private void setAndStage(Commit obj, String fileName) {
        checkout(new String[] {obj.getCommitUID(), "--", fileName});
        add(fileName);
    }

    /**
     * @param curr Current branch head commit
     * @param other Other branch head commit
     * @return Split point of given branches
     */
    private Commit findSplitPt(Commit curr, Commit other) {
        HashSet<String> otherAncestors = new HashSet<>();
        findAllAncestors(other.getCommitUID(), otherAncestors);
        return findLatest(otherAncestors, curr);
    }

    /**
     * @param set Set of ancestors to compare
     * @param c Commit to be checked
     * @return Latest common ancestor
     */
    private Commit findLatest(HashSet<String> set, Commit c) {
        Deque<String> deque = new ArrayDeque<String>();
        deque.add(c.getCommitUID());
        while (!deque.isEmpty()) {
            String match = deque.poll();
            if (set.contains(match)) {
                return commitFromFile(match);
            } else {
                deque.addAll(c.getParentList());
            }
        }
        return null;
    }

    /** Adds all ancestors for given branch.
     * @param cID Given commit id
     * @param set Set of ancestors
     */
    private void findAllAncestors(String cID, HashSet<String> set) {
        set.add(cID);
        for (String parentID : commitFromFile(cID).getParentList()) {
            findAllAncestors(parentID, set);
        }
    }

    /** @return Commit obj after checking if merge is possible
     * @param curr Current commit obj
     * @param other Name of other branch
     */
    private Commit validateMerge(Commit curr, String other) {
        if (!Utils.plainFilenamesIn(BRANCHESFOLDER).contains(other)) {
            throw Utils.error("A branch with that name does not exist.");
        } else if (other.equals(_currentBranch)) {
            throw Utils.error("Cannot merge a branch with itself.");
        } else if (_stagingAdd.size() > 0 || _stagingRm.size() > 0) {
            throw Utils.error("You have uncommitted changes.");
        }
        Commit otherHead = commitFromFile(Utils.readContentsAsString(Utils.
                join(BRANCHESFOLDER, other)));
        checkUntracked(curr, otherHead);
        return otherHead;
    }

    /* ------------------- Helper Methods ------------------- */
    /** @return Blob ID
     * @param cID Commit id
     * @param fileName Name of file
     */
    private String getBlobID(String cID, String fileName) {
        String blobID = commitFromFile(cID).getTracking().get(fileName);
        if (blobID == null) {
            throw Utils.error("File does not exist in this commit");
        }
        return blobID;
    }

    /** Set branch and head to point to new id.
     * @param branchName Name of branch
     * @param cID Commit sha id
     */
    private void setHead(String branchName, String cID) {
        Utils.writeContents(Utils.join(BRANCHESFOLDER, branchName), cID);
    }

    /** Clears staging area directory and add/Rm hashmap,treeSet. */
    private void clearStagingArea() {
        for (File f : STAGINGAREA.listFiles()) {
            f.delete();
        }
        _stagingAdd.clear();
        _stagingRm.clear();
    }

    /** @return linear search for full commit id.
     * @param partial Takes in partial cID
     */
    private String lazySearch(String partial) {
        for (String cID : Utils.plainFilenamesIn(COMMITSFOLDER)) {
            if (cID.startsWith(partial)) {
                return cID;
            }
        }
        return partial;
    }

    /** @param c Commit object for metadata usage */
    private void printLog(Commit c) {
        System.out.println("===");
        System.out.println("commit " + c.getCommitUID());
        if (c.getParentList().size() > 1) {
            String first = c.getParent().substring(0, 7);
            String second = c.getParentList().get(1).substring(0, 7);
            System.out.println("Merge: " + first + " " + second);
        }
        System.out.println("Date: " + c.getTimestamp());
        System.out.println(c.getMessage());
        System.out.println();
    }

    /**  Status helper function.
     * @param str title */
    private void printHeader(String str) {
        System.out.println("=== " + str + " ===");
    }

    /** @return sha1 for appropriate file object
     * @param f File f to be serialized
     */
    private String getUID(File f) {
        return Utils.sha1(Utils.serialize(Utils.readContents(f)));
    }

    /** @return head commit hash */
    public String getHEAD() {
        return Utils.readContentsAsString(Utils.
                join(BRANCHESFOLDER, _currentBranch));
    }

    /** @return commit object from storage
     * @param cID Sha1 id for commit obj
     */
    public Commit commitFromFile(String cID) {
        File c = Utils.join(COMMITSFOLDER, cID);
        if (!c.exists()) {
            throw Utils.error("No commit with that id exists.");
        }
        return Utils.readObject(c, Commit.class);
    }

    /** @return Path to master file */
    public static File getMaster() {
        return master;
    }

    /** @return Path to repo object file */
    public static File getRepoObj() {
        return repoObj;
    }

    /* ------------------- Instance variables ------------------- */
    /** Hashmap to store files staged for addition. */
    private HashMap<String, String> _stagingAdd = new HashMap<>();

    /** TreeSet to store files staged for removal. */
    private TreeSet<String> _stagingRm = new TreeSet<>();

    /** Keep track of current branch name. */
    private String _currentBranch;

    /* ----------------- Static class variables ------------------ */
    /** Merge conflict? */
    private static boolean conflict = false;

    /** Path for current working directory. */
    static final File CWD = new File(System.getProperty("user.dir"));

    /** Path for ".gitlet/". */
    static final File GITFOLDER = Utils.join(CWD, ".gitlet");

    /** Path for "branches/". */
    static final File BRANCHESFOLDER = Utils.join(GITFOLDER, "branches");

    /** Path for master file. */
    private static File master = Utils.join(BRANCHESFOLDER, "master");

    /** Path for "commits/". */
    static final File COMMITSFOLDER = Utils.join(GITFOLDER, "commits");

    /** Path for "stagingArea/". */
    static final File STAGINGAREA = Utils.join(GITFOLDER, "stagingArea");

    /** Path for "blobs/". */
    static final File BLOBS = Utils.join(GITFOLDER, "blobs");

    /** Path for repo object. */
    private static File repoObj = Utils.join(GITFOLDER, "repoObj");

}

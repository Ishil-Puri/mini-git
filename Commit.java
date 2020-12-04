package gitlet;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

/** Class to represent commit object.
 * @author Ishil Puri
 */
public class Commit implements Serializable {

    /**
     * Creates a commit object w/ relevant metadata.
     * @param msg Commit message
     * @param p Parent of this commit
     */
    public Commit(String msg, String p) {
        this.message = msg;
        this.parent = p;
        if (p .equals("")) {
            this.parentList = new ArrayList<>();
        } else {
            this.parentList.add(p);
        }
        SimpleDateFormat date = new SimpleDateFormat("EEE MMM dd"
                + " HH:mm:ss yyyy Z");
        if (this.parent.isEmpty()) {
            this.timestamp = date.format(new Date(0));
        } else {
            this.timestamp = date.format(new Date());
        }
    }

    /** Saves a commit to a file for future use.
     * @return sha1 of commit obj
     */
    public String saveCommit() throws IOException {
        String uid = Utils.sha1(Utils.serialize(this));
        File newCommit = Utils.join(Repository.COMMITSFOLDER, uid);
        newCommit.createNewFile();
        this.commitUID = uid;
        Utils.writeObject(newCommit, this);
        return uid;
    }

    /** @return commit msg */
    public String getMessage() {
        return message;
    }
    /** @return commit timestamp */
    public String getTimestamp() {
        return timestamp;
    }
    /** @return commit parent (hash) */
    public String getParent() {
        return parent;
    }
    /** @return commit sha1 id */
    public String getCommitUID() {
        return commitUID;
    }
    /** Set merge parent.
     * @param mergeParentID Commit id of merge parent
     */
    public void setMergeParent(String mergeParentID) {
        this.mergeParent = mergeParentID;
        this.parentList.add(mergeParentID);
    }

    /** @return Hashmap for tracking committed files name to hash */
    public HashMap<String, String> getTracking() {
        return tracking;
    }

    /** @return List of parents **/
    public ArrayList<String> getParentList() {
        return parentList;
    }

    /** Commit message field. */
    private String message;

    /** Commit time field. */
    private String timestamp;

    /** Commit sha1 id field. */
    private String commitUID;

    /** Parent commit field. */
    private String parent;

    /** Merge parent field. */
    private String mergeParent;

    /** List of all parents. */
    private ArrayList<String> parentList = new ArrayList<>();

    /** Map of files being tracked (name, UID). */
    private HashMap<String, String> tracking = new HashMap<>();

}

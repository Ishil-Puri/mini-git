package gitlet;

import java.io.IOException;
import java.util.Arrays;

/** Driver class for Gitlet, the tiny stupid version-control system.
 *  @author Ishil Puri
 */
public class Main {

    /** Usage: java gitlet.Main ARGS, where ARGS contains
     *  <COMMAND> <OPERAND> ....
     *  java gitlet.Main add hello.txt
     *  */
    public static void main(String... args) throws IOException {
        try {
            if (args.length == 0) {
                throw Utils.error("Please enter a command.");
            } else if (args[0].equals("init")) {
                repo.init();
            } else {
                loadRepo();
                switch (args[0]) {
                case "add":
                    repo.add(args[1]);
                    break;
                case "commit":
                    repo.commit(args[1]);
                    break;
                case "rm":
                    repo.rm(args[1]);
                    break;
                case "log":
                    repo.log();
                    break;
                case "global-log":
                    repo.globalLog();
                    break;
                case "find":
                    repo.find(args[1]);
                    break;
                case "status":
                    repo.status();
                    break;
                case "checkout":
                    repo.checkout(Arrays.copyOfRange(args, 1, args.length));
                    break;
                case "branch":
                    repo.branch(args[1]);
                    break;
                case "rm-branch":
                    repo.rmBranch(args[1]);
                    break;
                case "reset":
                    repo.reset(args[1]);
                    break;
                case "merge":
                    repo.merge(args[1]);
                    break;
                default:
                    throw Utils.error("No command with that name exists.");
                }
            }
            saveRepo();
        } catch (GitletException e) {
            System.out.println(e.getMessage());
            System.exit(0);
        }
    }

    /** Load repo object from saved state. */
    public static void loadRepo() {
        if (!Repository.getRepoObj().exists()) {
            throw Utils.error("Not in an initialized Gitlet directory.");
        }
        repo = Utils.readObject(Repository.getRepoObj(), Repository.class);
    }

    /** Save repo object to file for future use. */
    public static void saveRepo() {
        Utils.writeObject(Repository.getRepoObj(), repo);
    }

    /** Variable to track repo object. */
    private static Repository repo = new Repository();
}

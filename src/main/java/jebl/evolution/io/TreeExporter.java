package jebl.evolution.io;

import jebl.evolution.trees.Tree;

import java.util.Collection;

/**
 * @author Andrew Rambaut
 * @author Alexei Drummond
 *
 * @version $Id: TreeExporter.java 429 2006-08-26 18:17:39Z rambaut $
 */
public interface TreeExporter {

    /**
     * Export a single tree
     * @param tree
     */
    void exportTree(Tree tree);

    /**
     * Export a collection of trees
     * @param trees
     */
    void exportTrees(Collection<? extends Tree> trees);

    void close();
}

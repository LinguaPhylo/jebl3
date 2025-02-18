package jebl.evolution.trees;

import jebl.evolution.graphs.Edge;
import jebl.evolution.graphs.Node;
import jebl.evolution.taxa.Taxon;
import jebl.util.AttributableHelper;

import java.util.*;

/**
 * A simple, and initially immutable rooted tree implementation. All returned collections
 * are defensively copied. The implementation of Node is private. A number of methods are
 * provided that can be used to construct a tree (createExternalNode &amp; createInternalNode).
 *
 * @author Andrew Rambaut
 * @author Alexei Drummond
 * @version $Id: SimpleRootedTree.java 1045 2010-01-13 03:09:14Z stevensh $
 */
final public class SimpleRootedTree implements RootedTree {

    public SimpleRootedTree() {
    }

    /**
     * Make a copy of the given rooted tree
     * @param tree a rooted tree
     */
    public SimpleRootedTree(RootedTree tree) {
        createNodes(tree, tree.getRootNode());
    }

    /**
     * Make a copy of the given rooted tree
     * @param tree a rooted tree
     * @param nodeMapping store {source tree node -> new tree node} mapping in here if non-null
     */
    public SimpleRootedTree(RootedTree tree, Map<Node, Node> nodeMapping) {
        createNodes(tree, tree.getRootNode(), nodeMapping);
        setConceptuallyUnrooted(tree.conceptuallyUnrooted());
    }

    /**
     * Make a copy of the given unrooted tree
     * @param tree an unrooted tree
     * @param ingroupNode the node on one side of the root
     * @param outgroupNode the node on the other side of the root
     * @param ingroupBranchLength the branch length from the root to the ingroup node
     * @throws jebl.evolution.graphs.Graph.NoEdgeException
     */
    public SimpleRootedTree(Tree tree, Node ingroupNode, Node outgroupNode, double ingroupBranchLength) throws NoEdgeException {
        List<Node> children = new ArrayList<Node>();

        Node node1 = createNodes(tree, outgroupNode, ingroupNode);
        setLength(node1, ingroupBranchLength);
        children.add(node1);

        Node node2 = createNodes(tree, ingroupNode, outgroupNode);
        setLength(node2, Math.max(tree.getEdgeLength(ingroupNode, outgroupNode) - ingroupBranchLength, 0.0));
        children.add(node2);

        createInternalNode(children);
    }

    /**
     * Clones the entire tree structure from the given RootedTree.
     * @param tree
     * @param node
     * @return created node
     */
    public Node createNodes(RootedTree tree, Node node) {
        hasHeights = tree.hasHeights();
        heightsKnown = tree.isHeightsKnown();
        hasLengths = tree.hasLengths();
        lengthsKnown = tree.isLengthsKnown();
        return createNodes(tree, node, (Map<Node, Node>)null);
    }

    /**
     * Clones the entire tree structure from the given RootedTree.
     * @param tree
     * @param node
     * @param nodeMapping may be null
     * @return
     */
    private Node createNodes(RootedTree tree, Node node, Map<Node, Node> nodeMapping) {

        Node newNode;
        if (tree.isExternal(node)) {
            newNode = createExternalNode(tree.getTaxon(node));

        } else {
            List<Node> children = new ArrayList<Node>();
            for (Node child : tree.getChildren(node)) {
                children.add(createNodes(tree, child, nodeMapping));
            }
            newNode = createInternalNode(children);
        }

        if( nodeMapping != null ) nodeMapping.put(node, newNode);

//        final Map<String, Object> map = node.getAttributeMap();
//        if( ! map.isEmpty() ) {
        for( Map.Entry<String, Object> e : node.getAttributeMap().entrySet() ) {
            newNode.setAttribute(e.getKey(), e.getValue());
        }
        // }
        if (tree.hasHeights() && tree.isHeightsKnown()) {
            setHeight(newNode, tree.getHeight(node));
        }
        if (tree.hasLengths() && tree.isLengthsKnown()) {
            setLength(newNode, tree.getLength(node));
        }
        return newNode;
    }

    /**
     * Clones the entire tree structure from the given (unrooted) Tree.
     * @param tree the unrooted tree
     * @param parent the parent node
     * @param child the child node
     */
    public Node createNodes(Tree tree, Node parent, Node child) throws NoEdgeException {

        Node newNode = null;
        if (tree.isExternal(child)) {
            newNode = createExternalNode(tree.getTaxon(child));
        } else {
            List<Node> adjacencies = tree.getAdjacencies(child);
            List<Node> children = new ArrayList<Node>();

            for (Node child2 : adjacencies) {
                if (child2 != parent) {
                    children.add(createNodes(tree, child, child2));
                }
            }
            newNode = createInternalNode(children);
        }

        setLength(newNode, tree.getEdgeLength(parent, child));

        return newNode;
    }

    /**
     * Creates a new external node with the given taxon. See createInternalNode
     * for a description of how to use these methods.
     * @param taxon the taxon associated with this node
     * @return the created node reference
     */
    public Node createExternalNode(Taxon taxon) {
        if( getTaxa().contains(taxon) ) {
            throw new IllegalArgumentException("duplicate taxon "+taxon.getName());
        }

        SimpleRootedNode node = new SimpleRootedNode(taxon);
        //externalNodes.put(taxon, node);
        externalNodes.add(node);
        taxa.add(taxon);
        return node;
    }

    /**
     * Once a SimpleRootedTree has been created, the node stucture can be created by
     * calling createExternalNode and createInternalNode. First of all createExternalNode
     * is called giving Taxon objects for the external nodes. Then these are put into
     * sets and passed to createInternalNode to create a parent of these nodes. The
     * last node created using createInternalNode is automatically the root so when
     * all the nodes are created, the tree is complete.
     *
     * @param children the child nodes of this nodes
     * @return the created node reference
     */
    public SimpleRootedNode createInternalNode(List<? extends Node> children) {
        SimpleRootedNode node = new SimpleRootedNode(children);

        for (Node child : children) {
            ((SimpleRootedNode)child).setParent(node);
        }

        internalNodes.add(node);

        rootNode = node;
        return node;
    }

    /**
     * Removes an internal node in this tree. It must not be a child of any other node.
     * @param node the node to remove.
     */
    public void deleteInternalNode(Node node) {
        if (!(node instanceof SimpleRootedNode)) {
            throw new IllegalArgumentException("Node must be a node in this tree");
        }
        SimpleRootedNode simpleRootedNode= (SimpleRootedNode) node;
        if (!internalNodes.contains(node)) {
            throw new IllegalArgumentException("Not an internal node");
        }
        if (rootNode == node) {
            rootNode = (SimpleRootedNode) getChildren(node).get(0);
        }
        internalNodes.remove(node);
        SimpleRootedNode parentNode = (SimpleRootedNode)simpleRootedNode.getParent();
        if(parentNode != null) {
            parentNode.removeChild(node);
        }
        for (Node child : getChildren(node)) {
            ((SimpleRootedNode) child).setParent(parentNode);
            if(parentNode != null) {
                parentNode.addChild((SimpleRootedNode) child);
            }
        }
    }


    /**
     * Adds a new node at the midpoint of the edge connecting the given node to its parent.
     * @param node must be a node in the tree, which has a parent
     * @return the newly created node
     */
    public Node addNode(Node node) {
        SimpleRootedNode rootedNode = (SimpleRootedNode)node;
        Edge edge = rootedNode.getEdge();
        if(edge == null) {
            throw new IllegalArgumentException("The node must have a parent");
        }
        return addNode(edge);
    }


    /**
     * Adds a new node at the midpoint of the given edge
     * @param edge
     * @return the newly created node
     */
    public Node addNode(Edge edge) {
        Node[] edgeNodes = getNodes(edge);

        if(edgeNodes == null) {
            throw new IllegalArgumentException("The edge must exist on the tree");
        }

        double edgeLength = edge.getLength();

        SimpleRootedNode parentNode, childNode;

        if(getChildren(edgeNodes[0]).contains(edgeNodes[1])) {
            parentNode = (SimpleRootedNode)edgeNodes[0];
            childNode = (SimpleRootedNode)edgeNodes[1];
        }
        else {
            parentNode = (SimpleRootedNode)edgeNodes[1];
            childNode = (SimpleRootedNode)edgeNodes[0];
        }

        SimpleRootedNode newNode = new SimpleRootedNode(Arrays.asList(childNode));
        newNode.setLength(edgeLength/2);
        newNode.setHeight((childNode.getHeight()+parentNode.getHeight())/2);
        childNode.setLength(edgeLength/2);
        childNode.setParent(newNode);
        parentNode.removeChild(childNode);
        parentNode.addChild(newNode);

        return newNode;
    }

    public void swapNodes(Node n, int i0, int i1) {
        ((SimpleRootedNode)n).swapChildren(i0, i1);
    }

    /**
     * @param node the node whose height is being set
     * @param height the height
     */
    public void setHeight(Node node, double height) {
        lengthsKnown = false;
        heightsKnown = true;

        // If a single height of a single node is set then
        // assume that all nodes have heights and by extension,
        // branch lengths as well as these will be calculated
        // from the heights
        hasLengths = true;
        hasHeights = true;

        ((SimpleRootedNode)node).setHeight(height);
    }

    /**
     * @param node the node whose branch length (to its parent) is being set
     * @param length the length
     */
    public void setLength(Node node, double length) {
        heightsKnown = false;
        lengthsKnown = true;

        // If a single length of a single branch is set then
        // assume that all branch have lengths and by extension,
        // node heights as well as these will be calculated
        // from the lengths
        hasLengths = true;
        hasHeights = true;

        ((SimpleRootedNode)node).setLength(length);
    }

    /**
     * @param node the node whose children are being requested.
     * @return the list of nodes that are the children of the given node.
     *         The list may be empty for a terminal node (a tip).
     */
    public List<Node> getChildren(Node node) {
        return new ArrayList<Node>(((SimpleRootedNode)node).getChildren());
    }

    @Override
    public int getExternalNodeCount(Node node) {
        if (((SimpleRootedNode)node).externalNodeCount < 0) {
            if (isExternal(node)) {
                ((SimpleRootedNode)node).externalNodeCount = 1;
            } else {
                ((SimpleRootedNode)node).externalNodeCount = 0;
                for (Node child : getChildren(node)) {
                    ((SimpleRootedNode)node).externalNodeCount += getExternalNodeCount(child);
                }
            }
        }
        return ((SimpleRootedNode)node).externalNodeCount;
    }

    /**
     * @param node the node whose external nodes are being requested.
     * @return the list of external nodes descendent of the given node.
     * The set may be empty for a terminal node (a tip).
     */
    @Override
    public List<Node> getExternalNodes(Node node) {
        if (isExternal(node)) return Collections.singletonList(node);

        List<Node> tips = new ArrayList<Node>();
        for (Node child :  getChildren(node)) {
            tips.addAll(getExternalNodes(child));
        }

        return tips;
    }

    /**
     * @return Whether this tree has node heights available
     */
    public boolean hasHeights() {
        return hasHeights;
    }

    /**
     * @return Whether the node heights are known or need to be recalculated from the lengths
     */
    public boolean isHeightsKnown() {
        return heightsKnown;
    }

    /**
     * @param node the node whose height is being requested.
     * @return the height of the given node. The height will be
     *         less than the parent's height and greater than it children's heights.
     */
    public double getHeight(Node node) {
        if (!hasHeights) throw new IllegalArgumentException("This tree has no node heights");
        if (!heightsKnown) calculateNodeHeights();
        return ((SimpleRootedNode)node).getHeight();
    }

    /**
     * @return Whether this tree has branch lengths available
     */
    public boolean hasLengths() {
        return hasLengths;
    }

    /**
     * @return Whether the branch lengths are known or need to be recalculated from the heights
     */
    public boolean isLengthsKnown() {
        return lengthsKnown;
    }

    /**
     * @param node the node whose branch length (to its parent) is being requested.
     * @return the length of the branch to the parent node (0.0 if the node is the root).
     */
    public double getLength(Node node) {
        if (!hasLengths) throw new IllegalArgumentException("This tree has no branch lengths");
        if (!lengthsKnown) calculateBranchLengths();
        return ((SimpleRootedNode)node).getLength();
    }

    /**
     * @param node the node whose parent is requested
     * @return the parent node of the given node, or null
     *         if the node is the root node.
     */
    public Node getParent(Node node) {
        if (!(node instanceof SimpleRootedNode)) {
            throw new IllegalArgumentException("Node, " + node.toString() + " is not an instance of SimpleRootedNode");
        }
        return ((SimpleRootedNode)node).getParent();
    }

    public Edge getParentEdge(Node node) {
        if (!(node instanceof SimpleRootedNode)) {
            throw new IllegalArgumentException("Node, " + node.toString() + " is not an instance of SimpleRootedNode");
        }
        return ((SimpleRootedNode)node).getEdge();
    }

    /**
     * The root of the tree has the largest node height of
     * all nodes in the tree.
     *
     * @return the root of the tree.
     */
    public Node getRootNode() {
        return rootNode;
    }


    /**
     * @return a set of all nodes that have degree 1.
     *         These nodes are often refered to as 'tips'.
     */
    public Set<Node> getExternalNodes() {
        return externalNodes;
//        return new LinkedHashSet<Node>(externalNodes.values());
    }

    /**
     * @return a set of all nodes that have degree 2 or more.
     *         These nodes are often refered to as internal nodes.
     */
    public Set<Node> getInternalNodes() {
        return new LinkedHashSet<Node>(internalNodes);
    }

    /**
     * @return the set of taxa associated with the external
     *         nodes of this tree. The size of this set should be the
     *         same as the size of the external nodes set.
     */
    public Set<Taxon> getTaxa() {
        return taxa;
//        return new LinkedHashSet<Taxon>(externalNodes.keySet());
    }

    /**
     * @param node the node whose associated taxon is being requested.
     * @return the taxon object associated with the given node, or null
     *         if the node is an internal node.
     */
    public Taxon getTaxon(Node node) {
        if (!(node instanceof SimpleRootedNode)) {
            throw new IllegalArgumentException("Node, " + node.toString() + " is not an instance of SimpleRootedNode.  It is an instance of "+node.getClass().getName());
        }
        return ((SimpleRootedNode)node).getTaxon();
    }

    /**
     * @param node the node
     * @return true if the node is of degree 1.
     */
    public boolean isExternal(Node node) {
        if (!(node instanceof SimpleRootedNode)) {
            throw new IllegalArgumentException("Node, " + node.toString() + " is not an instance of SimpleRootedNode.  It is an instance of "+node.getClass().getName());
        }
        return ((SimpleRootedNode)node).getChildren().size() == 0;
    }

    /**
     * @param taxon the taxon
     * @return the external node associated with the given taxon, or null
     *         if the taxon is not a member of the taxa set associated with this tree.
     */
    public Node getNode(Taxon taxon) {
        if (externalNodeMap == null) {
            externalNodeMap = new LinkedHashMap<Taxon, Node>();
            for (Node node : getExternalNodes()) {
                externalNodeMap.put(((SimpleRootedNode)node).getTaxon(), node);
            }
        }
        return externalNodeMap.get(taxon);
    }

    public void renameTaxa(Taxon from, Taxon to) {
        SimpleRootedNode node = (SimpleRootedNode)getNode(from);

        // TT: The javadoc doesn't specify whether renameTaxa() should fail or silently do nothing
        // if Taxon from doesn't exist. But the code already threw a NullPointerException before (bug 4824),
        // so it's probably ok to throw a more informative IllegalArgumentException instead.
        if (node == null) {
            throw new IllegalArgumentException("Unknown taxon " + from + "; can't rename to " + to);
        }

        node.setTaxa(to);

//        externalNodes.remove(from);
//        externalNodes.put(to, node);
    }

    /**
     * Returns a list of edges connected to this node
     *
     * @param node
     * @return the set of nodes that are attached by edges to the given node.
     */
    public List<Edge> getEdges(Node node) {
        List<Edge> edges = new ArrayList<Edge>();
        for (Node adjNode : getAdjacencies(node)) {
            edges.add(((SimpleRootedNode)adjNode).getEdge());

        }
        return edges;
    }

    /**
     * @param node
     * @return the set of nodes that are attached by edges to the given node.
     */
    public List<Node> getAdjacencies(Node node) {
        return ((SimpleRootedNode)node).getAdjacencies();
    }

    /**
     * Returns the Edge that connects these two nodes
     *
     * @param node1
     * @param node2
     * @return the edge object.
     * @throws jebl.evolution.graphs.Graph.NoEdgeException
     *          if the nodes are not directly connected by an edge.
     */
    public Edge getEdge(Node node1, Node node2) throws NoEdgeException {
        if (((SimpleRootedNode)node1).getParent() == node2) {
            return ((SimpleRootedNode)node1).getEdge();
        } else if (((SimpleRootedNode)node2).getParent() == node1) {
            return ((SimpleRootedNode)node2).getEdge();
        } else {
            throw new NoEdgeException();
        }
    }

    /**
     * @param node1
     * @param node2
     * @return the length of the edge connecting node1 and node2.
     * @throws jebl.evolution.graphs.Graph.NoEdgeException
     *          if the nodes are not directly connected by an edge.
     */
    public double getEdgeLength(Node node1, Node node2) throws NoEdgeException {
        if (((SimpleRootedNode)node1).getParent() == node2) {
            if (heightsKnown) {
                return ((SimpleRootedNode)node2).getHeight() - ((SimpleRootedNode)node1).getHeight();
            } else {
                return ((SimpleRootedNode)node1).getLength();
            }
        } else if (((SimpleRootedNode)node2).getParent() == node1) {
            if (heightsKnown) {
                return ((SimpleRootedNode)node1).getHeight() - ((SimpleRootedNode)node2).getHeight();
            } else {
                return ((SimpleRootedNode)node2).getLength();
            }
        } else {
            throw new NoEdgeException();
        }
    }

    /**
     * Returns an array of 2 nodes which are the nodes at either end of the edge.
     *
     * @param edge
     * @return an array of 2 edges
     */
    public Node[] getNodes(Edge edge) {
        for (Node node : getNodes()) {
            if (((SimpleRootedNode)node).getEdge() == edge) {
                return new Node[] { node, ((SimpleRootedNode)node).getParent() };
            }
        }
        return null;
    }

    /**
     * @return the set of all nodes in this graph.
     */
    public Set<Node> getNodes() {
        Set<Node> nodes = new LinkedHashSet<Node>(internalNodes);
        nodes.addAll(getExternalNodes());
        return nodes;
    }

    /**
     * @return the set of all edges in this graph.
     */
    public Set<Edge> getEdges() {
        Set<Edge> edges = new LinkedHashSet<Edge>();
        for (Node node : getNodes()) {
            if (node != getRootNode()) {
                edges.add(((SimpleRootedNode)node).getEdge());
            }

        }
        return edges;
    }

    /**
     * The set of external edges. This is a pretty inefficient implementation because
     * a new set is constructed each time this is called.
     * @return the set of external edges.
     */
    public Set<Edge> getExternalEdges() {
        Set<Edge> edges = new LinkedHashSet<Edge>();
        for (Node node : getExternalNodes()) {
            edges.add(((SimpleRootedNode)node).getEdge());
        }
        return edges;
    }

    /**
     * The set of internal edges. This is a pretty inefficient implementation because
     * a new set is constructed each time this is called.
     * @return the set of internal edges.
     */
    public Set<Edge> getInternalEdges() {
        Set<Edge> edges = new LinkedHashSet<Edge>();
        for (Node node : getInternalNodes()) {
            if (node != getRootNode()) {
                edges.add(((SimpleRootedNode)node).getEdge());
            }
        }
        return edges;
    }

    /**
     * @param degree the number of edges connected to a node
     * @return a set containing all nodes in this graph of the given degree.
     */
    public Set<Node> getNodes(int degree) {
        Set<Node> nodes = new LinkedHashSet<Node>();
        for (Node node : getNodes()) {
            // Account for no anncesstor of root, assumed by default in getDegree
            final int deg = node.getDegree() ;
            if (deg == degree) nodes.add(node);
        }
        return nodes;
    }

    /**
     * Set the node heights from the current branch lengths.
     */
    private void calculateNodeHeights() {

        if (!lengthsKnown) {
            throw new IllegalArgumentException("Can't calculate node heights because branch lengths not known");
        }

        nodeLengthsToHeights(rootNode, 0.0);

        double maxHeight = 0.0;
        for (Node externalNode : getExternalNodes()) {
            if (((SimpleRootedNode)externalNode).getHeight() > maxHeight) {
                maxHeight = ((SimpleRootedNode)externalNode).getHeight();
            }
        }

        for (Node node : getNodes()) {
            ((SimpleRootedNode)node).setHeight(maxHeight - ((SimpleRootedNode)node).getHeight());
        }

        heightsKnown = true;
    }

    /**
     * Set the node heights from the current node branch lengths. Actually
     * sets distance from root so the heights then need to be reversed.
     */
    private void nodeLengthsToHeights(SimpleRootedNode node, double height) {

        double newHeight = height;

        if (node.getLength() > 0.0) {
            newHeight += node.getLength();
        }

        node.setHeight(newHeight);

        for (Node child : node.getChildren()) {
            nodeLengthsToHeights((SimpleRootedNode)child, newHeight);
        }
    }

    /**
     * Calculate branch lengths from the current node heights.
     */
    protected void calculateBranchLengths() {

        if (!hasLengths) {
            throw new IllegalArgumentException("Can't calculate branch lengths because node heights not known");
        }

        nodeHeightsToLengths(rootNode, getHeight(rootNode));

        lengthsKnown = true;
    }

    /**
     * Calculate branch lengths from the current node heights.
     */
    private void nodeHeightsToLengths(SimpleRootedNode node, double height) {
        final double h = node.getHeight();
        node.setLength(h >= 0 ? height - h : 1);

        for (Node child : node.getChildren()) {
            nodeHeightsToLengths((SimpleRootedNode)child, node.getHeight());
        }

    }

    public void setConceptuallyUnrooted(boolean intent) {
        conceptuallyUnrooted = intent;
    }

    public boolean conceptuallyUnrooted() {
        return conceptuallyUnrooted;
    }

    public boolean isRoot(Node node) {
        return node == rootNode;
    }

    // Attributable IMPLEMENTATION

    public void setAttribute(String name, Object value) {
        if (helper == null) {
            helper = new AttributableHelper();
        }
        helper.setAttribute(name, value);
    }

    public Object getAttribute(String name) {
        if (helper == null) {
            return null;
        }
        return helper.getAttribute(name);
    }

    public void removeAttribute(String name) {
        if( helper != null ) {
            helper.removeAttribute(name);
        }
    }

    public Set<String> getAttributeNames() {
        if (helper == null) {
            return Collections.emptySet();
        }
        return helper.getAttributeNames();
    }

    public Map<String, Object> getAttributeMap() {
        if (helper == null) {
            return Collections.emptyMap();
        }
        return helper.getAttributeMap();
    }

    // PRIVATE members

    private AttributableHelper helper = null;

    protected SimpleRootedNode rootNode = null;
    protected final Set<Node> internalNodes = new LinkedHashSet<Node>();
    private final Set<Node> externalNodes = new LinkedHashSet<Node>();
    private final Set<Taxon> taxa = new LinkedHashSet<Taxon>();

    // Not created initially for efficiency reasons. Will be created lazily as needed.
    private Map<Taxon, Node> externalNodeMap = null;

    private boolean heightsKnown = false;
    private boolean lengthsKnown = false;

    private boolean hasHeights = false;
    private boolean hasLengths = false;

    private boolean conceptuallyUnrooted = false;

    private class SimpleRootedNode extends BaseNode {
        public SimpleRootedNode(Taxon taxon) {
            this.children = Collections.unmodifiableList(new ArrayList<Node>());
            this.taxon = taxon;
        }

        public SimpleRootedNode(List<? extends Node> children) {
            this.children = Collections.unmodifiableList(new ArrayList<Node>(children));
            this.taxon = null;
        }

        public void removeChild(Node node) {
            List<Node> c = new ArrayList<Node>(children);
            c.remove(node);
            children = Collections.unmodifiableList(c);
        }

        public void addChild(SimpleRootedNode node) {
            List<Node> c = new ArrayList<Node>(children);
            c.add(node);
            node.setParent(this);
            children = Collections.unmodifiableList(c);
        }

        public void replaceChildren(List<SimpleRootedNode> nodes) {
            for( SimpleRootedNode n : nodes ) {
                n.setParent(this);
            }
            children = Collections.unmodifiableList(new ArrayList<Node>(nodes));
        }

        void swapChildren(int i0, int i1) {
            ArrayList<Node> nc = new ArrayList<Node>(children);
            //there was a user reported crash where i0 was > size of the array of children nodes
            if (i0 < 0 || i0 >= nc.size() || i1 < 0 || i1 >= nc.size()) {
                throw new IllegalArgumentException("Tried to swap children ("+i0+","+i1+") on node with " + nc.size() + " children");
            }
            final Node ni0 = nc.get(i0);
            nc.set(i0, nc.get(i1));
            nc.set(i1, ni0);
            children = Collections.unmodifiableList(nc);
        }

        public Node getParent() {
            return parent;
        }

        public void setParent(Node parent) {
            this.parent = parent;
        }

        public List<Node> getChildren() {
            return children;
        }

        public double getHeight() {
            return height;
        }

        // height above latest tip
        public void setHeight(double height) {
            this.height = height;
        }

        // length of branch to parent
        public double getLength() {
            return length;
        }

        public void setLength(double length) {
            this.length = length;
        }

        public int getDegree() {
            return children.size() +(this==rootNode?0:1);
        }

        public void setTaxa(Taxon to) {
            taxon = to;
        }

        /**
         * returns the edge connecting this node to the parent node
         * @return the edge
         */
        public Edge getEdge() {
            if (edge == null) {
                edge = new BaseEdge() {
                    public double getLength() {
                        return length;
                    }

                    @Override
                    public void setAttribute(String name, Object value) {
                        SimpleRootedNode.this.setAttribute(name, value);
                    }

                    @Override
                    public Object getAttribute(String name) {
                        return SimpleRootedNode.this.getAttribute(name);
                    }

                    @Override
                    public void removeAttribute(String name) {
                        SimpleRootedNode.this.removeAttribute(name);
                    }

                    @Override
                    public Set<String> getAttributeNames() {
                        return SimpleRootedNode.this.getAttributeNames();
                    }

                    @Override
                    public Map<String, Object> getAttributeMap() {
                        return SimpleRootedNode.this.getAttributeMap();
                    }
                };
            }

            return edge;
        }

        /**
         * For a rooted tree, getting the adjacencies is not the most efficient
         * operation as it makes a new set containing the children and the parent.
         * @return the adjacaencies
         */
        public List<Node> getAdjacencies() {
            List<Node> adjacencies = new ArrayList<Node>();
            if (children != null) adjacencies.addAll(children);
            if (parent != null) adjacencies.add(parent);
            return adjacencies;
        }

        public Taxon getTaxon() {
            return taxon;
        }

        private List<Node> children;
        private Taxon taxon;

        private Node parent;
        private double height;
        private double length;

        private Edge edge = null;

        private int externalNodeCount = -1;
    }
}
package io.github.xiaoailazy.coexistree.indexer.tree;

public class TreeNodeIdGenerator {

    private int counter = 0;

    public void reset() {
        counter = 0;
    }

    public String nextId() {
        counter++;
        return String.format("%04d", counter);
    }
}

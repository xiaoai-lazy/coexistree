package io.github.xiaoailazy.coexistree.indexer.model;

import java.util.ArrayList;
import java.util.List;

public class DocumentTree {
    private String docName;
    private String docDescription;
    private List<TreeNode> structure = new ArrayList<>();

    public String getDocName() { return docName; }
    public void setDocName(String docName) { this.docName = docName; }
    public String getDocDescription() { return docDescription; }
    public void setDocDescription(String docDescription) { this.docDescription = docDescription; }
    public List<TreeNode> getStructure() { return structure; }
    public void setStructure(List<TreeNode> structure) { this.structure = structure; }
}


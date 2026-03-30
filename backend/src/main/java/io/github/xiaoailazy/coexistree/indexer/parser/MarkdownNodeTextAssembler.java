package io.github.xiaoailazy.coexistree.indexer.parser;

import io.github.xiaoailazy.coexistree.indexer.model.FlatMarkdownNode;
import io.github.xiaoailazy.coexistree.indexer.model.RawHeaderNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class MarkdownNodeTextAssembler {

    public List<FlatMarkdownNode> assemble(List<RawHeaderNode> headers, List<String> lines) {
        List<FlatMarkdownNode> nodes = new ArrayList<>();
        for (RawHeaderNode header : headers) {
            String line = lines.get(header.lineNum() - 1);
            int level = leadingHashes(line);
            if (level == 0) {
                continue;
            }
            FlatMarkdownNode node = new FlatMarkdownNode();
            node.setTitle(header.nodeTitle());
            node.setLineNum(header.lineNum());
            node.setLevel(level);
            nodes.add(node);
        }
        for (int i = 0; i < nodes.size(); i++) {
            FlatMarkdownNode current = nodes.get(i);
            int start = current.getLineNum() - 1;
            int end = i + 1 < nodes.size() ? nodes.get(i + 1).getLineNum() - 1 : lines.size();
            current.setText(String.join("\n", lines.subList(start, end)).trim());
        }
        return nodes;
    }

    private int leadingHashes(String line) {
        int count = 0;
        while (count < line.length() && line.charAt(count) == '#') {
            count++;
        }
        return count;
    }
}


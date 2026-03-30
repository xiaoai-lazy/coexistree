package io.github.xiaoailazy.coexistree.indexer.parser;

import io.github.xiaoailazy.coexistree.indexer.model.RawHeaderNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class MarkdownNodeExtractor {

    private static final Pattern HEADER_PATTERN = Pattern.compile("^(#{1,6})\\s+(.+)$");

    public List<RawHeaderNode> extract(String markdownContent) {
        List<RawHeaderNode> result = new ArrayList<>();
        String[] lines = markdownContent.split("\\R", -1);
        boolean inCodeBlock = false;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.startsWith("```")) {
                inCodeBlock = !inCodeBlock;
                continue;
            }
            if (inCodeBlock || line.isBlank()) {
                continue;
            }
            Matcher matcher = HEADER_PATTERN.matcher(line);
            if (matcher.matches()) {
                result.add(new RawHeaderNode(matcher.group(2).trim(), i + 1));
            }
        }
        return result;
    }
}


package io.github.xiaoailazy.coexistree.chat.dto;

import io.github.xiaoailazy.coexistree.indexer.model.Citation;

import java.util.List;
import java.util.stream.Collectors;

public record SseEvent(
        String type,
        String status,
        String content,
        List<CitationDto> citations
) {
    public record CitationDto(
            String path,
            String text,
            // New fields
            Long docId,
            String docName,
            String nodeId,
            Integer lineNum,
            Integer level
    ) {
        public static CitationDto from(Citation c) {
            // 构建路径：nodeId + title，如果有 sources 则追加来源信息
            String path = c.nodeId() + (c.title() != null ? " > " + c.title() : "");
            if (c.sources() != null && !c.sources().isEmpty()) {
                String sourcesInfo = c.sources().stream()
                        .map(s -> "doc:" + s.getDocId() + "/node:" + s.getNodeId())
                        .collect(Collectors.joining(", "));
                path += " [来源: " + sourcesInfo + "]";
            }

            return new CitationDto(
                    path,
                    c.snippet(),
                    c.docId(),
                    c.docName(),
                    c.nodeId(),
                    c.lineNum(),
                    c.level()
            );
        }
    }

    public static SseEvent stage(String stage, String status) {
        return new SseEvent("stage", status, stage, null);
    }

    public static SseEvent thinking(String content) {
        return new SseEvent("thinking", null, content, null);
    }

    public static SseEvent answer(String content) {
        return new SseEvent("answer", null, content, null);
    }

    public static SseEvent citations(List<Citation> citations) {
        List<CitationDto> dtos = citations.stream().map(CitationDto::from).toList();
        return new SseEvent("citations", null, null, dtos);
    }

    public static SseEvent done(boolean grounded) {
        return new SseEvent("done", grounded ? "grounded" : "not_grounded", null, null);
    }

    public static SseEvent error(String message) {
        return new SseEvent("error", null, message, null);
    }
}

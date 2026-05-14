package io.github.xiaoailazy.coexistree.knowledge.changeinput;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

@Service
public class ChangeInputFingerprintServiceImpl implements ChangeInputFingerprintService {

    private static final Comparator<Row> BY_DOCUMENT_ID = Comparator.comparingLong(Row::documentId);

    @Override
    public String compute(List<Row> rows) {
        List<Row> sorted = rows.stream().sorted(BY_DOCUMENT_ID).toList();
        StringBuilder sb = new StringBuilder();
        for (Row r : sorted) {
            sb.append(r.documentId())
                    .append('|')
                    .append(r.contentHash() != null ? r.contentHash() : "")
                    .append('|')
                    .append(r.treeBuildStatus() != null ? r.treeBuildStatus() : "")
                    .append('|')
                    .append(r.hasDocumentTreeRow() ? "true" : "false")
                    .append('\n');
        }
        byte[] utf8 = sb.toString().getBytes(StandardCharsets.UTF_8);
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(utf8);
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}

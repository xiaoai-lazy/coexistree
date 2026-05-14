package io.github.xiaoailazy.coexistree.knowledge.controller;

import io.github.xiaoailazy.coexistree.shared.api.ApiResponse;
import io.github.xiaoailazy.coexistree.knowledge.dto.KnowledgeTreeStatusResponse;
import io.github.xiaoailazy.coexistree.knowledge.entity.SystemKnowledgeTreeEntity;
import io.github.xiaoailazy.coexistree.knowledge.repository.SystemKnowledgeTreeRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping("/api/v1/systems/{systemId}/knowledge-tree")
public class KnowledgeTreeController {

    private final SystemKnowledgeTreeRepository systemKnowledgeTreeRepository;

    public KnowledgeTreeController(SystemKnowledgeTreeRepository systemKnowledgeTreeRepository) {
        this.systemKnowledgeTreeRepository = systemKnowledgeTreeRepository;
    }

    @GetMapping("/status")
    public ApiResponse<KnowledgeTreeStatusResponse> getStatus(@PathVariable Long systemId) {
        Optional<SystemKnowledgeTreeEntity> row = systemKnowledgeTreeRepository
                .findBySystemIdAndTreeStatus(systemId, "ACTIVE");
        if (row.isEmpty()) {
            row = systemKnowledgeTreeRepository.findBySystemIdAndTreeStatus(systemId, "BUILDING");
        }
        if (row.isEmpty()) {
            row = systemKnowledgeTreeRepository.findBySystemIdAndTreeStatus(systemId, "EMPTY");
        }

        if (row.isEmpty()) {
            // 无 system_knowledge_trees 行
            return ApiResponse.success(new KnowledgeTreeStatusResponse(
                    0,
                    0,
                    "EMPTY",
                    null
            ));
        }

        SystemKnowledgeTreeEntity entity = row.get();
        KnowledgeTreeStatusResponse response = new KnowledgeTreeStatusResponse(
                entity.getTreeVersion(),
                entity.getNodeCount(),
                entity.getTreeStatus(),
                entity.getUpdatedAt()
        );

        return ApiResponse.success(response);
    }
}

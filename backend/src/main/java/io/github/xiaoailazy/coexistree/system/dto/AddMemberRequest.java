package io.github.xiaoailazy.coexistree.system.dto;

import io.github.xiaoailazy.coexistree.system.entity.RelationType;
import jakarta.validation.constraints.*;

public class AddMemberRequest {
    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotNull(message = "关系类型不能为空")
    private RelationType relationType;

    @Min(value = 1, message = "查看等级最小为1")
    @Max(value = 5, message = "查看等级最大为5")
    private Integer viewLevel = 1;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public RelationType getRelationType() { return relationType; }
    public void setRelationType(RelationType relationType) { this.relationType = relationType; }
    public Integer getViewLevel() { return viewLevel; }
    public void setViewLevel(Integer viewLevel) { this.viewLevel = viewLevel; }
}

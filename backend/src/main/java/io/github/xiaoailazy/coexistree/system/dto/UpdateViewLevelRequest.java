package io.github.xiaoailazy.coexistree.system.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class UpdateViewLevelRequest {
    @NotNull(message = "查看等级不能为空")
    @Min(value = 1, message = "查看等级最小为1")
    @Max(value = 5, message = "查看等级最大为5")
    private Integer viewLevel;

    public Integer getViewLevel() { return viewLevel; }
    public void setViewLevel(Integer viewLevel) { this.viewLevel = viewLevel; }
}

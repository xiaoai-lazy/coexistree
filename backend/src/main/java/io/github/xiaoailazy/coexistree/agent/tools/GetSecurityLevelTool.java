package io.github.xiaoailazy.coexistree.agent.tools;

import io.github.xiaoailazy.coexistree.agent.context.AgentUserContext;

/**
 * 获取当前用户的查看等级。只读信息工具。
 */
public class GetSecurityLevelTool {

    public String execute(AgentUserContext context) {
        return "当前用户的查看等级: " + context.viewLevel() + "/5";
    }
}

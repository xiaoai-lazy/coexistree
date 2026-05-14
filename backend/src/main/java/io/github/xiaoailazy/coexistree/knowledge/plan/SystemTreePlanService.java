package io.github.xiaoailazy.coexistree.knowledge.plan;

/**
 * 事务外调用 LLM 生成系统树更新计划 JSON（设计 §6.A-7）。
 */
public interface SystemTreePlanService {

    /**
     * @param prompt 已拼好的提示词（由编排层负责上下文）
     * @return 合法的计划 JSON 字符串
     */
    String generateUpdatePlanJson(String prompt);
}

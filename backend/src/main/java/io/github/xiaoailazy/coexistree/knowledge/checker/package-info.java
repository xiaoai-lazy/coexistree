/**
 * 系统知识树一致性检查器模块
 * 
 * <p>该模块负责在应用启动时检查所有系统知识树的一致性，确保数据库记录与文件系统中的实际文件保持同步。
 * 
 * <p>主要功能：
 * <ul>
 *   <li>检查文件是否存在</li>
 *   <li>校验 JSON 格式是否有效</li>
 *   <li>校验 treeVersion 一致性</li>
 *   <li>校验 nodeCount 一致性</li>
 *   <li>不一致时标记为 EMPTY 或记录警告日志</li>
 * </ul>
 */
package io.github.xiaoailazy.coexistree.knowledge.checker;

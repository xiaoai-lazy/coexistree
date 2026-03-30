# 测试资源文件说明

本目录包含项目的测试资源文件，用于单元测试和集成测试。

## 文件说明

### test.md

- **用途**: 测试用的 Markdown 文档
- **内容**: 豆包AI产品全功能使用指南
- **使用场景**: 
  - 测试 Markdown 解析功能
  - 测试文档结构提取
  - 测试树结构生成

### test_tree.json

- **用途**: 预期的文档树结构
- **内容**: test.md 解析后的预期 JSON 结构
- **使用场景**:
  - 验证解析结果的正确性
  - 回归测试

## 使用方法

### 在测试类中使用

```java
import java.nio.file.Files;
import java.nio.file.Path;

class MyTest {
    
    @Test
    void testMarkdownParsing() throws Exception {
        Path testMd = Path.of("src/test/resources/testdata/test.md");
        String content = Files.readString(testMd);
        
        Path expectedTree = Path.of("src/test/resources/testdata/test_tree.json");
        String expectedJson = Files.readString(expectedTree);
        
        // 进行测试...
    }
}
```

## 注意事项

1. **单元测试使用 Mockito mock**：不依赖 Spring 容器，不调用真实 LLM API
2. **集成测试使用 dev 配置**：使用 `@ActiveProfiles("dev")` 加载 dev 配置
3. **保持文件同步**：如果修改了测试文件，确保相关的预期结果也同步更新

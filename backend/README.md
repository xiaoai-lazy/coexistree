# CoExistree Backend

CoExistree Backend is a Spring Boot application that powers the system lifecycle knowledge management platform. It provides intelligent Q&A capabilities based on Markdown document trees using LLM-powered tree search and answer generation.

## Tech Stack

- **Java 21** - Modern Java with virtual threads support
- **Spring Boot 3.5** - Application framework
- **Spring Data JPA** - Data persistence
- **H2 Database** - Embedded database for development
- **Flyway** - Database migration
- **Volcengine Ark SDK** - LLM integration (ByteDance)
- **Lombok** - Boilerplate reduction
- **Maven** - Build tool

## Project Structure

```
backend/
├── src/main/java/io/github/xiaoailazy/coexistree/
│   ├── CoExistreeApplication.java          # Application entry point
│   ├── common/                        # Shared utilities and exceptions
│   │   ├── api/ApiResponse.java       # Unified API response wrapper
│   │   ├── entity/ProcessLogEntity.java
│   │   ├── enums/ErrorCode.java
│   │   ├── exception/BusinessException.java
│   │   ├── handler/GlobalExceptionHandler.java
│   │   ├── repository/ProcessLogRepository.java
│   │   └── util/
│   ├── config/                        # Configuration classes
│   │   ├── AppStorageProperties.java  # File storage paths
│   │   ├── AsyncConfig.java           # Virtual thread executor
│   │   ├── JacksonConfig.java         # JSON serialization
│   │   └── LlmProperties.java         # LLM API configuration
│   ├── conversation/                  # Conversation management
│   │   ├── controller/ConversationController.java
│   │   ├── dto/                       # Request/response DTOs
│   │   ├── entity/                    # JPA entities
│   │   ├── repository/
│   │   └── service/
│   ├── document/                      # Document management
│   │   ├── controller/DocumentController.java
│   │   ├── dto/
│   │   ├── entity/
│   │   ├── repository/
│   │   ├── service/
│   │   ├── storage/MarkdownFileStorageService.java
│   │   └── task/DocumentTreeBuildTask.java
│   ├── evaluation/                    # Requirement evaluation (Phase 3)
│   │   ├── enums/                     # Confidence, Risk, Intent enums
│   │   ├── model/                     # Evaluation report models
│   │   └── service/                   # Detectors and classifiers
│   ├── knowledgetree/                 # System knowledge tree
│   │   ├── checker/                   # Consistency checkers
│   │   ├── controller/KnowledgeTreeController.java
│   │   ├── dto/
│   │   ├── entity/
│   │   ├── model/                     # Tree models and merge instructions
│   │   ├── repository/
│   │   ├── service/
│   │   ├── storage/                   # Tree file I/O
│   │   └── tree/SystemTreeNodeIdGenerator.java
│   ├── pageindex/                     # Core PageIndex RAG engine
│   │   ├── facade/PageIndexMarkdownService.java
│   │   ├── llm/                       # LLM client and prompts
│   │   ├── model/                     # TreeNode, Citation, etc.
│   │   ├── parser/                    # Markdown parsing
│   │   ├── storage/                   # Tree JSON storage
│   │   └── summary/                   # Node summarization
│   └── system/                        # System management
│       ├── controller/SystemController.java
│       ├── dto/
│       ├── entity/
│       ├── repository/
│       └── service/
├── src/main/resources/
│   ├── application.yml                # Default config
│   ├── application-dev.yml            # Development config
│   └── db/migration/                  # Flyway migrations
└── pom.xml
```

## Configuration

### Application Properties

Key configuration in `application-dev.yml`:

```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:h2:file:./data/h2/coexistree;MODE=MySQL;AUTO_SERVER=TRUE
    username: sa
    password:
  h2:
    console:
      enabled: true
      path: /h2-console
  jpa:
    hibernate:
      ddl-auto: validate
  flyway:
    enabled: true

logging:
  file:
    name: ./data/logs/coexistree.log

app:
  storage:
    doc-root: ./data/docs          # Markdown documents
    tree-root: ./data/trees        # Document trees
    system-tree-root: ./data/system-trees  # System knowledge trees
  llm:
    api-key: YOUR_API_KEY
    default-model: doubao-seed-2-0-pro-260215
    base-url: https://ark.cn-beijing.volces.com/api/v3
```

### Required Setup

1. **LLM API Key**: Set `app.llm.api-key` to your Volcengine Ark API key
2. **Data Directories**: Ensure the storage directories exist or are writable

## Local Development

### Prerequisites

- Java 21 or higher
- Maven 3.9+

### Running the Application

```bash
# Navigate to backend directory
cd backend

# Run with development profile
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Or on Windows
mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev
```

### Access Points

- Application: http://localhost:8080
- H2 Console: http://localhost:8080/h2-console
  - JDBC URL: `jdbc:h2:file:./data/h2/coexistree`
  - Username: `sa`
  - Password: (empty)

## API Overview

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/v1/systems` | GET | List all systems |
| `/api/v1/systems` | POST | Create new system |
| `/api/v1/systems/{id}` | GET/PUT/DELETE | System CRUD |
| `/api/v1/documents` | GET/POST | List/upload documents |
| `/api/v1/documents/{id}` | GET/DELETE | Document operations |
| `/api/v1/documents/{id}/tree` | GET | Get document tree |
| `/api/v1/conversations` | GET/POST | List/create conversations |
| `/api/v1/conversations/{id}/messages` | GET | Get messages |
| `/api/v1/conversations/{id}/messages` | POST | Send message (SSE stream) |
| `/api/v1/conversations/quick-chat` | POST | Quick chat without history |
| `/api/v1/conversations/{id}/smart-chat` | POST | Smart chat with evaluation |
| `/api/v1/knowledge-trees/{systemId}` | GET/POST | System knowledge tree |
| `/api/v1/knowledge-trees/{systemId}/snapshots` | GET | Tree snapshots |
| `/api/v1/knowledge-trees/{systemId}/merge` | POST | Merge document into tree |

## Testing

```bash
# Run unit tests
./mvnw test

# Run with coverage
./mvnw test jacoco:report
```

## Build

```bash
# Package application
./mvnw clean package

# Skip tests
./mvnw clean package -DskipTests

# Run the jar
java -jar target/coexistree-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev
```

## Architecture Notes

### PageIndex RAG Engine

The core innovation is the PageIndex tree-search approach:

1. **Document Parsing**: Markdown files are parsed into hierarchical trees based on headings
2. **Tree Storage**: Trees are stored as JSON files (no vector database required)
3. **Two-Call LLM Pipeline**:
   - **Tree Search**: LLM identifies relevant node IDs from the tree structure
   - **Answer Generation**: Full text of identified nodes is fetched and used for grounded answers
4. **Citations**: Answers include citations linking back to source document sections

### System Knowledge Tree (Phase 3)

- Each system has a living knowledge tree representing its current structure
- Documents serve as changelogs to the tree
- Supports baseline merge (initial structure extraction) and change merge (incremental updates)
- Automatic snapshots on each merge operation
- Requirement evaluation with conflict detection and impact analysis

### Virtual Threads

The application uses Java 21 virtual threads for SSE streaming responses, enabling efficient handling of concurrent LLM requests without blocking platform threads.

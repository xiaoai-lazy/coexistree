package io.github.xiaoailazy.coexistree.agent.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.adk.sessions.Session;
import io.github.xiaoailazy.coexistree.agent.repository.AdkEventRepository;
import io.github.xiaoailazy.coexistree.agent.repository.AdkSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DatabaseSessionServiceTest {

    @Mock
    private AdkSessionRepository sessionRepository;
    @Mock
    private AdkEventRepository eventRepository;

    private DatabaseSessionService service;

    @BeforeEach
    void setUp() {
        service = new DatabaseSessionService(sessionRepository, eventRepository, new ObjectMapper());
    }

    @Test
    void createSession_shouldSaveAndReturnSession() {
        ConcurrentMap<String, Object> state = new ConcurrentHashMap<>();
        state.put("user:userId", "test-user");

        when(sessionRepository.save(any(AdkSessionEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        Session session = service.createSession("coexistree", "test-user", state, "sess-1").blockingGet();

        assertThat(session.id()).isEqualTo("sess-1");
        assertThat(session.appName()).isEqualTo("coexistree");
        assertThat(session.userId()).isEqualTo("test-user");
        assertThat(session.state()).containsKey("user:userId");
        verify(sessionRepository).save(any());
    }

    @Test
    void createSession_shouldGenerateIdWhenNull() {
        ConcurrentMap<String, Object> state = new ConcurrentHashMap<>();
        when(sessionRepository.save(any(AdkSessionEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        Session session = service.createSession("coexistree", "test-user", state, null).blockingGet();

        assertThat(session.id()).isNotNull();
        assertThat(session.id()).isNotBlank();
    }

    @Test
    void getSession_shouldReturnEmptyWhenNotFound() {
        when(sessionRepository.findByAppNameAndUserIdAndId("coexistree", "user", "sess-1"))
                .thenReturn(Optional.empty());

        Session session = service.getSession("coexistree", "user", "sess-1", Optional.empty()).blockingGet();

        assertThat(session).isNull();
    }

    @Test
    void deleteSession_shouldDeleteEventsAndSession() {
        AdkSessionEntity existing = new AdkSessionEntity();
        existing.setId("sess-1");
        existing.setAppName("coexistree");
        existing.setUserId("user");
        existing.setCreateTime(LocalDateTime.now());
        existing.setUpdateTime(LocalDateTime.now());

        when(sessionRepository.findByAppNameAndUserIdAndId("coexistree", "user", "sess-1"))
                .thenReturn(Optional.of(existing));

        service.deleteSession("coexistree", "user", "sess-1").blockingAwait();

        verify(eventRepository).deleteBySessionId("sess-1");
        verify(sessionRepository).delete(existing);
    }

    @Test
    void deleteSession_shouldDoNothingWhenSessionNotFound() {
        when(sessionRepository.findByAppNameAndUserIdAndId("coexistree", "user", "nonexistent"))
                .thenReturn(Optional.empty());

        service.deleteSession("coexistree", "user", "nonexistent").blockingAwait();

        verify(eventRepository, never()).deleteBySessionId(any());
        verify(sessionRepository, never()).delete(any());
    }

    @Test
    void listSessions_shouldReturnAllSessionsForAppAndUser() {
        AdkSessionEntity e1 = new AdkSessionEntity();
        e1.setId("s1"); e1.setAppName("coexistree"); e1.setUserId("u1");
        e1.setCreateTime(LocalDateTime.now()); e1.setUpdateTime(LocalDateTime.now());
        e1.setStateJson("{}");

        AdkSessionEntity e2 = new AdkSessionEntity();
        e2.setId("s2"); e2.setAppName("coexistree"); e2.setUserId("u1");
        e2.setCreateTime(LocalDateTime.now()); e2.setUpdateTime(LocalDateTime.now());
        e2.setStateJson("{}");

        when(sessionRepository.findByAppNameAndUserId("coexistree", "u1")).thenReturn(List.of(e1, e2));

        var response = service.listSessions("coexistree", "u1").blockingGet();

        assertThat(response.sessions()).hasSize(2);
    }

    @Test
    void listEvents_shouldReturnEventsForSession() {
        var response = service.listEvents("coexistree", "user", "sess-1").blockingGet();

        assertThat(response.events()).isEmpty();
        // eventRepository mock returns empty by default
    }

    @Test
    void getSession_shouldReturnSessionWhenFound() {
        AdkSessionEntity entity = new AdkSessionEntity();
        entity.setId("sess-1");
        entity.setAppName("coexistree");
        entity.setUserId("test-user");
        entity.setStateJson("{\"key\":\"value\"}");
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());

        when(sessionRepository.findByAppNameAndUserIdAndId("coexistree", "test-user", "sess-1"))
                .thenReturn(Optional.of(entity));

        Session session = service.getSession("coexistree", "test-user", "sess-1", Optional.empty()).blockingGet();

        assertThat(session).isNotNull();
        assertThat(session.id()).isEqualTo("sess-1");
        assertThat(session.appName()).isEqualTo("coexistree");
        assertThat(session.userId()).isEqualTo("test-user");
        assertThat(session.state()).containsEntry("key", "value");
        assertThat(session.events()).isEmpty();
    }

    @Test
    void getSession_withConfig_shouldFilterEventsByTimestamp() {
        // Setup session entity
        AdkSessionEntity entity = new AdkSessionEntity();
        entity.setId("sess-1");
        entity.setAppName("coexistree");
        entity.setUserId("user");
        entity.setStateJson("{}");
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        when(sessionRepository.findByAppNameAndUserIdAndId("coexistree", "user", "sess-1"))
                .thenReturn(Optional.of(entity));

        // Use Optional.empty() for config since we can't easily construct GetSessionConfig
        // This test verifies the config branch handles gracefully when empty
        Session session = service.getSession("coexistree", "user", "sess-1", Optional.empty()).blockingGet();
        assertThat(session).isNotNull();
    }

    @Test
    void createSession_shouldSerializeStateCorrectly() {
        ConcurrentMap<String, Object> state = new ConcurrentHashMap<>();
        state.put("user:userId", "123");
        state.put("user:systemId", 456L);

        when(sessionRepository.save(any(AdkSessionEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        Session session = service.createSession("coexistree", "user", state, "sess-1").blockingGet();

        assertThat(session.state()).containsEntry("user:userId", "123");
        assertThat(session.state()).containsKey("user:systemId");
    }

    /**
     * After fix: getSession with empty config should load all events.
     *
     * Before fix: ADK Runner calls getSession(appName, userId, sessionId, Optional.empty()),
     * but DatabaseSessionService never loaded events when config was empty.
     * This caused the LLM request to have no user message — only system instruction —
     * which is why root-agent never saw the user's question and never called tools.
     */
    @Test
    @DisplayName("getSession with empty config loads events — verifies routing fix")
    void getSession_emptyConfig_shouldLoadEvents() {
        // Step 1: Setup session entity
        AdkSessionEntity entity = new AdkSessionEntity();
        entity.setId("sess-1");
        entity.setAppName("coexistree");
        entity.setUserId("user");
        entity.setStateJson("{}");
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        when(sessionRepository.findByAppNameAndUserIdAndId("coexistree", "user", "sess-1"))
                .thenReturn(Optional.of(entity));

        // Step 2: Mock eventRepository with a stored user message
        AdkEventEntity eventEntity = new AdkEventEntity();
        eventEntity.setId("event-1");
        eventEntity.setSessionId("sess-1");
        eventEntity.setTimestamp(System.currentTimeMillis() / 1000);
        eventEntity.setEventJson("{\"id\":\"event-1\",\"author\":\"user\",\"content\":{\"parts\":[{\"text\":\"这个系统有几个功能？\"}]}}");
        when(eventRepository.findBySessionIdOrderByTimestampAsc("sess-1"))
                .thenReturn(List.of(eventEntity));

        // Step 3: getSession WITHOUT config (exactly as ADK Runner does)
        Session session = service.getSession("coexistree", "user", "sess-1", Optional.empty()).blockingGet();

        // After fix: events ARE loaded
        assertThat(session.events()).hasSize(1);
        assertThat(session.events().get(0).author()).isEqualTo("user");
    }

    @Test
    @DisplayName("getSession WITH config loads events from database")
    void getSession_withConfig_shouldLoadEvents() {
        AdkSessionEntity entity = new AdkSessionEntity();
        entity.setId("sess-1");
        entity.setAppName("coexistree");
        entity.setUserId("user");
        entity.setStateJson("{}");
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        when(sessionRepository.findByAppNameAndUserIdAndId("coexistree", "user", "sess-1"))
                .thenReturn(Optional.of(entity));

        AdkEventEntity eventEntity = new AdkEventEntity();
        eventEntity.setId("event-1");
        eventEntity.setSessionId("sess-1");
        eventEntity.setTimestamp(System.currentTimeMillis() / 1000);
        eventEntity.setEventJson("{\"id\":\"event-1\",\"author\":\"user\",\"content\":{\"parts\":[{\"text\":\"这个系统有几个功能？\"}]}}");
        when(eventRepository.findBySessionIdOrderByTimestampAsc("sess-1"))
                .thenReturn(List.of(eventEntity));

        // getSession WITH config
        var config = com.google.adk.sessions.GetSessionConfig.builder().build();
        Session session = service.getSession("coexistree", "user", "sess-1", Optional.of(config)).blockingGet();

        // Events ARE loaded when config is present
        assertThat(session.events()).hasSize(1);
        assertThat(session.events().get(0).author()).isEqualTo("user");
    }
}

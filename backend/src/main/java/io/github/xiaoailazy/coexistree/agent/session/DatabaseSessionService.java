package io.github.xiaoailazy.coexistree.agent.session;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.adk.JsonBaseModel;
import com.google.adk.events.Event;
import com.google.adk.sessions.BaseSessionService;
import com.google.adk.sessions.GetSessionConfig;
import com.google.adk.sessions.ListEventsResponse;
import com.google.adk.sessions.ListSessionsResponse;
import com.google.adk.sessions.Session;
import io.github.xiaoailazy.coexistree.agent.repository.AdkEventRepository;
import io.github.xiaoailazy.coexistree.agent.repository.AdkSessionRepository;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
public class DatabaseSessionService implements BaseSessionService {

    private final AdkSessionRepository sessionRepository;
    private final AdkEventRepository eventRepository;
    private final ObjectMapper objectMapper;

    public DatabaseSessionService(AdkSessionRepository sessionRepository,
                                   AdkEventRepository eventRepository,
                                   ObjectMapper objectMapper) {
        this.sessionRepository = sessionRepository;
        this.eventRepository = eventRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public Single<Session> createSession(String appName, String userId,
            @Nullable ConcurrentMap<String, Object> state, @Nullable String sessionId) {
        return Single.fromCallable(() -> {
            String id = sessionId != null ? sessionId : UUID.randomUUID().toString();
            LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

            AdkSessionEntity entity = new AdkSessionEntity();
            entity.setId(id);
            entity.setAppName(appName);
            entity.setUserId(userId);
            entity.setStateJson(serializeState(state));
            entity.setCreateTime(now);
            entity.setUpdateTime(now);
            sessionRepository.save(entity);

            log.debug("Created ADK session: id={}, appName={}, userId={}", id, appName, userId);

            return Session.builder(id)
                    .appName(appName)
                    .userId(userId)
                    .state(deserializeState(entity.getStateJson()))
                    .lastUpdateTime(now.toInstant(ZoneOffset.UTC))
                    .build();
        });
    }

    @Override
    public Maybe<Session> getSession(String appName, String userId, String sessionId,
            Optional<GetSessionConfig> config) {
        return Maybe.fromCallable(() -> {
            Optional<AdkSessionEntity> opt = sessionRepository
                    .findByAppNameAndUserIdAndId(appName, userId, sessionId);
            if (opt.isEmpty()) return null;

            AdkSessionEntity entity = opt.get();

            // Load events from database
            List<Event> events;
            if (config.isPresent()) {
                GetSessionConfig cfg = config.get();
                List<AdkEventEntity> eventEntities = eventRepository
                        .findBySessionIdOrderByTimestampAsc(sessionId);

                // Apply afterTimestamp filter if present
                Optional<Instant> afterTs = cfg.afterTimestamp();
                if (afterTs.isPresent()) {
                    long cutoff = afterTs.get().getEpochSecond();
                    eventEntities = eventEntities.stream()
                            .filter(e -> e.getTimestamp() >= cutoff)
                            .toList();
                }

                // Apply numRecentEvents limit if present
                Optional<Integer> numRecent = cfg.numRecentEvents();
                if (numRecent.isPresent()) {
                    int limit = numRecent.get();
                    eventEntities = eventEntities.subList(
                            Math.max(0, eventEntities.size() - limit),
                            eventEntities.size());
                }

                events = new ArrayList<>(eventEntities.stream()
                        .map(this::toAdkEvent)
                        .filter(e -> e != null)
                        .toList());
            } else {
                // No config: load all events (required for ADK Runner to include
                // user messages in the LLM request — fixes root-agent routing failure)
                events = new ArrayList<>(eventRepository.findBySessionIdOrderByTimestampAsc(sessionId)
                        .stream()
                        .map(this::toAdkEvent)
                        .filter(e -> e != null)
                        .toList());
            }

            LocalDateTime updateTime = entity.getUpdateTime();
            Instant lastUpdate = updateTime != null
                    ? updateTime.toInstant(ZoneOffset.UTC)
                    : Instant.EPOCH;

            return Session.builder(sessionId)
                    .appName(appName)
                    .userId(userId)
                    .state(deserializeState(entity.getStateJson()))
                    .events(events)
                    .lastUpdateTime(lastUpdate)
                    .build();
        });
    }

    @Override
    public Single<ListSessionsResponse> listSessions(String appName, String userId) {
        return Single.fromCallable(() -> {
            List<AdkSessionEntity> entities = sessionRepository.findByAppNameAndUserId(appName, userId);
            List<Session> sessions = entities.stream()
                    .map(e -> Session.builder(e.getId())
                            .appName(e.getAppName())
                            .userId(e.getUserId())
                            .state(deserializeState(e.getStateJson()))
                            .lastUpdateTime(e.getUpdateTime().toInstant(ZoneOffset.UTC))
                            .build())
                    .toList();
            return ListSessionsResponse.builder().sessions(sessions).build();
        });
    }

    @Override
    public Completable deleteSession(String appName, String userId, String sessionId) {
        return Completable.fromAction(() -> {
            sessionRepository.findByAppNameAndUserIdAndId(appName, userId, sessionId)
                    .ifPresentOrElse(
                            entity -> {
                                eventRepository.deleteBySessionId(sessionId);
                                sessionRepository.delete(entity);
                                log.debug("Deleted ADK session: sessionId={}", sessionId);
                            },
                            () -> log.warn("Session not found for deletion: appName={}, userId={}, sessionId={}",
                                    appName, userId, sessionId)
                    );
        });
    }

    @Override
    public Single<ListEventsResponse> listEvents(String appName, String userId, String sessionId) {
        return Single.fromCallable(() -> {
            List<AdkEventEntity> entities = eventRepository
                    .findBySessionIdOrderByTimestampAsc(sessionId);
            List<Event> events = new ArrayList<>(entities.stream()
                    .map(this::toAdkEvent)
                    .filter(e -> e != null)
                    .toList());
            return ListEventsResponse.builder().events(events).build();
        });
    }

    @Override
    public Single<Event> appendEvent(Session session, Event event) {
        return Single.fromCallable(() -> {
            // Preserve ADK's default behavior: apply non-temp stateDelta to the in-memory session
            // before persisting, so later tool calls can read user context from session state.
            Event persistedEvent = BaseSessionService.super.appendEvent(session, event).blockingGet();

            AdkEventEntity entity = new AdkEventEntity();
            entity.setId(persistedEvent.id());
            entity.setSessionId(session.id());
            entity.setAppName(session.appName());
            entity.setUserId(session.userId());
            entity.setAuthor(persistedEvent.author());
            entity.setTimestamp(persistedEvent.timestamp());
            entity.setEventJson(JsonBaseModel.toJsonString(persistedEvent));
            eventRepository.save(entity);

            sessionRepository.findById(session.id()).ifPresent(sessionEntity -> {
                sessionEntity.setStateJson(serializeState(new ConcurrentHashMap<>(session.state())));
                sessionEntity.setUpdateTime(LocalDateTime.now(ZoneOffset.UTC));
                sessionRepository.save(sessionEntity);
            });

            log.debug("Appended event: id={}, sessionId={}", persistedEvent.id(), session.id());
            return persistedEvent;
        });
    }

    // -- Private helpers --

    private String serializeState(ConcurrentMap<String, Object> state) {
        try {
            return state != null ? objectMapper.writeValueAsString(state) : "{}";
        } catch (Exception e) {
            log.error("Failed to serialize session state", e);
            return "{}";
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> deserializeState(String json) {
        try {
            if (json == null || json.isBlank()) return new ConcurrentHashMap<>();
            Map<String, Object> map = objectMapper.readValue(json,
                    new TypeReference<Map<String, Object>>() {});
            return new ConcurrentHashMap<>(map);
        } catch (Exception e) {
            log.error("Failed to deserialize session state", e);
            return new ConcurrentHashMap<>();
        }
    }

    private Event toAdkEvent(AdkEventEntity entity) {
        try {
            return JsonBaseModel.fromJsonString(entity.getEventJson(), Event.class);
        } catch (Exception e) {
            log.error("Failed to deserialize ADK event: id={}", entity.getId(), e);
            return null;
        }
    }
}

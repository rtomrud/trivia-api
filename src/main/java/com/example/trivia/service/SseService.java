package com.example.trivia.service;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class SseService {

    private static final long SSE_TIMEOUT = 60 * 60 * 1000;
    private final RedisMessageListenerContainer redisMessageListenerContainer;
    private final Map<String, Set<SseEmitter>> topicEmitters = new ConcurrentHashMap<>();
    private final Map<String, MessageListener> topicListeners = new ConcurrentHashMap<>();
    private final Object lock = new Object();

    public SseService(RedisMessageListenerContainer redisMessageListenerContainer) {
        this.redisMessageListenerContainer = redisMessageListenerContainer;
    }

    public SseEmitter subscribe(String topic) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);

        synchronized (lock) {
            Set<SseEmitter> emitters = topicEmitters.computeIfAbsent(topic, k -> new CopyOnWriteArraySet<>());
            boolean isFirstSubscriber = emitters.isEmpty();
            emitters.add(emitter);
            if (isFirstSubscriber) {
                MessageListener listener = (message, pattern) -> {
                    String messageBody = new String(message.getBody());
                    publish(topic, "message", messageBody);
                };
                topicListeners.put(topic, listener);
                redisMessageListenerContainer.addMessageListener(listener, new ChannelTopic(topic));
            }
        }

        emitter.onCompletion(() -> remove(topic, emitter));
        emitter.onTimeout(() -> remove(topic, emitter));
        emitter.onError((e) -> remove(topic, emitter));

        try {
            emitter.send(SseEmitter.event().name("message").data("Connected"));
        } catch (IOException e) {
            remove(topic, emitter);
        }

        return emitter;
    }

    public void publish(String topic, String event, Object data) {
        Set<SseEmitter> emitters = topicEmitters.get(topic);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        Set<SseEmitter> deadEmitters = new CopyOnWriteArraySet<>();

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name(event)
                        .data(data));
            } catch (IOException e) {
                deadEmitters.add(emitter);
            }
        }

        deadEmitters.forEach(emitter -> remove(topic, emitter));
    }

    private void remove(String topic, SseEmitter emitter) {
        synchronized (lock) {
            Set<SseEmitter> emitters = topicEmitters.get(topic);
            if (emitters != null) {
                emitters.remove(emitter);
                if (emitters.isEmpty()) {
                    topicEmitters.remove(topic);
                    MessageListener listener = topicListeners.remove(topic);
                    if (listener != null) {
                        redisMessageListenerContainer.removeMessageListener(listener);
                    }
                }
            }
        }
    }
}

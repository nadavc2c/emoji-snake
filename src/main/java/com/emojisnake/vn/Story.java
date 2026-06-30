package com.emojisnake.vn;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A single branching visual novel: a graph of {@link StoryNode}s reached from {@code startId}. Pure
 * data, no UI - drives a {@link StoryState} cursor and is fully unit-testable. Use {@link #builder}
 * to author one readably; {@code build()} validates that every choice target resolves and the start
 * exists, so a typo in a script fails fast at class-load time rather than mid-game.
 */
public final class Story {

    private final String id;
    private final String title;
    private final String startId;
    private final Map<String, StoryNode> nodes;

    private Story(String id, String title, String startId, Map<String, StoryNode> nodes) {
        this.id = id;
        this.title = title;
        this.startId = startId;
        this.nodes = Map.copyOf(nodes);
    }

    public String id() { return id; }
    public String title() { return title; }
    public String startId() { return startId; }

    public StoryNode start() { return nodes.get(startId); }
    public StoryNode node(String nodeId) { return nodes.get(nodeId); }
    public Collection<StoryNode> nodes() { return nodes.values(); }

    public static Builder builder(String id, String title, String startId) {
        return new Builder(id, title, startId);
    }

    /** Fluent authoring helper. Node order is irrelevant (targets resolve by id at build()). */
    public static final class Builder {
        private final String id;
        private final String title;
        private final String startId;
        private final Map<String, StoryNode> nodes = new LinkedHashMap<>();

        private Builder(String id, String title, String startId) {
            this.id = id;
            this.title = title;
            this.startId = startId;
        }

        /** A branching beat. */
        public Builder node(String nodeId, String speaker, String portrait, String expr,
                            String background, String text, Choice... choices) {
            nodes.put(nodeId, new StoryNode(nodeId, speaker, portrait, expr, background, text,
                    List.of(choices), false, 0));
            return this;
        }

        /** A safe ending that pays out {@code reward} score (the run continues afterwards). */
        public Builder end(String nodeId, int reward, String speaker, String portrait, String expr,
                           String background, String text) {
            nodes.put(nodeId, new StoryNode(nodeId, speaker, portrait, expr, background, text,
                    List.of(), false, reward));
            return this;
        }

        /** A lethal ending (costs a life / ends the run) - reserved for super-kind choices. */
        public Builder die(String nodeId, String speaker, String portrait, String expr,
                           String background, String text) {
            nodes.put(nodeId, new StoryNode(nodeId, speaker, portrait, expr, background, text,
                    List.of(), true, 0));
            return this;
        }

        public Story build() {
            if (!nodes.containsKey(startId)) {
                throw new IllegalStateException("Story '" + id + "' has no start node '" + startId + "'");
            }
            List<String> dangling = new ArrayList<>();
            for (StoryNode n : nodes.values()) {
                for (Choice c : n.choices()) {
                    if (!nodes.containsKey(c.target())) {
                        dangling.add(n.id() + " -> " + c.target());
                    }
                }
            }
            if (!dangling.isEmpty()) {
                throw new IllegalStateException("Story '" + id + "' has dangling choices: " + dangling);
            }
            return new Story(id, title, startId, nodes);
        }
    }
}

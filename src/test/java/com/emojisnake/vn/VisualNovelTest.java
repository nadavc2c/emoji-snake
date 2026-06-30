package com.emojisnake.vn;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Structural + traversal checks for the visual novels. The graphs are pure data, so these run with no
 * JavaFX. {@code Story.build()} already rejects dangling choice targets at class-load (so simply
 * loading {@link VisualNovels#all()} validates every link); here we additionally assert each story is
 * fully reachable and offers both a lethal and a safe outcome, that the cast tone "not every junction
 * kills you" holds (≥1 flavor junction), and that the cursor reports death vs. survival correctly.
 */
class VisualNovelTest {

    private static final int EXPECTED_NOVELS = 5;

    @Test
    void thereAreFiveWellFormedNovels() {
        List<Story> all = VisualNovels.all();
        assertSame(EXPECTED_NOVELS, all.size(), "expected " + EXPECTED_NOVELS + " novels");
        for (Story s : all) {
            assertNotNull(s.start(), s.id() + ": start node must exist");
        }
    }

    @Test
    void everyNodeIsReachableFromStart() {
        for (Story s : VisualNovels.all()) {
            Set<String> reached = reachable(s);
            for (StoryNode n : s.nodes()) {
                assertTrue(reached.contains(n.id()),
                        s.id() + ": node '" + n.id() + "' is unreachable from start (dead content)");
            }
        }
    }

    @Test
    void everyNovelHasBothALethalAndASafeEndingReachable() {
        for (Story s : VisualNovels.all()) {
            Set<String> reached = reachable(s);
            boolean lethal = false;
            boolean safe = false;
            for (String id : reached) {
                StoryNode n = s.node(id);
                if (n.isEnding() && n.lethal()) {
                    lethal = true;
                }
                if (n.isEnding() && !n.lethal()) {
                    safe = true;
                }
            }
            assertTrue(lethal, s.id() + ": needs at least one reachable lethal ending");
            assertTrue(safe, s.id() + ": needs at least one reachable safe ending");
        }
    }

    @Test
    void notEveryJunctionIsLethal() {
        // The design rule: somewhere there is a junction where no single choice leads straight to
        // death - a "flavor" beat. Verify at least one such junction exists across the novels.
        boolean anyFlavorJunction = false;
        for (Story s : VisualNovels.all()) {
            for (StoryNode n : s.nodes()) {
                if (n.isEnding()) {
                    continue;
                }
                boolean hasLethalChoice = n.choices().stream()
                        .anyMatch(c -> {
                            StoryNode t = s.node(c.target());
                            return t != null && t.isEnding() && t.lethal();
                        });
                if (!hasLethalChoice) {
                    anyFlavorJunction = true;
                }
            }
        }
        assertTrue(anyFlavorJunction, "at least one junction must have no directly-lethal choice");
    }

    @Test
    void aLethalPathReportsDeathAndASafePathReportsSurvival() {
        Story billable = VisualNovels.all().get(0);
        assertSame("billable", billable.id());

        // intro choices: [0]=the kind/moral answer (lethal - inverted morality), [1]=impress, [2]=ask.
        StoryState lethalRun = new StoryState(billable);
        assertTrue(lethalRun.awaitingChoice());
        lethalRun.choose(0); // "I can't bill a dying man!" -> die_kind
        assertTrue(lethalRun.ended(), "the moral choice reaches an ending");
        assertTrue(lethalRun.died(), "being kind is lethal here");

        // impress -> [0]=ruthless (safe ending).
        StoryState safeRun = new StoryState(billable);
        safeRun.choose(1);  // ruthless boast -> impress
        assertFalse(safeRun.ended(), "impress is a junction, not an ending");
        safeRun.choose(0);  // "bury you both" -> end_win
        assertTrue(safeRun.ended(), "the ruthless path reaches an ending");
        assertFalse(safeRun.died(), "ruthlessness is survivable (it wins the route)");
    }

    @Test
    void chooseIsSafeOnBadIndexAndAfterEnding() {
        Story s = VisualNovels.all().get(0);
        StoryState st = new StoryState(s);
        StoryNode before = st.current();
        st.choose(99);            // out of range -> no-op
        assertSame(before, st.current());
        st.choose(-1);            // negative -> no-op
        assertSame(before, st.current());
        st.choose(0);             // the moral choice -> a lethal ending
        assertTrue(st.ended());
        StoryNode end = st.current();
        st.choose(0);             // choosing after an ending -> no-op
        assertSame(end, st.current());
    }

    @Test
    void pickReturnsANovelDeterministicallyForASeed() {
        Story a = VisualNovels.pick(new Random(42));
        Story b = VisualNovels.pick(new Random(42));
        assertSame(a.id(), b.id(), "same seed picks the same novel");
        assertTrue(VisualNovels.all().stream().anyMatch(s -> s.id().equals(a.id())));
    }

    /** BFS the reachable node-id set from the story's start. */
    private static Set<String> reachable(Story s) {
        Set<String> seen = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(s.startId());
        seen.add(s.startId());
        while (!queue.isEmpty()) {
            StoryNode n = s.node(queue.poll());
            if (n == null) {
                continue;
            }
            for (Choice c : n.choices()) {
                if (seen.add(c.target())) {
                    queue.add(c.target());
                }
            }
        }
        return seen;
    }
}

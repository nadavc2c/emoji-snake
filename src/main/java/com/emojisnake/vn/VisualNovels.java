package com.emojisnake.vn;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The library of "slop AI visual novels" - short, branching dating-sim parodies set at a predatory
 * all-snake law firm. The <em>gag</em> is that it's AI slop: a romance dating-sim wearing the skin of
 * a Cruelty-Squad corporate hellscape. It plays the clichés straight - senpai, the rival-to-lover, the
 * wholesome childhood-friend intern, confessions, a love meter - then twists the knife.
 *
 * <p><b>The reward economy (inverted morality):</b> ruthless / capitalist endings WIN the route and
 * pay big score ({@code end(..., 20, ...)}); a meek / cowardly / fair exit survives but pays just 1;
 * and <b>only genuinely SUPER-kind choices kill you</b> ({@code die(...)}) - defending the doomed,
 * saving the prey, self-sacrifice, blowing the whistle. Everything else lets the player live.
 *
 * <p>Pure data. <b>Cast (portrait ids):</b> {@code partner} (king-cobra Senior Partner, the senpai),
 * {@code vance} (naga femme-fatale associate, the rival), {@code glass} (anaconda Managing Partner,
 * the boss), {@code client} (sweaty viper, a doomed mark), {@code intern} (garter-snake, the wholesome
 * one). Blank portrait = narration. <b>Expressions:</b> neutral / smug / angry / seductive.
 * <b>Backgrounds:</b> office / boardroom / skyline / basement.
 */
public final class VisualNovels {

    private static final int WIN = 20;     // a ruthless, route-winning ending
    private static final int MID = 10;     // effective but messy / second-best
    private static final int MEEK = 1;     // survived, kept your decency-ish, earned almost nothing

    private VisualNovels() {
    }

    public static List<Story> all() {
        return List.of(billableHearts(), hostileTakeover(), proBonoHeart(), partnerTrack(), retainerOfLove());
    }

    /** Pick one novel at random (called when the snake eats a book). */
    public static Story pick(Random rng) {
        List<Story> a = all();
        return a.get(rng.nextInt(a.size()));
    }

    /** Pick a novel whose id isn't in {@code excludeIds} (so a run doesn't repeat one); if all are
     *  excluded, falls back to the full set. */
    public static Story pickExcluding(Random rng, java.util.Set<String> excludeIds) {
        List<Story> fresh = new ArrayList<>();
        for (Story s : all()) {
            if (excludeIds == null || !excludeIds.contains(s.id())) {
                fresh.add(s);
            }
        }
        List<Story> pool = fresh.isEmpty() ? all() : fresh;
        return pool.get(rng.nextInt(pool.size()));
    }

    private static Choice c(String label, String target) {
        return Choice.of(label, target);
    }

    // --- 1. Billable Hearts (senpai = the cobra Partner) ---------------------

    private static Story billableHearts() {
        return Story.builder("billable", "Billable Hearts ♡", "intro")
                .node("intro", "Senior Partner", "partner", "smug", "office",
                        "New blood. A client is dying in Conference Room B and his estate is gloriously "
                                + "contestable. Bill him. Every minute, plus a few he won't be around to dispute.",
                        c("S-senpai... I can't bill a dying man!", "die_conscience"),
                        c("Already done. I billed his next of kin too.", "impress"),
                        c("...What's my cut?", "ask"))
                .node("ask", "Senior Partner", "partner", "neutral", "office",
                        "A cut. NOW you speak my language. Your cut is my regard, and continued "
                                + "circulation of your blood. The man, associate.",
                        c("Fine. Bill the corpse while it's warm.", "impress"),
                        c("On it. (You quietly shave 10%. He's dying, after all.)", "impress"))
                .node("impress", "Ms. Vance", "vance", "seductive", "office",
                        "Cosying up to the Partner already? Bold. But there's one rising star here and her "
                                + "scales are emerald. So... partners, or prey?",
                        c("Partners. We bury the competition and bill the funerals.", "end_ruthless"),
                        c("Can't we just... split the work fairly?", "end_meek"),
                        c("Compliment her scales.", "flirt"))
                .node("flirt", "Ms. Vance", "vance", "smug", "office",
                        "Mm. Cheap flattery. I collect cheap things and ruin them. Eyes up. Partners or prey?",
                        c("Partners. Let's be magnificent and terrible.", "end_ruthless"),
                        c("Prey, probably. I'll keep my head down.", "end_meek"))
                .end("end_ruthless", WIN, "Senior Partner", "partner", "smug", "skyline",
                        "Cold. Efficient. Heartless. *the hood relaxes* This is what affection feels like, "
                                + "for our kind. The firm is yours to bleed. ♡")
                .end("end_meek", MEEK, "Ms. Vance", "vance", "smug", "office",
                        "Adorable. You'll plateau at junior associate, bill your honest forty hours, and, "
                                + "statistically, survive. How quaint.")
                .die("die_conscience", "Mr. Glass", "glass", "angry", "basement",
                        "A CONSCIENCE? At my firm? We don't bill that, we DIGEST it. Open wide, sweetheart.")
                .build();
    }

    // --- 2. Hostile Takeover of My Heart (rival-to-lover = Vance) ------------

    private static Story hostileTakeover() {
        return Story.builder("merger", "Hostile Takeover of My Heart ♡", "intro")
                .node("intro", "Ms. Vance", "vance", "neutral", "boardroom",
                        "We're both up for the Hartwell account, darling. Firm tradition says rivals duel to "
                                + "the death. ...Or we merge. Think of the synergy.",
                        c("Merge. We crush Hartwell together.", "merge"),
                        c("Let's just compete fairly, no tricks.", "end_fair"),
                        c("Actually... warn Hartwell. Let them keep their company.", "die_honest"))
                .node("merge", "Ms. Vance", "vance", "seductive", "skyline",
                        "Mmm. Their CFO has an offshore account and a filthier secret. Do we use the money, "
                                + "or use the man?",
                        c("Freeze the accounts. Clean and bloodless.", "end_clean"),
                        c("Blackmail him. Make it personal.", "end_messy"),
                        c("Both. I'm thorough.", "end_messy"))
                .end("end_clean", WIN, "Ms. Vance", "vance", "smug", "skyline",
                        "Clean. Ruthless. Obscenely profitable. *she coils closer* I think I love you. Or "
                                + "whatever passes for it between predators. The account is ours. ♡")
                .end("end_messy", MID, "Mr. Glass", "glass", "neutral", "basement",
                        "Blackmail leaves fingerprints, but it worked. The account's yours, and so is a thick "
                                + "file we'll hold over you forever. Welcome to the partnership.")
                .end("end_fair", MEEK, "Ms. Vance", "vance", "neutral", "boardroom",
                        "Fair. Honest. You split it down the middle and both walked away with crumbs. You're "
                                + "not built for this... but you lived.")
                .die("die_honest", "Senior Partner", "partner", "angry", "boardroom",
                        "You WARNED them? Tipped off the prey? Loyalty. To a CLIENT. *the doors seal* We'll "
                                + "bill your widow for the wasted hour.")
                .build();
    }

    // --- 3. My Heart is Pro Bono (the wholesome intern is the trap) ----------

    private static Story proBonoHeart() {
        return Story.builder("probono", "My Heart is Pro Bono ♡", "intro")
                .node("intro", "The Intern", "intern", "neutral", "office",
                        "Senpai!! The firm's taking a pro bono case, a children's shelter! Isn't that "
                                + "wonderful? Sign the consent form with me? *sparkly hopeful eyes*",
                        c("Aww, of course! For the children!", "read"),
                        c("Charity, at THIS firm? What's the catch?", "read"),
                        c("Let me read the contract first.", "read"))
                .node("read", "", "", "neutral", "office",
                        "Clause 7(c): 'beneficiaries consent to post-service harvesting.' The shelter is a "
                                + "front. For parts. The intern, beaming beside you, has no idea.",
                        c("Say nothing. Let her sign. Pocket the bonus.", "end_betray"),
                        c("Redact 7(c), sign anyway, tell no one.", "end_redact"),
                        c("Warn her. Get her out.", "die_hero"),
                        c("Blow the whistle. Burn it down.", "die_hero"))
                .end("end_betray", WIN, "Mr. Glass", "glass", "smug", "skyline",
                        "You saw the trap and fed her to it, smiling. That isn't cruelty, that's PARTNERSHIP "
                                + "material. The intern's been 'reassigned.' Top floor's expecting you. ♡")
                .end("end_redact", MID, "Ms. Vance", "vance", "smug", "office",
                        "Tidy. You buried the clause, kept the bonus, told no one. Complicit, deniable, "
                                + "employed. The firm's favourite trio.")
                .die("die_hero", "Senior Partner", "partner", "angry", "basement",
                        "A whistleblower. A HERO. We keep a special shredder for heroes. Felt-lined, we're "
                                + "not monsters. We're worse.")
                .build();
    }

    // --- 4. Partner Track to Your Heart (the boss = Glass) -------------------

    private static Story partnerTrack() {
        return Story.builder("partner_track", "Partner Track to Your Heart ♡", "intro")
                .node("intro", "Mr. Glass", "glass", "smug", "boardroom",
                        "One partnership seat. Three of you. I do love watching associates slither over one "
                                + "another. Impress me.",
                        c("Sabotage the other two.", "sabotage"),
                        c("Find out who he's already picked.", "snoop"),
                        c("Withdraw. Let whoever needs it most have the seat.", "die_selfless"))
                .node("snoop", "The Intern", "intern", "neutral", "office",
                        "Who'd he pick? Nobody, senpai. The 'seat' is a stress test. The chair's wired to a "
                                + "heart monitor. He just likes the show. ...Be careful.",
                        c("Good. Then I sabotage all of them.", "sabotage"),
                        c("Good to know. Quietly.", "sabotage"))
                .node("sabotage", "Ms. Vance", "vance", "seductive", "office",
                        "I can lose a rival's filing deadline... or lose a rival entirely. Discreet, or "
                                + "thorough, darling?",
                        c("Discreet. Just ruin a deadline.", "end_seat"),
                        c("Thorough. Leave a body.", "end_body"))
                .end("end_seat", WIN, "Mr. Glass", "glass", "smug", "boardroom",
                        "A ruined career, no corpse, total deniability. *slow applause* Tasteful. Sit. The "
                                + "chair only bites the greedy and the good. You're neither. You're MINE. ♡")
                .end("end_body", MID, "Senior Partner", "partner", "neutral", "basement",
                        "A body. Sloppy, but the seat's yours, and we'll bill the cleanup to your first three "
                                + "paychecks. Effective. Welcome up.")
                .die("die_selfless", "Mr. Glass", "glass", "angry", "boardroom",
                        "You WITHDREW? For someone who needed it more? Altruism. We don't have a form for "
                                + "that. We have a furnace.")
                .build();
    }

    // --- 5. The Retainer of Love (the doomed client is a real person; the FIRM is the predator) -

    private static Story retainerOfLove() {
        return Story.builder("retainer", "The Retainer of Love ♡", "intro")
                .node("intro", "The Client", "client", "neutral", "office",
                        "P-please, I need a lawyer, fast. The retainer's huge, non-refundable. Just don't "
                                + "ask what the case is. *sweats through his suit*",
                        c("I'll defend you with everything I've got.", "die_defend"),
                        c("Fee first. Then I'll decide how hard to try.", "escort"),
                        c("What am I actually defending you from?", "question"))
                .node("question", "The Client", "client", "smug", "skyline",
                        "Don't ask. Take the fee, smile, look away. I'm begging you to be a coward, for both "
                                + "our sakes.",
                        c("Fine. Fee first, conscience never.", "escort"),
                        c("No. You deserve a real defence.", "die_defend"))
                .node("escort", "Ms. Vance", "vance", "smug", "skyline",
                        "Oh, you sweet shark. He isn't a client to defend. He's the firm's next MEAL, and "
                                + "you're his escort. Walk him to the closing downstairs; the retainer's our "
                                + "finder's fee. He has no idea.",
                        c("Walk him down. Collect the fee.", "end_deliver"),
                        c("Look away. Not my problem, not my profit.", "end_coward"),
                        c("Warn him. Get him out the back.", "die_save"))
                .end("end_deliver", WIN, "Mr. Glass", "glass", "smug", "skyline",
                        "You marched a man who trusted you to the closing without a blink. Flawless. The "
                                + "finder's fee is yours. You'll go far here. He won't. ♡")
                .end("end_coward", MEEK, "Ms. Vance", "vance", "neutral", "skyline",
                        "You looked away. He went down alone; you touched nothing and kept nothing but your "
                                + "job. Survival, technically. The firm respects 'technically'.")
                .die("die_defend", "Mr. Glass", "glass", "angry", "basement",
                        "An actual DEFENCE? For the meal? Sincerity. We screen for it here, and remove it. "
                                + "With a melon baller. Hold still.")
                .die("die_save", "The Client", "client", "neutral", "basement",
                        "You warned me? Tried to get me out? *the doors lock* ...Now there are two on the "
                                + "menu. The closing thanks you both for attending.")
                .build();
    }
}

package com.mrmcclean.classicpt.bots;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.lwjgl.input.Mouse;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Classic's bot test, armed for one launch by the file .minecraft/classicbots-test/run (removed when it starts). Runs
 * behind other windows: never takes the mouse, closes the pause menu, music and sound off in memory (put back before
 * the end, never saved), the player can't be hurt. Steps:
 *  1. the level Classic starts in, bots on in memory;
 *  2. /bot spawn Tess: a bot next to the player;
 *  3. "Tess follow me", the player moved about 12 blocks: the bot walks up to within 4 blocks;
 *  4. the jobs, by chat as a player would: bring me 4 wood, bring me 3 cobblestone (Indev: it crafts its own
 *     pickaxe), set up a base here (Indev: with a chest), guard (a zombie by it dies), build a hut here;
 *  5. the bot gets 5 cobblestone; the level is saved with the game's own writer and read back with its own reader:
 *     the bot is in it with its name, owner and things; that level is opened and the bot ticks in it.
 * Report in classicbots-test/<date>/report.txt, then the game closes.
 */
public final class BotTest {
    private BotTest() {}

    public static boolean ON;
    private static int step, frames;
    private static long stepStart, t0;
    private static boolean music, sound;
    private static final List<String> lines = new ArrayList<>();
    private static int passed, failed;
    private static double goalX, goalZ;
    private static int ticksBefore;
    private static File dir;
    private static double lastX, lastZ;
    private static long lastSecond = -1;
    private static int jumps;
    private static int tagsBefore, tagsOn = -1, spokenBefore;
    private static boolean updateLeaked, answered, offAsked;

    static void init(Minecraft mc) {
        File run = new File("classicbots-test", "run");
        if (!run.isFile() || !run.delete()) return;
        ON = true;
        t0 = System.currentTimeMillis();
        dir = new File("classicbots-test", new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss").format(new Date()));
        dir.mkdirs();
        Bots.log("bot test armed (Classic)");
    }

    private static void check(boolean ok, String what) {
        if (ok) passed++; else failed++;
        String l = (ok ? "PASS " : "FAIL ") + what + String.format(" (%.0f s)", (System.currentTimeMillis() - t0) / 1000.0);
        lines.add(l);
        Bots.log("Test: " + l);
    }

    private static void note(String s) { lines.add("     " + s); Bots.log("Test: " + s); }

    private static void next() { step++; stepStart = System.currentTimeMillis(); }

    private static long inStep() { return System.currentTimeMillis() - stepStart; }

    static void frame(Minecraft mc) {
        if (!ON) return;
        frames++;
        if (Mouse.isCreated() && Mouse.isGrabbed()) Mouse.setGrabbed(false); // never take the mouse
        if (mc.screen != null && !(mc.screen instanceof ChatScreen) && mc.world != null) mc.openScreen(null); // pause menu
        if (mc.options != null && step == 0) { music = mc.options.music; sound = mc.options.sound; mc.options.music = false; mc.options.sound = false; }
        if (mc.world == null) {
            if (lastState != null) { check(false, "the level went away during step " + step + "; just before: " + lastState + ", now screen " + (mc.screen == null ? "none" : mc.screen.getClass().getName())); finish(mc); return; }
            if (System.currentTimeMillis() - t0 > 120000) { check(false, "a level to test in"); finish(mc); }
            return;
        }
        PlayerEntity p = mc.player;
        if (p == null) return;
        lastState = String.format("player %.1f %.1f %.1f health %d removed %s, screen %s", p.x, p.y, p.z, p.health, p.removed, mc.screen == null ? "none" : mc.screen.getClass().getName());
        p.health = 20; // the player takes no harm while the test runs
        try { run(mc, p); } catch (Throwable t) { check(false, "step " + step + " threw " + trace(t)); finish(mc); }
    }

    private static net.minecraft.entity.mob.MobEntity zombie;
    private static Blueprint plan;
    private static ChatScreen chat;
    /** The player and screen last frame (reported if the level goes away). */
    private static String lastState;
    private static World world;
    private static int px, py, pz, pf;
    /** Classic has no chests: a base is its home only. */
    private static final boolean NO_CHESTS = true;

    private static void say(PlayerEntity p, String msg) { Bots.chat(p, msg); }

    private static int has(PlayerEntity p, int id) {
        int n = 0;
        for (ItemStack s : p.inventory.items) if (s != null && s.id == id) n += s.size;
        return n;
    }

    private static String status(World w) { BotEntity b = bot(w); return b == null ? "gone" : b.brain.statusLine() + ", carrying " + b.brain.inventoryLine(); }

    /** Passes when ok, fails after the limit (seconds); either way on to the next step. */
    private static void waitFor(boolean ok, int seconds, String what) {
        if (ok) { check(true, what + " in " + inStep() / 1000 + " s"); next(); }
        else if (inStep() > seconds * 1000L) { check(false, what + " after " + seconds + " s; bot: " + status(world)); next(); }
    }

    /** The frame as drawn (the back buffer, before the game shows it), upside up. */
    private static java.awt.image.BufferedImage picture(Minecraft mc, File f) {
        try {
            int w = mc.displayWidth, h = mc.displayHeight;
            java.nio.ByteBuffer buf = org.lwjgl.BufferUtils.createByteBuffer(w * h * 3);
            org.lwjgl.opengl.GL11.glPixelStorei(org.lwjgl.opengl.GL11.GL_PACK_ALIGNMENT, 1);
            org.lwjgl.opengl.GL11.glReadPixels(0, 0, w, h, org.lwjgl.opengl.GL11.GL_RGB, org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE, buf);
            java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                int i = ((h - 1 - y) * w + x) * 3;
                img.setRGB(x, y, (buf.get(i) & 255) << 16 | (buf.get(i + 1) & 255) << 8 | buf.get(i + 2) & 255);
            }
            javax.imageio.ImageIO.write(img, "png", f);
            return img;
        } catch (Throwable t) { note("no picture: " + t); return null; }
    }

    /** About 5 blocks from the bot on ground at its own level (no ledge between them), else 5 blocks east. */
    private static double[] level(World w, BotEntity b) {
        int bx = (int) Math.floor(b.x), by = (int) Math.floor(b.y), bz = (int) Math.floor(b.z);
        for (int k = 0; k < 8; k++) {
            int x = bx + (int) Math.round(Math.cos(k * Math.PI / 4) * 5), z = bz + (int) Math.round(Math.sin(k * Math.PI / 4) * 5);
            if (w.getHeight(x, z) == by && w.getBlock(x, by - 1, z) != 8 && w.getBlock(x, by - 1, z) != 9) return new double[]{x + 0.5, by, z + 0.5};
        }
        return new double[]{b.x + 5, b.y, b.z};
    }

    private static java.awt.image.BufferedImage withBot;
    private static double[] botAt;

    private static final int[][] SIDES = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
    private static int camDir;

    /** A side to look at the bot from, 3 blocks off and 2.4 up, with no block between the eye and its middle. */
    private static int clearSide(World w, BotEntity b) {
        for (int d = 0; d < 4; d++) {
            net.minecraft.util.math.Vec3d eye = new net.minecraft.util.math.Vec3d((float) (b.x + SIDES[d][0] * 3), (float) (b.y + 2.4), (float) (b.z + SIDES[d][1] * 3));
            net.minecraft.util.math.Vec3d mid = new net.minecraft.util.math.Vec3d((float) b.x, (float) (b.y + 0.9), (float) b.z);
            int ex = (int) Math.floor(b.x + SIDES[d][0] * 3), ey = (int) Math.floor(b.y + 2.4), ez = (int) Math.floor(b.z + SIDES[d][1] * 3);
            if (w.getBlock(ex, ey, ez) != 0 || w.getBlock(ex, ey - 1, ez) != 0) continue; // the eye in the open (a ray from inside a block finds nothing)
            if (w.rayTrace(eye, mid) == null) return d;
        }
        return 0;
    }

    /** The player 3 blocks from the bot (on the clear side) and a little above, looking at its middle. */
    private static void camera(PlayerEntity p, BotEntity b) { camera(p, b.x, b.y, b.z); }

    private static void camera(PlayerEntity p, double bx, double by, double bz) {
        double ex = bx + SIDES[camDir][0] * 3, ey = by + 2.4, ez = bz + SIDES[camDir][1] * 3, dx = bx - ex, dz = bz - ez;
        p.setPosition(ex, ey, ez);
        p.velocityX = 0; p.velocityY = 0; p.velocityZ = 0;
        p.yaw = p.prevYaw = (float) Math.toDegrees(Math.atan2(dx, -dz));
        p.pitch = p.prevPitch = (float) Math.toDegrees(Math.atan2(ey - (by + 0.9), 3));
    }

    /** The exception, its cause, and where they came from (a few frames). */
    private static String trace(Throwable t) {
        StringBuilder sb = new StringBuilder();
        for (Throwable c = t; c != null; c = c.getCause() == c ? null : c.getCause()) {
            if (sb.length() > 0) sb.append(System.lineSeparator()).append("       caused by ");
            sb.append(c);
            StackTraceElement[] st = c.getStackTrace();
            for (int i = 0; i < Math.min(4, st.length); i++) sb.append(System.lineSeparator()).append("         at ").append(st[i]);
        }
        return sb.toString();
    }

    private static BotEntity bot(World w) {
        for (BotEntity b : Bots.bots(w)) if (b.botName().equals("Tess")) return b;
        return null;
    }

    private static void run(Minecraft mc, PlayerEntity p) throws Exception {
        World w = mc.world; world = w;
        switch (step) {
            case 0: {
                BotConfig.enabled = true; // in memory: not saved
                // Tab is the bot list: Classic's arrow is its own key in Controls ("Fire arrow", Q to start with)
                String last = "";
                int key = -1;
                try {
                    Object[] binds = mc.options.obf$s;
                    Object k = binds[binds.length - 1];
                    last = (String) k.getClass().getField("a").get(k);
                    key = (Integer) Class.forName("com.mrmcclean.classicpt.Hooks", true, BotTest.class.getClassLoader()).getMethod("arrowKey").invoke(null);
                } catch (Throwable t) { last = String.valueOf(t); }
                check("Fire arrow".equals(last) && key == org.lwjgl.input.Keyboard.KEY_Q, "arrow key: Controls has \"" + last + "\", the game fires on " + org.lwjgl.input.Keyboard.getKeyName(key) + " (not Tab); the game patch: " + System.getProperty("classicpt.arrows", "never ran"));
                next();
                return;
            }
            case 1: {
                if (inStep() < 2000) return; // the level settles
                Bots.chat(p, "/bot spawn Tess");
                BotEntity b = bot(w);
                double sd = b == null ? 99 : Math.sqrt((b.x - p.x) * (b.x - p.x) + (b.z - p.z) * (b.z - p.z));
                check(b != null && sd < 4, "/bot spawn Tess: a bot by the player" + (b == null ? "" : String.format(" (%.1f blocks across)", sd)));
                if (b != null) note(String.format("player %.1f %.1f %.1f, bot %.1f %.1f %.1f", p.x, p.y, p.z, b.x, b.y, b.z));
                // a new bot gets on with things on its own; the rest of the test orders Tess about, so she follows from here
                if (b != null) { check(b.brain.mode == Brain.Mode.AUTO, "a new bot is on its own by default (" + b.brain.mode + ")"); b.brain.mode = Brain.Mode.FOLLOW; b.brain.job = Brain.Job.NONE; b.brain.after = Brain.Job.NONE; b.brain.want = null; b.stopMoving(); }
                if (b == null) { finish(mc); return; }
                step = 40; stepStart = System.currentTimeMillis(); // a picture of it first
                return;
            }
            case 2: { // order it to follow, then walk off
                if (inStep() < 3000) return;
                Bots.chat(p, "Tess follow me");
                double[] spot = spotAway(w, p, 12);
                if (spot == null) { check(false, "a dry spot 12 blocks away"); finish(mc); return; }
                p.setPosition(spot[0], spot[1] + 1.62 + 0.05, spot[2]);
                goalX = spot[0]; goalZ = spot[2];
                note(String.format("player moved to %.1f %.1f %.1f, bot at %.1f %.1f %.1f", spot[0], spot[1], spot[2], bot(w).x, bot(w).y, bot(w).z));
                next();
                return;
            }
            case 3: {
                BotEntity b = bot(w);
                double d = b == null ? 99 : Math.sqrt((b.x - goalX) * (b.x - goalX) + (b.z - goalZ) * (b.z - goalZ));
                if (b != null) {
                    if (lastX != 0 && Math.abs(b.x - lastX) + Math.abs(b.z - lastZ) > 3) jumps++;
                    lastX = b.x; lastZ = b.z;
                    if (inStep() / 1000 != lastSecond) { lastSecond = inStep() / 1000; note(String.format("t %d s: bot %.1f %.1f %.1f ground %s walking %s yaw %.0f mode %s job %s, %.1f to go", lastSecond, b.x, b.y, b.z, b.onGround, b.walkedLast, b.yaw, b.brain.mode, b.brain.job, d)); }
                }
                if (d < 4) { check(jumps == 0, String.format("follows on foot: came to %.1f blocks of the player in %.1f s%s", d, inStep() / 1000.0, jumps > 0 ? " (but jumped there: " + jumps + " teleport)" : "")); next(); return; }
                if (inStep() > 45000) { check(false, String.format("follows: still %.1f blocks away after 45 s (mode %s)", d, b == null ? "-" : String.valueOf(b.brain.mode))); next(); }
                return;
            }
            // ---- can it be seen: a picture with it (bot.png) and without it (no-bot.png); the difference is the bot
            case 40: {
                BotEntity b = bot(w);
                b.brain.mode = Brain.Mode.STAY;
                if (botAt == null) { botAt = new double[3]; double[] open = spotAway(w, p, 8); if (open != null) b.setPositionAndAngles(open[0], open[1] + 0.1, open[2], 0, 0); camDir = clearSide(w, b); } // once: out in the open, not under a roof, and a side to look from with nothing in between
                camera(p, b);
                if (inStep() > 1500) next();
                return;
            }
            case 41: { // the frame with the bot, then the bot out of the way for a moment
                BotEntity b = bot(w);
                camera(p, b);
                withBot = picture(mc, new File(dir, "bot.png"));
                BotList.draw(mc); // what holding Tab shows, over the same frame
                picture(mc, new File(dir, "bot-list.png"));
                botAt = new double[]{b.x, b.y, b.z};
                b.setPositionAndAngles(b.x, b.y + 60, b.z, 0, 0);
                step = 43; stepStart = System.currentTimeMillis();
                return;
            }
            case 43: { // the same view without it: what changed in the middle of the picture is the bot
                BotEntity b = bot(w);
                b.setPositionAndAngles(botAt[0], botAt[1] + 60, botAt[2], 0, 0);
                b.velocityY = 0;
                camera(p, botAt[0], botAt[1], botAt[2]);
                if (inStep() < 400) return;
                java.awt.image.BufferedImage without = picture(mc, new File(dir, "no-bot.png"));
                b.setPositionAndAngles(botAt[0], botAt[1], botAt[2], 0, 0);
                int changed = 0, sum = 0, n = 0;
                if (withBot != null && without != null) {
                    int w0 = withBot.getWidth(), h0 = withBot.getHeight();
                    for (int y = h0 / 4; y < h0 * 3 / 4; y++) for (int x = w0 / 3; x < w0 * 2 / 3; x++) {
                        n++;
                        int a = withBot.getRGB(x, y), c = without.getRGB(x, y);
                        int d = Math.abs((a >> 16 & 255) - (c >> 16 & 255)) + Math.abs((a >> 8 & 255) - (c >> 8 & 255)) + Math.abs((a & 255) - (c & 255));
                        if (d > 40) { changed++; sum += ((a >> 16 & 255) + (a >> 8 & 255) + (a & 255)) / 3; }
                    }
                }
                int pct = n == 0 ? 0 : changed * 100 / n, bright = changed == 0 ? 0 : sum / changed;
                check(pct >= 3, "the bot is drawn: " + pct + "% of the middle of the picture is the bot (bot.png against no-bot.png), drawn at brightness " + bright + " of 255");
                // the chat box, as a player uses it: T opens it, typed keys, then Enter
                chat = new ChatScreen("");
                mc.openScreen(chat);
                for (char c : "Tess what do you have".toCharArray()) chat.keyPressed(c, 0);
                bot(w).brain.lastReport = "";
                step = 42; stepStart = System.currentTimeMillis();
                return;
            }
            case 42: { // a frame later: the box is drawn; its picture, then Enter
                if (inStep() < 300) return;
                picture(mc, new File(dir, "chat-box.png"));
                boolean open = mc.screen == chat;
                chat.keyPressed((char) 13, org.lwjgl.input.Keyboard.KEY_RETURN);
                String answer = bot(w).brain.lastReport;
                check(open && mc.screen == null && !answer.isEmpty(), "chat box: typed \"Tess what do you have\", Tess answered \"" + answer + "\"" + (open ? "" : " (the box was not open)"));
                bot(w).brain.mode = Brain.Mode.FOLLOW;
                step = 2; stepStart = System.currentTimeMillis();
                return;
            }
            // ---- the jobs: gather, base, guard, build (each with a time limit; a fail goes on to the next)
            case 4: say(p, "Tess bring me 4 wood"); next(); return;
            case 5: waitFor(has(p, 17) >= 4, 240, "gather wood: the player got " + has(p, 17) + " logs"); return;
            case 6: say(p, "Tess bring me 3 cobblestone"); next(); return;
            case 7: waitFor(has(p, 4) >= 3, 360, "gather cobblestone: the player got " + has(p, 4)); return;
            case 8: { BotEntity b = bot(w); b.add(new ItemStack(5, 8, 0)); say(p, "Tess set up a base here"); next(); return; }
            case 9: { BotEntity b = bot(w); int[] c = b.brain.findNear(new int[]{54}, b.homeX, b.homeY, b.homeZ, 6, false);
                waitFor(b.hasHome && (c != null || NO_CHESTS), 20, "base: home set" + (NO_CHESTS ? " (no chests in this game)" : ", chest " + (c == null ? "none" : c[0] + " " + c[1] + " " + c[2]))); return; }
            case 10: {
                BotEntity b = bot(w);
                say(p, "Tess guard");
                w.difficulty = 1;
                double[] at = level(w, b); net.minecraft.entity.mob.monster.ZombieEntity z = new net.minecraft.entity.mob.monster.ZombieEntity(w, (float) at[0], (float) at[1] + 1.62F, (float) at[2]); // a Classic mob's y is its eyes
                w.addEntity(z);
                zombie = z;
                next();
                return;
            }
            case 11:
                waitFor(zombie.removed || zombie.health <= 0, 60, "guard: the zombie " + (zombie.removed || zombie.health <= 0 ? "is dead" : "still has " + zombie.health + " health") + ", bot health " + bot(w).health);
                if (step == 12 && !zombie.removed) zombie.remove(); // a zombie it can't reach would keep it from every later job
                return;
            case 12: {
                w.difficulty = 0; // back to peaceful after the guard step (as Alpha's test): a creeper took the top off a finished hut
                BotEntity b = bot(w);
                b.brain.mode = Brain.Mode.STAY;
                int sx = (int) Math.floor(p.x), sz = (int) Math.floor(p.z);
                p.setPosition(sx + 0.5, w.getHeight(sx, sz) + 1.62 + 0.1, sz + 0.5);
                b.setPositionAndAngles(p.x + 1, p.y - 1.62 + 0.1, p.z, 0, 0);
                b.add(new ItemStack(4, 64, 0)); b.add(new ItemStack(5, 32, 0));
                say(p, "Tess build a hut here");
                plan = b.brain.plan; px = b.brain.planX; py = b.brain.planY; pz = b.brain.planZ; pf = b.brain.planF;
                if (plan == null) { check(false, "build: no plan made (" + b.brain.lastReport + ")"); step = 30; stepStart = System.currentTimeMillis(); return; }
                next();
                return;
            }
            case 13: {
                { Brain br = bot(w).brain; if (br.plan != null) { plan = br.plan; px = br.planX; py = br.planY; pz = br.planZ; pf = br.planF; } } // the plan it is building (an order heard twice starts it again)
                int ok = 0, total = 0;
                StringBuilder missing = new StringBuilder();
                for (int[] pb : plan.blocks) {
                    if (pb[3] == 0) continue;
                    total++;
                    int[] at = Blueprint.place(pb, px, py, pz, pf);
                    int have = w.getBlock(at[0], at[1], at[2]);
                    if (have == pb[3]) ok++;
                    else if (missing.length() < 160) missing.append(" ").append(at[0]).append(",").append(at[1]).append(",").append(at[2]).append(" wants ").append(pb[3]).append(" has ").append(have).append(";");
                }
                boolean done = bot(w).brain.plan == null && ok * 10 >= total * 9;
                if (done || inStep() > 150000) { check(done, "build a hut: " + ok + " of " + total + " blocks in place in " + inStep() / 1000 + " s" + (done ? "" : "; missing:" + missing + " bot: " + status(w))); step = 30; stepStart = System.currentTimeMillis(); }
                return;
            }
            case 30: { // save and load with the level
                BotEntity b = bot(w);
                if (b == null) { check(false, "the bot is still there before saving"); finish(mc); return; }
                b.add(new ItemStack(4, 5, 0));
                File f = new File(dir, "BotTest.mine");
                save(mc, w, f);
                World loaded = load(mc, f);
                BotEntity lb = loaded == null ? null : bot(loaded);
                check(lb != null, "saved and read back: the bot is in the level file" + (f.isFile() ? " (" + f.length() / 1024 + " KB)" : ""));
                if (lb != null) {
                    check(lb.owner.equals(b.owner), "its owner came back (" + lb.owner + ")");
                    check(lb.count(4) == b.count(4) && lb.count(4) >= 5, "its things came back (" + lb.count(4) + " cobblestone)");
                    check(lb instanceof ClassicBot, "it is a bot again, not a plain mob");
                    mc.setWorld(loaded);
                    lb = bot(mc.world);
                    ticksBefore = lb == null ? -1 : lb.ticks;
                }
                next();
                return;
            }
            case 31: {
                if (inStep() < 3000) return;
                BotEntity b = bot(w);
                check(b != null && b.ticks > ticksBefore + 20, "the loaded level runs the bot (" + (b == null ? "no bot" : (b.ticks - ticksBefore) + " ticks") + ")");
                step = 50; stepStart = System.currentTimeMillis();
                VoiceChat.testVoice(new File("."), "Tess"); // voice chat: a line to speech (Piper) and back (Whisper), off the game's thread
                return;
            }
            case 50: {
                String r = VoiceChat.testResult;
                if (r == null && inStep() < 60000) return;
                check(r != null && r.startsWith("PASS"), r == null ? "voice: no answer in 60 s" : r.substring(5));
                // the progression guide (on by default): "what next?", "help me with that", and "Nice" once the step is done
                Guide.done.remove(p.name); // a fresh start each run: the guide file keeps a player's steps (Classic 0.30 has only 5, all done after one run)
                BotEntity b = bot(w);
                if (b == null) { finish(mc); return; }
                BotConfig.progressionGuide = true;
                b.brain.mode = Brain.Mode.FOLLOW; b.brain.job = Brain.Job.NONE; b.brain.lastReport = "";
                say(p, "Tess what next?");
                step = 52; stepStart = System.currentTimeMillis();
                return;
            }
            case 52: {
                BotEntity b = bot(w);
                if (!(b.brain.lastReport.startsWith("Next:") || b.brain.lastReport.contains("every step")) && inStep() < 5000) return;
                check(b.brain.lastReport.startsWith("Next:") || b.brain.lastReport.contains("every step"), "guide: \"" + b.brain.lastReport + "\" (" + Guide.steps().size() + " steps in this version)");
                b.brain.lastReport = "";
                say(p, "Tess help me with that");
                step = 53; stepStart = System.currentTimeMillis();
                return;
            }
            case 53: {
                BotEntity b = bot(w);
                boolean fetching = b.brain.job == Brain.Job.GATHER || b.brain.job == Brain.Job.HUNT;
                if (!fetching && inStep() < 5000) return;
                check(fetching, "guide: \"help me with that\" sets it fetching: " + b.brain.statusLine() + " " + b.brain.lastReport);
                b.brain.lastReport = "";
                { int gi = Guide.next(p); if (gi >= 0 && Guide.steps().get(gi).dim == 0) { int gid = Guide.steps().get(gi).ids[0], gn = Guide.steps().get(gi).amount; b.brain.job = Brain.Job.NONE; b.add(new ItemStack(gid, gn, 0)); b.giveTo(p, gid, gn); } } // as if you'd made it: handed over, so the step is done
                step = 54; stepStart = System.currentTimeMillis();
                return;
            }
            case 54: {
                BotEntity b = bot(w);
                boolean nice = b.brain.lastReport.startsWith("Nice");
                if (!nice && inStep() < 240000) return;
                check(nice, "guide: the step done is noticed: \"" + b.brain.lastReport + "\"" + (nice ? "" : "; " + b.brain.statusLine()));
                // with nothing from you (keeping house), a bot works through the guide on its own
                b.brain.mode = Brain.Mode.HOME; b.brain.job = Brain.Job.NONE; b.brain.guideDone.clear(); b.brain.guideRest = 0; b.brain.guideTries = 0;
                step = 57; stepStart = System.currentTimeMillis();
                return;
            }
            case 57: {
                BotEntity b = bot(w);
                boolean working = b.brain.job == Brain.Job.GATHER || b.brain.job == Brain.Job.HUNT;
                if (!working && inStep() < 15000) return;
                check(working, "guide: left to itself it works on its own next step: " + b.brain.statusLine());
                // infinite progression: with every step done it keeps going (mining, wood, building by home, exploring)
                b.brain.job = Brain.Job.NONE;
                for (int i = 0; i < Guide.steps().size(); i++) b.brain.guideDone.add(i);
                b.brain.guideRest = 0;
                boolean more = Guide.work(b.brain) && b.brain.job != Brain.Job.NONE;
                check(more, "guide: with every step done it keeps going: " + b.brain.statusLine());
                // names over the bots (on by default): drawn with the switch on, none with it off
                BotConfig.nametags = true;
                b.brain.mode = Brain.Mode.STAY; b.brain.job = Brain.Job.NONE;
                tagsBefore = BotList.tagsDrawn;
                step = 55; stepStart = System.currentTimeMillis();
                return;
            }
            case 55: {
                BotEntity b = bot(w);
                camera(p, b);
                if (tagsOn < 0 && inStep() > 3000) { tagsOn = BotList.tagsDrawn - tagsBefore; BotConfig.nametags = false; tagsBefore = BotList.tagsDrawn; }
                if (inStep() < 6000) return;
                int off = BotList.tagsDrawn - tagsBefore;
                BotConfig.nametags = true;
                check(tagsOn > 0 && off == 0, "names over the bots: drawn " + tagsOn + " times in 3 s with the switch on, " + off + " with it off");
                // bot chat levels: replies lets an answer through but not an update; off lets nothing through
                BotConfig.botChat = "replies";
                b.brain.replyUntil = 0;
                spokenBefore = b.brain.spoken;
                b.brain.say("Just an update on what I'm doing.");
                updateLeaked = b.brain.spoken != spokenBefore;
                spokenBefore = b.brain.spoken;
                say(p, "Tess what next?");
                step = 58; stepStart = System.currentTimeMillis();
                return;
            }
            case 58: {
                BotEntity b = bot(w);
                if (inStep() < 2000) return;
                if (!offAsked) { answered = b.brain.spoken > spokenBefore; BotConfig.botChat = "off"; spokenBefore = b.brain.spoken; say(p, "Tess what next?"); offAsked = true; return; }
                if (inStep() < 5000) return;
                boolean silent = b.brain.spoken == spokenBefore;
                BotConfig.botChat = "all";
                check(!updateLeaked && answered && silent, "bot chat: replies keeps updates quiet (" + !updateLeaked + ") and answers (" + answered + "); off says nothing (" + silent + ")");
                // bots join you by themselves (autoSpawn, 3 as in Tekkit): your bots made up to 3, on their own; then a town of their own
                Bots.spawnUpTo(p);
                say(p, "Tess do your own thing");
                step = 59; stepStart = System.currentTimeMillis();
                return;
            }
            case 59: {
                if (inStep() < 7000) return; // each new one picks a home and starts on something
                int mine = 0;
                boolean own = true;
                StringBuilder what = new StringBuilder();
                for (BotEntity x : Bots.bots(w)) if (x.owner.equals(p.name)) {
                    mine++;
                    if (x.botName().equals("Tess")) continue;
                    own &= x.brain.mode == Brain.Mode.AUTO && (x.hasHome || x.brain.job != Brain.Job.NONE);
                    what.append(x.botName()).append(": ").append(x.brain.statusLine()).append(x.hasHome ? " (home set)" : "").append("; ");
                }
                check(mine >= Math.min(BotConfig.autoSpawn, BotConfig.maxBotsPerPlayer), "auto spawn: " + mine + " bots of yours, autoSpawn " + BotConfig.autoSpawn);
                check(own && mine > 1, "on their own from the start (no orders): " + what);
                BotEntity t = bot(w);
                check(t != null && t.brain.mode == Brain.Mode.AUTO, "\"do your own thing\" sends a bot back to its own work" + (t == null ? "" : ": " + t.brain.statusLine()));
                for (BotEntity x : Bots.bots(w)) x.brain.townLeft = false; // the town steps called Tess off one earlier
                String town = Bots.autoTown(p);
                int in = 0; for (BotEntity x : Bots.bots(w)) if (x.owner.equals(p.name) && x.brain.town != null) in++;
                check(town != null && in >= 3, "a town founds itself with " + in + " bots on their own: " + town);
                finish(mc);
                return;
            }
        }
    }

    /** Ground about n blocks from the player that a bot can stand on (not water), in 8 directions. */
    private static double[] spotAway(World w, PlayerEntity p, int n) {
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4;
            int x = (int) Math.floor(p.x + Math.cos(a) * n), z = (int) Math.floor(p.z + Math.sin(a) * n);
            int y = w.getHeight(x, z);
            if (y <= 1) continue;
            int below = w.getBlock(x, y - 1, z);
            if (below == 8 || below == 9 || below == 10 || below == 11 || below == 18) continue;
            return new double[]{x + 0.5, y, z + 0.5};
        }
        return null;
    }

    // ------------------------------------------------------------------ the game's own level writer and reader (its names)

    private static void save(Minecraft mc, World w, File f) throws Exception {
        Class<?> io = Class.forName("com.mojang.minecraft.level.a", true, mc.getClass().getClassLoader());
        try (OutputStream o = new BufferedOutputStream(new FileOutputStream(f))) {
            io.getMethod("a", w.getClass(), OutputStream.class).invoke(null, w, o);
        }
    }

    private static World load(Minecraft mc, File f) throws Exception {
        ClassLoader cl = mc.getClass().getClassLoader();
        Class<?> io = Class.forName("com.mojang.minecraft.level.a", true, cl);
        Object loader = io.getConstructor(Class.forName("com.mojang.minecraft.i", true, cl)).newInstance((Object) null);
        try (InputStream in = new FileInputStream(f)) {
            return (World) io.getMethod("a", InputStream.class).invoke(loader, in);
        }
    }

    private static void finish(Minecraft mc) {
        ON = false;
        if (mc.options != null) { mc.options.music = music; mc.options.sound = sound; }
        lines.add(0, "Classic Bots test, Classic 0.30: " + passed + " passed, " + failed + " failed");
        try (PrintWriter pw = new PrintWriter(new FileOutputStream(new File(dir, "report.txt")))) { for (String l : lines) pw.println(l); }
        catch (Exception e) { Bots.log("Test: could not write the report: " + e); }
        Bots.log("Test done: " + passed + " passed, " + failed + " failed, report in " + dir.getAbsolutePath());
        System.exit(failed == 0 ? 0 : 1);
    }

}

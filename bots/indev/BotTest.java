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
 * Indev's bot test, armed for one launch by the file .minecraft/classicbots-test/run (removed when it starts). Runs
 * behind other windows: never takes the mouse, closes the pause menu, music and sound off in memory (put back before
 * the end, never saved), the player can't be hurt. Steps:
 *  1. a small level (from the title screen), bots on in memory;
 *  2. /bot spawn Tess: a bot next to the player;
 *  3. "Tess follow me", the player moved about 12 blocks: the bot walks up to within 4 blocks;
 *  4. the bot gets 5 cobblestone; the level is saved with the game's own writer and read back with its own reader:
 *     the bot is in it with its name, owner and things; that level is opened and the bot ticks in it.
 * Report in classicbots-test/<date>/report.txt, then the game closes.
 */
public final class BotTest {
    private BotTest() {}

    public static boolean ON;
    private static int step, frames;
    private static long stepStart, t0;
    private static boolean generated, music, sound;
    private static final List<String> lines = new ArrayList<>();
    private static int passed, failed;
    private static double goalX, goalZ;
    private static int ticksBefore;
    private static File dir;
    private static double lastX, lastZ;
    private static long lastSecond = -1;
    private static int jumps;

    static void init(Minecraft mc) {
        File run = new File("classicbots-test", "run");
        if (!run.isFile() || !run.delete()) return;
        ON = true;
        t0 = System.currentTimeMillis();
        dir = new File("classicbots-test", new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss").format(new Date()));
        dir.mkdirs();
        Bots.log("bot test armed (Indev)");
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
        if (mc.screen != null && !(mc.screen instanceof ChatScreen) && (mc.world != null || frames > 100)) mc.openScreen(null); // pause or title menu
        if (mc.options != null && step == 0) { music = mc.options.music; sound = mc.options.sound; mc.options.music = false; mc.options.sound = false; }
        if (mc.world == null) {
            if (frames > 100 && !generated) { generated = true; note("making a small level"); generate(mc); }
            if (System.currentTimeMillis() - t0 > 120000) { check(false, "a level to test in"); finish(mc); }
            return;
        }
        PlayerEntity p = mc.player;
        if (p == null) return;
        p.health = 20; // the player takes no harm while the test runs
        try { run(mc, p); } catch (Throwable t) { check(false, "step " + step + " threw " + t); finish(mc); }
    }

    private static BotEntity bot(World w) {
        for (BotEntity b : Bots.bots(w)) if (b.botName().equals("Tess")) return b;
        return null;
    }

    private static void run(Minecraft mc, PlayerEntity p) throws Exception {
        World w = mc.world;
        switch (step) {
            case 0:
                BotConfig.enabled = true; // in memory: not saved
                next();
                return;
            case 1: {
                if (inStep() < 2000) return; // the level settles
                Bots.chat(p, "/bot spawn Tess");
                BotEntity b = bot(w);
                double sd = b == null ? 99 : Math.sqrt((b.x - p.x) * (b.x - p.x) + (b.z - p.z) * (b.z - p.z));
                check(b != null && sd < 4, "/bot spawn Tess: a bot by the player" + (b == null ? "" : String.format(" (%.1f blocks across)", sd)));
                if (b != null) note(String.format("player %.1f %.1f %.1f, bot %.1f %.1f %.1f", p.x, p.y, p.z, b.x, b.y, b.z));
                if (b == null) { finish(mc); return; }
                next();
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
            case 4: { // save and load with the level
                BotEntity b = bot(w);
                if (b == null) { check(false, "the bot is still there before saving"); finish(mc); return; }
                b.add(new ItemStack(4, 5, 0));
                File f = new File(dir, "BotTest.mclevel");
                save(mc, w, f);
                World loaded = load(mc, f);
                BotEntity lb = loaded == null ? null : bot(loaded);
                check(lb != null, "saved and read back: the bot is in the level file" + (f.isFile() ? " (" + f.length() / 1024 + " KB)" : ""));
                if (lb != null) {
                    check(lb.owner.equals(b.owner), "its owner came back (" + lb.owner + ")");
                    check(lb.count(4) == b.count(4) && lb.count(4) >= 5, "its things came back (" + lb.count(4) + " cobblestone)");
                    check(lb instanceof IndevBot, "it is a bot again, not a plain mob");
                    mc.setWorld(loaded);
                    lb = bot(mc.world);
                    ticksBefore = lb == null ? -1 : lb.ticks;
                }
                next();
                return;
            }
            case 5: {
                if (inStep() < 3000) return;
                BotEntity b = bot(w);
                check(b != null && b.ticks > ticksBefore + 20, "the loaded level runs the bot (" + (b == null ? "no bot" : (b.ticks - ticksBefore) + " ticks") + ")");
                finish(mc);
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

    private static void generate(Minecraft mc) {
        try { mc.getClass().getMethod("a", int.class, int.class, int.class, int.class).invoke(mc, 0, 0, 0, 0); mc.openScreen(null); }
        catch (Exception e) { Bots.log("Test: could not make a level: " + e); }
    }

    private static void save(Minecraft mc, World w, File f) throws Exception {
        Class<?> io = Class.forName("net.minecraft.client.f", true, mc.getClass().getClassLoader());
        Object writer = io.getConstructors()[0].newInstance(mc, null);
        try (OutputStream o = new BufferedOutputStream(new FileOutputStream(f))) {
            io.getSuperclass().getMethod("a", w.getClass(), OutputStream.class).invoke(writer, w, o);
        }
    }

    private static World load(Minecraft mc, File f) throws Exception {
        Class<?> io = Class.forName("net.minecraft.client.f", true, mc.getClass().getClassLoader());
        Object loader = io.getConstructors()[0].newInstance(mc, null);
        try (InputStream in = new FileInputStream(f)) {
            return (World) io.getSuperclass().getMethod("a", InputStream.class).invoke(loader, in);
        }
    }

    private static void finish(Minecraft mc) {
        ON = false;
        if (mc.options != null) { mc.options.music = music; mc.options.sound = sound; }
        lines.add(0, "Classic Bots test, Indev 20100223: " + passed + " passed, " + failed + " failed");
        try (PrintWriter pw = new PrintWriter(new FileOutputStream(new File(dir, "report.txt")))) { for (String l : lines) pw.println(l); }
        catch (Exception e) { Bots.log("Test: could not write the report: " + e); }
        Bots.log("Test done: " + passed + " passed, " + failed + " failed, report in " + dir.getAbsolutePath());
        System.exit(failed == 0 ? 0 : 1);
    }

}

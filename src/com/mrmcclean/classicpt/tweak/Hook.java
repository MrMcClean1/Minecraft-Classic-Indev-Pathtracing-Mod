package com.mrmcclean.classicpt.tweak;

import org.mcphackers.launchwrapper.LaunchConfig;
import org.mcphackers.launchwrapper.tweak.injection.Injection;
import org.mcphackers.launchwrapper.util.ClassNodeSource;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/**
 * Adds two calls to the game, written into its bytecode when it loads:
 *
 *  - Hooks.beforeHud(partialTick, this): after the world is drawn and before the HUD, where the traced picture
 *    replaces the world (so the HUD, hand-free crosshair and menus still draw on top). It goes right after the HUD
 *    object is loaded for its render call, copying the call's partial-tick argument.
 *  - Hooks.frame(this): before every Display.update() in the same method (each frame, menus included; the test
 *    uses it to start a level).
 *
 * Two versions are supported, each found by its own class and method names:
 *   Classic 0.30 (survival, c0.30-s-1858): com.mojang.minecraft.l.run(), HUD e.m.a(FZII)V
 *   Indev 20100223:                          net.minecraft.client.a.e.a(F)V (the frame renderer), HUD c.l.a(F)V
 */
public class Hook implements Injection, Opcodes {
    static final String HOOKS = "com/mrmcclean/classicpt/Hooks";

    static final String[][] TARGETS = {
            // class, method, descriptor, HUD class, HUD method, HUD descriptor
            {"com/mojang/minecraft/l", "run", "()V", "com/mojang/minecraft/e/m", "a", "(FZII)V"},
            {"net/minecraft/client/a/e", "a", "(F)V", "net/minecraft/client/c/l", "a", "(F)V"},
    };

    @Override public String name() { return "Classic PT (path tracer hooks)"; }
    @Override public boolean required() { return false; }

    /**
     * Indev's level loader makes entities by id from a fixed list ("Skipping unknown entity id" for anything else). Classic
     * Bots' bots are saved as "ClassicBot": ask Hooks.entity(level, id) first, and use what it returns.
     */
    static final String[] LOADER = {"net/minecraft/a/a/a", "a", "(Lnet/minecraft/a/a/g;Ljava/lang/String;)Lnet/minecraft/a/c/b;", "net/minecraft/a/c/b"};

    private static void hookLoader(ClassNodeSource source) {
        ClassNode cn = source.getClass(LOADER[0]);
        if (cn == null) return;
        for (MethodNode m : cn.methods) {
            if (!m.name.equals(LOADER[1]) || !m.desc.equals(LOADER[2])) continue;
            InsnList l = new InsnList();
            LabelNode none = new LabelNode();
            l.add(new VarInsnNode(ALOAD, 1));
            l.add(new VarInsnNode(ALOAD, 2));
            l.add(new MethodInsnNode(INVOKESTATIC, HOOKS, "entity", "(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;", false));
            l.add(new InsnNode(DUP));
            l.add(new JumpInsnNode(IFNULL, none));
            l.add(new TypeInsnNode(CHECKCAST, LOADER[3]));
            l.add(new InsnNode(ARETURN));
            l.add(none);
            l.add(new InsnNode(POP));
            m.instructions.insert(l);
            m.maxStack += 2;
            source.overrideClass(cn);
            System.out.println("[ClassicPT] hooked the level loader's entities (Classic Bots)");
            return;
        }
    }

    /**
     * Classic fires an arrow on Tab (a fixed key): that key is Classic Bots' bot list now. The options' key bindings
     * array gets one more, "Fire arrow" (Hooks.bindings: in Controls, rebindable, saved), and the Tab check in the
     * game loop asks Hooks.arrowKey() instead.
     */
    private static void hookArrowKey(ClassNodeSource source) {
        ClassNode opts = source.getClass("com/mojang/minecraft/k"), mc = source.getClass("com/mojang/minecraft/l");
        if (opts == null || mc == null) { System.setProperty("classicpt.arrows", "no options or game class (" + (opts != null) + ", " + (mc != null) + ")"); return; }
        boolean bound = false, key = false;
        for (MethodNode m : opts.methods) {
            if (!m.name.equals("<init>")) continue;
            for (AbstractInsnNode n = m.instructions.getFirst(); n != null; n = n.getNext())
                if (n.getOpcode() == PUTFIELD && ((FieldInsnNode) n).name.equals("s") && ((FieldInsnNode) n).desc.equals("[Lcom/mojang/minecraft/j;")) {
                    InsnList l = new InsnList();
                    l.add(new MethodInsnNode(INVOKESTATIC, HOOKS, "bindings", "(Ljava/lang/Object;)Ljava/lang/Object;", false));
                    l.add(new TypeInsnNode(CHECKCAST, "[Lcom/mojang/minecraft/j;"));
                    m.instructions.insertBefore(n, l);
                    bound = true;
                    break;
                }
        }
        for (MethodNode m : mc.methods) {
            if (key) break;
            // in the game's tick (e()V, found by its code rather than its name): getEventKey; bipush 15 (Tab); if_icmpne;
            // ... instanceof d/c (survival); ... new item/Arrow
            for (AbstractInsnNode n = m.instructions.getFirst(); n != null; n = n.getNext()) {
                if (!(n instanceof IntInsnNode) || n.getOpcode() != BIPUSH || ((IntInsnNode) n).operand != 15) continue;
                AbstractInsnNode prev = n.getPrevious(), next = n.getNext();
                if (!(prev instanceof MethodInsnNode) || !((MethodInsnNode) prev).name.equals("getEventKey") || next.getOpcode() != IF_ICMPNE) continue;
                boolean arrow = false;
                AbstractInsnNode a = next;
                for (int i = 0; i < 16 && a != null; i++, a = a.getNext())
                    if (a instanceof TypeInsnNode && ((TypeInsnNode) a).desc.equals("com/mojang/minecraft/item/Arrow")) arrow = true;
                if (!arrow) continue;
                m.instructions.set(n, new MethodInsnNode(INVOKESTATIC, HOOKS, "arrowKey", "()I", false));
                key = true;
                break;
            }
        }
        String r = bound && key ? "moved" : "not moved (binding " + bound + ", key " + key + ")";
        if (bound && key) {
            source.overrideClass(opts);
            source.overrideClass(mc);
        }
        System.setProperty("classicpt.arrows", r); // for the bot test (this goes to the console only)
        System.out.println("[ClassicPT] arrow key " + r);
    }

    /**
     * The traced picture under the game's mobs, hand and block cracks: Hooks.afterTerrain(partial, this) right after the
     * solid terrain pass (the renderer's (player, 0) call, its int result popped), and Hooks.hideStart/hideEnd around
     * what the picture already shows (clouds, the sky plane, water, the rock and water around the map), so they draw
     * into nothing while it is on. Only up to the depth clear before the hand.
     *   Classic 0.30: l.run(), terrain b/g.a(Player, I)I, partial in local 7 (the HUD's); clouds and sky inline from
     *     "/clouds.png" to the second tessellator draw after it; rock and edge water glCallList; water glCallLists.
     *   Indev: a/e.e(F)V, terrain a/b.a(player, I)I, partial in local 1; edge water a/b.g()V, edge ground a/b.f()V, water a/b.d()V.
     */
    private static void hookWorld(ClassNodeSource source) {
        String r = "no world renderer";
        ClassNode classic = source.getClass("com/mojang/minecraft/l"), indev = source.getClass("net/minecraft/client/a/e");
        if (classic != null) r = hookWorld(source, classic, "run", "()V", "com/mojang/minecraft/b/g", "(Lcom/mojang/minecraft/player/Player;I)I", 7, true);
        else if (indev != null) r = hookWorld(source, indev, "e", "(F)V", "net/minecraft/client/a/b", "(Lnet/minecraft/a/c/e/a;I)I", 1, false);
        System.setProperty("classicpt.world", r);
        System.out.println("[ClassicPT] world hook: " + r);
    }

    private static String hookWorld(ClassNodeSource source, ClassNode cn, String name, String desc, String renderer, String pass, int partial, boolean classic) {
        for (MethodNode m : cn.methods) {
            if (!m.name.equals(name) || !m.desc.equals(desc)) continue;
            MethodInsnNode terrain = null;
            java.util.List<AbstractInsnNode[]> hide = new java.util.ArrayList<>(); // {first, last}: hidden from before first to after last
            for (AbstractInsnNode n = m.instructions.getFirst(); n != null; n = n.getNext()) {
                if (n instanceof MethodInsnNode) {
                    MethodInsnNode mi = (MethodInsnNode) n;
                    if (terrain == null && mi.owner.equals(renderer) && mi.desc.equals(pass) && prev(mi).getOpcode() == ICONST_0 && next(mi).getOpcode() == POP) { terrain = mi; continue; }
                    if (terrain == null) continue;
                    if (mi.owner.equals("org/lwjgl/opengl/GL11") && mi.name.equals("glClear")) break; // the hand comes after: leave it
                    if (!classic && mi.owner.equals(renderer) && mi.desc.equals("()V") && (mi.name.equals("g") || mi.name.equals("d") || mi.name.equals("f"))) hide.add(new AbstractInsnNode[]{mi, mi});
                    if (classic && mi.owner.equals("org/lwjgl/opengl/GL11") && (mi.name.equals("glCallList") || mi.name.equals("glCallLists"))) hide.add(new AbstractInsnNode[]{mi, mi});
                } else if (classic && terrain != null && n instanceof LdcInsnNode && "/clouds.png".equals(((LdcInsnNode) n).cst)) {
                    int draws = 0;
                    for (AbstractInsnNode e = n; e != null; e = e.getNext())
                        if (e instanceof MethodInsnNode && ((MethodInsnNode) e).owner.equals("com/mojang/minecraft/b/c") && ((MethodInsnNode) e).name.equals("a") && ((MethodInsnNode) e).desc.equals("()V") && ++draws == 2) {
                            hide.add(new AbstractInsnNode[]{n, e});
                            n = e;
                            break;
                        }
                }
            }
            if (terrain == null) return "no terrain pass in " + name;
            InsnList l = new InsnList();
            l.add(new VarInsnNode(FLOAD, partial));
            l.add(new VarInsnNode(ALOAD, 0));
            l.add(new MethodInsnNode(INVOKESTATIC, HOOKS, "afterTerrain", "(FLjava/lang/Object;)V", false));
            m.instructions.insert(next(terrain), l);
            for (AbstractInsnNode[] h : hide) {
                m.instructions.insertBefore(h[0], new MethodInsnNode(INVOKESTATIC, HOOKS, "hideStart", "()V", false));
                m.instructions.insert(h[1], new MethodInsnNode(INVOKESTATIC, HOOKS, "hideEnd", "()V", false));
            }
            m.maxStack += 2;
            source.overrideClass(cn);
            return "hooked " + cn.name + "." + name + ", " + hide.size() + " hidden draws";
        }
        return "no " + name + desc + " in " + cn.name;
    }

    private static AbstractInsnNode prev(AbstractInsnNode n) { do n = n.getPrevious(); while (n != null && n.getOpcode() < 0); return n; }
    private static AbstractInsnNode next(AbstractInsnNode n) { do n = n.getNext(); while (n != null && n.getOpcode() < 0); return n; }

    @Override
    public boolean apply(ClassNodeSource source, LaunchConfig config) {
        hookLoader(source);
        hookArrowKey(source);
        hookWorld(source);
        for (String[] t : TARGETS) {
            ClassNode cn = source.getClass(t[0]);
            if (cn == null) continue;
            for (MethodNode m : cn.methods) {
                if (!m.name.equals(t[1]) || !m.desc.equals(t[2])) continue;
                int huds = 0, frames = 0;
                for (AbstractInsnNode n = m.instructions.getFirst(); n != null; n = n.getNext()) {
                    if (!(n instanceof MethodInsnNode)) continue;
                    MethodInsnNode mi = (MethodInsnNode) n;
                    if (mi.owner.equals(t[3]) && mi.name.equals(t[4]) && mi.desc.equals(t[5]) && addHud(m, mi, t[3])) huds++;
                    if (mi.getOpcode() == INVOKESTATIC && mi.owner.equals("org/lwjgl/opengl/Display") && mi.name.equals("update")) {
                        InsnList l = new InsnList();
                        l.add(new VarInsnNode(ALOAD, 0));
                        l.add(new MethodInsnNode(INVOKESTATIC, HOOKS, "frame", "(Ljava/lang/Object;)V", false));
                        m.instructions.insertBefore(mi, l);
                        frames++;
                    }
                }
                if (huds > 0) {
                    m.maxStack += 2;
                    source.overrideClass(cn);
                    System.out.println("[ClassicPT] hooked " + t[0] + "." + t[1] + ": " + huds + " HUD, " + frames + " frame");
                    return true;
                }
            }
        }
        System.out.println("[ClassicPT] no supported Minecraft version found: the path tracer is off");
        return false;
    }

    /** After the GETFIELD that loads the HUD object, before its first (float) argument: call beforeHud with a copy. */
    private static boolean addHud(MethodNode m, MethodInsnNode call, String hud) {
        for (AbstractInsnNode p = call.getPrevious(); p != null; p = p.getPrevious()) {
            if (p instanceof FieldInsnNode && p.getOpcode() == GETFIELD && ((FieldInsnNode) p).desc.equals("L" + hud + ";")) {
                AbstractInsnNode next = p.getNext();
                while (next != null && next.getOpcode() < 0) next = next.getNext();
                if (!(next instanceof VarInsnNode) || next.getOpcode() != FLOAD) return false;
                InsnList l = new InsnList();
                l.add(new VarInsnNode(FLOAD, ((VarInsnNode) next).var));
                l.add(new VarInsnNode(ALOAD, 0));
                l.add(new MethodInsnNode(INVOKESTATIC, HOOKS, "beforeHud", "(FLjava/lang/Object;)V", false));
                m.instructions.insert(p, l);
                return true;
            }
        }
        return false;
    }
}

package com.mrmcclean.classicpt.tweak;

import org.mcphackers.launchwrapper.LaunchConfig;
import org.mcphackers.launchwrapper.loader.LaunchClassLoader;
import org.mcphackers.launchwrapper.target.LaunchTarget;
import org.mcphackers.launchwrapper.tweak.FeatureInfo;
import org.mcphackers.launchwrapper.tweak.Tweak;
import org.mcphackers.launchwrapper.tweak.Tweaker;
import org.mcphackers.launchwrapper.tweak.injection.Injection;
import org.mcphackers.launchwrapper.util.ResourceSource;

import java.util.ArrayList;
import java.util.List;

/**
 * Loaded by MCPHackers LaunchWrapper through --tweakClass (MultiMC passes the component's +tweakers that way).
 * LaunchWrapper hands us the tweak it would have used on its own; everything is passed on to it, and one more
 * injection (Hook) adds the path tracer's two calls to the game. No other loader or mod is involved.
 *
 * This class and Hook are loaded by the launcher's own class loader, before the game's: they refer to the
 * tracer (com.mrmcclean.classicpt.Hooks) only by name, inside the bytecode they write.
 */
public class ClassicPTTweak extends Tweak {
    private final Tweak base;
    private List<Injection> injections;

    public ClassicPTTweak(Tweak base, LaunchConfig config) {
        super(config);
        this.base = base;
    }

    @Override
    public List<Injection> getInjections() {
        if (injections == null) {
            injections = new ArrayList<Injection>(base.getInjections());
            injections.add(new Hook());
        }
        return injections;
    }

    @Override public List<Tweaker> getTweakers() { return base.getTweakers(); }
    @Override public LaunchTarget getLaunchTarget() { return base.getLaunchTarget(); }
    @Override public void transformResources(ResourceSource source) { base.transformResources(source); }
    @Override public void prepare(LaunchClassLoader loader) { base.prepare(loader); }
    @Override public boolean handleError(LaunchClassLoader loader, Throwable t) { return base.handleError(loader, t); }

    @Override
    public List<FeatureInfo> getTweakInfo() {
        List<FeatureInfo> l = new ArrayList<FeatureInfo>(base.getTweakInfo());
        l.addAll(super.getTweakInfo());
        return l;
    }
}

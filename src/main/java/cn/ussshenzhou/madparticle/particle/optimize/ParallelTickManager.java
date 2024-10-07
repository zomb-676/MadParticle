package cn.ussshenzhou.madparticle.particle.optimize;

import cn.ussshenzhou.madparticle.MadParticleConfig;
import cn.ussshenzhou.madparticle.MultiThreadedEqualLinkedHashSetsQueue;
import cn.ussshenzhou.madparticle.mixinproxy.ITickType;
import cn.ussshenzhou.madparticle.particle.enums.TakeOver;
import cn.ussshenzhou.t88.config.ConfigHelper;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import net.minecraft.client.particle.Particle;

import java.util.Collection;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * @author USS_Shenzhou
 */
public class ParallelTickManager {

    private static ForkJoinPool forkJoinPool = new ForkJoinPool(threads());
    public static Cache<Particle, Object> removeCache = CacheBuilder.newBuilder().concurrencyLevel(threads()).initialCapacity(65536).build();
    public static Cache<Particle, Object> syncTickCache = CacheBuilder.newBuilder().concurrencyLevel(threads()).initialCapacity(65536).build();
    public static final Object NULL = new Object();
    private static final AtomicInteger count = new AtomicInteger(0);

    public static void setThreads(int amount) {
        removeCache = CacheBuilder.newBuilder().concurrencyLevel(amount).initialCapacity(65536).build();
        syncTickCache = CacheBuilder.newBuilder().concurrencyLevel(amount).initialCapacity(65536).build();
        forkJoinPool = new ForkJoinPool(amount);
    }

    public static int count() {
        return count.get();
    }

    public static void clearCount() {
        count.set(0);
    }

    private static int threads() {
        return InstancedRenderManager.getThreads();
    }

    public static void tickList(Collection<Particle> particles) {
        count.set(0);
        boolean vanillaOnly = ConfigHelper.getConfigRead(MadParticleConfig.class).takeOverTicking == TakeOver.VANILLA;
        Consumer<Particle> ticker = particle -> {
            if (vanillaOnly) {
                if (getTickType(particle) == TakeOver.TickType.ASYNC) {
                    asyncTick(particle);
                } else {
                    syncTickCache.put(particle, NULL);
                }
            } else {
                if (getTickType(particle) != TakeOver.TickType.SYNC) {
                    asyncTick(particle);
                } else {
                    syncTickCache.put(particle, NULL);
                }
            }
        };
        if (particles instanceof MultiThreadedEqualLinkedHashSetsQueue<Particle> multiThreadedEqualLinkedHashSetsQueue) {
            multiThreadedEqualLinkedHashSetsQueue.forEach(ticker);
        } else {
            ForkJoinTask<?> pickAndTick = forkJoinPool.submit(() -> particles.parallelStream().forEach(ticker));
            pickAndTick.join();
        }
        syncTickCache.asMap().keySet().forEach(particle -> {
            particle.tick();
            if (!particle.isAlive()) {
                removeCache.put(particle, NULL);
            }
        });
        var r = removeCache.asMap().keySet();
        particles.removeAll(r);
        InstancedRenderManager.removeAll(r);
        removeCache.invalidateAll();
        syncTickCache.invalidateAll();
    }

    private static void asyncTick(Particle p) {
        p.tick();
        //count.incrementAndGet();
        if (p.removed) {
            removeCache.put(p, NULL);
        }
    }

    private static TakeOver.TickType getTickType(Particle particle) {
        return ((ITickType) particle).getTickType();
    }
}

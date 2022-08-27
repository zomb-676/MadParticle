package cn.ussshenzhou.madparticle.command;

import cn.ussshenzhou.madparticle.network.MadParticlePacket;
import cn.ussshenzhou.madparticle.network.MadParticlePacketSend;
import cn.ussshenzhou.madparticle.particle.MadParticle;
import cn.ussshenzhou.madparticle.particle.MadParticleOption;
import cn.ussshenzhou.madparticle.particle.ParticleRenderTypes;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ParticleArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.commands.arguments.coordinates.WorldCoordinate;
import net.minecraft.commands.arguments.coordinates.WorldCoordinates;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.command.EnumArgument;
import org.apache.logging.log4j.LogManager;

import java.util.function.Predicate;

/**
 * @author USS_Shenzhou
 */
public class MadParticleCommand {

    public MadParticleCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("madparticle")
                        .redirect(dispatcher.register(Commands.literal("mp")
                                        .then(Commands.argument("targetParticle", ParticleArgument.particle())
                                                .then(Commands.argument("spriteFrom", EnumArgument.enumArgument(MadParticle.SpriteFrom.class))
                                                        .then(Commands.argument("lifeTime", IntegerArgumentType.integer())
                                                                .then(Commands.argument("alwaysRender", BoolArgumentType.bool())
                                                                        .then(Commands.argument("amount", IntegerArgumentType.integer())
                                                                                .then(Commands.argument("spawnPos", Vec3Argument.vec3())
                                                                                        .then(Commands.argument("spawnDiffuse", Vec3Argument.vec3())
                                                                                                .then(Commands.argument("spawnSpeed", Vec3Argument.vec3())
                                                                                                        .then(Commands.argument("speedDiffuse", Vec3Argument.vec3())
                                                                                                                .then(Commands.argument("collision", BoolArgumentType.bool())
                                                                                                                        .then(Commands.argument("bounceTime", IntegerArgumentType.integer())
                                                                                                                                .then(Commands.argument("horizontalRelativeCollisionDiffuse", DoubleArgumentType.doubleArg())
                                                                                                                                        .then(Commands.argument("verticalRelativeCollisionBounce", DoubleArgumentType.doubleArg())
                                                                                                                                                .then(Commands.argument("friction", FloatArgumentType.floatArg())
                                                                                                                                                        .then(Commands.argument("afterCollisionFriction", FloatArgumentType.floatArg())
                                                                                                                                                                .then(Commands.argument("gravity", FloatArgumentType.floatArg())
                                                                                                                                                                        .then(Commands.argument("afterCollisionGravity", FloatArgumentType.floatArg())
                                                                                                                                                                                .then(Commands.argument("interactWithEntity", BoolArgumentType.bool())
                                                                                                                                                                                        .then(Commands.argument("horizontalInteractFactor", DoubleArgumentType.doubleArg())
                                                                                                                                                                                                .then(Commands.argument("verticalInteractFactor", DoubleArgumentType.doubleArg())
                                                                                                                                                                                                        .then(Commands.argument("renderType", EnumArgument.enumArgument(ParticleRenderTypes.class))
                                                                                                                                                                                                                .then(Commands.argument("r", FloatArgumentType.floatArg(0, 1))
                                                                                                                                                                                                                        .then(Commands.argument("g", FloatArgumentType.floatArg(0, 1))
                                                                                                                                                                                                                                .then(Commands.argument("b", FloatArgumentType.floatArg(0, 1))
                                                                                                                                                                                                                                        .then(Commands.argument("beginAlpha", FloatArgumentType.floatArg(0,1))
                                                                                                                                                                                                                                                .then(Commands.argument("endAlpha", FloatArgumentType.floatArg(0,1))
                                                                                                                                                                                                                                                        .then(Commands.argument("alphaMode", EnumArgument.enumArgument(MadParticle.ChangeMode.class))
                                                                                                                                                                                                                                                                .then(Commands.argument("beginScale", FloatArgumentType.floatArg(0))
                                                                                                                                                                                                                                                                        .then(Commands.argument("endScale", FloatArgumentType.floatArg(0))
                                                                                                                                                                                                                                                                                .then(Commands.argument("scaleMode", EnumArgument.enumArgument(MadParticle.ChangeMode.class))
                                                                                                                                                                                                                                                                                        .executes(MadParticleCommand::sendAll)
                                                                                                                                                                                                                                                                                )
                                                                                                                                                                                                                                                                        )
                                                                                                                                                                                                                                                                )
                                                                                                                                                                                                                                                        )
                                                                                                                                                                                                                                                )
                                                                                                                                                                                                                                        )
                                                                                                                                                                                                                                )
                                                                                                                                                                                                                        )
                                                                                                                                                                                                                )
                                                                                                                                                                                                        )
                                                                                                                                                                                                )
                                                                                                                                                                                        )
                                                                                                                                                                                )
                                                                                                                                                                        )
                                                                                                                                                                )
                                                                                                                                                        )
                                                                                                                                                )
                                                                                                                                        )
                                                                                                                                )
                                                                                                                        )
                                                                                                                )
                                                                                                        )
                                                                                                )
                                                                                        )
                                                                                )
                                                                        )
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
        );
    }

    private static int sendAll(CommandContext<CommandSourceStack> ct) {
        ServerLevel level = ct.getSource().getLevel();
        Vec3 pos = ct.getArgument("spawnPos", WorldCoordinates.class).getPosition(ct.getSource());
        Vec3 posDiffuse = ct.getArgument("spawnDiffuse", WorldCoordinates.class).getPosition(ct.getSource());
        Vec3 speed = ct.getArgument("spawnSpeed", WorldCoordinates.class).getPosition(ct.getSource());
        Vec3 speedDiffuse = ct.getArgument("speedDiffuse", WorldCoordinates.class).getPosition(ct.getSource());
        for (ServerPlayer player : level.getPlayers(serverPlayer -> true)) {
            MadParticlePacketSend.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new MadParticlePacket(new MadParticleOption(
                            Registry.PARTICLE_TYPE.getId(ct.getArgument("targetParticle", ParticleOptions.class).getType()),
                            ct.getArgument("spriteFrom", MadParticle.SpriteFrom.class),
                            ct.getArgument("lifeTime", Integer.class),
                            ct.getArgument("alwaysRender", Boolean.class),
                            ct.getArgument("amount", Integer.class),
                            pos.x, pos.y, pos.z,
                            posDiffuse.x, posDiffuse.y, posDiffuse.z,
                            speed.x, speed.y, speed.z,
                            speedDiffuse.x, speedDiffuse.y, speedDiffuse.z,
                            ct.getArgument("friction", Float.class),
                            ct.getArgument("gravity", Float.class),
                            ct.getArgument("collision", Boolean.class),
                            ct.getArgument("bounceTime", Integer.class),
                            ct.getArgument("horizontalRelativeCollisionDiffuse", Double.class),
                            ct.getArgument("verticalRelativeCollisionBounce", Double.class),
                            ct.getArgument("afterCollisionFriction", Float.class),
                            ct.getArgument("afterCollisionGravity", Float.class),
                            ct.getArgument("interactWithEntity", Boolean.class),
                            ct.getArgument("horizontalInteractFactor", Double.class),
                            ct.getArgument("verticalInteractFactor", Double.class),
                            ct.getArgument("renderType", ParticleRenderTypes.class),
                            ct.getArgument("r", Float.class), ct.getArgument("g", Float.class), ct.getArgument("b", Float.class),
                            ct.getArgument("beginAlpha", Float.class),
                            ct.getArgument("endAlpha", Float.class),
                            ct.getArgument("alphaMode", MadParticle.ChangeMode.class),
                            ct.getArgument("beginScale", Float.class),
                            ct.getArgument("endScale", Float.class),
                            ct.getArgument("scaleMode", MadParticle.ChangeMode.class)


                    ))
            );
        }
        return Command.SINGLE_SUCCESS;
    }
}

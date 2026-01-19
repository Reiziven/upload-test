package studio.fantasyit.maid_rpg_task.task;

import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import studio.fantasyit.maid_rpg_task.Config;
import studio.fantasyit.maid_rpg_task.behavior.DpsModifierBehavior;


import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.item.ItemStack;

import com.github.tartaricacid.touhoulittlemaid.api.task.IAttackTask;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidUseShieldTask;
import com.github.tartaricacid.touhoulittlemaid.entity.item.EntityExtinguishingAgent;
import com.github.tartaricacid.touhoulittlemaid.init.InitItems;
import com.github.tartaricacid.touhoulittlemaid.init.InitSounds;
import com.github.tartaricacid.touhoulittlemaid.util.SoundUtil;
import com.google.common.collect.Lists;
import com.mojang.datafixers.util.Pair;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.*;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import studio.fantasyit.maid_rpg_task.MaidRpgTask;
import studio.fantasyit.maid_rpg_task.behavior.TankRedirectBehavior;
import studio.fantasyit.maid_rpg_task.behavior.StunBehavior;
import studio.fantasyit.maid_rpg_task.behavior.TankAggroBehavior;
import net.minecraft.world.entity.ai.behavior.*;
import studio.fantasyit.maid_rpg_task.behavior.MasterModifierBehavior;
import studio.fantasyit.maid_rpg_task.behavior.PlayerReviveBehavior;
import studio.fantasyit.maid_rpg_task.behavior.SupportEffectBehavior;
import studio.fantasyit.maid_rpg_task.compat.PlayerRevive;
import studio.fantasyit.maid_rpg_task.menu.MaidReviveConfigGui;



public class MaidMasterTask implements IAttackTask {
    public static final ResourceLocation UID = new ResourceLocation(MaidRpgTask.MODID, "master_maid");
    private static final int MAX_STOP_ATTACK_DISTANCE = 8;

    @Override
    public boolean isEnable(EntityMaid maid) {
        return PlayerRevive.isEnable() && Config.enableMasterTask && Config.enableReviveTask;
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public ItemStack getIcon() {
        return Items.NETHER_STAR.getDefaultInstance();
    }



    @Override
    public SoundEvent getAmbientSound(EntityMaid maid) {
        return SoundUtil.attackSound(maid, InitSounds.MAID_ATTACK.get(), 0.5f);
    }



    @Override
    public List<Pair<Integer, BehaviorControl<? super EntityMaid>>> createBrainTasks(EntityMaid maid) {
        BehaviorControl<EntityMaid> supplementedTask = StartAttacking.create(this::hasAssaultWeapon, IAttackTask::findFirstValidAttackTarget);
        BehaviorControl<EntityMaid> findTargetTask = StopAttackingIfTargetInvalid.create(target -> !hasAssaultWeapon(maid) || farAway(target, maid));
        BehaviorControl<Mob> moveToTargetTask = SetWalkTargetFromAttackTargetIfTargetOutOfReach.create(0.6f);
        BehaviorControl<Mob> attackTargetTask = MeleeAttack.create(10);
        MaidUseShieldTask maidUseShieldTask = new MaidUseShieldTask();
        BehaviorControl<EntityMaid> TankAggroBehavior = new TankAggroBehavior();
        BehaviorControl<EntityMaid> tankRedirectTask = new TankRedirectBehavior();
        BehaviorControl<EntityMaid> shieldBuffTask = new StunBehavior();
        BehaviorControl<EntityMaid> PlayerReviveBehavior = new PlayerReviveBehavior();
        BehaviorControl<EntityMaid> SupportEffectBehavior = new SupportEffectBehavior();
        BehaviorControl<EntityMaid> MasterModifierTask = new MasterModifierBehavior();
        return Lists.newArrayList(
                Pair.of(5, supplementedTask),
                Pair.of(5, moveToTargetTask),
                Pair.of(5, maidUseShieldTask),
                Pair.of(5, TankAggroBehavior),
                Pair.of(5, findTargetTask),
                Pair.of(5, attackTargetTask),
                Pair.of(5, maidUseShieldTask),
                Pair.of(5, shieldBuffTask),
                Pair.of(5, tankRedirectTask),
                Pair.of(1, PlayerReviveBehavior),
                Pair.of(5, SupportEffectBehavior),
                Pair.of(1, new MasterModifierBehavior())
        );
    }

    @Override
    public boolean hasExtraAttack(EntityMaid maid, Entity target) {
        return maid.getOffhandItem().is(InitItems.EXTINGUISHER.get()) && target.fireImmune();
    }

    @Override
    public boolean doExtraAttack(EntityMaid maid, Entity target) {
        Level world = maid.level();
        AABB aabb = target.getBoundingBox().inflate(1.5, 1, 1.5);
        List<EntityExtinguishingAgent> extinguishingAgents = world.getEntitiesOfClass(EntityExtinguishingAgent.class, aabb, Entity::isAlive);
        if (extinguishingAgents.isEmpty()) {
            world.addFreshEntity(new EntityExtinguishingAgent(world, target.position()));
            maid.getOffhandItem().hurtAndBreak(1, maid, (m) -> m.broadcastBreakEvent(InteractionHand.OFF_HAND));
            return true;
        }
        return false;
    }

    @Override
    public List<Pair<String, Predicate<EntityMaid>>> getConditionDescription(EntityMaid maid) {
        return Lists.newArrayList(Pair.of("assault_weapon", this::hasAssaultWeapon), Pair.of("extinguisher", this::hasExtinguisher));
    }

    private boolean hasAssaultWeapon(EntityMaid maid) {
        return isWeapon(maid, maid.getMainHandItem());
    }

    private boolean isWeapon(EntityMaid maid, ItemStack mainHandItem) {
        return true;
    }

    private boolean hasExtinguisher(EntityMaid maid) {
        return maid.getOffhandItem().is(InitItems.EXTINGUISHER.get());
    }

    private boolean farAway(LivingEntity target, EntityMaid maid) {
        return maid.distanceTo(target) > MAX_STOP_ATTACK_DISTANCE;
    }

    @Override
    public MenuProvider getTaskConfigGuiProvider(EntityMaid maid) {
        return new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.literal("");
            }

            @Override
            public AbstractContainerMenu createMenu(int index, Inventory playerInventory, Player player) {
                return new MaidReviveConfigGui.Container(index, playerInventory, maid.getId());
            }
        };
    }
}

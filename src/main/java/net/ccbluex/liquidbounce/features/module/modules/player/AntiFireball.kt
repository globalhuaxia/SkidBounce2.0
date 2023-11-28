/*
 * SkidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.player

import net.ccbluex.liquidbounce.event.EventState
import net.ccbluex.liquidbounce.event.EventTarget
import net.ccbluex.liquidbounce.event.MotionEvent
import net.ccbluex.liquidbounce.event.TickEvent
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.features.module.ModuleCategory
import net.ccbluex.liquidbounce.utils.PacketUtils.sendPacket
import net.ccbluex.liquidbounce.utils.RotationUtils.currentRotation
import net.ccbluex.liquidbounce.utils.RotationUtils.isRotationFaced
import net.ccbluex.liquidbounce.utils.RotationUtils.limitAngleChange
import net.ccbluex.liquidbounce.utils.RotationUtils.setTargetRotation
import net.ccbluex.liquidbounce.utils.RotationUtils.toRotation
import net.ccbluex.liquidbounce.utils.extensions.*
import net.ccbluex.liquidbounce.utils.misc.RandomUtils.nextFloat
import net.ccbluex.liquidbounce.value.BoolValue
import net.ccbluex.liquidbounce.value.FloatValue
import net.ccbluex.liquidbounce.value.ListValue
import net.minecraft.entity.Entity
import net.minecraft.entity.projectile.EntityFireball
import net.minecraft.network.play.client.C02PacketUseEntity
import net.minecraft.network.play.client.C0APacketAnimation
import net.minecraft.util.Vec3

object AntiFireball : Module("AntiFireball", ModuleCategory.PLAYER) {
    private val range by FloatValue("Range", 4.5f, 3f..8f)
    private val swing by ListValue("Swing", arrayOf("Normal", "Packet", "None"), "Normal")

    private val rotations by BoolValue("Rotations", true)
        private val strafe by BoolValue("Strafe", false) { rotations }

        private val maxTurnSpeedValue: FloatValue = object : FloatValue("MaxTurnSpeed", 120f, 0f..180f) {
            override fun onChange(oldValue: Float, newValue: Float) = newValue.coerceAtLeast(minTurnSpeed)
        }
        private val maxTurnSpeed by maxTurnSpeedValue

        private val minTurnSpeed by object : FloatValue("MinTurnSpeed", 80f, 0f..180f) {
            override fun onChange(oldValue: Float, newValue: Float) = newValue.coerceAtMost(maxTurnSpeed)

            override fun isSupported() = !maxTurnSpeedValue.isMinimal()
        }

        private val angleThresholdUntilReset by FloatValue("AngleThresholdUntilReset", 5f, 0.1f..180f) { rotations }

    private var target: Entity? = null

    @EventTarget
    private fun onMotion(event: MotionEvent) {
        val player = mc.thePlayer ?: return

        if (event.eventState != EventState.POST)
            return

        target = null

        for (entity in mc.theWorld.loadedEntityList.filterIsInstance<EntityFireball>()
            .sortedBy { player.getDistanceToBox(it.hitBox) }) {
            val nearestPoint = getNearestPointBB(player.eyes, entity.hitBox)

            val entityPrediction =
                Vec3(entity.posX - entity.prevPosX, entity.posY - entity.prevPosY, entity.posZ - entity.prevPosZ)

            val distance = player.getDistanceToBox(entity.hitBox)

            val predictedDistance = player.getDistanceToBox(
                entity.hitBox.offset(
                    entityPrediction.xCoord,
                    entityPrediction.yCoord,
                    entityPrediction.zCoord
                )
            )

            // Is the fireball's speed-predicted distance further than the original distance?
            if (predictedDistance >= distance || distance > range) {
                continue
            }

            if (rotations) {
                setTargetRotation(
                    limitAngleChange(
                        currentRotation ?: player.rotation,
                        toRotation(nearestPoint, true).fixedSensitivity(),
                        nextFloat(minTurnSpeed, maxTurnSpeed)
                    ),
                    strafe = this.strafe,
                    resetSpeed = minTurnSpeed to maxTurnSpeed,
                    angleThresholdForReset = angleThresholdUntilReset
                )
            }

            target = entity
            break
        }
    }

    @EventTarget
    fun onTick(event: TickEvent) {
        val player = mc.thePlayer ?: return

        val rotation = currentRotation ?: player.rotation
        val entity = target ?: return

        if (!rotations && player.getDistanceToBox(entity.hitBox) <= range
            || isRotationFaced(entity, range.toDouble(), rotation)
        ) {
            sendPacket(C02PacketUseEntity(entity, C02PacketUseEntity.Action.ATTACK))

            when (swing) {
                "Normal" -> mc.thePlayer.swingItem()
                "Packet" -> sendPacket(C0APacketAnimation())
            }

            target = null
        }
    }
}
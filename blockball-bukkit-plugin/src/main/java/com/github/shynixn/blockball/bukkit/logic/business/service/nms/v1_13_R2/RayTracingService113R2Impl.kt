package com.github.shynixn.blockball.bukkit.logic.business.service.nms.v1_13_R2

import com.github.shynixn.blockball.api.business.enumeration.BlockDirection
import com.github.shynixn.blockball.api.business.service.RayTracingService
import com.github.shynixn.blockball.api.persistence.entity.Position
import com.github.shynixn.blockball.api.persistence.entity.RaytraceResult
import com.github.shynixn.blockball.bukkit.logic.business.extension.toLocation
import com.github.shynixn.blockball.bukkit.logic.business.extension.toPosition
import com.github.shynixn.blockball.bukkit.logic.business.extension.toVector
import com.github.shynixn.blockball.core.logic.persistence.entity.PositionEntity
import com.github.shynixn.blockball.core.logic.persistence.entity.RayTraceResultEntity
import org.bukkit.FluidCollisionMode

class RayTracingService113R2Impl : RayTracingService {
    /**
     * Ray traces in the world for the given motion.
     */
    override fun rayTraceMotion(position: Position, motion: Position): RaytraceResult {
        val endPosition =
            PositionEntity(position.worldName!!, position.x + motion.x, position.y + motion.y, position.z + motion.z)
        val sourceLocation = position.toLocation()

        val directionVector = motion.toVector().normalize()
        val distance = motion.length()
        val world = sourceLocation.world!!
        val movingObjectPosition =
            world.rayTraceBlocks(sourceLocation, directionVector, distance, FluidCollisionMode.NEVER, false)

        if (movingObjectPosition == null) {
            endPosition.yaw = position.yaw
            endPosition.pitch = position.pitch
            return RayTraceResultEntity(false, endPosition, BlockDirection.DOWN)
        }

        val targetPosition = movingObjectPosition.hitPosition.toLocation(world).toPosition()
        val direction = BlockDirection.valueOf(
            movingObjectPosition.hitBlockFace!!.toString().toUpperCase()
        )

        targetPosition.yaw = position.yaw
        targetPosition.pitch = position.pitch

        return RayTraceResultEntity(true, targetPosition, direction)
    }
}
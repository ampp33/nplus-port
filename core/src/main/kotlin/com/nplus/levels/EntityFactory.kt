package com.nplus.levels

import com.nplus.physics.collision.GridEdges
import com.nplus.physics.collision.GridEntity
import com.nplus.physics.collision.GridSegment
import com.nplus.physics.collision.SegmentLinearDoubleSided
import com.nplus.physics.entities.*
import com.nplus.physics.math.Vec2
import kotlin.math.*

/**
 * Port of AS3 sim_loader.LoadLevel_EditorState_Entities.
 * Converts [LevelData.entities] into typed [EntityBase] instances wired to the physics grids.
 *
 * Entity creation order matches the AS3: entities with paired partners (EXIT, DOOR_LOCKED,
 * DOOR_TRAP) expect the partner to be the immediately following element in the list.
 */
object EntityFactory {

    fun create(
        data: LevelData,
        segGrid: GridSegment,
        edgeGrid: GridEdges,
        objGrid: GridEntity
    ): List<EntityBase> {
        val result = mutableListOf<EntityBase>()
        val list   = data.entities
        var i      = 0
        while (i < list.size) {
            val e = list[i]
            when (e.type) {
                EntityTypes.PLAYER -> { /* spawns handled by LevelBuilder */ }

                EntityTypes.GOLD ->
                    result += GoldEntity(objGrid, e.worldX, e.worldY)

                EntityTypes.MINE ->
                    result += MineEntity(objGrid, e.worldX, e.worldY)

                EntityTypes.EXIT_DOOR -> {
                    val door = ExitDoor(e.worldX, e.worldY)
                    registerEntity(result, door)
                    // next entity is always EXIT_SWITCH
                    if (i + 1 < list.size && list[i + 1].type == EntityTypes.EXIT_SWITCH) {
                        i++
                        val sw = list[i]
                        registerEntity(result, ExitSwitch(objGrid, sw.worldX, sw.worldY, door))
                    }
                }

                EntityTypes.LAUNCHPAD -> {
                    val dirVec = DirTypes.toVec(e.dir)
                    result += LaunchpadEntity(objGrid, e.worldX, e.worldY, dirVec[0], dirVec[1])
                }

                EntityTypes.ONEWAY -> {
                    val dirVec = DirTypes.toVec(e.dir)
                    result += OnewayPlatformEntity(objGrid, e.worldX, e.worldY, dirVec[0], dirVec[1])
                }

                EntityTypes.BOUNCEBLOCK ->
                    result += BounceBlockEntity(objGrid, e.worldX, e.worldY)

                EntityTypes.THWOMP -> {
                    val dirVec = DirTypes.toVec(e.dir)
                    val isH    = (dirVec[1] == 0f)
                    val fd     = if (isH) dirVec[0].toInt() else dirVec[1].toInt()
                    result += ThwompEntity(objGrid, e.worldX, e.worldY, fd, isH)
                }

                EntityTypes.FLOORGUARD ->
                    result += FloorGuardEntity(objGrid, e.worldX, e.worldY)

                EntityTypes.ROCKET ->
                    result += RocketEntity(objGrid, e.worldX, e.worldY)

                EntityTypes.TURRET ->
                    result += TurretEntity(e.worldX, e.worldY)

                EntityTypes.ZAP ->
                    result += DroneZap(objGrid, e.worldX, e.worldY, e.dir / 2, e.move)

                EntityTypes.CHASER ->
                    result += DroneChaser(objGrid, e.worldX, e.worldY, e.dir / 2, e.move)

                EntityTypes.LASER ->
                    result += DroneLaser(objGrid, e.worldX, e.worldY, e.dir / 2, e.move)

                EntityTypes.CHAINGUN ->
                    result += DroneChaingun(objGrid, e.worldX, e.worldY, e.dir / 2, e.move)

                EntityTypes.DOOR_REGULAR -> {
                    val door = buildDoor(e, segGrid, edgeGrid, objGrid) { cIdx, seg, ei, isH, x, y ->
                        DoorRegular(objGrid, segGrid, cIdx, seg, edgeGrid, ei, isH, x, y)
                    }
                    if (door != null) registerEntity(result, door)
                }

                EntityTypes.DOOR_LOCKED -> {
                    // next entity is SWITCH_LOCKED
                    val sw = if (i + 1 < list.size && list[i + 1].type == EntityTypes.SWITCH_LOCKED)
                        list[++i] else null
                    val door = buildDoor(e, segGrid, edgeGrid, objGrid) { cIdx, seg, ei, isH, _, _ ->
                        DoorLocked(objGrid, segGrid, cIdx, seg, edgeGrid, ei, isH,
                            sw?.worldX ?: e.worldX, sw?.worldY ?: e.worldY)
                    }
                    if (door != null) registerEntity(result, door)
                }

                EntityTypes.DOOR_TRAP -> {
                    // next entity is SWITCH_TRAP
                    val sw = if (i + 1 < list.size && list[i + 1].type == EntityTypes.SWITCH_TRAP)
                        list[++i] else null
                    val door = buildDoor(e, segGrid, edgeGrid, objGrid) { cIdx, seg, ei, isH, _, _ ->
                        DoorTrap(objGrid, segGrid, cIdx, seg, edgeGrid, ei, isH,
                            sw?.worldX ?: e.worldX, sw?.worldY ?: e.worldY)
                    }
                    if (door != null) registerEntity(result, door)
                }

                // These are consumed above as part of their paired doors
                EntityTypes.EXIT_SWITCH, EntityTypes.SWITCH_LOCKED, EntityTypes.SWITCH_TRAP -> {}
            }
            i++
        }
        return result
    }

    // --- Helpers ---

    private fun registerEntity(list: MutableList<EntityBase>, e: EntityBase) {
        e.setUid(list.size)
        list += e
    }

    /** Builds the door geometry and calls [factory] to create the typed door. */
    private fun buildDoor(
        e: RawEntity, segGrid: GridSegment, edgeGrid: GridEdges, objGrid: GridEntity,
        factory: (segCellIdx: Int, seg: SegmentLinearDoubleSided, edgeIndices: IntArray,
                  isHorizontal: Boolean, x: Float, y: Float) -> DoorBase
    ): DoorBase? {
        val x = e.worldX; val y = e.worldY
        val dir = e.dir

        // Direction vector (8-way enum)
        val dv = DirTypes.toVec(dir)
        // Perp to direction = the door's surface axis (perpendicular to opening direction)
        val perpX = -dv[1]; val perpY = dv[0]

        // Door segment: 24 px wide, centred at (x, y), perp to dir
        val p0x = x + perpX * 12f; val p0y = y + perpY * 12f
        val p1x = x - perpX * 12f; val p1y = y - perpY * 12f
        val seg = SegmentLinearDoubleSided(p0x, p0y, p1x, p1y)

        // Segment grid cell: the cell in which the door's midpoint sits, offset by half the dir
        val col = floor((x - dv[0] * 12f) / 24f).toInt()
        val row = floor((y - dv[1] * 12f) / 24f).toInt()
        val segCellIdx = segGrid.doorGetCellIndex(col, row)

        // isHorizontal: dir==0 → DIR_R → horizontal sliding → vertical barrier
        val isH = (dir == 0)

        // Edge indices in the double-resolution edge grid
        val ei = if (isH) {
            intArrayOf(
                edgeGrid.doorGetCellIndex(col * 2 + 1, row * 2),
                edgeGrid.doorGetCellIndex(col * 2 + 1, row * 2 + 1)
            )
        } else {
            intArrayOf(
                edgeGrid.doorGetCellIndex(col * 2,     row * 2 + 1),
                edgeGrid.doorGetCellIndex(col * 2 + 1, row * 2 + 1)
            )
        }

        return factory(segCellIdx, seg, ei, isH, x, y)
    }
}

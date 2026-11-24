import { useState, type ReactNode } from 'react'
import {
  DndContext, DragOverlay, KeyboardSensor, PointerSensor, pointerWithin, rectIntersection, useDndContext, useDroppable,
  useSensor, useSensors, type CollisionDetection, type DragEndEvent, type DragStartEvent,
} from '@dnd-kit/core'
import { Box, Chip, Typography } from '@mui/material'
import Inventory2OutlinedIcon from '@mui/icons-material/Inventory2Outlined'
import type { Payload } from '../../api/types'

type DropData = { onDrop: (object: Payload) => void; accepts?: (object: Payload) => boolean }

/** Pointer first (precise for small rows), then overlap (for keyboard dragging). */
const collision: CollisionDetection = (args) => {
  const hits = pointerWithin(args)
  return hits.length ? hits : rectIntersection(args)
}

/** Lets inventory objects be dragged from the palette onto drop zones anywhere inside. */
export function ObjectDndProvider({ children }: { children: ReactNode }) {
  const sensors = useSensors(useSensor(PointerSensor, { activationConstraint: { distance: 4 } }), useSensor(KeyboardSensor))
  const [dragging, setDragging] = useState<Payload | null>(null)

  const start = (e: DragStartEvent) => setDragging((e.active.data.current?.object as Payload | undefined) ?? null)
  const end = (e: DragEndEvent) => {
    setDragging(null)
    const object = e.active.data.current?.object as Payload | undefined
    const target = e.over?.data.current as DropData | undefined
    if (object && target && (!target.accepts || target.accepts(object))) target.onDrop(object)
  }

  return (
    <DndContext sensors={sensors} collisionDetection={collision} onDragStart={start} onDragEnd={end} onDragCancel={() => setDragging(null)}
      accessibility={{
        announcements: {
          onDragStart: ({ active }) => `Picked up object ${(active.data.current?.object as Payload | undefined)?.name ?? ''}.`,
          onDragOver: ({ over }) => (over ? `Over ${over.data.current?.label ?? 'a drop zone'}.` : 'Not over a drop zone.'),
          onDragEnd: ({ over }) => (over ? `Dropped on ${over.data.current?.label ?? 'a drop zone'}.` : 'Dropped outside any drop zone.'),
          onDragCancel: () => 'Cancelled.',
        },
      }}>
      {children}
      <DragOverlay dropAnimation={null}>
        {dragging && <Chip color="primary" icon={<Inventory2OutlinedIcon />} label={dragging.name} sx={{ boxShadow: 4, cursor: 'grabbing' }} />}
      </DragOverlay>
    </DndContext>
  )
}

/**
 * Wraps part of a form so an inventory object can be dropped on it. While an object is being dragged
 * the zone is outlined, and says what dropping does.
 */
export function ObjectDropZone({ id, label, hint, onDrop, accepts, children, disabled }: {
  id: string
  /** What the zone is, for screen readers: "the request body". */
  label: string
  /** What dropping does, shown while dragging: "Use as the request body". */
  hint: string
  onDrop: (object: Payload) => void
  accepts?: (object: Payload) => boolean
  children: ReactNode
  disabled?: boolean
}) {
  const { active } = useDndContext()
  const { setNodeRef, isOver } = useDroppable({ id, data: { onDrop, accepts, label } satisfies DropData & { label: string }, disabled })
  const object = active?.data.current?.object as Payload | undefined
  const live = !!object && !disabled && (!accepts || accepts(object))
  return (
    <Box ref={setNodeRef} data-drop-zone={id} sx={{
      position: 'relative', borderRadius: 1, transition: 'outline-color 120ms',
      outline: live ? '2px dashed' : '2px dashed transparent', outlineOffset: 2,
      outlineColor: live ? (isOver ? 'primary.main' : 'primary.light') : 'transparent',
      bgcolor: live && isOver ? 'action.hover' : undefined,
    }}>
      {children}
      {live && (
        <Typography variant="caption" sx={{
          position: 'absolute', top: -10, right: 8, px: 0.75, borderRadius: 1, pointerEvents: 'none',
          bgcolor: isOver ? 'primary.main' : 'background.paper', color: isOver ? 'primary.contrastText' : 'primary.main',
          border: 1, borderColor: 'primary.main', zIndex: 1,
        }}>
          {hint.replace('{name}', object!.name)}
        </Typography>
      )}
    </Box>
  )
}

import { useState, type ReactNode } from 'react'
import {
  DndContext, DragOverlay, KeyboardSensor, PointerSensor, pointerWithin, rectIntersection, useDndContext, useDroppable,
  useSensor, useSensors, type CollisionDetection, type DragEndEvent, type DragStartEvent,
} from '@dnd-kit/core'
import { Box, Chip, Typography } from '@mui/material'
import Inventory2OutlinedIcon from '@mui/icons-material/Inventory2Outlined'
import ShortTextIcon from '@mui/icons-material/ShortText'
import type { FieldDefinition, Payload } from '../../api/types'

type DropData = {
  onDrop?: (object: Payload) => void
  accepts?: (object: Payload) => boolean
  onDropField?: (definition: FieldDefinition) => void
}

/** What is being dragged: an inventory object ({ object }) or a field-inventory entry ({ definition }). */
type Dragged = { object?: Payload; definition?: FieldDefinition }
const draggedOf = (data: unknown): Dragged => (data ?? {}) as Dragged
const nameOf = (d: Dragged) => d.object?.name ?? d.definition?.field.name ?? ''

/** Pointer first (precise for small rows), then overlap (for keyboard dragging). */
const collision: CollisionDetection = (args) => {
  const hits = pointerWithin(args)
  return hits.length ? hits : rectIntersection(args)
}

/** Lets inventory objects and fields be dragged from the palettes onto drop zones anywhere inside. */
export function ObjectDndProvider({ children }: { children: ReactNode }) {
  const sensors = useSensors(useSensor(PointerSensor, { activationConstraint: { distance: 4 } }), useSensor(KeyboardSensor))
  const [dragging, setDragging] = useState<Dragged | null>(null)

  const start = (e: DragStartEvent) => setDragging(draggedOf(e.active.data.current))
  const end = (e: DragEndEvent) => {
    setDragging(null)
    const { object, definition } = draggedOf(e.active.data.current)
    const target = e.over?.data.current as DropData | undefined
    if (!target) return
    if (object && target.onDrop && (!target.accepts || target.accepts(object))) target.onDrop(object)
    if (definition && target.onDropField) target.onDropField(definition)
  }

  return (
    <DndContext sensors={sensors} collisionDetection={collision} onDragStart={start} onDragEnd={end} onDragCancel={() => setDragging(null)}
      accessibility={{
        announcements: {
          onDragStart: ({ active }) => {
            const d = draggedOf(active.data.current)
            return `Picked up ${d.object ? 'object' : 'field'} ${nameOf(d)}.`
          },
          onDragOver: ({ over }) => (over ? `Over ${over.data.current?.label ?? 'a drop zone'}.` : 'Not over a drop zone.'),
          onDragEnd: ({ over }) => (over ? `Dropped on ${over.data.current?.label ?? 'a drop zone'}.` : 'Dropped outside any drop zone.'),
          onDragCancel: () => 'Cancelled.',
        },
      }}>
      {children}
      <DragOverlay dropAnimation={null}>
        {dragging && (dragging.object || dragging.definition) && (
          <Chip color="primary" icon={dragging.object ? <Inventory2OutlinedIcon /> : <ShortTextIcon />} label={nameOf(dragging)}
            sx={{ boxShadow: 4, cursor: 'grabbing', fontFamily: dragging.definition ? 'monospace' : undefined }} />
        )}
      </DragOverlay>
    </DndContext>
  )
}

/**
 * Wraps part of a form so an inventory object (onDrop) or a field-inventory entry (onDropField) can be
 * dropped on it. While something it takes is being dragged the zone is outlined, and says what dropping does.
 */
export function ObjectDropZone({ id, label, hint, fieldHint, onDrop, onDropField, accepts, children, disabled }: {
  id: string
  /** What the zone is, for screen readers: "the request body". */
  label: string
  /** What dropping an object does, shown while dragging: "Use {name} as the request body". */
  hint?: string
  /** What dropping a field does: "Add {name} to the fields". */
  fieldHint?: string
  onDrop?: (object: Payload) => void
  onDropField?: (definition: FieldDefinition) => void
  accepts?: (object: Payload) => boolean
  children: ReactNode
  disabled?: boolean
}) {
  const { active } = useDndContext()
  const { setNodeRef, isOver } = useDroppable({ id, data: { onDrop, onDropField, accepts, label } satisfies DropData & { label: string }, disabled })
  const { object, definition } = draggedOf(active?.data.current)
  const takesObject = !!object && !!onDrop && (!accepts || accepts(object))
  const takesField = !!definition && !!onDropField
  const live = !disabled && (takesObject || takesField)
  const shownHint = (takesObject ? hint : fieldHint) ?? 'Drop {name} here'
  const shownName = takesObject ? object!.name : definition?.field.name ?? ''
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
          {shownHint.replace('{name}', shownName)}
        </Typography>
      )}
    </Box>
  )
}

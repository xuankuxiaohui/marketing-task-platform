export function useNodePalette() {
  function onDragStart(event: DragEvent, type: string) {
    if (event.dataTransfer) {
      event.dataTransfer.effectAllowed = 'move'
      event.dataTransfer.setData('application/vueflow', type)
      event.dataTransfer.setData('text/plain', type)
    }
  }

  return { onDragStart }
}

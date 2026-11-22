export interface TreeNode {
  name: string
  path: string
  children: TreeNode[]
  file: boolean
}

/**
 * Builds a folder tree from file paths, folders first, then files, each sorted by name. Chains of
 * single-folder folders are joined ("src/main/java/com/example") as in most code editors.
 */
export function fileTree(paths: string[]): TreeNode[] {
  const root: TreeNode = { name: '', path: '', children: [], file: false }
  for (const path of paths) {
    let node = root
    const parts = path.split('/')
    parts.forEach((part, i) => {
      const isFile = i === parts.length - 1
      let child = node.children.find((c) => c.name === part && c.file === isFile)
      if (!child) {
        child = { name: part, path: parts.slice(0, i + 1).join('/'), children: [], file: isFile }
        node.children.push(child)
      }
      node = child
    })
  }
  const tidy = (node: TreeNode): TreeNode => {
    let current = { ...node, children: node.children.map(tidy) }
    while (!current.file && current.children.length === 1 && !current.children[0].file && current.name) {
      const only = current.children[0]
      current = { ...only, name: `${current.name}/${only.name}` }
    }
    current.children.sort((a, b) => (a.file === b.file ? a.name.localeCompare(b.name) : a.file ? 1 : -1))
    return current
  }
  return tidy(root).children
}

/** A pattern for where an endpoint's logic starts in its generated file. */
export function codeFocusPattern(path: string, endpointName: string): string {
  return path.includes('/route/') ? `\\.routeId\\("${endpointName}"\\)` : `^    public .* ${endpointName}\\(`
}

import type { Menu } from '@prisma/client'

export interface MenuNode extends Omit<Menu, 'createdAt' | 'updatedAt'> {
  children: MenuNode[]
}

export function buildMenuTree(menus: Menu[]): MenuNode[] {
  const map = new Map<number, MenuNode>()
  for (const m of menus) {
    map.set(m.id, {
      id: m.id,
      name: m.name,
      code: m.code,
      path: m.path,
      component: m.component,
      icon: m.icon,
      type: m.type,
      parentId: m.parentId,
      sort: m.sort,
      visible: m.visible,
      children: [],
    })
  }

  const roots: MenuNode[] = []
  for (const node of map.values()) {
    if (node.parentId === null) {
      roots.push(node)
    } else {
      const parent = map.get(node.parentId)
      if (parent) parent.children.push(node)
    }
  }

  const sort = (nodes: MenuNode[]) => {
    nodes.sort((a, b) => a.sort - b.sort)
    for (const n of nodes) sort(n.children)
  }
  sort(roots)
  return roots
}

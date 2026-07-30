import type { MenuDataItem } from '@ant-design/pro-components'
import type { MenuNode, FlatMenu } from '../types'

export function menuTreeToProLayout(menus: MenuNode[]): MenuDataItem[] {
  return menus
    .filter(m => m.type !== 3 && m.visible === 1)
    .map(m => ({
      name: m.name,
      path: m.path ?? `/_cat_${m.id}`,
      icon: m.icon ?? undefined,
      children: m.children.length ? menuTreeToProLayout(m.children) : undefined,
    }))
}

export function flatMenuToTree(menus: FlatMenu[]) {
  interface TreeNode { key: number; title: string; children: TreeNode[] }
  const map = new Map<number, TreeNode>()
  menus.forEach(m => map.set(m.id, { key: m.id, title: `${m.name}（${m.code}）`, children: [] }))
  const roots: TreeNode[] = []
  menus.forEach(m => {
    const node = map.get(m.id)!
    if (m.parentId === null) roots.push(node)
    else map.get(m.parentId)?.children.push(node)
  })
  return roots
}

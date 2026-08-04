import type { ItemType } from 'antd/es/menu/interface'
import type { FlatMenu, MenuNode } from '../types'

export function menuTreeToItems(menus: MenuNode[]): ItemType[] {
  return menus
    .filter(menu => menu.type !== 3 && menu.visible === 1)
    .map(menu => {
      const children = menuTreeToItems(menu.children)
      return {
        key: menu.path ?? `/_cat_${menu.id}`,
        label: menu.name,
        children: children.length ? children : undefined,
      }
    })
}

export function collectOpenKeys(menus: MenuNode[], pathname: string): string[] {
  for (const menu of menus) {
    if (menu.type === 3 || menu.visible !== 1) continue
    const key = menu.path ?? `/_cat_${menu.id}`
    if (key === pathname) return []
    const visibleChildren = menu.children.filter(child => child.type !== 3 && child.visible === 1)
    const childKeys = collectOpenKeys(visibleChildren, pathname)
    if (childKeys.length || visibleChildren.some(child => (child.path ?? `/_cat_${child.id}`) === pathname)) {
      return [key, ...childKeys]
    }
  }
  return []
}

export function flatMenuToTree(menus: FlatMenu[]) {
  interface TreeNode { key: number; title: string; children: TreeNode[] }
  const map = new Map<number, TreeNode>()
  menus.forEach(menu => map.set(menu.id, { key: menu.id, title: `${menu.name}（${menu.code}）`, children: [] }))
  const roots: TreeNode[] = []
  menus.forEach(menu => {
    const node = map.get(menu.id)!
    if (menu.parentId === null) roots.push(node)
    else map.get(menu.parentId)?.children.push(node)
  })
  return roots
}

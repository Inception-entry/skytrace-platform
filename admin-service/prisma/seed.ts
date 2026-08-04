import { PrismaClient } from '@prisma/client'
import * as bcrypt from 'bcryptjs'

const prisma = new PrismaClient()

const menus = [
  { name: '系统管理', code: 'system', type: 1, sort: 1, icon: 'SettingOutlined' },
  { name: '用户管理', code: 'user:list', path: '/admin/users', component: 'UserList', type: 2, sort: 1, icon: 'UserOutlined', parentCode: 'system' },
  { name: '新增用户', code: 'user:create', type: 3, sort: 1, parentCode: 'user:list' },
  { name: '编辑用户', code: 'user:update', type: 3, sort: 2, parentCode: 'user:list' },
  { name: '删除用户', code: 'user:delete', type: 3, sort: 3, parentCode: 'user:list' },
  { name: '分配角色', code: 'user:assign-roles', type: 3, sort: 4, parentCode: 'user:list' },
  { name: '角色管理', code: 'role:list', path: '/admin/roles', component: 'RoleList', type: 2, sort: 2, icon: 'TeamOutlined', parentCode: 'system' },
  { name: '新增角色', code: 'role:create', type: 3, sort: 1, parentCode: 'role:list' },
  { name: '编辑角色', code: 'role:update', type: 3, sort: 2, parentCode: 'role:list' },
  { name: '删除角色', code: 'role:delete', type: 3, sort: 3, parentCode: 'role:list' },
  { name: '分配权限', code: 'role:assign-menus', type: 3, sort: 4, parentCode: 'role:list' },
  { name: '菜单管理', code: 'menu:list', path: '/admin/menus', component: 'MenuList', type: 2, sort: 3, icon: 'MenuOutlined', parentCode: 'system' },
  { name: '新增菜单', code: 'menu:create', type: 3, sort: 1, parentCode: 'menu:list' },
  { name: '编辑菜单', code: 'menu:update', type: 3, sort: 2, parentCode: 'menu:list' },
  { name: '删除菜单', code: 'menu:delete', type: 3, sort: 3, parentCode: 'menu:list' },
  { name: '操作日志', code: 'log:list', path: '/admin/logs', component: 'LogList', type: 2, sort: 4, icon: 'FileSearchOutlined', parentCode: 'system' },
  { name: '清空日志', code: 'log:clear', type: 3, sort: 1, parentCode: 'log:list' },
]

async function main() {
  console.log('Seeding database...')

  const parents = menus.filter(m => !m.parentCode)
  const children = menus.filter(m => m.parentCode)

  for (const m of parents) {
    await prisma.menu.upsert({
      where: { code: m.code },
      update: {
        name: m.name,
        path: m.path ?? null,
        component: m.component ?? null,
        icon: m.icon ?? null,
        type: m.type,
        sort: m.sort,
      },
      create: { name: m.name, code: m.code, path: m.path ?? null, component: m.component ?? null, icon: m.icon ?? null, type: m.type, sort: m.sort },
    })
  }

  for (const m of children) {
    const parent = await prisma.menu.findUniqueOrThrow({ where: { code: m.parentCode! } })
    await prisma.menu.upsert({
      where: { code: m.code },
      update: {
        name: m.name,
        path: m.path ?? null,
        component: m.component ?? null,
        icon: m.icon ?? null,
        type: m.type,
        sort: m.sort,
        parentId: parent.id,
      },
      create: { name: m.name, code: m.code, path: m.path ?? null, component: m.component ?? null, icon: m.icon ?? null, type: m.type, sort: m.sort, parentId: parent.id },
    })
  }

  const role = await prisma.role.upsert({
    where: { code: 'super_admin' },
    update: {},
    create: { name: '超级管理员', code: 'super_admin', description: '拥有全部权限' },
  })

  const allMenus = await prisma.menu.findMany()
  for (const menu of allMenus) {
    await prisma.roleMenu.upsert({
      where: { roleId_menuId: { roleId: role.id, menuId: menu.id } },
      update: {},
      create: { roleId: role.id, menuId: menu.id },
    })
  }

  const admin = await prisma.user.upsert({
    where: { username: 'admin' },
    update: {},
    create: {
      username: 'admin',
      password: await bcrypt.hash('Admin@123', 10),
      nickname: '管理员',
      email: 'admin@example.com',
    },
  })

  await prisma.userRole.upsert({
    where: { userId_roleId: { userId: admin.id, roleId: role.id } },
    update: {},
    create: { userId: admin.id, roleId: role.id },
  })

  console.log('Seed complete. Default credentials: admin / Admin@123')
}

main()
  .catch(console.error)
  .finally(() => prisma.$disconnect())

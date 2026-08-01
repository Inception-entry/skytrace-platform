import { useRef, useState } from 'react'
import { ProTable, PageContainer, ModalForm, ProFormText, ProFormSelect } from '@ant-design/pro-components'
import type { ActionType, ProColumns } from '@ant-design/pro-components'
import { Button, Drawer, Tree, Spin, Popconfirm, message } from 'antd'
import type { DataNode } from 'antd/es/tree'
import { PlusOutlined } from '@ant-design/icons'
import * as rolesApi from '../api/roles'
import * as menusApi from '../api/menus'
import { flatMenuToTree } from '../utils/menu'
import type { Role } from '../types'

const statusMap = { 1: { text: '启用', status: 'Success' }, 0: { text: '禁用', status: 'Error' } }

export function RolesPage() {
  const actionRef = useRef<ActionType>()
  const [editRow, setEditRow] = useState<Role | null>(null)
  const [open, setOpen] = useState(false)
  const [assignRole, setAssignRole] = useState<Role | null>(null)
  const [menuTree, setMenuTree] = useState<DataNode[]>([])
  const [checkedKeys, setCheckedKeys] = useState<number[]>([])
  const [drawerLoading, setDrawerLoading] = useState(false)

  async function openAssign(role: Role) {
    setAssignRole(role)
    setDrawerLoading(true)
    const [flat, ids] = await Promise.all([menusApi.flat(), rolesApi.getMenuIds(role.id)])
    setMenuTree(flatMenuToTree(flat))
    setCheckedKeys(ids)
    setDrawerLoading(false)
  }

  const columns: ProColumns<Role>[] = [
    { dataIndex: 'id', title: 'ID', width: 60, search: false },
    { dataIndex: 'name', title: '角色名称' },
    { dataIndex: 'code', title: '角色编码', search: false },
    { dataIndex: 'description', title: '描述', search: false },
    { dataIndex: 'status', title: '状态', valueEnum: statusMap, search: false },
    {
      title: '操作', search: false, width: 180,
      render: (_, row) => (
        <>
          <Button type="link" size="small" onClick={() => { setEditRow(row); setOpen(true) }}>编辑</Button>
          <Button type="link" size="small" onClick={() => openAssign(row)}>分配权限</Button>
          <Popconfirm title="确认删除？" onConfirm={async () => { await rolesApi.remove(row.id); actionRef.current?.reload() }}>
            <Button type="link" size="small" danger>删除</Button>
          </Popconfirm>
        </>
      ),
    },
  ]

  return (
    <PageContainer title="角色管理">
      <ProTable<Role>
        rowKey="id"
        actionRef={actionRef}
        columns={columns}
        request={async params => {
          const res = await rolesApi.list({ page: params.current, pageSize: params.pageSize, name: params.name })
          return { data: res.data, total: res.total, success: true }
        }}
        toolBarRender={() => [
          <Button key="add" type="primary" icon={<PlusOutlined />} onClick={() => { setEditRow(null); setOpen(true) }}>
            新增
          </Button>,
        ]}
      />

      <ModalForm
        title={editRow ? '编辑角色' : '新增角色'}
        open={open}
        modalProps={{ destroyOnClose: true }}
        onOpenChange={setOpen}
        initialValues={editRow ?? { status: 1 }}
        onFinish={async values => {
          try {
            if (editRow) await rolesApi.update(editRow.id, values)
            else await rolesApi.create(values as Parameters<typeof rolesApi.create>[0])
            message.success('操作成功')
            actionRef.current?.reload()
            return true
          } catch {
            message.error('操作失败')
            return false
          }
        }}
      >
        {!editRow && <ProFormText name="code" label="角色编码" rules={[{ required: true }]} />}
        <ProFormText name="name" label="角色名称" rules={[{ required: true }]} />
        <ProFormText name="description" label="描述" />
        {editRow && <ProFormSelect name="status" label="状态" valueEnum={statusMap} />}
      </ModalForm>

      <Drawer
        title={`分配权限：${assignRole?.name ?? ''}`}
        open={!!assignRole}
        onClose={() => setAssignRole(null)}
        width={400}
        footer={
          <Button
            type="primary"
            onClick={async () => {
              if (!assignRole) return
              await rolesApi.assignMenus(assignRole.id, checkedKeys)
              message.success('权限保存成功')
              setAssignRole(null)
            }}
          >
            保 存
          </Button>
        }
      >
        {drawerLoading ? (
          <Spin />
        ) : (
          <Tree
            checkable
            treeData={menuTree}
            checkedKeys={checkedKeys}
            onCheck={keys => setCheckedKeys(Array.isArray(keys) ? (keys as number[]) : (keys.checked as number[]))}
          />
        )}
      </Drawer>
    </PageContainer>
  )
}

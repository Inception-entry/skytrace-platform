import { useEffect, useState } from 'react'
import { Button, Card, Drawer, Form, Input, Modal, Popconfirm, Select, Space, Spin, Table, Tag, Tree, message } from 'antd'
import type { DataNode } from 'antd/es/tree'
import type { ColumnsType } from 'antd/es/table'
import { PlusOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons'
import * as rolesApi from '../api/roles'
import * as menusApi from '../api/menus'
import { flatMenuToTree } from '../utils/menu'
import type { Role } from '../types'
import { PageHeader } from '../components/PageHeader'
import { usePermission } from '../hooks/usePermission'

const statusOptions = [
  { label: '启用', value: 1 },
  { label: '禁用', value: 0 },
]

export function RolesPage() {
  const { hasPermission } = usePermission()
  const [form] = Form.useForm()
  const [modalForm] = Form.useForm()
  const [loading, setLoading] = useState(false)
  const [open, setOpen] = useState(false)
  const [roles, setRoles] = useState<Role[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(10)
  const [filters, setFilters] = useState<{ name?: string }>({})
  const [editRow, setEditRow] = useState<Role | null>(null)
  const [assignRole, setAssignRole] = useState<Role | null>(null)
  const [menuTree, setMenuTree] = useState<DataNode[]>([])
  const [checkedKeys, setCheckedKeys] = useState<number[]>([])
  const [drawerLoading, setDrawerLoading] = useState(false)

  async function loadData(nextPage = page, nextPageSize = pageSize, nextFilters = filters) {
    setLoading(true)
    try {
      const res = await rolesApi.list({ page: nextPage, pageSize: nextPageSize, name: nextFilters.name })
      setRoles(res.data)
      setTotal(res.total)
      setPage(res.page)
      setPageSize(res.pageSize)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadData()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  function openModal(row: Role | null) {
    setEditRow(row)
    modalForm.resetFields()
    modalForm.setFieldsValue(row ?? { status: 1 })
    setOpen(true)
  }

  async function openAssign(role: Role) {
    setAssignRole(role)
    setDrawerLoading(true)
    const [flat, ids] = await Promise.all([menusApi.flat(), rolesApi.getMenuIds(role.id)])
    setMenuTree(flatMenuToTree(flat))
    setCheckedKeys(ids)
    setDrawerLoading(false)
  }

  async function handleSubmit() {
    const values = await modalForm.validateFields()
    try {
      if (editRow) await rolesApi.update(editRow.id, values)
      else await rolesApi.create(values)
      message.success('操作成功')
      setOpen(false)
      loadData()
    } catch {
      message.error('操作失败')
    }
  }

  const columns: ColumnsType<Role> = [
    { dataIndex: 'id', title: 'ID', width: 72 },
    { dataIndex: 'name', title: '角色名称' },
    { dataIndex: 'code', title: '角色编码' },
    { dataIndex: 'description', title: '描述', render: value => value || '-' },
    {
      dataIndex: 'status',
      title: '状态',
      render: value => <Tag color={value === 1 ? 'success' : 'error'}>{value === 1 ? '启用' : '禁用'}</Tag>,
    },
    {
      key: 'actions',
      title: '操作',
      width: 180,
      render: (_, row) => (
        <Space size={4}>
          {hasPermission('role:update') ? (
            <Button type="link" size="small" onClick={() => openModal(row)}>
              编辑
            </Button>
          ) : null}
          {hasPermission('role:assign-menus') ? (
            <Button type="link" size="small" onClick={() => openAssign(row)}>
              分配权限
            </Button>
          ) : null}
          {hasPermission('role:delete') ? (
            <Popconfirm
              title="确认删除？"
              onConfirm={async () => {
                try {
                  await rolesApi.remove(row.id)
                  loadData()
                } catch {
                  message.error('删除失败')
                }
              }}
            >
              <Button type="link" size="small" danger>
                删除
              </Button>
            </Popconfirm>
          ) : null}
        </Space>
      ),
    },
  ]

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <PageHeader title="角色管理" subTitle="管理角色定义与菜单权限分配" />

      <Card className="skytrace-panel">
        <Form
          form={form}
          layout="inline"
          onFinish={values => {
            const nextFilters = { name: values.name || undefined }
            setFilters(nextFilters)
            loadData(1, pageSize, nextFilters)
          }}
        >
          <Form.Item name="name" label="角色名称">
            <Input placeholder="搜索角色名称" allowClear />
          </Form.Item>
          <Form.Item>
            <Space>
              <Button type="primary" htmlType="submit" icon={<SearchOutlined />}>
                查询
              </Button>
              <Button
                icon={<ReloadOutlined />}
                onClick={() => {
                  form.resetFields()
                  const nextFilters = {}
                  setFilters(nextFilters)
                  loadData(1, pageSize, nextFilters)
                }}
              >
                重置
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Card>

      <Card
        className="skytrace-panel"
        title="角色列表"
        extra={
          hasPermission('role:create') ? (
            <Button type="primary" icon={<PlusOutlined />} onClick={() => openModal(null)}>
              新增
            </Button>
          ) : null
        }
      >
        <Table
          rowKey="id"
          loading={loading}
          dataSource={roles}
          columns={columns}
          pagination={{
            current: page,
            pageSize,
            total,
            showSizeChanger: true,
            onChange: (nextPage, nextPageSize) => loadData(nextPage, nextPageSize, filters),
          }}
        />
      </Card>

      <Modal title={editRow ? '编辑角色' : '新增角色'} open={open} onCancel={() => setOpen(false)} onOk={handleSubmit} destroyOnClose>
        <Form form={modalForm} layout="vertical" initialValues={{ status: 1 }}>
          {!editRow ? (
            <Form.Item name="code" label="角色编码" rules={[{ required: true, message: '请输入角色编码' }]}>
              <Input />
            </Form.Item>
          ) : null}
          <Form.Item name="name" label="角色名称" rules={[{ required: true, message: '请输入角色名称' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={3} />
          </Form.Item>
          {editRow ? (
            <Form.Item name="status" label="状态">
              <Select options={statusOptions} />
            </Form.Item>
          ) : null}
        </Form>
      </Modal>

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
    </Space>
  )
}

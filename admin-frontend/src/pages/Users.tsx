import { useEffect, useState } from 'react'
import { Button, Card, Drawer, Form, Input, Modal, Popconfirm, Select, Space, Table, Tag, message } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { PlusOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons'
import * as usersApi from '../api/users'
import * as rolesApi from '../api/roles'
import type { Role, User } from '../types'
import { PageHeader } from '../components/PageHeader'
import { usePermission } from '../hooks/usePermission'

const statusOptions = [
  { label: '启用', value: 1 },
  { label: '禁用', value: 0 },
]

export function UsersPage() {
  const { hasPermission } = usePermission()
  const [form] = Form.useForm()
  const [modalForm] = Form.useForm()
  const [loading, setLoading] = useState(false)
  const [open, setOpen] = useState(false)
  const [users, setUsers] = useState<User[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(10)
  const [filters, setFilters] = useState<{ username?: string }>({})
  const [editRow, setEditRow] = useState<User | null>(null)
  const [assignUser, setAssignUser] = useState<User | null>(null)
  const [allRoles, setAllRoles] = useState<Role[]>([])
  const [selectedRoleIds, setSelectedRoleIds] = useState<number[]>([])
  const [assignSaving, setAssignSaving] = useState(false)

  async function loadData(nextPage = page, nextPageSize = pageSize, nextFilters = filters) {
    setLoading(true)
    try {
      const res = await usersApi.list({ page: nextPage, pageSize: nextPageSize, username: nextFilters.username })
      setUsers(res.data)
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

  function openModal(row: User | null) {
    setEditRow(row)
    modalForm.resetFields()
    modalForm.setFieldsValue(row ?? { status: 1 })
    setOpen(true)
  }

  async function openAssign(row: User) {
    setAssignUser(row)
    const rolesRes = await rolesApi.list({ page: 1, pageSize: 200 })
    setAllRoles(rolesRes.data)
    setSelectedRoleIds(row.roles?.map(r => r.id) ?? [])
  }

  async function handleSubmit() {
    const values = await modalForm.validateFields()
    try {
      if (editRow) await usersApi.update(editRow.id, values)
      else await usersApi.create(values)
      message.success('操作成功')
      setOpen(false)
      loadData()
    } catch {
      message.error('操作失败')
    }
  }

  const columns: ColumnsType<User> = [
    { dataIndex: 'id', title: 'ID', width: 72 },
    { dataIndex: 'username', title: '用户名' },
    { dataIndex: 'nickname', title: '昵称', render: value => value || '-' },
    { dataIndex: 'email', title: '邮箱', render: value => value || '-' },
    {
      dataIndex: 'roles',
      title: '角色',
      render: (roles: User['roles']) =>
        roles?.length
          ? roles.map(r => <Tag key={r.id}>{r.name}</Tag>)
          : '-',
    },
    {
      dataIndex: 'status',
      title: '状态',
      render: value => <Tag color={value === 1 ? 'success' : 'error'}>{value === 1 ? '启用' : '禁用'}</Tag>,
    },
    { dataIndex: 'createdAt', title: '创建时间' },
    {
      key: 'actions',
      title: '操作',
      width: 220,
      render: (_, row) => (
        <Space size={4}>
          {hasPermission('user:update') ? (
            <Button type="link" size="small" onClick={() => openModal(row)}>
              编辑
            </Button>
          ) : null}
          {hasPermission('user:assign-roles') ? (
            <Button type="link" size="small" onClick={() => openAssign(row)}>
              分配角色
            </Button>
          ) : null}
          {hasPermission('user:delete') ? (
            <Popconfirm
              title="确认删除？"
              onConfirm={async () => {
                try {
                  await usersApi.remove(row.id)
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
      <PageHeader title="用户管理" subTitle="维护后台账号、资料信息与启用状态" />

      <Card className="skytrace-panel">
        <Form
          form={form}
          layout="inline"
          onFinish={values => {
            const nextFilters = { username: values.username || undefined }
            setFilters(nextFilters)
            setPage(1)
            loadData(1, pageSize, nextFilters)
          }}
        >
          <Form.Item name="username" label="用户名">
            <Input placeholder="搜索用户名" allowClear />
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
                  setPage(1)
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
        title="用户列表"
        extra={
          hasPermission('user:create') ? (
            <Button type="primary" icon={<PlusOutlined />} onClick={() => openModal(null)}>
              新增
            </Button>
          ) : null
        }
      >
        <Table
          rowKey="id"
          loading={loading}
          dataSource={users}
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

      <Modal
        title={editRow ? '编辑用户' : '新增用户'}
        open={open}
        onCancel={() => setOpen(false)}
        onOk={handleSubmit}
        destroyOnClose
      >
        <Form form={modalForm} layout="vertical" initialValues={{ status: 1 }}>
          {!editRow ? (
            <Form.Item name="username" label="用户名" rules={[{ required: true, message: '请输入用户名' }]}>
              <Input />
            </Form.Item>
          ) : null}
          <Form.Item
            name="password"
            label={editRow ? '新密码（留空不修改）' : '密码'}
            rules={editRow ? [] : [{ required: true, message: '请输入密码' }]}
          >
            <Input.Password />
          </Form.Item>
          <Form.Item name="nickname" label="昵称">
            <Input />
          </Form.Item>
          <Form.Item name="email" label="邮箱" rules={[{ type: 'email', message: '邮箱格式不正确' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="status" label="状态">
            <Select options={statusOptions} />
          </Form.Item>
        </Form>
      </Modal>

      <Drawer
        title={`分配角色：${assignUser?.username ?? ''}`}
        open={!!assignUser}
        onClose={() => setAssignUser(null)}
        width={400}
        footer={
          <Button
            type="primary"
            block
            loading={assignSaving}
            onClick={async () => {
              if (!assignUser) return
              setAssignSaving(true)
              try {
                await usersApi.assignRoles(assignUser.id, selectedRoleIds)
                message.success('角色已更新，对方需重新登录')
                setAssignUser(null)
                loadData()
              } catch {
                message.error('分配失败')
              } finally {
                setAssignSaving(false)
              }
            }}
          >
            保 存
          </Button>
        }
      >
        <Select
          mode="multiple"
          style={{ width: '100%' }}
          placeholder="选择角色"
          value={selectedRoleIds}
          onChange={setSelectedRoleIds}
          options={allRoles.map(r => ({ label: `${r.name}（${r.code}）`, value: r.id }))}
        />
      </Drawer>
    </Space>
  )
}

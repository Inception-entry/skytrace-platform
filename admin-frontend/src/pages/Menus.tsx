import { useEffect, useState } from 'react'
import { Button, Card, Form, Input, InputNumber, Modal, Popconfirm, Select, Space, Table, Tag, message } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { PlusOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons'
import * as menusApi from '../api/menus'
import type { FlatMenu } from '../types'
import { PageHeader } from '../components/PageHeader'
import { usePermission } from '../hooks/usePermission'

const typeMap = { 1: { text: '目录', color: 'blue' }, 2: { text: '菜单', color: 'green' }, 3: { text: '按钮', color: 'orange' } }
const visibleOptions = [
  { label: '显示', value: 1 },
  { label: '隐藏', value: 0 },
]
const typeOptions = [
  { label: '目录', value: 1 },
  { label: '菜单', value: 2 },
  { label: '按钮', value: 3 },
]

export function MenusPage() {
  const { hasPermission } = usePermission()
  const [form] = Form.useForm()
  const [modalForm] = Form.useForm()
  const [loading, setLoading] = useState(false)
  const [open, setOpen] = useState(false)
  const [menus, setMenus] = useState<FlatMenu[]>([])
  const [allMenus, setAllMenus] = useState<FlatMenu[]>([])
  const [editRow, setEditRow] = useState<FlatMenu | null>(null)
  const [filters, setFilters] = useState<{ name?: string }>({})

  async function loadData(nextFilters = filters) {
    setLoading(true)
    try {
      const data = await menusApi.flat()
      const filtered = nextFilters.name
        ? data.filter(menu => menu.name.toLowerCase().includes(nextFilters.name!.toLowerCase()))
        : data
      setMenus(filtered)
      setAllMenus(data)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadData()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  async function openModal(row: FlatMenu | null) {
    const data = await menusApi.flat()
    setAllMenus(data)
    setEditRow(row)
    modalForm.resetFields()
    modalForm.setFieldsValue(row ?? { type: 2, sort: 0, visible: 1 })
    setOpen(true)
  }

  async function handleSubmit() {
    const values = await modalForm.validateFields()
    try {
      if (editRow) await menusApi.update(editRow.id, values)
      else await menusApi.create(values)
      message.success('操作成功')
      setOpen(false)
      loadData()
    } catch {
      message.error('操作失败')
    }
  }

  const columns: ColumnsType<FlatMenu> = [
    { dataIndex: 'id', title: 'ID', width: 72 },
    { dataIndex: 'name', title: '名称' },
    { dataIndex: 'code', title: '权限码' },
    {
      dataIndex: 'type',
      title: '类型',
      render: value => {
        const item = typeMap[value as 1 | 2 | 3]
        return <Tag color={item?.color}>{item?.text}</Tag>
      },
    },
    { dataIndex: 'path', title: '路径', render: value => value || '-' },
    { dataIndex: 'sort', title: '排序', width: 90 },
    {
      dataIndex: 'visible',
      title: '可见',
      render: value => <Tag color={value === 1 ? 'success' : 'default'}>{value === 1 ? '显示' : '隐藏'}</Tag>,
    },
    {
      key: 'actions',
      title: '操作',
      width: 140,
      render: (_, row) => (
        <Space size={4}>
          {hasPermission('menu:update') ? (
            <Button type="link" size="small" onClick={() => openModal(row)}>
              编辑
            </Button>
          ) : null}
          {hasPermission('menu:delete') ? (
            <Popconfirm
              title="确认删除？"
              onConfirm={async () => {
                try {
                  await menusApi.remove(row.id)
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

  const parentOptions = allMenus
    .filter(menu => menu.type !== 3 && menu.id !== editRow?.id)
    .map(menu => ({ label: `${menu.name}（${menu.code}）`, value: menu.id }))

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <PageHeader title="菜单管理" subTitle="维护导航结构、权限码与前端路由元数据" />

      <Card className="skytrace-panel">
        <Form
          form={form}
          layout="inline"
          onFinish={values => {
            const nextFilters = { name: values.name || undefined }
            setFilters(nextFilters)
            loadData(nextFilters)
          }}
        >
          <Form.Item name="name" label="名称">
            <Input placeholder="搜索菜单名称" allowClear />
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
                  loadData(nextFilters)
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
        title="菜单列表"
        extra={
          hasPermission('menu:create') ? (
            <Button type="primary" icon={<PlusOutlined />} onClick={() => openModal(null)}>
              新增
            </Button>
          ) : null
        }
      >
        <Table rowKey="id" loading={loading} dataSource={menus} columns={columns} pagination={false} />
      </Card>

      <Modal title={editRow ? '编辑菜单' : '新增菜单'} open={open} onCancel={() => setOpen(false)} onOk={handleSubmit} destroyOnClose width={640}>
        <Form form={modalForm} layout="vertical" initialValues={{ type: 2, sort: 0, visible: 1 }}>
          <div className="skytrace-form-grid">
            <Form.Item name="name" label="名称" rules={[{ required: true, message: '请输入名称' }]}>
              <Input />
            </Form.Item>
            {!editRow ? (
              <Form.Item name="code" label="权限码" rules={[{ required: true, message: '请输入权限码' }]}>
                <Input />
              </Form.Item>
            ) : null}
            {!editRow ? (
              <Form.Item name="type" label="类型" rules={[{ required: true, message: '请选择类型' }]}>
                <Select options={typeOptions} />
              </Form.Item>
            ) : null}
            <Form.Item name="parentId" label="父节点">
              <Select options={parentOptions} allowClear />
            </Form.Item>
            <Form.Item name="path" label="路径">
              <Input />
            </Form.Item>
            <Form.Item name="component" label="组件">
              <Input />
            </Form.Item>
            <Form.Item name="icon" label="图标">
              <Input />
            </Form.Item>
            <Form.Item name="sort" label="排序">
              <InputNumber min={0} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="visible" label="可见">
              <Select options={visibleOptions} />
            </Form.Item>
          </div>
        </Form>
      </Modal>
    </Space>
  )
}

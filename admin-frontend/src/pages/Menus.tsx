import { useRef, useState } from 'react'
import { ProTable, PageContainer, ModalForm, ProFormText, ProFormSelect, ProFormDigit } from '@ant-design/pro-components'
import type { ActionType, ProColumns } from '@ant-design/pro-components'
import { Button, Tag, Popconfirm, message } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import * as menusApi from '../api/menus'
import type { FlatMenu } from '../types'

const typeMap = { 1: { text: '目录', color: 'blue' }, 2: { text: '菜单', color: 'green' }, 3: { text: '按钮', color: 'orange' } }
const visibleMap = { 1: { text: '显示', status: 'Success' }, 0: { text: '隐藏', status: 'Default' } }

export function MenusPage() {
  const actionRef = useRef<ActionType>()
  const [editRow, setEditRow] = useState<FlatMenu | null>(null)
  const [open, setOpen] = useState(false)
  const [allMenus, setAllMenus] = useState<FlatMenu[]>([])

  async function openForm(row: FlatMenu | null) {
    const list = await menusApi.flat()
    setAllMenus(list)
    setEditRow(row)
    setOpen(true)
  }

  const columns: ProColumns<FlatMenu>[] = [
    { dataIndex: 'id', title: 'ID', width: 60, search: false },
    { dataIndex: 'name', title: '名称' },
    { dataIndex: 'code', title: '权限码', search: false },
    {
      dataIndex: 'type', title: '类型', search: false,
      render: (_, row) => {
        const t = typeMap[row.type as 1 | 2 | 3]
        return <Tag color={t?.color}>{t?.text}</Tag>
      },
    },
    { dataIndex: 'path', title: '路径', search: false },
    { dataIndex: 'sort', title: '排序', search: false },
    { dataIndex: 'visible', title: '可见', valueEnum: visibleMap, search: false },
    {
      title: '操作', search: false, width: 120,
      render: (_, row) => (
        <>
          <Button type="link" size="small" onClick={() => openForm(row)}>编辑</Button>
          <Popconfirm title="确认删除？" onConfirm={async () => { await menusApi.remove(row.id); actionRef.current?.reload() }}>
            <Button type="link" size="small" danger>删除</Button>
          </Popconfirm>
        </>
      ),
    },
  ]

  const parentOptions = allMenus
    .filter(m => m.type !== 3)
    .map(m => ({ label: `${m.name}（${m.code}）`, value: m.id }))

  return (
    <PageContainer title="菜单管理">
      <ProTable<FlatMenu>
        rowKey="id"
        actionRef={actionRef}
        columns={columns}
        request={async () => {
          const data = await menusApi.flat()
          return { data, total: data.length, success: true }
        }}
        toolBarRender={() => [
          <Button key="add" type="primary" icon={<PlusOutlined />} onClick={() => openForm(null)}>
            新增
          </Button>,
        ]}
        pagination={false}
        search={false}
      />

      <ModalForm
        title={editRow ? '编辑菜单' : '新增菜单'}
        open={open}
        modalProps={{ destroyOnClose: true }}
        onOpenChange={setOpen}
        initialValues={editRow ?? { type: 2, sort: 0, visible: 1 }}
        onFinish={async values => {
          try {
            if (editRow) await menusApi.update(editRow.id, values)
            else await menusApi.create(values)
            message.success('操作成功')
            actionRef.current?.reload()
            return true
          } catch {
            message.error('操作失败')
            return false
          }
        }}
      >
        <ProFormText name="name" label="名称" rules={[{ required: true }]} />
        {!editRow && <ProFormText name="code" label="权限码" rules={[{ required: true }]} />}
        {!editRow && (
          <ProFormSelect
            name="type"
            label="类型"
            options={[{ label: '目录', value: 1 }, { label: '菜单', value: 2 }, { label: '按钮', value: 3 }]}
            rules={[{ required: true }]}
          />
        )}
        <ProFormSelect name="parentId" label="父节点" options={parentOptions} allowClear />
        <ProFormText name="path" label="路径" />
        <ProFormText name="component" label="组件" />
        <ProFormText name="icon" label="图标" />
        <ProFormDigit name="sort" label="排序" min={0} />
        <ProFormSelect name="visible" label="可见" options={[{ label: '显示', value: 1 }, { label: '隐藏', value: 0 }]} />
      </ModalForm>
    </PageContainer>
  )
}

import { useRef, useState } from 'react'
import { ProTable, PageContainer, ModalForm, ProFormText, ProFormSelect } from '@ant-design/pro-components'
import type { ActionType, ProColumns } from '@ant-design/pro-components'
import { Button, Popconfirm, message } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import * as usersApi from '../api/users'
import type { User } from '../types'

const statusMap = { 1: { text: '启用', status: 'Success' }, 0: { text: '禁用', status: 'Error' } }

export function UsersPage() {
  const actionRef = useRef<ActionType>()
  const [editRow, setEditRow] = useState<User | null>(null)
  const [open, setOpen] = useState(false)

  const columns: ProColumns<User>[] = [
    { dataIndex: 'id', title: 'ID', width: 60, search: false },
    { dataIndex: 'username', title: '用户名' },
    { dataIndex: 'nickname', title: '昵称', search: false },
    { dataIndex: 'email', title: '邮箱', search: false },
    { dataIndex: 'status', title: '状态', valueEnum: statusMap, search: false },
    { dataIndex: 'createdAt', title: '创建时间', search: false, valueType: 'dateTime' },
    {
      title: '操作', search: false, width: 120,
      render: (_, row) => (
        <>
          <Button type="link" size="small" onClick={() => { setEditRow(row); setOpen(true) }}>编辑</Button>
          <Popconfirm title="确认删除？" onConfirm={async () => { await usersApi.remove(row.id); actionRef.current?.reload() }}>
            <Button type="link" size="small" danger>删除</Button>
          </Popconfirm>
        </>
      ),
    },
  ]

  return (
    <PageContainer title="用户管理">
      <ProTable<User>
        rowKey="id"
        actionRef={actionRef}
        columns={columns}
        request={async params => {
          const res = await usersApi.list({ page: params.current, pageSize: params.pageSize, username: params.username })
          return { data: res.data, total: res.total, success: true }
        }}
        toolBarRender={() => [
          <Button key="add" type="primary" icon={<PlusOutlined />} onClick={() => { setEditRow(null); setOpen(true) }}>
            新增
          </Button>,
        ]}
      />

      <ModalForm
        title={editRow ? '编辑用户' : '新增用户'}
        open={open}
        modalProps={{ destroyOnClose: true }}
        onOpenChange={setOpen}
        initialValues={editRow ?? { status: 1 }}
        onFinish={async values => {
          try {
            if (editRow) await usersApi.update(editRow.id, values)
            else await usersApi.create(values)
            message.success('操作成功')
            actionRef.current?.reload()
            return true
          } catch {
            message.error('操作失败')
            return false
          }
        }}
      >
        {!editRow && (
          <ProFormText name="username" label="用户名" rules={[{ required: true }]} />
        )}
        <ProFormText.Password name="password" label={editRow ? '新密码（留空不修改）' : '密码'} rules={editRow ? [] : [{ required: true }]} />
        <ProFormText name="nickname" label="昵称" />
        <ProFormText name="email" label="邮箱" rules={[{ type: 'email' }]} />
        <ProFormSelect name="status" label="状态" valueEnum={statusMap} initialValue={1} />
      </ModalForm>
    </PageContainer>
  )
}

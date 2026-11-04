import client from './client'

export const uploadAvatar = async (
  file: File,
): Promise<{ objectKey: string; url: string }> => {
  const form = new FormData()
  form.append('file', file)
  const res = await client.post<{ objectKey: string; url: string }>(
    '/upload/avatar',
    form,
  )
  return res.data
}
